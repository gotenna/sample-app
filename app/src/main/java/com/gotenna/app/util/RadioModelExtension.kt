package com.gotenna.app.util

import com.gotenna.radio.sdk.common.models.radio.RadioModel
import com.gotenna.radio.sdk.common.models.radio.RadioState

fun RadioModel.isConnected() = state == RadioState.CONNECTED