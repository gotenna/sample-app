package com.gotenna.app.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gotenna.app.model.ListItem
import com.gotenna.app.ui.compose.mobyDickText
import com.gotenna.app.util.replaceItem
import com.gotenna.app.util.toListItems
import com.gotenna.radio.sdk.GotennaClient
import com.gotenna.radio.sdk.common.results.*
import com.gotenna.radio.sdk.common.models.FrequencyBandwidth
import com.gotenna.radio.sdk.common.models.PowerLevel
import com.gotenna.radio.sdk.common.models.radio.*
import com.gotenna.radio.sdk.common.results.GripResult
import com.gotenna.radio.sdk.legacy.modules.messaging.atak.wrapper.GMGroupMember
import com.gotenna.radio.sdk.legacy.sdk.firmware.GTFirmwareVersion
import com.gotenna.radio.sdk.legacy.sdk.frequency.GTBandwidth
import com.gotenna.radio.sdk.legacy.sdk.frequency.GTFrequencyChannel
import com.gotenna.radio.sdk.legacy.sdk.frequency.GTPowerLevel
import com.gotenna.radio.sdk.legacy.sdk.session.messages.GTMessageType
import com.gotenna.radio.sdk.legacy.sdk.transport.responses.RssiScanResult
import com.gotenna.radio.sdk.common.utils.GIDUtils
import com.gotenna.radio.sdk.common.utils.toHexString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.nio.charset.Charset
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@ExperimentalTime
class HomeViewModel : ViewModel() {
    private val uiScope = viewModelScope
    private val _connectionType = MutableStateFlow(ConnectionType.USB)
    private val _radios = MutableStateFlow<List<ListItem>>(emptyList())
    val logOutput = MutableStateFlow("Start of logs:\n\n")

    val connectTypeIndex = _connectionType.mapLatest { it.ordinal }
    val isConnectAvailable = _radios.mapLatest { list -> list.any { it.isSelected } }

    val radios = _radios.asStateFlow()
    val selectedRadio = MutableStateFlow<RadioModel?>(null)
    val gripFile = MutableStateFlow<SendToNetwork.GripFile?>(null)

    private val _radioModels = MutableStateFlow<List<RadioModel>>(emptyList())
    val radioModels = _radioModels.asStateFlow()

