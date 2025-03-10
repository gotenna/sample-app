package com.gotenna.app.model

import com.gotenna.radio.sdk.common.models.RadioModel

/**
 * For the sample app UI.
 */
data class RadioListItem(
    val radioModel: RadioModel,
    val isSelected: Boolean
)