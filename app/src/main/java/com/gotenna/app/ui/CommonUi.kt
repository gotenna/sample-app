package com.gotenna.app.ui

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.gotenna.app.R
import com.gotenna.app.ui.theme.*
import kotlinx.coroutines.delay
import java.lang.IllegalArgumentException

@Composable
fun SimpleText(text: Any, color: Color, fontSize: TextUnit, clickAction: (() -> Unit)? = null) {
    val string = when (text) {
        is String -> text

        is Int -> stringResource(text)

        else -> throw IllegalArgumentException()
    }

    Text(
        text = string,
        color = color,
        fontSize = fontSize,
        modifier = clickAction?.let { Modifier.clickable { it() } } ?: Modifier
    )
}

@Composable
fun BoldText(text: Int, color: Color, fontSize: TextUnit) {
    Text(
        text = stringResource(text),
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SimpleTextField(
    text: String,
    label: Int,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    TextField(
        value = text,
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        onValueChange = { onValueChange(it) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = White),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    )

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
        delay(100)
        keyboard?.show()
    }
}

@Composable
fun SimpleImage(image: Int, description: Int, color: Color? = null, clickAction: (() -> Unit)? = null) {
    Image(
        painter = painterResource(image),
        contentDescription = stringResource(description),
        colorFilter = color?.let { ColorFilter.tint(it) },
        modifier = clickAction?.let { Modifier.clickable { it() } } ?: Modifier
    )
}

@Composable
fun SimpleCheckbox(isChecked: Boolean, checkAction: () -> Unit) {
    Image(
        painter = if (isChecked) {
            painterResource(R.drawable.ic_radio_button_checked)
        } else {
            painterResource(R.drawable.ic_radio_button_unchecked)
        },
        contentDescription = stringResource(R.string.checkbox_description),
        modifier = Modifier.clickable { checkAction() }
    )
}

@Composable
fun SimpleButton(text: Int, backgroundColor: Color, textColor: Color, width: Dp? = null, clickAction: () -> Unit) {
    Button(
        onClick = clickAction,
        colors = ButtonDefaults.buttonColors(backgroundColor),
        modifier = width?.let { Modifier.width(it) } ?: Modifier
    ) {
        Text(
            text = stringResource(text),
            color = textColor,
        )
    }
}

@Composable
fun WideButton(text: Int, backgroundColor: Color, textColor: Color, fraction: Float, clickAction: () -> Unit) {
    Button(
        onClick = clickAction,
        colors = ButtonDefaults.buttonColors(backgroundColor),
        modifier = Modifier.fillMaxWidth(fraction),
        contentPadding = PaddingValues(vertical = 16.dp),
        shape = if (fraction == 1f) RectangleShape else RoundedCornerShape(4.dp)
    ) {
        Text(
            text = stringResource(text),
            color = textColor,
            fontSize = Normal
        )
    }
}

@Composable
fun DefaultWideButton(text: String, backgroundColor: Color = Green, textColor: Color = Black, fraction: Float = 1f, clickAction: () -> Unit) {
    Button(
        onClick = clickAction,
        colors = ButtonDefaults.buttonColors(backgroundColor),
        modifier = Modifier.fillMaxWidth(fraction),
        contentPadding = PaddingValues(vertical = 16.dp),
        shape = if (fraction == 1f) RectangleShape else RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = Normal
        )
    }
}

@Composable
fun DefaultWideButton(@StringRes text: Int, backgroundColor: Color = Green, textColor: Color = Black, fraction: Float = 1f, clickAction: () -> Unit) {
    Button(
        onClick = clickAction,
        colors = ButtonDefaults.buttonColors(backgroundColor),
        modifier = Modifier.fillMaxWidth(fraction),
        contentPadding = PaddingValues(vertical = 16.dp),
        shape = if (fraction == 1f) RectangleShape else RoundedCornerShape(4.dp)
    ) {
        Text(
            text = stringResource(text),
            color = textColor,
            fontSize = Normal
        )
    }
}

@Composable
fun SimpleAlertDialog(
    title: Int,
    message: Int,
    confirmText: Int,
    dismissText: Int,
    isCancelable: Boolean,
    confirmAction: () -> Unit,
    dismissAction: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (isCancelable) {
                dismissAction()
            }
        },
        title = {
            Text(text = stringResource(title))
        },
        text = {
            Text(text = stringResource(message))
        },
        confirmButton = {
            TextButton(onClick = confirmAction) {
                Text(text = stringResource(confirmText))
            }
        },
        dismissButton = {
            TextButton(onClick = dismissAction) {
                Text(text = stringResource(dismissText))
            }
        }
    )
}

@Composable
fun VerticalDivider(fraction: Float = 1f) {
    Spacer(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight(fraction)
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
    )
}

@Composable
fun HorizontalDivider(startPadding: Dp) {
    Divider(
        color = DividerBackground,
        modifier = Modifier.padding(start = startPadding)
    )
}

@Composable
fun SimpleTopAppBar(text: Any, backgroundColor: Color) {
    TopAppBar(
        title = {
            SimpleText(text = text, color = White, fontSize = Medium)
        },
        backgroundColor = backgroundColor
    )
}

@Composable
fun ButtonToggleGroup(
    labels: List<String>,
    backgroundColor: Color,
    selectedColor: Color,
    selectedIndex: Int,
    fraction: Float? = null,
    clickAction: (Int) -> Unit
) {
    Row(
        modifier = fraction?.let { Modifier.fillMaxWidth(it) } ?: Modifier
    ) {
        labels.forEachIndexed { index, label ->
            OutlinedButton(
                onClick = { clickAction(index) },
                shape = when(index) {
                    0 -> GroupLeftRoundedCornerShape

                    labels.lastIndex -> GroupRightRoundedCornerShape

                    else -> RectangleShape
                },
                colors = if (index == selectedIndex) {
                    ButtonDefaults.buttonColors(selectedColor)
                } else {
                    ButtonDefaults.buttonColors(backgroundColor)
                },
                modifier = Modifier
                    .weight(1f)
                    .offset((-1 * index).dp, 0.dp)
            ) {
                SimpleText(text = label, color = White, fontSize = Small)
            }
        }
    }
}

@Composable
fun ConnectedIndicator() {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(Green)
    )
}

fun showToast(context: Context, text: Any, length: Int = Toast.LENGTH_SHORT) {
    val string = when (text) {
        is String -> text

        is Int -> context.getString(text)

        else -> throw IllegalArgumentException()
    }

    Toast.makeText(context, string, length).show()
}