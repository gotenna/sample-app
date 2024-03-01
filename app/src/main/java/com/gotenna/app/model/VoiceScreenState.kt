package com.gotenna.app.model

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.gotenna.radio.sdk.common.models.radio.ht.CodecModes
import com.gotenna.radio.sdk.common.models.radio.ht.SampleMode

data class VoiceScreenState(
    val recording: State<Boolean> = mutableStateOf(false),
    val codec2Modes: State<List<CodecModes>> = mutableStateOf(CodecModes.values().toList()),
    val sampleModes: State<List<SampleMode>> = mutableStateOf(SampleMode.values().toList())
)
