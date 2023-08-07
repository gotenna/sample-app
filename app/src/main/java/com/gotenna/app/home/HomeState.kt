package com.gotenna.app.home

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.gotenna.app.model.ListItem

data class HomeState(
    val connectionTypeIndex: State<Int> = mutableStateOf(0),
    val isConnectAvailable: State<Boolean> = mutableStateOf(false),
    val radios: State<List<ListItem>> = mutableStateOf(emptyList()),
    val connectionTypeChangeAction: ((Int) -> Unit)? = null,
    val radioClickAction: ((ListItem) -> Unit)? = null,
    val radioLongClickAction: ((ListItem) -> Unit)? = null,
    val connectRadiosAction: (() -> Unit)? = null,
    val scanRadiosAction: (() -> Unit)? = null
)
