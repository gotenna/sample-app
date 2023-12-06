package com.gotenna.app.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gotenna.app.model.RadioListItem
import com.gotenna.app.ui.compose.mobyDickText
import com.gotenna.app.util.isConnected
import com.gotenna.app.util.isScannedOrDisconnected
import com.gotenna.app.util.replaceItem
import com.gotenna.app.util.toListItems
import com.gotenna.app.util.toMutableStateFlow
import com.gotenna.common.models.CommandMetaData
import com.gotenna.common.models.Coordinate
import com.gotenna.common.models.EncryptionParameters
import com.gotenna.common.models.FrequencyBandwidth
import com.gotenna.common.models.GMGroupMember
import com.gotenna.common.models.GTMessagePriority
import com.gotenna.common.models.GTMessageType
import com.gotenna.common.models.GotennaHeaderWrapper
import com.gotenna.common.models.GripResult
import com.gotenna.common.models.MapObject
import com.gotenna.common.models.MessageTypeWrapper
import com.gotenna.common.models.PowerLevel
import com.gotenna.common.models.SendToNetwork
import com.gotenna.radio.sdk.GotennaClient
import com.gotenna.radio.sdk.common.models.radio.ConnectionType
import com.gotenna.radio.sdk.common.models.radio.GidType
import com.gotenna.radio.sdk.common.models.radio.RadioModel
import com.gotenna.radio.sdk.common.models.radio.SendToRadio
import com.gotenna.radio.sdk.common.results.RadioResult
import com.gotenna.radio.sdk.common.results.executedOrNull
import com.gotenna.radio.sdk.common.results.getErrorOrNull
import com.gotenna.radio.sdk.common.results.isSuccess
import com.gotenna.radio.sdk.common.utils.GIDUtils
import com.gotenna.radio.sdk.legacy.sdk.firmware.GTFirmwareVersion
import com.gotenna.radio.sdk.legacy.sdk.frequency.GTBandwidth
import com.gotenna.radio.sdk.legacy.sdk.frequency.GTFrequencyChannel
import com.gotenna.radio.sdk.legacy.sdk.frequency.GTPowerLevel
import com.gotenna.radio.sdk.legacy.sdk.session.properties.Properties
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.Charset
import java.util.Date
import java.util.Random
import java.util.UUID
import kotlin.time.ExperimentalTime

@ExperimentalTime
class HomeViewModel : ViewModel() {
    private val _connectionType = MutableStateFlow(ConnectionType.USB)
    private val _radios = MutableStateFlow<List<RadioListItem>>(emptyList())
    // For UI logging.
    val logOutput = MutableStateFlow("Start of logs:\n\n")

    private val connectTypeIndex = _connectionType.mapLatest { it.ordinal }

    private val isConnectAvailable = _radios.mapLatest { list ->
        list.any { it.isSelected }
    }

    private val scannedRadiosCount = _radios.mapLatest { list ->
        list.count { it.radioModel.isScannedOrDisconnected() }
    }

    val connectedRadiosCount = _radios.mapLatest { list ->
        list.count { it.radioModel.isConnected() }
    }

    val radios = _radios.asStateFlow()
    val selectedRadio = MutableStateFlow<RadioModel?>(null)
    val gripFile = MutableStateFlow<SendToNetwork.GripFile?>(null)
    var gidNumber: Long = 0L

    private val _radioModels = MutableStateFlow<List<RadioModel>>(emptyList())
    val radioModels = _radioModels.asStateFlow()
    private var groupGid: Long = -1

    private val _isSelectAll = _radios.mapLatest { list ->
        val notConnectedRadios = list.filter {
            !it.radioModel.isConnected()
        }
        notConnectedRadios.all { it.isSelected }
    }.toMutableStateFlow(viewModelScope, false)

    private val isSelectAll = _isSelectAll.asStateFlow()

    private val _isUpdatingFirmware = MutableStateFlow(false)
    val isUpdatingFirmware = _isUpdatingFirmware.asStateFlow()

    private lateinit var updateFirmwareJob: Job

