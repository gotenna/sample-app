@file:OptIn(ExperimentalPermissionsApi::class, ExperimentalPermissionsApi::class)

package com.gotenna.app.ui.compose

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.gotenna.app.R
import com.gotenna.app.home.HomeState
import com.gotenna.app.model.RadioListItem
import com.gotenna.app.ui.*
import com.gotenna.app.ui.theme.*
import com.gotenna.app.util.clickableWithRipple
import com.gotenna.app.util.isConnected
import com.gotenna.radio.sdk.common.models.radio.ConnectionType
import com.gotenna.radio.sdk.common.models.radio.RadioModel

@Preview
@Composable
fun HomeScreenPreview() {
    HomeScreen(state = HomeState(), onNavigateToDetail = {})
}

@Composable
fun HomeScreen(
    state: HomeState,
    onNavigateToDetail: (RadioListItem) -> Unit,
) {
    val permissionsState =
        rememberMultiplePermissionsState(permissions = listOf(Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
    ) {

        SimpleTopAppBar(text = R.string.home_title, backgroundColor = Black)
        if (!permissionsState.allPermissionsGranted) {
            Button(onClick = {permissionsState.launchMultiplePermissionRequest()}) {
                Text("permissions")
            }
        }
        ControlPanel(
            connectionTypeIndex = state.connectionTypeIndex.value,
            connectionTypeChangeActon = {
                state.connectionTypeChangeAction?.let { action ->
                    action(
                        it
                    )
                }
            },
            isShowSelectAllButton = state.radios.value.isNotEmpty() && !state.radios.value.all { it.radioModel.isConnected() },
            isSelectAll = state.isSelectAll.value,
            selectAllCheckAction = { state.selectAllCheckAction?.let { it() } },
            scannedRadiosCount = state.scannedRadiosCount.value,
            connectedRadiosCount = state.connectedRadiosCount.value
        )

        RadioList(
            listItems = state.radios.value,
            itemClickAction = { state.radioClickAction?.let { action -> action(it) } },
            itemLongClickAction = { state.radioLongClickAction?.let { action -> action(it) } },
            navigateAction = {
                onNavigateToDetail.invoke(it)
            }
        )

        WideButton(
            text = if (state.isConnectAvailable.value) R.string.connect_label else R.string.scan_label,
            backgroundColor = Green,
            textColor = Black,
            fraction = 1f,
            clickAction = {
                if (state.isConnectAvailable.value) {
                    state.connectRadiosAction?.invoke()
                } else {
                    state.scanRadiosAction?.invoke()
                }
            }
        )
    }
}

@Composable
fun ControlPanel(
    connectionTypeIndex: Int,
    connectionTypeChangeActon: (Int) -> Unit,
    isShowSelectAllButton: Boolean,
    isSelectAll: Boolean,
    selectAllCheckAction: () -> Unit,
    scannedRadiosCount: Int,
    connectedRadiosCount: Int
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        SimpleText(text = R.string.type_of_connection_hint, color = Gray, fontSize = Small)
        Spacer(modifier = Modifier.height(8.dp))

        ButtonToggleGroup(
            labels = ConnectionType.values().map { it.name },
            backgroundColor = NotSelectedBackground,
            selectedColor = HighlightedBackground,
            selectedIndex = connectionTypeIndex,
            clickAction = { connectionTypeChangeActon(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                SimpleText(text = R.string.select_radios_hint, color = Gray, fontSize = Small)

                if (scannedRadiosCount != 0 || connectedRadiosCount != 0) {
                    SimpleText(
                        text = stringResource(
                            id = R.string.scanned_and_connected_radios_count_text,
                            formatArgs = arrayOf(scannedRadiosCount, connectedRadiosCount)
                        ),
                        color = Green,
                        fontSize = Small
                    )
                }
            }

            if (isShowSelectAllButton) {
                SelectAllButton(isSelectAll, selectAllCheckAction)
            }
        }
    }
}

@Composable
fun SelectAllButton(isChecked: Boolean, checkAction: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        SimpleText(text = R.string.select_all_label, color = Green, fontSize = Small)
        Spacer(modifier = Modifier.width(8.dp))
        SimpleCheckbox(isChecked = isChecked, checkAction = checkAction)
    }
}

@Composable
fun ColumnScope.RadioList(
    listItems: List<RadioListItem>,
    itemClickAction: (RadioListItem) -> Unit,
    itemLongClickAction: (RadioListItem) -> Unit,
    navigateAction: (RadioListItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .background(ListItemBackground)
    ) {
        itemsIndexed(listItems) { index, listItem ->
            RadioItem(
                listItem = listItem,
                index = index,
                clickAction = { itemClickAction(listItem) },
                longClickAction = { itemLongClickAction(listItem) },
                navigateAction = navigateAction
            )

            if (index != listItems.lastIndex) {
                HorizontalDivider(startPadding = 16.dp)
            }
        }
    }
}

@Composable
fun RadioItem(
    listItem: RadioListItem,
    index: Int,
    clickAction: (RadioListItem) -> Unit,
    longClickAction: (RadioListItem) -> Unit,
    navigateAction: (RadioListItem) -> Unit
) {
    val radio = listItem.radioModel
    val isConnected = radio.isConnected()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ListItemBackground)
            .clickableWithRipple(
                clickAction = {
                    if (isConnected) {
                        navigateAction.invoke(listItem)
                    } else {
                        clickAction(listItem)
                    }
                },
                longClickAction = { longClickAction(listItem) }
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                SimpleText(text = "Radio ${index + 1}", color = White, fontSize = Normal)
                Spacer(modifier = Modifier.width(16.dp))

                if (isConnected) {
                    ConnectedIndicator()
                }
            }

            SimpleText(text = "serial ${radio.serialNumber} address ${radio.address}", color = Gray, fontSize = Normal)
        }

        if (!isConnected) {
            SimpleCheckbox(isChecked = listItem.isSelected, checkAction = { clickAction(listItem) })
        }
    }
}