package com.gotenna.app.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

fun <T> MutableStateFlow<List<T>>.replaceItem(targetItem: T, newItem: T) = update { list ->
    list.map {
        if (it == targetItem) {
            newItem
        } else {
            it
        }
    }
}

fun <R> Flow<R>.toStateFlow(coroutineScope: CoroutineScope, initialValue: R) = stateIn(coroutineScope, SharingStarted.Lazily, initialValue)

fun <R> Flow<R>.toMutableStateFlow(coroutineScope: CoroutineScope, initialValue: R) = MutableStateFlow(initialValue).also {
    coroutineScope.launch {
        this@toMutableStateFlow.collect(it)
    }
}