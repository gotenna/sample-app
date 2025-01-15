package com.gotenna.app.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gotenna.app.model.RadioListItem
import com.gotenna.app.ui.compose.mobyDickText
import com.gotenna.app.util.isConnected
import com.gotenna.app.util.isScannedOrDisconnected
import com.gotenna.app.util.replaceItem
import com.gotenna.app.util.toListItems
import com.gotenna.app.util.toMutableStateFlow
import com.gotenna.radio.sdk.GotennaClient
import com.gotenna.radio.sdk.common.configuration.GTBandwidth
import com.gotenna.radio.sdk.common.configuration.GTFirmwareVersion
import com.gotenna.radio.sdk.common.configuration.GTFrequencyChannel
import com.gotenna.radio.sdk.common.configuration.GTOperationMode
import com.gotenna.radio.sdk.common.configuration.GTPowerLevel
import com.gotenna.radio.sdk.common.configuration.GidType
import com.gotenna.radio.sdk.common.models.CommandMetaData
import com.gotenna.radio.sdk.common.models.ConnectionType
import com.gotenna.radio.sdk.common.models.Coordinate
import com.gotenna.radio.sdk.common.models.DeliveryResult
import com.gotenna.radio.sdk.common.models.EncryptionParameters
import com.gotenna.radio.sdk.common.models.FrequencyBandwidth
import com.gotenna.radio.sdk.common.models.GMGroupMember
import com.gotenna.radio.sdk.common.models.GTMessagePriority
import com.gotenna.radio.sdk.common.models.GTMessageType
import com.gotenna.radio.sdk.common.models.GotennaHeaderWrapper
import com.gotenna.radio.sdk.common.models.GripResult
import com.gotenna.radio.sdk.common.models.MapObject
import com.gotenna.radio.sdk.common.models.MessageTypeWrapper
import com.gotenna.radio.sdk.common.models.PowerLevel
import com.gotenna.radio.sdk.common.models.RadioModel
import com.gotenna.radio.sdk.common.models.SendToNetwork
import com.gotenna.radio.sdk.common.models.SendToRadio
import com.gotenna.radio.sdk.utils.RadioResult
import com.gotenna.radio.sdk.utils.SdkError
import com.gotenna.radio.sdk.utils.executedOrNull
import com.gotenna.radio.sdk.utils.generateRandomizedPersonalGID
import com.gotenna.radio.sdk.utils.getErrorOrNull
import com.gotenna.radio.sdk.utils.isSuccess
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.charset.Charset
import java.time.Clock
import java.time.ZoneId
import java.util.Date
import java.util.UUID
import kotlin.random.Random
import kotlin.system.measureTimeMillis
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
    private val nodesInNetwork = mutableMapOf<String, Long>()
    private val statsMap = mutableMapOf<String, MutableList<TransferStat>>()
    data class TransferStat(
        val sender: Boolean = false,
        val successful: Boolean = false,
        val newSession: Boolean = false,
        val numberOfSegments: Int = 1,
        val numberOfMissingSegments: Int = 0,
        val numberOfNacks: Int = 0,
        val averageHops: Int = 1,
        val averageRssi: Int = -1,
        val numberOfSentSegments: Int = 0,
        val waitingForAck: Boolean = false
    )

    private var observeStateJob: Job? = null
    private var observeMessageJob: Job? = null

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
                    radios.forEach { radioModel ->
                        launch {
                            radioModel.radioEvents.collect { command ->
                                when (val executed = command.executedOrNull()) {
                                is SendToNetwork.GripFile -> {
                                    when (val gripResult = executed.gripResult) {
                                        is GripResult.ReceiveFileStarted -> {
                                            logOutput.update { it + "grip data incoming from ${gripResult.originGidHash} msgId: ${gripResult.id} with expected segments ${gripResult.expectedNumberOfSegments}\n\n" }
                                        }

                                        is GripResult.GripFullData -> {
                                            val transferStat = TransferStat(
                                                successful = true,
                                                numberOfSegments = gripResult.numberOfSegments,
                                                numberOfMissingSegments = 0,
                                                numberOfNacks = gripResult.numberOfRetries,
                                                averageHops = gripResult.averageHopCount,
                                                averageRssi = gripResult.averageRssiValue
                                            )
                                            logOutput.update { it + "grip full file msgId: ${gripResult.id}\n" }
                                            println("RECEIVER: delivery success for ${gripResult.id}")
                                            if (statsMap.containsKey(executed.commandHeader.senderCallsign)) {
                                                statsMap[executed.commandHeader.senderCallsign]!!.add(
                                                    transferStat
                                                )
                                            } else {
                                                statsMap[executed.commandHeader.senderCallsign] =
                                                    mutableListOf(transferStat)
                                            }
                                        }

                                        is GripResult.UnsuccessfulPartialData -> {
                                            val transferStat = TransferStat(
                                                successful = false,
                                                numberOfSegments = gripResult.numberOfSegmentsReceived + gripResult.missingSegments,
                                                numberOfMissingSegments = gripResult.missingSegments,
                                                numberOfNacks = gripResult.numberOfRetries,
                                                averageHops = gripResult.averageHopCount,
                                                averageRssi = gripResult.averageRssiValue
                                            )
                                            logOutput.update { it + "grip unsuccessful file msgId: ${gripResult.id}\n" }
                                            println("RECEIVER: delivery failure for ${gripResult.id}")
                                            if (statsMap.containsKey(executed.commandHeader.senderCallsign)) {
                                                statsMap[executed.commandHeader.senderCallsign]!!.add(
                                                    transferStat
                                                )
                                            } else {
                                                statsMap[executed.commandHeader.senderCallsign] =
                                                    mutableListOf(transferStat)
                                            }
                                        }

                                        is GripResult.PartiallyAssembledFile -> {
                                            val transferStat = TransferStat(
                                                successful = false,
                                                numberOfSegments = gripResult.numberOfSegments + gripResult.numberOfMissingSegments,
                                                numberOfMissingSegments = gripResult.numberOfMissingSegments,
                                                numberOfNacks = gripResult.numberOfRetries,
                                                averageHops = gripResult.averageHopCount,
                                                averageRssi = gripResult.averageRssiValue
                                            )
                                            logOutput.update { it + "grip partial file msgId: ${gripResult.id}\n" }
                                            println("RECEIVER: delivery failure for ${gripResult.id}")
                                            if (statsMap.containsKey(executed.commandHeader.senderCallsign)) {
                                                statsMap[executed.commandHeader.senderCallsign]!!.add(
                                                    transferStat
                                                )
                                            } else {
                                                statsMap[executed.commandHeader.senderCallsign] =
                                                    mutableListOf(transferStat)
                                            }
                                        }

                                        else -> {
                                        }
                                    }
                                }
                                is SendToRadio.DeviceInfo -> {
                                    logOutput.update { it + "Device Info: battery level: ${executed.batteryLevel} charging: ${executed.batteryCharging} system temp: ${executed.systemTemperature} power amp temp ${executed.powerAmpTemperature} ${Clock.system(
                                        ZoneId.systemDefault()).instant().toString()}\n\n" }
                                }
                                is SendToNetwork.Location -> {
                                    // save the radio's callsign and gid
                                    if (!nodesInNetwork.containsKey(executed.commandHeader.senderCallsign)) {
                                        logOutput.update { it + "Adding ${executed.commandHeader.senderCallsign} to grip test tracking ${(executed.gripResult as GripResult.GripFullData).averageHopCount} hops away\n" }
                                        nodesInNetwork[executed.commandHeader.senderCallsign] =
                                            executed.commandHeader.senderGid
                                    }
                                }
                                is SendToNetwork.AnyNetworkMessage -> {
                                    when (val gripResult = executed.gripResult) {
                                        is GripResult.PartiallyAssembledFile -> {
                                            val transferStat = TransferStat(
                                                successful = true,
                                                numberOfSegments = gripResult.numberOfSegments + gripResult.numberOfMissingSegments,
                                                numberOfMissingSegments = gripResult.numberOfMissingSegments,
                                                numberOfNacks = gripResult.numberOfRetries,
                                                averageHops = gripResult.averageHopCount,
                                                averageRssi = gripResult.averageRssiValue
                                            )
                                            if (statsMap.containsKey(executed.commandHeader.senderCallsign)) {
                                                statsMap[executed.commandHeader.senderCallsign]!!.add(
                                                    transferStat
                                                )
                                            } else {
                                                statsMap[executed.commandHeader.senderCallsign] =
                                                    mutableListOf(transferStat)
                                            }
                                        }

                                        else -> {
                                            logOutput.update { it + "grip type: ${gripResult::class.java.simpleName}\n" }
                                        }
                                    }
                                }
                            }
                            when (val error = command.getErrorOrNull()) {
                                is SdkError.GripPartialFileDelivered -> {
                                    if (error.gripResult is GripResult.UnsuccessfulPartialData) {
                                        val gripResult =
                                            (command.getErrorOrNull() as SdkError.GripPartialFileDelivered).gripResult as GripResult.UnsuccessfulPartialData
                                        logOutput.update { it + "grip partial file error for msgId: ${gripResult.id}\n" }
                                        println("RECEIVER: delivery failed for ${gripResult.id}")
                                        val transferStat = TransferStat(
                                            successful = false,
                                            numberOfSegments = gripResult.numberOfSegmentsReceived + gripResult.missingSegments,
                                            numberOfMissingSegments = gripResult.missingSegments,
                                            numberOfNacks = gripResult.numberOfRetries,
                                            averageHops = gripResult.averageHopCount,
                                            averageRssi = gripResult.averageRssiValue,
                                            newSession = true
                                        )
                                        if (statsMap.containsKey(gripResult.originGid.toString())) {
                                            statsMap[gripResult.originGid.toString()]!!.add(
                                                transferStat
                                            )
                                        } else {
                                            statsMap[gripResult.originGid.toString()] =
                                                mutableListOf(transferStat)
                                        }
                                    } else {

                                    }
                                }

                                is SdkError.GripFileNotFound -> {
                                    val gripResult =
                                        (command.getErrorOrNull() as SdkError.GripPartialFileDelivered).gripResult as GripResult.UntrackedTransfer
                                    logOutput.update { it + "grip file not found\n" }
                                    println("RECEIVER: delivery failed for ${gripResult.id}")
                                    val transferStat = TransferStat(
                                        successful = false,
                                        numberOfSegments = 0,
                                        numberOfMissingSegments = 0,
                                        numberOfNacks = 0,
                                        averageHops = gripResult.averageHopCount,
                                        averageRssi = gripResult.averageRssiValue
                                    )
                                    if (statsMap.containsKey("untracked")) {
                                        statsMap["untracked"]!!.add(transferStat)
                                    } else {
                                        statsMap["untracked"] = mutableListOf(transferStat)
                                    }
                                }

                                else -> {}
                            }
                        }
                    // For each connected radio observe their connection state.
                    /*radios.forEach { radio ->
                        logOutput.update { it + "Device: ${radio.serialNumber} gid: ${radio.personalGid}\n\n" }
                        observeStateJob?.cancel()
                        observeStateJob = launch {
                            radio.observeState().collect { state ->
                                logOutput.update { it + "New device state is: $state for device: ${radio.serialNumber} ${Date()}\n\n" }
                            }
                        }

                        testing()

                        // Observe messages from a radio.
                        observeMessageJob?.cancel()
                        observeMessageJob = launch {
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
                                        logOutput.update {
                                            it + "Grip file delivered: ${Date()}\ndata: ${executed}\n"
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
                            }*/
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
                val result = it.radioModel.connect(false)
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

    val recordingState = MutableStateFlow(false)

    var codec2SampleRate by mutableStateOf("")
        private set

    var audioSampleRate by mutableStateOf("")
        private  set

    private val numberPattern = Regex("(^[0-9]+\$|^\$)")
    fun updateCodec2SampleRate(input: String) {
        if (input.matches(numberPattern)) {
            codec2SampleRate = input
        }
    }

    fun updatedAudioSampleRate(input: String) {
        if (input.matches(numberPattern)) {
            audioSampleRate = input
        }
    }

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
    
    fun setOperationMode(mode: GTOperationMode) = viewModelScope.launch(Dispatchers.IO) {
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
            how = "h-e",
            staleTime = 60,
            lat = 35.291802,
            long = 80.846604,
            altitude = 237.21325546763973,
            team = "CYAN",
            accuracy = 11,
            creationTime = 1718745135755,
            messageId = 0,
            commandMetaData = CommandMetaData(messageType= GTMessageType.BROADCAST, destinationGid=0, isPeriodic=false, priority=GTMessagePriority.NORMAL, senderGid=904610228241489),
            commandHeader = GotennaHeaderWrapper(timeStamp=1718745135761, messageTypeWrapper= MessageTypeWrapper.LOCATION, appCode=0, senderGid=904610228241489, senderUUID="ANDROID-2440142b8ac6d5d7", senderCallsign="JONAS", encryptionParameters=null),
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
                groupGid = generateRandomizedPersonalGID(),
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
            "Success send group returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send group returned data is ${result?.getErrorOrNull()}\n\n"
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
                keyUuid = byteArrayOf(0),
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
                )
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Success send encryption exchange returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send encryption exchange returned data is ${result?.getErrorOrNull()}\n\n"
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
                                lat = Random.nextDouble(),
                                long = Random.nextDouble()
                            ),
                            positionInRoute = 0,
                            isWaypoint = false
                        ), MapObject.ObjectData.Route.RoutePoint(
                            coordinates = Coordinate(
                                lat = Random.nextDouble(),
                                long = Random.nextDouble()
                            ),
                            positionInRoute = 1,
                            isWaypoint = true
                        )
                    ),
                )
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Success send route returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send route returned data is ${result?.getErrorOrNull()}\n\n"
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
                    radius = Random.nextDouble(),
                    centerPoint = Coordinate(
                        lat = Random.nextDouble(),
                        long = Random.nextDouble()
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
            "Success send circle returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send circle returned data is ${result?.getErrorOrNull()}\n\n"
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
                        Coordinate(Random.nextDouble(), Random.nextDouble()),
                        Coordinate(Random.nextDouble(), Random.nextDouble()),
                        Coordinate(Random.nextDouble(), Random.nextDouble())
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
            "Success send shape returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send shape returned data is ${result?.getErrorOrNull()}\n\n"
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
            "Success send chat returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send chat returned data is ${result?.getErrorOrNull()}\n\n"
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
                        lat = Random.nextDouble(),
                        long = Random.nextDouble(),
                        altitude = Random.nextDouble()
                    ),
                    height = Random.nextDouble(),
                    locationError = Random.nextDouble(),
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
                        lat = Random.nextDouble(),
                        long = Random.nextDouble(),
                        altitude = Random.nextDouble()
                    ),
                    height = Random.nextDouble(),
                    locationError = Random.nextDouble(),
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
                    azimuth = Random.nextDouble(),
                )
            )
        )
        val output = if (result?.isSuccess() == true) {
            "Success send vehicle returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send vehicle returned data is ${result?.getErrorOrNull()}\n\n"
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
                        lat = Random.nextDouble(),
                        long = Random.nextDouble(),
                        altitude = Random.nextDouble()
                    ),
                    height = Random.nextDouble(),
                    locationError = Random.nextDouble(),
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
                            lat = Random.nextDouble(),
                            long = Random.nextDouble(),
                            altitude = Random.nextDouble()
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
            "Success send casevac returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send casevac returned data is ${result?.getErrorOrNull()}\n\n"
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
                        lat = Random.nextDouble(),
                        long = Random.nextDouble(),
                        altitude = Random.nextDouble()
                    ),
                    height = Random.nextDouble(),
                    locationError = Random.nextDouble(),
                    type = "a-f-g",
                    toc = Random.nextInt(),
                    moa = Random.nextInt(),
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
            "Success send 9line returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send 9line returned data is ${result?.getErrorOrNull()}\n\n"
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

    fun sendHighThroughput1Segment(gidNumber: String) = viewModelScope.launch(Dispatchers.IO) {
        // send some high rate messages to the destination
        val mutex = Mutex()
        val requestList = mutableListOf<Deferred<RadioResult<DeliveryResult>?>>()
        val numberOfRequests = Random.nextInt(1, 10)
        var successCounter = 0
        val deliveryTimes = mutableListOf<Long>()
        for (x in 0..numberOfRequests) {
            requestList.add(
                async {
                    var sent: RadioResult<DeliveryResult>?
                    val time = measureTimeMillis {
                        sent = selectedRadio.value?.send(SendToNetwork.ChatMessage(
                            text = x.toString(),
                            chatId = 1,
                            chatMessageId = UUID.randomUUID().toString(),
                            conversationId = "AXLE",
                            conversationName = "AXLE",
                            commandMetaData = CommandMetaData(
                                messageType = GTMessageType.PRIVATE, destinationGid = gidNumber.toLong(), isPeriodic = false, senderGid = selectedRadio.value?.personalGid ?: 0
                            ),
                            commandHeader = GotennaHeaderWrapper(
                                messageTypeWrapper = MessageTypeWrapper.CHAT_MESSAGE,
                                recipientUUID = UUID.randomUUID().toString(),
                                senderGid = selectedRadio.value?.personalGid ?: 0,
                                senderUUID = UUID.randomUUID().toString(),
                                senderCallsign = "test",
                                encryptionParameters = null,
                                uuid = UUID.randomUUID().toString()
                            ),
                        ))
                        if (sent?.executedOrNull() is DeliveryResult.DeliveryCompleted) {
                            mutex.withLock {
                                successCounter++
                            }
                        }
                    }
                    mutex.withLock {
                        deliveryTimes.add(time)
                    }
                    sent
                }
            )
        }
        val totalTime = measureTimeMillis {
            requestList.awaitAll()
        }
        println("send lots of grip: total requests: ${requestList.size}, successful: $successCounter successrate: ${(successCounter.toDouble() / requestList.size.toDouble()) * 100}% average time in ms: ${deliveryTimes.average()} total time in ms: $totalTime")
        logOutput.update { it + "total requests: ${requestList.size}, successful: $successCounter successrate: ${(successCounter/requestList.size) * 100}% average time in ms: ${deliveryTimes.average()} total time in ms: $totalTime\n" }
    }

    private var fileJob: Job? = null

    fun sendFile(gidNumber: String, file: File) {
        println("file job is active: ${fileJob?.isActive}")
        if (fileJob?.isActive == true) {
            fileJob?.cancel()
        } else {
            fileJob = viewModelScope.launch(Dispatchers.IO) {
//        sendHighThroughput1Segment(gidNumber)
                if (gidNumber.isBlank()) {
                    return@launch
                }
                launch {


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
                // testing sending 1 segment messages while file transfer is ongoing
                /*launch {
                    sendHighThroughput1Segment(gidNumber)
                }*/

            }
        }
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
        groupGid = generateRandomizedPersonalGID()
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
    private var pliJob: Job? = null
    fun startPliSending() {
        if (pliJob?.isActive != true) {
            pliJob = viewModelScope.launch(Dispatchers.IO) {
                while (isActive) {
                    selectedRadio.value?.send(
                        SendToNetwork.Location(
                            how = "h-e",
                            staleTime = 60,
                            lat = 35.291802,
                            long = 80.846604,
                            altitude = 237.21325546763973,
                            team = "CYAN",
                            accuracy = 11,
                            creationTime = 1718745135755,
                            messageId = 0,
                            commandMetaData = CommandMetaData(
                                messageType = GTMessageType.BROADCAST,
                                destinationGid = 0,
                                isPeriodic = false,
                                priority = GTMessagePriority.NORMAL,
                                senderGid = selectedRadio.value?.personalGid ?: 0
                            ),
                            commandHeader = GotennaHeaderWrapper(
                                messageTypeWrapper = MessageTypeWrapper.LOCATION,
                                senderGid = selectedRadio.value?.personalGid ?: 0,
                                senderUUID = "ANDROID-2440142b8ac6d5d7",
                                senderCallsign = "${selectedRadio.value?.serialNumber}",
                            ),
                        )
                    )
                    delay(60000)
                }
            }
        } else {
            pliJob?.cancel()
            startPliSending()
        }
    }

    fun stopPliSending() {
        pliJob?.cancel()
    }
    private var testJob: Job? = null
    fun startNodeTestRun() {
        testJob?.cancel()
        testJob = viewModelScope.launch(Dispatchers.IO) {
            val testRunSize = 100
            val size = 61
            logOutput.update { it + "starting grip test run to ${nodesInNetwork.size} nodes with $size bytes file running $testRunSize times\n\n" }
            statsMap.clear()
            val time = measureTimeMillis {
                for (i in 1..testRunSize) {
                    val testFileData = Random.nextBytes(size)
//                    nodesInNetwork.forEach { (serial, destination) ->
                        val test = SendToNetwork.GripFile(
                            data = testFileData,
                            fileName = "",
                            partialData = false,
                            numberOfSegments = 0,
                            senderGid = selectedRadio.value?.personalGid ?: 0,
                            commandMetaData = CommandMetaData(
                                messageType = GTMessageType.BROADCAST,
//                                destinationGid = destination,
                                senderGid = selectedRadio.value?.personalGid ?: 0
                            ),
                            commandHeader = GotennaHeaderWrapper(
                                messageTypeWrapper = MessageTypeWrapper.GRIP_FILE,
                                recipientUUID = "",//UUID.randomUUID().toString(),
                                senderGid = selectedRadio.value?.personalGid ?: 0,
                                senderUUID = UUID.randomUUID().toString(),
                                senderCallsign = "${selectedRadio.value?.serialNumber}",
                            )
                        )
                        /*if (i % 10 == 0) {
                            logOutput.update { it + "Starting run $i for device $serial" }
                        }*/
                        val sendResult = selectedRadio.value?.send(test)
                        val stat = when (val transfer = sendResult?.executedOrNull()) {
                            is DeliveryResult.DeliveryCompleted -> {
                                logOutput.update { it + "transfer marked complete for msgId: ${(transfer.gripResult as GripResult.DeliverySuccess).id}\n\n" }
                                println("SENDER: delivery completed for ${transfer.gripResult}")
                                TransferStat(
                                    sender = true,
                                    successful = sendResult.isSuccess(),
                                    numberOfSegments = (transfer.gripResult as GripResult.DeliverySuccess).numberOfSegments,
                                    numberOfMissingSegments = -1,
                                    numberOfNacks = (transfer.gripResult as GripResult.DeliverySuccess).numberOfRetries,
                                    averageHops = -1,
                                    averageRssi = -1
                                )
                            }
                            is DeliveryResult.DeliveryCanceled -> {
                                logOutput.update { it + "transfer marked cancelled for msgId: ${(transfer.gripResult as GripResult.DeliverySuccess).id}\n\n" }
                                println("SENDER: delivery cancelled for ${transfer.gripResult}")
                                TransferStat(
                                    sender = true,
                                    successful = false,
                                    numberOfSegments = (transfer.gripResult as GripResult.DeliveryCancel).numberOfSegments,
                                    numberOfMissingSegments = -1,
                                    numberOfNacks = (transfer.gripResult as GripResult.DeliveryCancel).numberOfRetries,
                                    averageHops = -1,
                                    averageRssi = -1,
                                    waitingForAck = (transfer.gripResult as GripResult.DeliveryCancel).waitingForAck,
                                    numberOfSentSegments = (transfer.gripResult as GripResult.DeliveryCancel).sentSegments
                                )
                            }
                            else -> {
                                if (sendResult?.getErrorOrNull() is SdkError.FailedToDeliver) {
//                                    logOutput.update { it + "Failed to deliver when sending to $serial msgId: ${((sendResult.getErrorOrNull() as SdkError.FailedToDeliver).gripResult as GripResult.DeliveryCancel).id}\n\n" }
                                    println("SENDER: delivery cancelled for ${sendResult.getErrorOrNull()}")
                                    TransferStat(
                                        sender = true,
                                        successful = false,
                                        numberOfSegments = ((sendResult.getErrorOrNull() as SdkError.FailedToDeliver).gripResult as GripResult.DeliveryCancel).numberOfSegments,
                                        numberOfMissingSegments = 0,
                                        numberOfNacks = ((sendResult.getErrorOrNull() as SdkError.FailedToDeliver).gripResult as GripResult.DeliveryCancel).numberOfRetries,
                                        averageHops = -1,
                                        averageRssi = -1,
                                        waitingForAck = ((sendResult.getErrorOrNull() as SdkError.FailedToDeliver).gripResult as GripResult.DeliveryCancel).waitingForAck,
                                        numberOfSentSegments = ((sendResult.getErrorOrNull() as SdkError.FailedToDeliver).gripResult as GripResult.DeliveryCancel).sentSegments
                                    )
                                } else {
//                                    logOutput.update { it + "Failed to deliver when sending to $serial ${sendResult?.getErrorOrNull()}\n\n" }
                                    println("SENDER: delivery cancelled for ${sendResult?.getErrorOrNull()}")
                                    TransferStat(
                                        sender = true,
                                        successful = false,
                                        numberOfSegments = 0,
                                        numberOfMissingSegments = 0,
                                        numberOfNacks = 0,
                                        averageHops = -1,
                                        averageRssi = -1
                                    )
                                }
                            }
                        }

                        /*if (statsMap.containsKey(serial)) {
                            statsMap[serial]!!.add(stat)
                        } else {
                            statsMap[serial] = mutableListOf(stat)
                        }*/
//                    }

                }
            }


            var output = "Test run completed in ${time/1000}s: \n"
            statsMap.forEach { serial, statList ->
                output += "$serial -> "
                val successfulDeliveryPercent = (statList.count { it.successful }.toDouble() / (statList.size).toDouble()) * 100
                output += "successful delivery rate ${successfulDeliveryPercent}%\n"
                var nackPercent = 0.0
                statList.forEach {
                    nackPercent += it.numberOfNacks.toDouble()
                }
                output += "average nack percentage ${nackPercent / statList.sumOf { it.numberOfSegments }}%\n"
                val averageSentSegments = (statList.sumOf { it.numberOfSentSegments }.toDouble() / statList.sumOf { it.numberOfSegments }) * 100
                output += "average percentage of segments sent ${100 - averageSentSegments}%\n"
                val waitingForAcks = statList.count { it.waitingForAck }
                output += "number of times cancelled because waiting for ack from receiver $waitingForAcks\n\n"
            }
            logOutput.update { it + output }
        }
    }

    fun showReceiverStats() {
        viewModelScope.launch(Dispatchers.IO) {
            var output = "Result data:\n"
            statsMap.forEach { serial, statList ->
                output += "$serial -> "
                val successfulDeliveryPercent = (statList.count { it.successful }.toDouble() / (statList.size).toDouble()) * 100
                output += "successful delivery rate ${successfulDeliveryPercent}% of ${statList.size} transfers\n"
                var nackPercent = 0.0
                var deliveryCompleteRate = 0.0
                var averageHops = 0.0
                var averageRssi = 0.0
                var totalSegments = 0
                statList.forEach {
                    nackPercent += it.numberOfNacks
                    deliveryCompleteRate += (it.numberOfMissingSegments.toDouble() / it.numberOfSegments.toDouble()) * 100
                    averageHops += it.averageHops
                    averageRssi += it.averageRssi
                    totalSegments += it.numberOfSegments
                }
                output += "number of segments expected $totalSegments\n"
                output += "average number of nacks sent per file transfer ${nackPercent / statList.size}\n"
                output += "average amount of file data that delivered ${100 - (deliveryCompleteRate / statList.size)}%\n"
                output += "average hops ${averageHops / (statList.size)}\n"
                output += "number of times sender started a new session ${statList.count { it.newSession }}"
                output += "average rssi ${averageRssi / (statList.size)}\n\n"
            }
            logOutput.update { it + output }
//            statsMap.clear()
        }
    }
}
