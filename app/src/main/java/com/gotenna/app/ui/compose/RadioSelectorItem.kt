package com.gotenna.app.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gotenna.app.ui.theme.Gray

@Composable
fun RadioSelectorItem(
    data: RadioSelectorItem,
    modifier: Modifier = Modifier,
) {
    RadioSelectorItem(
        serialNumber = data.serialNumber,
        gid = data.gid,
        modifier = modifier
    )
}

@Composable
fun RadioSelectorItem(
    serialNumber: String,
    gid: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(4.dp)
    ) {
        Text(
            text = serialNumber,
            style = MaterialTheme.typography.body1,
            color = Gray,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "GID: $gid",
            style = MaterialTheme.typography.body2,
            color = Gray
        )
    }
}

@Preview
@Composable
fun RadioSelectorItemPreview() {
    RadioSelectorItem(
        serialNumber = "MXR193300060",
        gid = "94191159026586",
    )
}