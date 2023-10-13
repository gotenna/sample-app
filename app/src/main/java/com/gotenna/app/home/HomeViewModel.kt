package com.gotenna.app.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gotenna.app.model.ListItem
import com.gotenna.app.ui.compose.mobyDickText
import com.gotenna.app.util.*
import com.gotenna.radio.sdk.GotennaClient
import com.gotenna.radio.sdk.common.models.*
import com.gotenna.radio.sdk.common.results.*
import com.gotenna.radio.sdk.common.models.radio.*
import com.gotenna.radio.sdk.common.results.GripResult
import com.gotenna.radio.sdk.legacy.modules.messaging.atak.wrapper.GMGroupMember
import com.gotenna.radio.sdk.legacy.sdk.firmware.GTFirmwareVersion
import com.gotenna.radio.sdk.legacy.sdk.frequency.GTBandwidth
import com.gotenna.radio.sdk.legacy.sdk.frequency.GTFrequencyChannel
import com.gotenna.radio.sdk.legacy.sdk.frequency.GTPowerLevel
import com.gotenna.radio.sdk.legacy.sdk.session.messages.GTMessageType
import com.gotenna.radio.sdk.common.utils.GIDUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.nio.charset.Charset
import java.util.*
import kotlin.time.ExperimentalTime

@ExperimentalTime
class HomeViewModel : ViewModel() {
    private val _connectionType = MutableStateFlow(ConnectionType.USB)
    private val _radios = MutableStateFlow<List<ListItem>>(emptyList())
    val logOutput = MutableStateFlow("Start of logs:\n\n")

    val connectTypeIndex = _connectionType.mapLatest { it.ordinal }
    val isConnectAvailable = _radios.mapLatest { list -> list.any { it.isSelected } }
    val scannedRadiosCount = _radios.mapLatest { list ->
        list.count { (it.item as RadioModel).isScannedOrDisconnected() }
    }
    val connectedRadiosCount = _radios.mapLatest { list ->
        list.count { (it.item as RadioModel).isConnected() }
    }

    val radios = _radios.asStateFlow()
    val selectedRadio = MutableStateFlow<RadioModel?>(null)
    val gripFile = MutableStateFlow<SendToNetwork.GripFile?>(null)

    private val _radioModels = MutableStateFlow<List<RadioModel>>(emptyList())
    val radioModels = _radioModels.asStateFlow()

    private val _isSelectAll = _radios.mapLatest { list ->
        val notConnectedRadios = list.filter { !(it.item as RadioModel).isConnected() }
        notConnectedRadios.all { it.isSelected }
    }.toMutableStateFlow(viewModelScope, false)
    val isSelectAll = _isSelectAll.asStateFlow()

