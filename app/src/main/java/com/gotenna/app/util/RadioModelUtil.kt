package com.gotenna.app.util

import com.gotenna.app.model.RadioListItem
import com.gotenna.radio.sdk.common.models.RadioModel
import com.gotenna.radio.sdk.common.models.RadioState

fun RadioModel.isConnected() = radioState.value == RadioState.CONNECTED

fun RadioModel.isScannedOrDisconnected() = radioState.value == RadioState.SCANNED || radioState.value == RadioState.DISCONNECTED

fun List<RadioModel>.toListItems() = map { radioModel ->
    RadioListItem(radioModel, false)
}