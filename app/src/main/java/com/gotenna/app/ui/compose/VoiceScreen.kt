@file:OptIn(ExperimentalPermissionsApi::class, ExperimentalPermissionsApi::class)

package com.gotenna.app.ui.compose

import android.Manifest
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.gotenna.app.home.HomeViewModel
import com.gotenna.app.home.startPlayback
import com.gotenna.app.home.startPlaybackEncoded
import com.gotenna.app.home.startRecording
import com.gotenna.app.home.stopRecording
import com.gotenna.app.home.updateCodec2Settings
import com.gotenna.app.model.VoiceScreenState
import com.gotenna.app.ui.DefaultWideButton
import com.gotenna.app.ui.SimpleTopAppBar
import com.gotenna.app.ui.theme.Black
import com.gotenna.app.ui.theme.DialogBackground
import com.gotenna.app.ui.theme.Gray
import com.gotenna.app.ui.theme.Green
import com.gotenna.app.ui.theme.Red
import com.gotenna.app.ui.theme.ScreenBackground
import com.gotenna.radio.sdk.common.models.radio.ht.CodecModes
import com.gotenna.radio.sdk.common.models.radio.ht.HTAudioManager
import com.gotenna.radio.sdk.common.models.radio.ht.SampleMode

@Composable
fun VoiceScreen(state: VoiceScreenState, viewmodel: HomeViewModel, context: Context) {
    val permissionsState =
        rememberMultiplePermissionsState(
            permissions = listOf(
                Manifest.permission.RECORD_AUDIO
            )
        )

    Column(
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), enabled = true)
            .background(ScreenBackground)
            .padding(horizontal = 8.dp)
    ) {
        SimpleTopAppBar(text = "Voice transmission", backgroundColor = Black) {

        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!permissionsState.allPermissionsGranted) {
            DefaultWideButton(text = "Enable Permissions") {
                permissionsState.launchMultiplePermissionRequest()
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            HTAudioManager.initializeAudioManager(CodecModes.CODEC2_MODE_1600, SampleMode.SAMPLE_16000, context = context)
        }

        Text(
            text = "Press the play button after granting permissions to begin recording audio.",
            color = Gray
        )

        Spacer(modifier = Modifier.height(8.dp))

        DefaultWideButton(
            text = if (!state.recording.value) "Record" else "Stop",
            backgroundColor = if (!state.recording.value) Green else Red
        ) {
            if (state.recording.value) {
                viewmodel.stopRecording()
            } else {
                viewmodel.startRecording()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        DefaultWideButton(text = "Play encoded audio locally") {
            viewmodel.startPlaybackEncoded()
        }
        Spacer(modifier = Modifier.height(8.dp))

        DefaultWideButton(text = "Play raw audio locally") {
            viewmodel.startPlayback()
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Adjust the Codec2 settings below if needed before recording audio.",
            color = Gray
        )

        Spacer(modifier = Modifier.height(8.dp))

        var expanded by remember { mutableStateOf(false) }
        val items = state.codec2Modes
        var selectedIndex by remember {
            mutableStateOf(0)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .fillMaxWidth()
                .clickable(onClick = { expanded = true })
        ) {
            Text(
                text = "Codec2 mode: ${items.value[selectedIndex].name}",
                color = Gray
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DialogBackground)
            ) {
                items.value.forEachIndexed { index, mode ->
                    DropdownMenuItem(onClick = {
                        selectedIndex = index
                        expanded = false
                    }) {
                        Text(text = mode.name, color = Gray)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        var sampleModeExpanded by remember { mutableStateOf(false) }
        val sampleModes = SampleMode.values()
        var sampleSelectedIndex by remember {
            mutableStateOf(0)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .fillMaxWidth()
                .clickable(onClick = { sampleModeExpanded = true })
        ) {
            Text(
                text = "Codec2 Sample Mode: ${sampleModes[sampleSelectedIndex].name}",
                color = Gray
            )
            DropdownMenu(
                expanded = sampleModeExpanded,
                onDismissRequest = { sampleModeExpanded = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DialogBackground)
            ) {
                sampleModes.forEachIndexed { index, mode ->
                    DropdownMenuItem(onClick = {
                        sampleSelectedIndex = index
                        sampleModeExpanded = false
                    }) {
                        Text(text = mode.name, color = Gray)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        createTextField({ newValue -> viewmodel.updatedAudioSampleRate(newValue) }, viewmodel.audioSampleRate, "Enter the audio sample rate", "16000" )
        Spacer(modifier = Modifier.height(8.dp))

        DefaultWideButton(text = "Apply settings") {
            viewmodel.updateCodec2Settings(items.value[selectedIndex], sampleModes[sampleSelectedIndex])
        }
    }

}

@Composable
fun createTextField(viewmodelUpdate: (String) -> Unit, textState: String, label: String, placeholder: String) {
    TextField(value = textState, onValueChange = {
        viewmodelUpdate(it)
    }, label = {
        Text(text = label)
    }, placeholder = {
        Text(text = placeholder)
    }, colors = TextFieldDefaults.textFieldColors(
        textColor = Gray,
        placeholderColor = Gray,
        focusedIndicatorColor = Gray,
        unfocusedIndicatorColor = Gray,
        disabledLabelColor = Gray,
        focusedLabelColor = Gray,
        errorLabelColor = Red,
        disabledPlaceholderColor = Gray,
        disabledTextColor = Gray,
        backgroundColor = ScreenBackground,
        unfocusedLabelColor = Gray,
    ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}