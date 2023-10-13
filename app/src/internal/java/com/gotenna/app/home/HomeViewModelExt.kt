package com.gotenna.app.home

import androidx.lifecycle.viewModelScope
import com.gotenna.radio.sdk.common.models.ChannelScan
import com.gotenna.radio.sdk.common.models.Dnop
import com.gotenna.radio.sdk.common.models.GetChannelData
import com.gotenna.radio.sdk.common.models.RelayHealthCheckRequest
import com.gotenna.radio.sdk.common.models.radio.CommandMetaData
import com.gotenna.radio.sdk.common.models.radio.GotennaHeaderWrapper
import com.gotenna.radio.sdk.common.models.radio.MessageTypeWrapper
import com.gotenna.radio.sdk.common.results.executedOrNull
import com.gotenna.radio.sdk.common.results.getErrorOrNull
import com.gotenna.radio.sdk.common.results.isSuccess
import com.gotenna.radio.sdk.legacy.sdk.session.messages.GTMessageType
import com.gotenna.radio.sdk.legacy.sdk.transport.responses.RssiScanResult
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

// T2

/**
 * Add extension functions for features that we don't want to expose to our public clients.
 */
fun HomeViewModel.scanChannels() {
    viewModelScope.launch {
        val result = selectedRadio.value?.send(
            ChannelScan(
                scanBand = RssiScanResult.ScanBand.UHF,
                scanWidth = RssiScanResult.ScanWidth.CURRENT_FREQ_SET,
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

fun HomeViewModel.getChannelData() {
    viewModelScope.launch {
        val result = selectedRadio.value?.send(
            GetChannelData()
        )
        val output = if (result?.isSuccess() == true) {
            "Success channel data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure channel data is ${result?.getErrorOrNull()}\n\n"
        }

        logOutput.update { it + output }
    }
}

fun HomeViewModel.sendDnop(privateMessage: Boolean, gidNumber: String = "0") {
    viewModelScope.launch {
        val result = selectedRadio.value?.send(
            Dnop(
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
                    senderGid = selectedRadio.value?.personalGid ?: 0
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

fun HomeViewModel.sendRelayHealthCheck() {
    viewModelScope.launch {
        val result = selectedRadio.value?.send(RelayHealthCheckRequest())
        val output = if (result?.isSuccess() == true) {
            "Success send relay health check returned data is ${result.executedOrNull()}\n\n"
        } else {
            "Failure send relay health check returned data is ${result?.getErrorOrNull()}\n\n"
        }
        logOutput.update { it + output }
    }
}