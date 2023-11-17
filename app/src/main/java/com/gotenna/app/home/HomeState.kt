package com.gotenna.app.home

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.gotenna.app.model.RadioListItem

data class HomeState(
    val connectionTypeIndex: State<Int> = mutableStateOf(0),
    val isConnectAvailable: State<Boolean> = mutableStateOf(false),
    val radios: State<List<RadioListItem>> = mutableStateOf(emptyList()),
    val isSelectAll: State<Boolean> = mutableStateOf(false),
    val connectionTypeChangeAction: ((Int) -> Unit)? = null,
    val radioClickAction: ((RadioListItem) -> Unit)? = null,
    val radioLongClickAction: ((RadioListItem) -> Unit)? = null,
    val connectRadiosAction: (() -> Unit)? = null,
    val scanRadiosAction: (() -> Unit)? = null,
    val selectAllCheckAction: (() -> Unit)? = null,
    val scannedRadiosCount: State<Int> = mutableStateOf(0),
    val connectedRadiosCount: State<Int> = mutableStateOf(0)
)