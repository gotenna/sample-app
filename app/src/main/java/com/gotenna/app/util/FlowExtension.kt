package com.gotenna.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

fun <T> MutableStateFlow<List<T>>.replaceItem(targetItem: T, newItem: T) = update { list ->
    list.map {
        if (it == targetItem) {
            newItem
        } else {
            it
        }
    }
}