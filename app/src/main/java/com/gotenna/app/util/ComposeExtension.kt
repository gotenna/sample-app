package com.gotenna.app.util

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.clickableWithRipple(clickAction: () -> Unit, longClickAction: (() -> Unit)?) = composed {
    combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = rememberRipple(),
        onClick = { clickAction() },
        onLongClick = { longClickAction?.invoke() }
    )
}