package com.gotenna.app.util

import com.gotenna.app.model.RadioListItem
import com.gotenna.radio.sdk.common.models.radio.RadioModel
import com.gotenna.radio.sdk.common.models.radio.RadioState

fun RadioModel.isConnected() = state == RadioState.CONNECTED

fun RadioModel.isScannedOrDisconnected() = state == RadioState.SCANNED || state == RadioState.DISCONNECTED

fun List<RadioModel>.toListItems() = map { radioModel ->
    RadioListItem(radioModel, false)
}