    init {
        uiScope.launch {
            GotennaClient.observeRadios().collect { radios ->
                _radioModels.update { radios }
                _radios.update { radios.toListItems() }
                // TODO a stop gap for now to not recreate several jobs each time these are updated
                if (radios.size > 3) {
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
                        while (radio.state == RadioState.CONNECTED) {
                            if (Random().nextBoolean()) {
                                sendCasevac(false)
                            }
                            sendLocation(privateMessage = false, radio = radio)
                            delay(TimeUnit.SECONDS.toMillis(15))
                        }
                    }*/
                            radio.receive
                                .filter {
                                    !(it is RadioResult.Success && it.executedOrNull() is RadioCommand.FailedToParseResponse)
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
                                                        it + "A grip file has been delivered, result: ${(command.executedOrNull() as SendToNetwork.GripFile).gripResult}"
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
        if ((listItem.item as RadioModel).state == RadioState.CONNECTED) {
            selectedRadio.update { listItem.item }
        }
    }

    fun scanRadios() = uiScope.launch {
        _radios.value.forEach { disconnectRadio(it) }
        _radios.update { GotennaClient.scan(_connectionType.value).toListItems() }
    }

    fun connectRadios() = uiScope.launch(Dispatchers.IO) {
        _radios.value.filter { it.isSelected }.forEach {
            launch {
                (it.item as RadioModel).connect()
            }
        }
    }

    fun disconnectRadio(listItem: ListItem) = uiScope.launch {
        val radio = listItem.item as RadioModel

        if (radio.state == RadioState.CONNECTED) {
            radio.disconnect()
        }
    }

    @Composable
    fun toState() = HomeState(
        connectionTypeIndex = connectTypeIndex.collectAsState(initial = 0),
        isConnectAvailable = isConnectAvailable.collectAsState(initial = false),
        radios = radios.collectAsState(),
        connectionTypeChangeAction = { disconnectAllRadiosAndUpdateConnectionType(it) },
        radioClickAction = { updateRadiosOnSectionChange(it) },
        radioLongClickAction = { disconnectRadio(it) },
        connectRadiosAction = { connectRadios() },
        scanRadiosAction = { scanRadios() }
    )

    // TODO these probably go in another viewmodel or need to get the radio from the existing list

    fun setSdkToken(tokenValue: String) {
        uiScope.launch {
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
        uiScope.launch {
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
        uiScope.launch {
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
        uiScope.launch {
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
        uiScope.launch {
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
        uiScope.launch {
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
        uiScope.launch {
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
        uiScope.launch {
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
        uiScope.launch {
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
        uiScope.launch {
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
                    useGripProtocol = false
                ),
                commandHeader = GotennaHeaderWrapper(
                    uuid = UUID.randomUUID().toString(),
                    senderGid = radio?.personalGid ?: 1234,
                    senderCallsign = "Test",
                    messageTypeWrapper = MessageTypeWrapper.LOCATION,
                    appCode = 123,
                    senderUUID = "ANDROID-253d2e0c5acb0ef5",
                    recipientUUID = UUID.randomUUID().toString(),
                    encryptionParameters = EncryptionParameters(
                        "abcd",
                        byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                    )
                ),
            )
            val byteData = Integer.valueOf(data.bytes.copyOfRange(3, 4).toHexString(), 16)

            logOutput.update { it + "sending location object with sequence number of: $byteData\n" }

            val result = radio?.send(
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
        uiScope.launch {
            coroutineScope {
                awaitAll(async {
                    val testMessage = "Test".toByteArray(Charset.defaultCharset())
                    val result = selectedRadio.value?.send(
                        SendToNetwork.AnyNetworkMessage(
                            data = testMessage,
                            commandMetaData = CommandMetaData(
                                messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                                destinationGid = gidNumber.toLong(),
                                useGripProtocol = true,
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
                                    useGripProtocol = true,
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
                                    useGripProtocol = true,
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
                                    useGripProtocol = true,
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
                                    useGripProtocol = true,
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

    fun scanChannels() {
        uiScope.launch {
            val result = selectedRadio.value?.send(
                SendToRadio.ChannelScan(
                    scanBand = RssiScanResult.ScanBand.UHF,
                    scanWidth = RssiScanResult.ScanWidth.TWO_HUNDRED_FIFTY_kHZ,
                )
            )
            val output = if (result?.isSuccess() == true) {
                "Success do channel scan data is ${result.executedOrNull()}\n\n"
            } else {
                "Failure do channel scan data is ${result?.getErrorOrNull()}\n\n"
            }

            logOutput.update { it + output }
        }
    }

    fun getChannelData() {
        uiScope.launch {
            val result = selectedRadio.value?.send(
                SendToRadio.GetChannelData()
            )
            val output = if (result?.isSuccess() == true) {
                "Success channel data is ${result.executedOrNull()}\n\n"
            } else {
                "Failure channel data is ${result?.getErrorOrNull()}\n\n"
            }

            logOutput.update { it + output }
        }
    }

    fun sendDnop(privateMessage: Boolean, gidNumber: String = "0") {
        uiScope.launch {
            val result = selectedRadio.value?.send(
                SendToNetwork.Dnop(
                    batteryCharge = 50,
                    isCharging = false,
                    rssiLevels = if (privateMessage) mapOf(
                        1234 to -12,
                        4567 to -50,
                        8901 to -100
                    ) else emptyMap(),
                    rssiLevelString = if (!privateMessage) ";1234:-12d;4567:-50d;8901:-100d" else "",
                    pliSent = 12,
                    pliReceived = 100,
                    commandMetaData = CommandMetaData(
                        messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                        destinationGid = gidNumber.toLong(),
                        useGripProtocol = false
                    ), commandHeader = GotennaHeaderWrapper(
                        uuid = UUID.randomUUID().toString(),
                        senderGid = 1234,
                        senderCallsign = "Test",
                        messageTypeWrapper = MessageTypeWrapper.DNOP,
                        appCode = 123,
                    )
                )
            )
            val output = if (result?.isSuccess() == true) {
                "Success send dnop returned data is ${result.executedOrNull()}\n\n"
            } else {
                "Failure send dnop returned data is ${result?.getErrorOrNull()}\n\n"
            }

            logOutput.update { it + output }
        }
    }

    fun sendFrontHaul(privateMessage: Boolean, gidNumber: String = "0") {
        sendChat(privateMessage, gidNumber)
    }

    fun sendGroup(privateMessage: Boolean, gidNumber: String = "0") {
        uiScope.launch {
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
                        useGripProtocol = false
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
        uiScope.launch {
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
                        useGripProtocol = false
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
        uiScope.launch {
            val result = selectedRadio.value?.send(
                SendToNetwork.EncryptionKeyExchangeData(
                    commandMetaData = CommandMetaData(
                        messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                        destinationGid = gidNumber.toLong(),
                        useGripProtocol = false
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
        uiScope.launch {
            val result = selectedRadio.value?.send(
                MapObject(
                    commandMetaData = CommandMetaData(
                        messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                        destinationGid = gidNumber.toLong(),
                        useGripProtocol = false
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
        uiScope.launch {
            val result = selectedRadio.value?.send(
                MapObject(
                    commandMetaData = CommandMetaData(
                        messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                        destinationGid = gidNumber.toLong(),
                        useGripProtocol = false
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
        uiScope.launch {
            val result = selectedRadio.value?.send(
                MapObject(
                    commandMetaData = CommandMetaData(
                        messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                        destinationGid = gidNumber.toLong(),
                        useGripProtocol = false
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
        uiScope.launch {
            val result = selectedRadio.value?.send(
                SendToNetwork.ChatMessage(
                    commandMetaData = CommandMetaData(
                        messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                        destinationGid = gidNumber.toLong(),
                        useGripProtocol = false
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
        uiScope.launch {
            val result = selectedRadio.value?.send(
                MapObject(
                    commandMetaData = CommandMetaData(
                        messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                        destinationGid = gidNumber.toLong(),
                        useGripProtocol = false
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
        uiScope.launch {
            val result = selectedRadio.value?.send(
                MapObject(
                    commandMetaData = CommandMetaData(
                        messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                        destinationGid = gidNumber.toLong(),
                        useGripProtocol = false
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
        uiScope.launch {
            val result = selectedRadio.value?.send(
                MapObject(
                    commandMetaData = CommandMetaData(
                        messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                        destinationGid = gidNumber.toLong(),
                        useGripProtocol = true,
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
        uiScope.launch {
            val result = selectedRadio.value?.send(
                MapObject(
                    commandMetaData = CommandMetaData(
                        messageType = if (privateMessage) GTMessageType.PRIVATE else GTMessageType.BROADCAST,
                        destinationGid = gidNumber.toLong(),
                        useGripProtocol = false
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
        uiScope.launch {
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
                        useGripProtocol = true,
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
        uiScope.launch {
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
        uiScope.launch {
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
        uiScope.launch(Dispatchers.IO) {
            logOutput.update { it + "got file of size ${fileData.size} target version: $targetFirmwareVersion\n\n" }
            val serialNumbers = radioModels.value.map { it.serialNumber }.toMutableList()
            repeat(times = 10) {
                val time = measureTime {
                    GotennaClient.bulkUpdateFirmware(fileData, serialNumbers)
                }
                logOutput.update { it + "elapsed time for all installs in seconds: ${time.inWholeSeconds}\n\n" }
                radioModels.value.forEach {
                    it.getRadioInfo()
                    it.performLedBlink()
                }
            }

            /*bulkUpdate.forEach { t, u ->
                if (u) {
                    logOutput.update { it + "update result for device: $t is success timestamp:${Date()}\n\n" }
                } else {
                    logOutput.update { it + "update result for device: $t is failure timestamp:${Date()}\n\n" }
                }
            }*/

            /*val tasks = mutableListOf<Deferred<RadioResult<Unit>>>()
            radioModels.value.forEach { radio ->
                tasks.add(
                    async {
                        logOutput.update { it + "Starting update for radio: ${radio.serialNumber}\n" }
                        val result = radio.updateFirmware(fileData, targetFirmwareVersion)
                        if (result?.isSuccess() == true) {
                            logOutput.update { it + "update result for radio: ${radio.serialNumber} is success timestamp:${Date()} ${result.executedOrNull()}\n\n" }
                        } else {
                            logOutput.update { it + "update result radio: ${radio.serialNumber}  is failure timestamp:${Date()} ${result?.getErrorOrNull()}\nn" }
                        }
                        result
                    }
                )
            }
            tasks.awaitAll()*/
        }
    }

    fun sendRelayHealthCheck() {
        uiScope.launch {
            val result = selectedRadio.value?.send(SendToRadio.RelayHealthCheckRequest())
            val output = if (result?.isSuccess() == true) {
                "Success send relay health check returned data is ${result.executedOrNull()}\n\n"
            } else {
                "Failure send relay health check returned data is ${result?.getErrorOrNull()}\n\n"
            }
            logOutput.update { it + output }
        }
    }

}
