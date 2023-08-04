package com.gotenna.app.util

import com.gotenna.app.model.ListItem

fun List<Any>.toListItems() = map {
    ListItem(it, false)
}