package com.gotenna.app.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gotenna.app.ui.theme.Gray
import com.gotenna.app.ui.theme.ListItemBackground

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RadioSelector(
    radios: List<RadioSelectorItem>,
    startExpanded: Boolean = false,
    onRadioSelected: (RadioSelectorItem) -> Unit
) {
    var expanded by remember { mutableStateOf(startExpanded) }
    var selectedIndex by remember { mutableStateOf(0) }

    if (radios.isNotEmpty()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                expanded = it
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ListItemBackground),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RadioSelectorItem(
                    data = radios[selectedIndex],
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                )
                Surface(
                    contentColor = Gray,
                    color = ListItemBackground,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                ) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                radios.forEachIndexed { index, radio ->
                    DropdownMenuItem(
                        content = {
                            RadioSelectorItem(
                                data = radio,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                            )
                        },
                        onClick = {
                            selectedIndex = index
                            expanded = false
                            onRadioSelected(radio)
                        }
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun RadioSelectorPreview() {
    val radios = listOf(
        object: RadioSelectorItem {
            override val serialNumber: String
                get() = "1234567890"
            override val gid: String
                get() = "0987654321"
        },
        object: RadioSelectorItem {
            override val serialNumber: String
                get() = "0987654321"
            override val gid: String
                get() = "1234567890"
        }
    )
    RadioSelector(
        radios = radios,
        onRadioSelected = { }
    )
}

@Preview
@Composable
fun RadioSelectorExpandedPreview() {
    val radios = listOf(
        object: RadioSelectorItem {
            override val serialNumber: String
                get() = "1234567890"
            override val gid: String
                get() = "0987654321"
        },
        object: RadioSelectorItem {
            override val serialNumber: String
                get() = "0987654321"
            override val gid: String
                get() = "1234567890"
        }
    )
    RadioSelector(
        radios = radios,
        startExpanded = true,
        onRadioSelected = { }
    )
}

interface RadioSelectorItem{
    val serialNumber: String
    val gid: String
}