package com.gotenna.app.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gotenna.radio.sdk.common.configuration.GTFrequencyChannel

@Composable
fun AdvancedFrequencyDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: (List<GTFrequencyChannel>) -> Unit,
    dialogTitle: String,
) {
    var controlChannels by remember { mutableStateOf(listOf<String>()) }
    var dataChannels by remember { mutableStateOf( listOf<String>()) }

    AlertDialog(
        modifier = Modifier.fillMaxHeight(),
        title = {
            Text(text = dialogTitle)
        },
        text = {
            LazyColumn {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Control Channels (MHz)")
                        IconButton(onClick = {
                            controlChannels = controlChannels.plus("")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null
                            )
                        }
                    }
                }

                items(controlChannels.size) {
                    TextField(
                        modifier = Modifier.padding(bottom = 8.dp),
                        value = controlChannels[it],
                        placeholder = { Text("448.000") },
                        onValueChange = { str ->
                            controlChannels = controlChannels.toMutableList().apply {
                                this[it] = str
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Data Channels (MHz)")
                        IconButton(onClick = {
                            dataChannels = dataChannels.plus("")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null
                            )
                        }
                    }
                }

                items(dataChannels.size) {
                    TextField(
                        modifier = Modifier.padding(bottom = 8.dp),
                        value = dataChannels[it],
                        placeholder = { Text("450.000") },
                        onValueChange = { str ->
                            dataChannels = dataChannels.toMutableList().apply {
                                this[it] = str
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val channels = listOf(
                        controlChannels.map { GTFrequencyChannel(it.toDouble(), true) },
                        dataChannels.map { GTFrequencyChannel(it.toDouble(), false) }
                    ).flatten()
                    onConfirmation(channels)
                }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
@Preview
fun AdvancedFrequencyDialogPreview() {
    AdvancedFrequencyDialog(
        onDismissRequest = {},
        onConfirmation = {},
        dialogTitle = "Set Frequency Channels",
    )
}