    init {
        viewModelScope.launch(Dispatchers.IO) {

            // Observe connected radios.
            GotennaClient.observeRadios().collect { radios ->
                _radioModels.update {
                    radios
                }
                _radios.update {
                    radios.toListItems()
                }
                // TODO a stop gap for now to not recreate several jobs each time these are updated
                if (radios.isNotEmpty()) {
                    // For each connected radio observe their connection state.
                    radios.forEach { radio ->
                        logOutput.update { it + "Device: ${radio.serialNumber} gid: ${radio.personalGid}\n\n" }
                        launch {
                            radio.observeState().collect { state ->
                                logOutput.update { it + "New device state is: $state for device: ${radio.serialNumber} ${Date()}\n\n" }
                            }
                        }

                        testing()

                        // Observe messages from a radio.
                        launch {
                            radio.receive.collect { command ->
                                when (val executed = command.executedOrNull()) {
                                    null -> {
                                        logOutput.update { it + "Incoming command for device: ${radio.serialNumber} is failure: ${command.getErrorOrNull()} for device ${radio.serialNumber} ${Date()}\n\n" }
                                    }
                                    is SendToNetwork.AnyNetworkMessage -> {
                                        logOutput.update {
                                            it + "Incoming command for any message device: ${radio.serialNumber} is success: ${
                                                command.executedOrNull()
                                            }\n\n"
                                        }
                                    }
                                    is SendToNetwork.GripFile -> {
                                        when (executed.gripResult) {
                                            is GripResult.GripFullData -> {
                                                logOutput.update {
                                                    it + "Grip full file delivered: ${Date()}\ndata: ${executed.gripResult}\n"
                                                }
                                            }
                                            else -> {
                                                logOutput.update {
                                                    it + "Grip full file delivered: ${Date()}\ndata: ${executed.gripResult}\n"
                                                }
                                            }
                                        }
                                    }
                                    is SendToNetwork.Group -> {
                                        if (executed.isInvite) {
                                            groupGid = executed.groupGid
                                            logOutput.update { it + "Received a group invitation setting :$groupGid on this radio" }
                                            setGroupGid(groupGid)
                                        }
                                    }
                                    is SendToRadio.FirmwareUpdate -> {
                                        when (executed.firmwareUpdateStatus) {
                                            is SendToRadio.FirmwareUpdateState.Started -> {
                                                logOutput.update { it + "Firmware update started at: ${Date()} for device ${radio.serialNumber}\n\n" }
                                            }
                                            is SendToRadio.FirmwareUpdateState.FinalizingUpdate -> {
                                                logOutput.update { it + "Firmware update finalizing at: ${Date()} for device ${radio.serialNumber}\n\n" }
                                            }
                                            is SendToRadio.FirmwareUpdateState.CompletedSuccessfully -> {
                                               logOutput.update { it + "Firmware update compelted at: ${Date()} for device ${radio.serialNumber}\n\n" }
                                            }
                                            is SendToRadio.FirmwareUpdateState.InProgress -> {
                                                //logOutput.update { it + "Firmware update progress ${firmwareUpdateState.progressPercent}\n" }
                                            }
                                        }
                                    }
                                    is SendToRadio.DeviceInfo -> {
                                        // ignore for now
                                    }
                                    else -> {
                                        logOutput.update { it + "Incoming command for device: ${radio.serialNumber} is success: ${command.executedOrNull()}\n\n" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun testing() {
        /*launch {
            logOutput.update { it + "Starting to run user simulated traffic. ${Date()}\n" }
            // adding this to simulate some of the behavior the device has by a client
            while (radio.isConnected()) {
                sendLocation(privateMessage = false, radio = radio)
                delay(TimeUnit.SECONDS.toMillis(15))
            }
        }
        launch {
            logOutput.update { it + "starting channel scans ${Date()}\n\n" }
            while (radio.isConnected()) {
                scanChannels()
                delay(150)
                getChannelData()
                delay(TimeUnit.MINUTES.toMillis(20))
            }
        }
        launch {
            while (radio.isConnected()) {
                getDeviceInfo()
                delay(100)
            }
        }
        launch {
            delay(20000)
            logOutput.update { it + "starting grip transmissions" }
            while (radio.isConnected()) {
                sendFile2(gidNumber, mobyDickText.toByteArray(Charset.defaultCharset()))
                delay(TimeUnit.SECONDS.toMillis(30))
            }
        }*/
    }


    fun disconnectAllRadiosAndUpdateConnectionType(index: Int) {
        _radios.value.forEach {
            disconnectRadio(it)
        }

        _radios.update { emptyList() }

        _connectionType.update { ConnectionType.values()[index] }
    }

    fun updateRadiosOnSectionChange(listItem: RadioListItem) {
        val newListItem = listItem.copy(isSelected = !listItem.isSelected)

        _radios.replaceItem(listItem, newListItem)
    }

    fun setSelectedRadio(listItem: RadioListItem) {
        if (listItem.radioModel.isConnected()) {
            selectedRadio.update {
                listItem.radioModel
            }
        }
    }

    fun scanRadios() = viewModelScope.launch(Dispatchers.IO) {
        _radios.value.forEach { disconnectRadio(it) }
        _radios.update { GotennaClient.scan(_connectionType.value).toListItems() }
    }

    fun connectRadios() = viewModelScope.launch(Dispatchers.IO) {
        _radios.value.filter { it.isSelected }.forEach {
            launch {
                val result = it.radioModel.connect()
            }
        }
    }

    fun disconnectRadio(radioListItem: RadioListItem) = viewModelScope.launch {
        val radio = radioListItem.radioModel

        if (radio.isConnected()) {
            radio.disconnect()
        }
    }

    private fun updateIsSelectAll() = viewModelScope.launch(Dispatchers.IO) {
        _isSelectAll.update {
            val isChecked = !it
            val newList = _radios.value.map { listItem ->
                listItem.copy(isSelected = isChecked)
            }

            _radios.update { newList }

            isChecked
        }
    }

    @Composable
    fun toState() = HomeState(
        connectionTypeIndex = connectTypeIndex.collectAsState(initial = 0),
        isConnectAvailable = isConnectAvailable.collectAsState(initial = false),
        radios = radios.collectAsState(),
        isSelectAll = isSelectAll.collectAsState(),
        connectionTypeChangeAction = { disconnectAllRadiosAndUpdateConnectionType(it) },
        radioClickAction = { updateRadiosOnSectionChange(it) },
        radioLongClickAction = { disconnectRadio(it) },
        connectRadiosAction = { connectRadios() },
        scanRadiosAction = { scanRadios() },
        selectAllCheckAction = { updateIsSelectAll() },
        scannedRadiosCount = scannedRadiosCount.collectAsState(initial = 0),
        connectedRadiosCount = connectedRadiosCount.collectAsState(initial = 0)
    )

    // TODO these probably go in another viewmodel or need to get the radio from the existing list

    fun setSdkToken(tokenValue: String) = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.setSdkToken(tokenValue)
        val output = if (result?.isSuccess() == true) {
            "Success set sdk token returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure set sdk token returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun setGid(gid: Long, type: GidType) = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.setGid(gid, type)
        val output = if (result?.isSuccess() == true) {
            "Success set gid returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure set gid returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun deleteGid(gid: Long, type: GidType) = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.deleteGid(gid, type)
        val output = if (result?.isSuccess() == true) {
            "Success delete gid returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure delete gid returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun setPowerAndBandwidth(power: GTPowerLevel, bandwidth: GTBandwidth) = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.setPowerAndBandwidth(power, bandwidth)
        val output = if (result?.isSuccess() == true) {
            "Success set power/bandwidth returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure set power/bandwidth returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }
    
    fun setOperationMode(mode: Properties.GTOperationMode) = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.setOperationMode(mode)
        val output = if (result?.isSuccess() == true) {
            "Success set operation mode to $mode returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure set operation mode to $mode returned data is ${result?.getErrorOrNull()}\n\n"
        }
        logOutput.update { it + output }
    }

    fun getPowerAndBandwidth() = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.getPowerAndBandwidth()
        val output = if (result?.isSuccess() == true) {
            "Success get power/bandwidth returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure get power/bandwidth returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun getDeviceInfo() = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.getLatestRadioInfo()
        val output = if (result?.isSuccess() == true) {
            "Success get device info returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure get device info returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun performLedBlink() = viewModelScope.launch(Dispatchers.IO) {
        val tasks = mutableListOf<Deferred<RadioResult<SendToRadio.PerformLedBlink>>>()
        radioModels.value.forEach {
            tasks.add(
                async {
                    it.performLedBlink()
                    /*val output = if (result.isSuccess()) {
                        "Success led blink device: ${it.serialNumber} returned data is ${result.executedOrNull()}\n\n"
                    } else {
                        "Failure led blink device: ${it.serialNumber} returned data is ${result.getErrorOrNull()}\n\n"
                    }
                    logOutput.update { it + output }*/
                }
            )

        }
        tasks.awaitAll()
    }

    fun setFrequencyChannels(channels: List<GTFrequencyChannel>) = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.setFrequencyChannels(channels)
        val output = if (result?.isSuccess() == true) {
            "Success set frequency returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure set frequency returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun getFrequencyChannels() = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.getFrequencyChannels()
        val output = if (result?.isSuccess() == true) {
            "Success get frequency returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure get frequency returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun sendLocation(privateMessage: Boolean, gidNumber: String = "0", radio: RadioModel? = null) = viewModelScope.launch(Dispatchers.IO) {
        val data = SendToNetwork.Location(
            how = "m-g",
            staleTime = 300,
            lat = 28.375301,
            long = -81.549396,
            altitude = 10000.0,
            team = "WHITE",
            accuracy = 13,
            creationTime = Date().toInstant().toEpochMilli(),
            commandMetaData = CommandMetaData(
                messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                destinationGid = gidNumber.toLong(),
                senderGid = radioModels.firstOrNull()?.firstOrNull()?.personalGid ?: 123
            ),
            commandHeader = GotennaHeaderWrapper(
                uuid = UUID.randomUUID().toString(),
                senderGid = radioModels.firstOrNull()?.firstOrNull()?.personalGid ?: 1234,
                senderCallsign = "Test",
                messageTypeWrapper = MessageTypeWrapper.LOCATION,
                appCode = 123,
                senderUUID = "ANDROID-253d2e0c5acb0ef5",
                recipientUUID = UUID.randomUUID().toString(),
                encryptionParameters = null
            ),
        )
//            val byteData = Integer.valueOf(data.bytes.copyOfRange(3, 4).toHexString(), 16)

//            logOutput.update { it + "sending location object with sequence number of: $byteData\n" }

        val result = radioModels.firstOrNull()?.firstOrNull()?.send(
            data
        )

        val output = if (result?.isSuccess() == true) {
            "Success send private location returned data is ${result.executedOrNull()}\n${Date()}\n\n"
        } else {
            "Failure send private location returned data is ${result?.getErrorOrNull()}\n${Date()}\n\n"
        }

        logOutput.update { it + output }
    }

    /**
     * For grip to work correctly the following is required to be used, destination gid, sender gid, messagetype = private in the metadata.
     * If using broadcast it is recommended to change the usegrip flag to false.
     * The result of a grip transfer should be radioresult.success<gripfile> with the included grip result, with the failure being returned as
     * radioresult.failure.
     */
    fun sendAnyMessage(privateMessage: Boolean, gidNumber: String = "0") = viewModelScope.launch(Dispatchers.IO) {
        coroutineScope {
            awaitAll(async {
                val testMessage = "Test".toByteArray(Charset.defaultCharset())
                val result = selectedRadio.value?.send(
                    SendToNetwork.AnyNetworkMessage(
                        data = testMessage,
                        commandMetaData = CommandMetaData(
                            messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                            destinationGid = gidNumber.toLong(),
                            senderGid = selectedRadio.value?.personalGid ?: 0
                        ), commandHeader = GotennaHeaderWrapper(
                            uuid = UUID.randomUUID().toString(),
                            senderGid = selectedRadio.value?.personalGid ?: 0,
                            senderCallsign = "Test",
                            messageTypeWrapper = MessageTypeWrapper.ANY_MESSAGE,
                            appCode = 123,
                            recipientUUID = UUID.randomUUID().toString(),
                            senderUUID = UUID.randomUUID().toString(),
                            encryptionParameters = EncryptionParameters(
                                UUID.randomUUID().toString(), "iv".toByteArray(
                                    Charset.defaultCharset()
                                )
                            ),
                        )
                    )
                )
                val output = if (result?.isSuccess() == true) {
                    "Success send grip any message returned data is ${result.executedOrNull()}\n\n"
                } else {
                    "Failure send grip any message returned data is ${result?.getErrorOrNull()}\n\n"
                }

                logOutput.update { it + output }
            },
                async {
                    val testMessage = "Test2".toByteArray(Charset.defaultCharset())
                    val result = selectedRadio.value?.send(
                        SendToNetwork.AnyNetworkMessage(
                            data = testMessage,
                            commandMetaData = CommandMetaData(
                                messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                                destinationGid = gidNumber.toLong(),
                                senderGid = selectedRadio.value?.personalGid ?: 0
                            ), commandHeader = GotennaHeaderWrapper(
                                uuid = UUID.randomUUID().toString(),
                                senderGid = selectedRadio.value?.personalGid ?: 0,
                                senderCallsign = "Test",
                                messageTypeWrapper = MessageTypeWrapper.ANY_MESSAGE,
                                appCode = 456,
                                recipientUUID = UUID.randomUUID().toString(),
                                senderUUID = UUID.randomUUID().toString(),
                                encryptionParameters = EncryptionParameters(
                                    UUID.randomUUID().toString(), "iv".toByteArray(
                                        Charset.defaultCharset()
                                    )
                                ),
                            )
                        )
                    )
                    val output = if (result?.isSuccess() == true) {
                        "Success send grip any message returned data is ${result.executedOrNull()}\n\n"
                    } else {
                        "Failure send grip any message returned data is ${result?.getErrorOrNull()}\n\n"
                    }
                },
                async {
                    val testMessage = "Test3".toByteArray(Charset.defaultCharset())
                    val result = selectedRadio.value?.send(
                        SendToNetwork.AnyNetworkMessage(
                            data = testMessage,
                            commandMetaData = CommandMetaData(
                                messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                                destinationGid = gidNumber.toLong(),
                                senderGid = selectedRadio.value?.personalGid ?: 0
                            ), commandHeader = GotennaHeaderWrapper(
                                uuid = UUID.randomUUID().toString(),
                                senderGid = selectedRadio.value?.personalGid ?: 0,
                                senderCallsign = "Test",
                                messageTypeWrapper = MessageTypeWrapper.ANY_MESSAGE,
                                appCode = 789,
                                recipientUUID = UUID.randomUUID().toString(),
                                senderUUID = UUID.randomUUID().toString(),
                                encryptionParameters = EncryptionParameters(
                                    UUID.randomUUID().toString(), "iv".toByteArray(
                                        Charset.defaultCharset()
                                    )
                                ),
                            )
                        )
                    )
                    val output = if (result?.isSuccess() == true) {
                        "Success send grip any message returned data is ${result.executedOrNull()}\n\n"
                    } else {
                        "Failure send grip any message returned data is ${result?.getErrorOrNull()}\n\n"
                    }
                },
                async {
                    val testMessage =
                        List(2000) { ('a'..'z').random() }.joinToString("").toByteArray(
                            Charset.defaultCharset()
                        )
                    val result = selectedRadio.value?.send(
                        SendToNetwork.AnyNetworkMessage(
                            data = testMessage,
                            commandMetaData = CommandMetaData(
                                messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                                destinationGid = gidNumber.toLong(),
                                senderGid = selectedRadio.value?.personalGid ?: 0
                            ), commandHeader = GotennaHeaderWrapper(
                                uuid = UUID.randomUUID().toString(),
                                senderGid = selectedRadio.value?.personalGid ?: 0,
                                senderCallsign = "Test",
                                messageTypeWrapper = MessageTypeWrapper.ANY_MESSAGE,
                                appCode = 987,
                                recipientUUID = UUID.randomUUID().toString(),
                                senderUUID = UUID.randomUUID().toString(),
                                encryptionParameters = EncryptionParameters(
                                    UUID.randomUUID().toString(), "iv".toByteArray(
                                        Charset.defaultCharset()
                                    )
                                ),
                            )
                        )
                    )
                    val output = if (result?.isSuccess() == true) {
                        "Success send grip any message returned data is ${result.executedOrNull()}\n\n"
                    } else {
                        "Failure send grip any message returned data is ${result?.getErrorOrNull()}\n\n"
                    }
                },
                async {
                    val testMessage = mobyDickText.toByteArray(Charset.defaultCharset())
                    val result = selectedRadio.value?.send(
                        SendToNetwork.AnyNetworkMessage(
                            data = testMessage,
                            commandMetaData = CommandMetaData(
                                messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                                destinationGid = gidNumber.toLong(),
                                senderGid = selectedRadio.value?.personalGid ?: 0
                            ), commandHeader = GotennaHeaderWrapper(
                                uuid = UUID.randomUUID().toString(),
                                senderGid = selectedRadio.value?.personalGid ?: 0,
                                senderCallsign = "Test",
                                messageTypeWrapper = MessageTypeWrapper.ANY_MESSAGE,
                                appCode = 654,
                                recipientUUID = UUID.randomUUID().toString(),
                                senderUUID = UUID.randomUUID().toString(),
                                encryptionParameters = EncryptionParameters(
                                    UUID.randomUUID().toString(), "iv".toByteArray(
                                        Charset.defaultCharset()
                                    )
                                ),
                            )
                        )
                    )
                    val output = if (result?.isSuccess() == true) {
                        "Success send grip any message returned data is ${result.executedOrNull()}\n\n"
                    } else {
                        "Failure send grip any message returned data is ${result?.getErrorOrNull()}\n\n"
                    }
                })
        }
    }


    fun sendFrontHaul(privateMessage: Boolean, gidNumber: String = "0") {
        sendChat(privateMessage, gidNumber)
    }

    fun sendGroup(privateMessage: Boolean, gidNumber: String = "0") = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.send(
            SendToNetwork.Group(
                groupGid = GIDUtils.generateRandomizedPersonalGID(),
                title = "test group",
                members = listOf(
                    GMGroupMember(uid = UUID.randomUUID().toString(), callSign = "test1"),
                    GMGroupMember(uid = UUID.randomUUID().toString(), callSign = "test2")
                ),
                commandMetaData = CommandMetaData(
                    messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                    destinationGid = gidNumber.toLong(),
                    senderGid = selectedRadio.value?.personalGid ?: 0
                ), commandHeader = GotennaHeaderWrapper(
                    uuid = UUID.randomUUID().toString(),
                    senderGid = selectedRadio.value?.personalGid ?: 0,
                    senderCallsign = "Test",
                    messageTypeWrapper = if (privateMessage) MessageTypeWrapper.GROUP_INVITE else MessageTypeWrapper.GROUP_UPDATE,
                    appCode = 123,
                )
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Success send private location returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send private location returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun sendFrequency(privateMessage: Boolean, gidNumber: String = "0") = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.send(
            SendToNetwork.SharedFrequency(
                uuid = UUID.randomUUID().toString(),
                name = "frequency title",
                powerSetting = PowerLevel.HALF_WATT,
                bandwidthSetting = FrequencyBandwidth.BW_7_28KHZ,
                useOnly = true,
                frequencyChannels = listOf(
                    SendToNetwork.SharedFrequency.FrequencyChannel(
                        149000000,
                        false
                    ), SendToNetwork.SharedFrequency.FrequencyChannel(159000000, true)
                ),
                commandMetaData = CommandMetaData(
                    messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                    destinationGid = gidNumber.toLong(),
                    senderGid = selectedRadio.value?.personalGid ?: 0
                ), commandHeader = GotennaHeaderWrapper(
                    uuid = UUID.randomUUID().toString(),
                    senderGid = 1234,
                    senderCallsign = "Test",
                    messageTypeWrapper = MessageTypeWrapper.FREQUENCY,
                    appCode = 123,
                )
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Success send frequency returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send frequency returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun sendEncryptionKeyExchange(privateMessage: Boolean, gidNumber: String = "0") = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.send(
            SendToNetwork.EncryptionKeyExchangeData(
                commandMetaData = CommandMetaData(
                    messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                    destinationGid = gidNumber.toLong(),
                    senderGid = selectedRadio.value?.personalGid ?: 0,
                ),
                commandHeader = GotennaHeaderWrapper(
                    uuid = UUID.randomUUID().toString(),
                    senderGid = 1234,
                    senderCallsign = "Test",
                    messageTypeWrapper = MessageTypeWrapper.ENCRYPTION_KEY,
                    appCode = 123,
                ),
                name = byteArrayOf(8, 108, 101, 104, 32, 32, 32, 32, 32, 32),
                uuidCounter = byteArrayOf(0),
                salt = byteArrayOf(
                    -59,
                    -118,
                    -32,
                    126,
                    -95,
                    -99,
                    -10,
                    19,
                    -44,
                    -73,
                    -2,
                    -62,
                    -115,
                    35,
                    69,
                    -55
                ),
                initializationVectorCounter = byteArrayOf(0, 0, 0, 0),
                keydata = byteArrayOf(
                    -86,
                    98,
                    80,
                    -55,
                    110,
                    -97,
                    125,
                    -40,
                    -37,
                    -69,
                    0,
                    116,
                    -60,
                    12,
                    -33,
                    102,
                    -110,
                    -109,
                    21,
                    -6,
                    118,
                    -103,
                    -105,
                    23,
                    -53,
                    24,
                    -10,
                    -113,
                    -12,
                    80,
                    -70,
                    -56,
                    -78,
                    -113,
                    19,
                    88,
                    -61,
                    113,
                    -39,
                    111,
                    -1,
                    -15,
                    42,
                    26,
                    73,
                    22,
                    44,
                    -20,
                    114,
                    -13,
                    8,
                    121,
                    126,
                    -117,
                    102,
                    -100,
                    -117,
                    -86,
                    89,
                    32,
                    95,
                    102,
                    42,
                    91,
                    -53,
                    -30,
                    -120,
                    -101,
                    -29,
                    -76,
                    114,
                    -29,
                    -27,
                    21,
                    -30,
                    -120,
                    73,
                    -114,
                    -58,
                    -119
                ),
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Success send private location returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send private location returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun sendRoute(privateMessage: Boolean, gidNumber: String = "0") = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.send(
            MapObject(
                commandMetaData = CommandMetaData(
                    messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                    destinationGid = gidNumber.toLong(),
                    senderGid = selectedRadio.value?.personalGid ?: 0,
                ),
                commandHeader = GotennaHeaderWrapper(
                    uuid = UUID.randomUUID().toString(),
                    senderGid = 1234,
                    senderCallsign = "Test",
                    messageTypeWrapper = MessageTypeWrapper.MAP_OBJECT,
                    appCode = 123,
                ),
                title = "route name",
                how = "m-g",
                data = MapObject.ObjectData.Route(
                    method = 1,
                    direction = 2,
                    type = 3,
                    order = 4,
                    strokeColor = 5,
                    points = listOf(
                        MapObject.ObjectData.Route.RoutePoint(
                            coordinates = Coordinate(
                                lat = Random().nextDouble(),
                                long = Random().nextDouble()
                            ),
                            positionInRoute = 0,
                            isWaypoint = false
                        ), MapObject.ObjectData.Route.RoutePoint(
                            coordinates = Coordinate(
                                lat = Random().nextDouble(),
                                long = Random().nextDouble()
                            ),
                            positionInRoute = 1,
                            isWaypoint = true
                        )
                    ),
                )
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Success send private location returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send private location returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun sendCircle(privateMessage: Boolean, gidNumber: String = "0") = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.send(
            MapObject(
                commandMetaData = CommandMetaData(
                    messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                    destinationGid = gidNumber.toLong(),
                    senderGid = selectedRadio.value?.personalGid ?: 0,
                ),
                commandHeader = GotennaHeaderWrapper(
                    uuid = UUID.randomUUID().toString(),
                    senderGid = 1234,
                    senderCallsign = "Test",
                    messageTypeWrapper = MessageTypeWrapper.MAP_OBJECT,
                    appCode = 123,
                ),
                title = "circle name",
                how = "m-g",
                data = MapObject.ObjectData.Circle(
                    radius = Random().nextDouble(),
                    centerPoint = Coordinate(
                        lat = Random().nextDouble(),
                        long = Random().nextDouble()
                    ),
                    rings = 1,
                    strokeColor = 3,
                    fillColor = 4,
                    geoFence = MapObject.GeoFence(
                        trigger = MapObject.GeoFenceTrigger.ENTRY,
                        monitorType = MapObject.GeoFenceMonitorType.FRIENDLY,
                        geoFenceRange = 1,
                        geoFenceMinElevation = 10,
                        geoFenceMaxElevation = 100
                    )
                )
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Success send private location returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send private location returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun sendShape(privateMessage: Boolean, gidNumber: String = "0") = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.send(
            MapObject(
                commandMetaData = CommandMetaData(
                    messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                    destinationGid = gidNumber.toLong(),
                    senderGid = selectedRadio.value?.personalGid ?: 0,
                ),
                commandHeader = GotennaHeaderWrapper(
                    uuid = UUID.randomUUID().toString(),
                    senderGid = 1234,
                    senderCallsign = "Test",
                    messageTypeWrapper = MessageTypeWrapper.MAP_OBJECT,
                    appCode = 123,
                ),
                title = "test shape",
                how = "h-g",
                data = MapObject.ObjectData.Shape(
                    points = listOf(
                        Coordinate(Random().nextDouble(), Random().nextDouble()),
                        Coordinate(Random().nextDouble(), Random().nextDouble()),
                        Coordinate(Random().nextDouble(), Random().nextDouble())
                    ),
                    fillColor = 123,
                    strokeColor = 123,
                    isClosed = true,
                    geoFence = MapObject.GeoFence(
                        trigger = MapObject.GeoFenceTrigger.ENTRY,
                        monitorType = MapObject.GeoFenceMonitorType.ALL,
                        geoFenceRange = 50,
                        geoFenceMinElevation = 100,
                        geoFenceMaxElevation = 1000
                    ),
                    strokeStyle = 1
                )
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Success send private location returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send private location returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun sendChat(privateMessage: Boolean, gidNumber: String = "0") = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.send(
            SendToNetwork.ChatMessage(
                commandMetaData = CommandMetaData(
                    messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                    destinationGid = gidNumber.toLong(),
                    senderGid = selectedRadio.value?.personalGid ?: 0
                ),
                commandHeader = GotennaHeaderWrapper(
                    uuid = UUID.randomUUID().toString(),
                    senderGid = selectedRadio.value?.personalGid ?: 0,
                    senderCallsign = "Test",
                    messageTypeWrapper = MessageTypeWrapper.CHAT_MESSAGE,
                    appCode = 123,
                    recipientUUID = UUID.randomUUID().toString(),
                    senderUUID = UUID.randomUUID().toString(),
                    encryptionParameters = null,
                ),
                text = "test chat",
                chatId = 12345,
                conversationId = UUID.randomUUID().toString(),
                conversationName = "blah",
                chatMessageId = UUID.randomUUID().toString()
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Success send private location returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send private location returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun sendMapItem(privateMessage: Boolean, gidNumber: String = "0") = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.send(
            MapObject(
                commandMetaData = CommandMetaData(
                    messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                    destinationGid = gidNumber.toLong(),
                    senderGid = selectedRadio.value?.personalGid ?: 0,
                ),
                commandHeader = GotennaHeaderWrapper(
                    uuid = UUID.randomUUID().toString(),
                    senderGid = 1234,
                    senderCallsign = "Test",
                    messageTypeWrapper = MessageTypeWrapper.MAP_OBJECT,
                    appCode = 123,
                ),
                how = "m-g",
                title = "map pin",
                data = MapObject.ObjectData.Pin(
                    coordinate = Coordinate(
                        lat = Random().nextDouble(),
                        long = Random().nextDouble(),
                        altitude = Random().nextDouble()
                    ),
                    height = Random().nextDouble(),
                    locationError = Random().nextDouble(),
                    type = "a-f-g",
                    iconPath = "icon/path/test.png",
                    team = "Green",
                    color = 1234,
                )
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Success send map object returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send map object returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun sendVehicle(privateMessage: Boolean, gidNumber: String = "0") = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.send(
            MapObject(
                commandMetaData = CommandMetaData(
                    messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                    destinationGid = gidNumber.toLong(),
                    senderGid = selectedRadio.value?.personalGid ?: 0,
                ),
                commandHeader = GotennaHeaderWrapper(
                    uuid = UUID.randomUUID().toString(),
                    senderGid = 1234,
                    senderCallsign = "Test",
                    messageTypeWrapper = MessageTypeWrapper.MAP_OBJECT,
                    appCode = 123,
                ),
                how = "m-g",
                title = "map vehicle",
                data = MapObject.ObjectData.Vehicle(
                    coordinate = Coordinate(
                        lat = Random().nextDouble(),
                        long = Random().nextDouble(),
                        altitude = Random().nextDouble()
                    ),
                    height = Random().nextDouble(),
                    locationError = Random().nextDouble(),
//                        typ = "a-f-g",
                    modelCategory = MapObject.ObjectData.Vehicle.VehicleCategories.AIRCRAFT,
                    modelName = "warthog",
                    outLine = true,
//                        modelType = "aircraft",
//                        trackCourse = "123",
                    color = 1234,
//                        strokeWeight = "1",
//                        strokeStyle = "1234",
//                        fillColor = "1234",
//                        tog = "123", TODO do we need in new?
                    iconPath = "path/icon/test.png",
                    azimuth = Random().nextDouble(),
                )
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Success send private location returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send private location returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun sendCasevac(privateMessage: Boolean, gidNumber: String = "0") = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.send(
            MapObject(
                commandMetaData = CommandMetaData(
                    messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                    destinationGid = gidNumber.toLong(),
                    senderGid = selectedRadio.value?.personalGid ?: 123
                ),
                commandHeader = GotennaHeaderWrapper(
                    uuid = UUID.randomUUID().toString(),
                    senderGid = 1234,
                    senderCallsign = "Test",
                    messageTypeWrapper = MessageTypeWrapper.MAP_OBJECT,
                    appCode = 123,
                ),
                how = "m-g",
                title = "casevac map item",
                data = MapObject.ObjectData.CasEvac(
                    coordinate = Coordinate(
                        lat = Random().nextDouble(),
                        long = Random().nextDouble(),
                        altitude = Random().nextDouble()
                    ),
                    height = Random().nextDouble(),
                    locationError = Random().nextDouble(),
                    frequency = "often?",
                    patientsByPrecedence = MapObject.ObjectData.CasEvac.PatientsByPrecedence(
                        1,
                        1,
                        1
                    ),
                    requiredEquipment = MapObject.ObjectData.CasEvac.RequiredEquipment(1, "yes?"),
                    patientsByType = MapObject.ObjectData.CasEvac.PatientByType(1, 1),
                    pickupSiteSecurity = MapObject.ObjectData.CasEvac.PickupSiteSecurity.NO_ENEMY,
                    pickupSiteMarker = MapObject.ObjectData.CasEvac.PickupSiteMarker(
                        MapObject.ObjectData.CasEvac.PickupSiteMarking.NONE,
                        "ballpark"
                    ),
                    patientsByNationality = MapObject.ObjectData.CasEvac.PatientsByNationality(
                        1,
                        1,
                        1,
                        1,
                        1,
                        1
                    ),
                    terrainAndObstacles = MapObject.ObjectData.CasEvac.TerrainAndObstacles(
                        1,
                        2,
                        "custom"
                    ),
                    remarks = "nope",
                    zmist = mutableListOf(
                        MapObject.ObjectData.CasEvac.Zmist(
                            "abc123",
                            "stab",
                            "bleeding",
                            "bleeding",
                            "bandaid"
                        )
                    ),
                    hlzBrief = MapObject.ObjectData.CasEvac.HlzBrief(
                        Coordinate(
                            lat = Random().nextDouble(),
                            long = Random().nextDouble(),
                            altitude = Random().nextDouble()
                        ),
                        markedBy = "flame",
                        obstacles = "fire",
                        windsFrom = "south",
                        friendlies = "none",
                        enemy = "lots",
                        remarks = "danger",
                    ),
                )
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Success send private location returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send private location returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun send9Line(privateMessage: Boolean, gidNumber: String = "0") = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.send(
            MapObject(
                commandMetaData = CommandMetaData(
                    messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                    destinationGid = gidNumber.toLong(),
                    senderGid = selectedRadio.value?.personalGid ?: 0,
                ),
                commandHeader = GotennaHeaderWrapper(
                    uuid = UUID.randomUUID().toString(),
                    senderGid = 1234,
                    senderCallsign = "Test",
                    messageTypeWrapper = MessageTypeWrapper.MAP_OBJECT,
                    appCode = 123,
                ),
                how = "m-g",
                title = "nine line",
                data = MapObject.ObjectData.NineLine(
                    coordinate = Coordinate(
                        lat = Random().nextDouble(),
                        long = Random().nextDouble(),
                        altitude = Random().nextDouble()
                    ),
                    height = Random().nextDouble(),
                    locationError = Random().nextDouble(),
                    type = "a-f-g",
                    toc = Random().nextInt(),
                    moa = Random().nextInt(),
                    weapons = listOf(
                        MapObject.ObjectData.NineLine.NineLineWeapon(
                            1,
                            null,
                            null,
                            null,
                            null,
                            null,
                            1,
                            1.0
                        )
                    ),
                    lineOne = 0,
                    lineTwo = MapObject.ObjectData.NineLine.LineTwo(
                        line = 0.0,
                        offset = 0
                    ),
                    lineThree = 0.0,
                    lineFive = MapObject.ObjectData.NineLine.LineFive(
                        line = 0,
                        description = null
                    ),
                    lineSix = 0,
                    lineSeven = MapObject.ObjectData.NineLine.LineSeven(
                        line = 0,
                        safetyZoneEnabled = false,
                        name = null,
                        code = 0,
                        other = null,
                        designatorUUID = null
                    ),
                    lineEight = MapObject.ObjectData.NineLine.LineEight(
                        closestLocked = false,
                        closestUUID = null
                    ),
                    lineNine = MapObject.ObjectData.NineLine.LineNine(
                        line = 0,
                        pull = 0,
                        shouldBlock = false,
                        blockLow = 0,
                        blockHigh = 0,
                        bearingDistance = 0.0,
                        bearingHeading = 0.0,
                        customDescription = null
                    ),
                )
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Success send private location returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send private location returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun sendFile2(gidNumber: Long, file: ByteArray) = viewModelScope.launch(Dispatchers.IO) {
        val grip = radioModels.firstOrNull()?.firstOrNull()?.send(
            SendToNetwork.GripFile(
                data = List(10250) { ('a'..'z').random() }.joinToString("").toByteArray(
                    Charset.defaultCharset()
                ),
                fileName = "mobyDick.txt",
                partialData = false,
                numberOfSegments = 0,
                commandMetaData = CommandMetaData(
                    messageType = if (gidNumber != 0L) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                    senderGid = radioModels.firstOrNull()?.firstOrNull()?.personalGid ?: 1234,
                    destinationGid = gidNumber
                ),
                commandHeader = GotennaHeaderWrapper(
                    uuid = UUID.randomUUID().toString(),
                    senderGid = radioModels.firstOrNull()?.firstOrNull()?.personalGid ?: 1234,
                    senderCallsign = "Test",
                    messageTypeWrapper = MessageTypeWrapper.GRIP_FILE,
                ),
            )
        )
        if (grip?.isSuccess() == true) {
            logOutput.update { it + "Result of sending grip file success: ${grip.executedOrNull()}" }
        } else {
            logOutput.update { it + "Result of sending grip file failure: ${grip?.getErrorOrNull()}" }
        }
    }

    fun sendFile(gidNumber: String, file: File) = viewModelScope.launch(Dispatchers.IO) {
        if (gidNumber.isBlank()) {
            return@launch
        }

        gripFile.update { null }
        val inputStream = file.inputStream()
        val content = ByteArray(file.length().toInt())
        inputStream.read(content)
        inputStream.close()
        val result = selectedRadio.value?.send(
            SendToNetwork.GripFile(
                data = content,
                fileName = file.name,
                partialData = false,
                numberOfSegments = 0,
                commandMetaData = CommandMetaData(
                    messageType = GTMessageType.PRIVATE,
                    destinationGid = gidNumber.toLong(),
                    senderGid = selectedRadio.value?.personalGid ?: 1234
                ),
                commandHeader = GotennaHeaderWrapper(
                    uuid = UUID.randomUUID().toString(),
                    senderGid = selectedRadio.value?.personalGid ?: 1234,
                    senderCallsign = "Test",
                    messageTypeWrapper = MessageTypeWrapper.GRIP_FILE,
                    appCode = 123,
                ),
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Success send grip file returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send grip file returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun setNetworkMacMode(networkMacMode: Int, backPressure: Int, backOffMethod: Int) = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.setNetworkMacMode(
            networkMacMode = networkMacMode,
            backPressure = backPressure,
            backOffMethod = backOffMethod,
        )
        val output = if (result?.isSuccess() == true) {
            "Success set network mac returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure set network mac returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun getMCUArch() = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.send(SendToRadio.RadioChipArchitecture())
        val output = if (result?.isSuccess() == true) {
            "Success get mcu architecture returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure get mcu architecture returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun installFirmwareFile(fileData: ByteArray, targetFirmwareVersion: GTFirmwareVersion) {
        val handler = CoroutineExceptionHandler{ _, exception ->
            println("FIRMWARE_UPDATE I'm the client exception handler an I got the exception: $exception")
        }
        if (!_isUpdatingFirmware.value) {
            _isUpdatingFirmware.update { true }
            updateFirmwareJob = viewModelScope.launch(handler) {
                logOutput.update { it + "got file of size ${fileData.size} target version: $targetFirmwareVersion\n\n" }
//            selectedRadio?.value?.updateFirmware(fileData, targetFirmwareVersion)
                val serials = radioModels.value.map { it.serialNumber }
                GotennaClient.bulkUpdateFirmware(fileData, serials)
                _isUpdatingFirmware.update { false  }
            }
        } else {
            println("FIRMWARE_UPDATE_CLIENT I have just cancelled the job")
            updateFirmwareJob.cancel()
            println("FIRMWARE_UPDATE, job is active: ${updateFirmwareJob.isActive} job is cancelled: ${updateFirmwareJob.isCancelled}")
            logOutput.update { it + "Firmware update has been cancelled.\n\n" }
            _isUpdatingFirmware.update { false }
        }
    }

    fun setTetherMode(enabled: Boolean, batteryThreshold: Int) = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.setTetherMode(enabled, batteryThreshold)
        val output = if (result?.isSuccess() == true) {
            "Success set tether returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure set tether returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun getTetherMode() = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.getTetherMode()
        val output = if (result?.isSuccess() == true) {
            "Success get tether returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure get tether returned data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }

    fun setGroupGid(gid: Long) = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.setGid(gid, GidType.GROUP)
        val output = if (result?.isSuccess() == true) {
            "Successfully set the group gid: $gid\n\n"
        } else {
            "Failed to set the group gid error: ${result?.getErrorOrNull()}\n\n"
        }
        logOutput.update { it + output }
    }

    fun sendGroupInvitation(destinationGid: Long) = viewModelScope.launch(Dispatchers.IO)  {
        groupGid = GIDUtils.generateRandomizedPersonalGID()
        setGroupGid(groupGid)
        val result = selectedRadio.value?.send(
            SendToNetwork.Group(
                groupGid = groupGid,
                title = "test group",
                members = listOf(
                    GMGroupMember(UUID.randomUUID().toString(), "Group Sender"),
                    GMGroupMember(UUID.randomUUID().toString(), "Group Receiver")
                ),
                isInvite = true,
                commandMetaData = CommandMetaData(
                    messageType = GTMessageType.PRIVATE,
                    destinationGid = destinationGid,
                    isPeriodic = false,
                    priority = GTMessagePriority.NORMAL,
                    senderGid = selectedRadio.value?.personalGid ?: 0
                ),
                commandHeader = GotennaHeaderWrapper(
                    messageTypeWrapper = MessageTypeWrapper.GROUP_INVITE,
                    recipientUUID = UUID.randomUUID().toString(),
                    senderGid = selectedRadio.value?.personalGid ?: 0,
                    senderUUID = UUID.randomUUID().toString(),
                    senderCallsign = "Group Sender",
                    encryptionParameters = null,
                    uuid = UUID.randomUUID().toString()
                ),
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Successfully set the group invite\n\n"
        } else {
            "Failed to set the group invite error: ${result?.getErrorOrNull()}\n\n"
        }
        logOutput.update { it + output }
    }

    fun sendChatToGroup() = viewModelScope.launch(Dispatchers.IO) {
        val result = selectedRadio.value?.send(
            SendToNetwork.ChatMessage(
                commandMetaData = CommandMetaData(
                    messageType = GTMessageType.GROUP,
                    destinationGid = groupGid,
                    isPeriodic = false,
                    priority = GTMessagePriority.NORMAL,
                    senderGid = selectedRadio.value?.personalGid ?: 0
                ),
                commandHeader = GotennaHeaderWrapper(
                    messageTypeWrapper = MessageTypeWrapper.GROUP_CHAT_MESSAGE,
                    recipientUUID = UUID.randomUUID().toString(),
                    senderGid = selectedRadio.value?.personalGid ?: 0,
                    senderUUID = UUID.randomUUID().toString(),
                    senderCallsign = "Group Sender",
                    encryptionParameters = null,
                    uuid = UUID.randomUUID().toString()
                ),
                text = "sent from serial ${selectedRadio.value?.serialNumber}",
                chatId = 1,
                chatMessageId = UUID.randomUUID().toString(),
                conversationId = null,
                conversationName = null,
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Successfully set the group message\n\n"
        } else {
            "Failed to set the group message error: ${result?.getErrorOrNull()}\n\n"
        }
        logOutput.update { it + output }
    }
}