    init {
        viewModelScope.launch {
            GotennaClient.observeRadios().collect { radios ->
                _radioModels.update { radios }
                _radios.update { radios.toListItems() }
                // TODO a stop gap for now to not recreate several jobs each time these are updated
                if (radios.isNotEmpty()) {
                    radios.forEach { radio ->
                        logOutput.update { it + "Device: ${radio.serialNumber} gid: ${radio.personalGid}\n\n" }
                        launch {
                            radio.observeState().collect { state ->
                                logOutput.update { it + "New device state is: $state for device: ${radio.serialNumber} ${Date()}\n\n" }
                            }
                        }
                        launch {
                            /*launch {
                        logOutput.update { it + "Starting to run user simulated traffic.\n" }
                        // adding this to simulate some of the behavior the device has by a client
                        while (radio.isConnected()) {
                            if (Random().nextBoolean()) {
                                sendCasevac(false)
                            }
                            sendLocation(privateMessage = false, radio = radio)
                            delay(TimeUnit.SECONDS.toMillis(15))
                        }
                    }*/
                            radio.receive.filter {
                                    if (it.isSuccess()) {
                                        when (it.executedOrNull()) {
                                            is SendToRadio.FirmwareUpdate -> {
                                                when ((it.executedOrNull() as SendToRadio.FirmwareUpdate).firmwareUpdateStatus) {
                                                    is SendToRadio.FirmwareUpdateState.InProgress -> {
                                                        ((it.executedOrNull() as SendToRadio.FirmwareUpdate).firmwareUpdateStatus as SendToRadio.FirmwareUpdateState.InProgress).progressPercent > 0
                                                    }
                                                    else -> true
                                                }
                                            }
                                            else -> true
                                        }
                                    } else {
                                        true
                                    }
                                }.collect { command ->
                                    when {
                                        command.isSuccess() -> {
                                            when {
                                                command.executedOrNull() is SendToNetwork.AnyNetworkMessage -> {
                                                    logOutput.update {
                                                        it + "Incoming command for any message device: ${radio.serialNumber} is success: ${
                                                            command.executedOrNull()
                                                        }\n\n"
                                                    }
                                                }
                                                command.executedOrNull() is SendToNetwork.GripFile -> {
                                                    logOutput.update {
                                                        it + "A grip file has been delivered, result: ${(command.executedOrNull() as SendToNetwork.GripFile).gripResult}\n\n"
                                                    }
                                                    logOutput.update {
                                                        it + "Grip delivered metadata is: ${(command.executedOrNull() as SendToNetwork.GripFile).commandMetaData}\n\n"
                                                    }
                                                    if ((command.executedOrNull() as SendToNetwork.GripFile).gripResult is GripResult.GripFullData) {
                                                        gripFile.update { command.executedOrNull() as SendToNetwork.GripFile }
                                                    }
                                                }
                                                command.executedOrNull() is SendToRadio.FirmwareUpdate -> {
                                                    when ((command.executedOrNull() as SendToRadio.FirmwareUpdate).firmwareUpdateStatus) {
                                                        is SendToRadio.FirmwareUpdateState.Started -> {
//                                                            logOutput.update { it + "Firmware update started at: ${Date()} for device ${radio.serialNumber}\n\n" }
                                                        }
                                                        is SendToRadio.FirmwareUpdateState.FinalizingUpdate -> {
//                                                            logOutput.update { it + "Firmware update finalizing at: ${Date()} for device ${radio.serialNumber}\n\n" }
                                                        }
                                                        is SendToRadio.FirmwareUpdateState.CompletedSuccessfully -> {
//                                                            logOutput.update { it + "Firmware update compelted at: ${Date()} for device ${radio.serialNumber}\n\n" }
                                                        }
                                                        is SendToRadio.FirmwareUpdateState.InProgress -> {
                                                            logOutput.update { it + "Firmware update progress ${((command.executedOrNull() as SendToRadio.FirmwareUpdate).firmwareUpdateStatus as SendToRadio.FirmwareUpdateState.InProgress).progressPercent}\n" }
                                                        }
                                                    }
                                                }
                                                command.executedOrNull() is SendToRadio.DeviceInfo -> {
                                                    // ignore for now
                                                }
                                                else -> {
                                                    logOutput.update { it + "Incoming command for device: ${radio.serialNumber} is success: ${command.executedOrNull()}\n\n" }
                                                }
                                            }
                                        }
                                        else -> {
                                            logOutput.update { it + "Incoming command for device: ${radio.serialNumber} is failure: ${command.getErrorOrNull()} for device ${radio.serialNumber} ${Date()}\n\n" }
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }
    }

    fun disconnectAllRadiosAndUpdateConnectionType(index: Int) {
        _radios.value.forEach {
            disconnectRadio(it)
        }

        _radios.update { emptyList() }

        _connectionType.update { ConnectionType.values()[index] }
    }

    fun updateRadiosOnSectionChange(listItem: ListItem) {
        val newListItem = listItem.copy(isSelected = !listItem.isSelected)

        _radios.replaceItem(listItem, newListItem)
    }

    fun setSelectedRadio(listItem: ListItem) {
        if ((listItem.item as RadioModel).isConnected()) {
            selectedRadio.update { listItem.item }
        }
    }

    fun scanRadios() = viewModelScope.launch {
        _radios.value.forEach { disconnectRadio(it) }
        _radios.update { GotennaClient.scan(_connectionType.value).toListItems() }
    }

    fun connectRadios() = viewModelScope.launch(Dispatchers.IO) {
        _radios.value.filter { it.isSelected }.forEach {
            viewModelScope {
                (it.item as RadioModel).connect()
            }
        }
    }

    fun disconnectRadio(listItem: ListItem) = viewModelScope.launch {
        val radio = listItem.item as RadioModel

        if (radio.isConnected()) {
            radio.disconnect()
        }
    }

    private fun updateIsSelectAll() = viewModelScope.launch {
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

    fun setSdkToken(tokenValue: String) {
        viewModelScope.launch {
            val result = selectedRadio.value?.setSdkToken(tokenValue)
            val output = if (result?.isSuccess() == true) {
                "Success set sdk token returned data is ${result.executedOrNull()}\n\n"
            } else {
                "Failure set sdk token returned data is ${result?.getErrorOrNull()}\n\n"
            }

            logOutput.update { it + output }
        }
    }

    fun setGid(gid: Long, type: GidType) {
        viewModelScope.launch {
            val result = selectedRadio.value?.setGid(gid, type)
            val output = if (result?.isSuccess() == true) {
                "Success set gid returned data is ${result.executedOrNull()}\n\n"
            } else {
                "Failure set gid returned data is ${result?.getErrorOrNull()}\n\n"
            }

            logOutput.update { it + output }
        }
    }

    fun deleteGid(gid: Long, type: GidType) {
        viewModelScope.launch {
            val result = selectedRadio.value?.deleteGid(gid, type)
            val output = if (result?.isSuccess() == true) {
                "Success delete gid returned data is ${result.executedOrNull()}\n\n"
            } else {
                "Failure delete gid returned data is ${result?.getErrorOrNull()}\n\n"
            }

            logOutput.update { it + output }
        }
    }

    fun setPowerAndBandwidth(power: GTPowerLevel, bandwidth: GTBandwidth) {
        viewModelScope.launch {
            val result = selectedRadio.value?.setPowerAndBandwidth(power, bandwidth)
            val output = if (result?.isSuccess() == true) {
                "Success set power/bandwidth returned data is ${result.executedOrNull()}\n\n"
            } else {
                "Failure set power/bandwidth returned data is ${result?.getErrorOrNull()}\n\n"
            }

            logOutput.update { it + output }
        }
    }

    fun getPowerAndBandwidth() {
        viewModelScope.launch {
            val result = selectedRadio.value?.getPowerAndBandwidth()
            val output = if (result?.isSuccess() == true) {
                "Success get power/bandwidth returned data is ${result.executedOrNull()}\n\n"
            } else {
                "Failure get power/bandwidth returned data is ${result?.getErrorOrNull()}\n\n"
            }

            logOutput.update { it + output }
        }
    }

    fun getDeviceInfo() {
        viewModelScope.launch {
            val result = selectedRadio.value?.getLatestRadioInfo()
            val output = if (result?.isSuccess() == true) {
                "Success get device info returned data is ${result.executedOrNull()}\n\n"
            } else {
                "Failure get device info returned data is ${result?.getErrorOrNull()}\n\n"
            }

            logOutput.update { it + output }
        }
    }

    fun performLedBlink() {
        viewModelScope.launch {
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
    }

    fun setFrequencyChannels(channels: List<GTFrequencyChannel>) {
        viewModelScope.launch {
            val result = selectedRadio.value?.setFrequencyChannels(channels)
            val output = if (result?.isSuccess() == true) {
                "Success set frequency returned data is ${result.executedOrNull()}\n\n"
            } else {
                "Failure set frequency returned data is ${result?.getErrorOrNull()}\n\n"
            }

            logOutput.update { it + output }
        }
    }

    fun getFrequencyChannels() {
        viewModelScope.launch {
            val result = selectedRadio.value?.getFrequencyChannels()
            val output = if (result?.isSuccess() == true) {
                "Success get frequency returned data is ${result.executedOrNull()}\n\n"
            } else {
                "Failure get frequency returned data is ${result?.getErrorOrNull()}\n\n"
            }

            logOutput.update { it + output }
        }
    }

    fun sendLocation(privateMessage: Boolean, gidNumber: String = "0", radio: RadioModel? = null) {
        viewModelScope.launch {
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
                    senderGid = selectedRadio.value?.personalGid ?: 0
                ),
                commandHeader = GotennaHeaderWrapper(
                    uuid = UUID.randomUUID().toString(),
                    senderGid = selectedRadio.value?.personalGid ?: 1234,
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

            val result = selectedRadio.value?.send(
                data
            )

            val output = if (result?.isSuccess() == true) {
                "Success send private location returned data is ${result.executedOrNull()}\n\n"
            } else {
                "Failure send private location returned data is ${result?.getErrorOrNull()}\n\n"
            }

            logOutput.update { it + output }
        }
    }

    /**
     * For grip to work correctly the following is required to be used, destination gid, sender gid, messagetype = private in the metadata.
     * If using broadcast it is recommended to change the usegrip flag to false.
     * The result of a grip transfer should be radioresult.success<gripfile> with the included grip result, with the failure being returned as
     * radioresult.failure.
     */
    fun sendAnyMessage(privateMessage: Boolean, gidNumber: String = "0") {
        viewModelScope.launch {
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
    }

    fun sendFrontHaul(privateMessage: Boolean, gidNumber: String = "0") {
        sendChat(privateMessage, gidNumber)
    }

    fun sendGroup(privateMessage: Boolean, gidNumber: String = "0") {
        viewModelScope.launch {
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
    }

    fun sendFrequency(privateMessage: Boolean, gidNumber: String = "0") {
        viewModelScope.launch {
            val result = selectedRadio.value?.send(
                SendToNetwork.SharedFrequency(
                    uuid = UUID.randomUUID().toString(),
                    name = "frequency title",
                    powerSetting = PowerLevel.HALF_WATT,
                    bandwidthSetting = FrequencyBandwidth.BW_4_84KHZ,
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
    }

    fun sendEncryptionKeyExchange(privateMessage: Boolean, gidNumber: String = "0") {
        viewModelScope.launch {
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
    }

    fun sendRoute(privateMessage: Boolean, gidNumber: String = "0") {
        viewModelScope.launch {
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
    }

    fun sendCircle(privateMessage: Boolean, gidNumber: String = "0") {
        viewModelScope.launch {
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
    }

    fun sendShape(privateMessage: Boolean, gidNumber: String = "0") {
        viewModelScope.launch {
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
    }

    fun sendChat(privateMessage: Boolean, gidNumber: String = "0") {
        viewModelScope.launch {
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
    }

    fun sendMapItem(privateMessage: Boolean, gidNumber: String = "0") {
        viewModelScope.launch {
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
    }

    fun sendVehicle(privateMessage: Boolean, gidNumber: String = "0") {
        viewModelScope.launch {
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

    }

    fun sendCasevac(privateMessage: Boolean, gidNumber: String = "0") {
        viewModelScope.launch {
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
                        patientsByPrecedence = MapObject.ObjectData.CasEvac.PatientsByPrecedence(1, 1, 1),
                        requiredEquipment = MapObject.ObjectData.CasEvac.RequiredEquipment(1, "yes?"),
                        patientsByType = MapObject.ObjectData.CasEvac.PatientByType(1, 1),
                        pickupSiteSecurity = MapObject.ObjectData.CasEvac.PickupSiteSecurity.NO_ENEMY,
                        pickupSiteMarker = MapObject.ObjectData.CasEvac.PickupSiteMarker(MapObject.ObjectData.CasEvac.PickupSiteMarking.NONE, "ballpark"),
                        patientsByNationality = MapObject.ObjectData.CasEvac.PatientsByNationality(1, 1, 1, 1, 1, 1),
                        terrainAndObstacles = MapObject.ObjectData.CasEvac.TerrainAndObstacles(1, 2, "custom"),
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
    }

    fun send9Line(privateMessage: Boolean, gidNumber: String = "0") {
        viewModelScope.launch {
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
    }

    fun sendFile(gidNumber: String, file: File) {
        if (gidNumber.isBlank()) {
            return
        }
        viewModelScope.launch {
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
    }

    fun setNetworkMacMode(networkMacMode: Int, backPressure: Int, backOffMethod: Int) {
        viewModelScope.launch {
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
    }

    fun getMCUArch() {
        viewModelScope.launch {
            val result = selectedRadio.value?.send(SendToRadio.RadioChipArchitecture())
            val output = if (result?.isSuccess() == true) {
                "Success get mcu architecture returned data is ${result.executedOrNull()}\n\n"
            } else {
                "Failure get mcu architecture returned data is ${result?.getErrorOrNull()}\n\n"
            }

            logOutput.update { it + output }
        }
    }

    fun installFirmwareFile(fileData: ByteArray, targetFirmwareVersion: GTFirmwareVersion) {
        viewModelScope.launch(Dispatchers.IO) {
            logOutput.update { it + "got file of size ${fileData.size} target version: $targetFirmwareVersion\n\n" }
            val update = selectedRadio.value?.updateFirmware(firmwareFile = fileData, targetFirmware = GTFirmwareVersion(1, 1, 1))
            logOutput.update { it + update }
            /*val time = measureTime {
                GotennaClient.bulkUpdateFirmware(fileData, serialNumbers)
            }*/
        }
    }

    fun setTetherMode(enabled: Boolean, batteryThreshold: Int) {
        viewModelScope.launch {
            val result = selectedRadio.value?.setTetherMode(enabled, batteryThreshold)
            val output = if (result?.isSuccess() == true) {
                "Success set tether returned data is ${result.executedOrNull()}\n\n"
            } else {
                "Failure set tether returned data is ${result?.getErrorOrNull()}\n\n"
            }

            logOutput.update { it + output }
        }
    }

    fun getTetherMode() {
        viewModelScope.launch {
            val result = selectedRadio.value?.getTetherMode()
            val output = if (result?.isSuccess() == true) {
                "Success get tether returned data is ${result.executedOrNull()}\n\n"
            } else {
                "Failure get tether returned data is ${result?.getErrorOrNull()}\n\n"
            }

            logOutput.update { it + output }
        }
    }
}
