package com.gotenna.app.ui.compose

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.gotenna.app.MainApplication
import com.gotenna.app.R
import com.gotenna.app.ui.*
import com.gotenna.app.ui.theme.*
import com.gotenna.radio.sdk.common.models.radio.GidType
import com.gotenna.radio.sdk.common.models.radio.RadioModel
import com.gotenna.radio.sdk.legacy.sdk.firmware.GTFirmwareVersion
import com.gotenna.radio.sdk.legacy.sdk.frequency.GTBandwidth
import com.gotenna.radio.sdk.legacy.sdk.frequency.GTFrequencyChannel
import com.gotenna.radio.sdk.legacy.sdk.frequency.GTPowerLevel
import com.gotenna.radio.sdk.common.utils.GIDUtils
import java.io.File
import java.io.FileOutputStream

public const val mobyDickText = "Call me Ishmael. Some years ago—never mind how long precisely—having little or no money in my purse, and nothing particular to interest me on shore, I thought I would sail about a little and see the watery part of the world. It is a way I have of driving off the spleen and regulating the circulation. Whenever I find myself growing grim about the mouth; whenever it is a damp, drizzly November in my soul; whenever I find myself involuntarily pausing before coffin warehouses, and bringing up the rear of every funeral I meet; and especially whenever my hypos get such an upper hand of me, that it requires a strong moral principle to prevent me from deliberately stepping into the street, and methodically knocking people’s hats off—then, I account it high time to get to sea as soon as I can. This is my substitute for pistol and ball. With a philosophical flourish Cato throws himself upon his sword; I quietly take to the ship."

class RadioMessage private constructor(
    val type: Type,
    val isPrivate: Boolean,
    val gid: String? = null
) {

    enum class Type {
        LOCATION,
        CHAT,
        MAP_ITEM,
        VEHICLE,
        CASEVAC,
        NINE_LINE,
        SHAPE,
        CIRCLE,
        ROUTE,
        QR,
        FREQUENCY,
        GROUP,
        FRONT_HAUL,
        DNOP,
        ANY_MESSAGE
    }

    class Builder(
        private val isPrivate: Boolean
    ) {
        private var gid: String? = null

        fun withRecipient(gid: String): Builder {
            this.gid = gid
            return this
        }

        fun build(
            type: Type
        ): RadioMessage {
            return RadioMessage(
                type = type,
                isPrivate = isPrivate,
                gid = gid,
            )
        }
    }
}

@Composable
fun RadioActions(
    currentRadioSerial : String,
    radios: List<RadioModel>,
    onSendFile: (String, File) -> Unit,
    onSendRadioMessage: (RadioMessage) -> Unit,
    onStartScan: () -> Unit,
    onGetScan: () -> Unit,
    onSetNetworkMacMode: (Int, Int, Int) -> Unit,
    onPerformLedBlink: () -> Unit,
    onSetGid: (Long, GidType) -> Unit,
    onDeleteGid: (Long, GidType) -> Unit,
    onSetSdkToken: (String) -> Unit,
    onSetPowerAndBandwidth: (GTPowerLevel, GTBandwidth) -> Unit,
    onGetPowerAndBandwidth: () -> Unit,
    onSetFrequencyChannels: (List<GTFrequencyChannel>) -> Unit,
    onGetFrequencyChannels: () -> Unit,
    onGetDeviceInfo: () -> Unit,
    onGetMCUArch: () -> Unit,
    onInstallFile: (ByteArray, GTFirmwareVersion) -> Unit,
    onSendRelayHealthCheck: () -> Unit
) {
    val context = LocalContext.current
    val gid: Long = GIDUtils.generateSerialGid(currentRadioSerial)
    var gidNumber by remember { mutableStateOf("0") }
    var isShowGidAcquiringDialog by remember { mutableStateOf(false) }
    var pickedFirmwareFile by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pickedFirmwareFile = it.data?.data
    }

    if (isShowGidAcquiringDialog) {
        GidAcquiringDialog(
            dismissAction = { isShowGidAcquiringDialog = false },
            confirmClickAction = { gidNumber = it }
        )
    }

    LazyColumn(
        modifier = Modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Destination Gid: $gidNumber",
                color = Gray
            )
        }

        item {
            val items = radios.map {
                object: RadioSelectorItem {
                    override val serialNumber: String
                        get() {
                            if (it.serialNumber == currentRadioSerial)
                                return "${it.serialNumber} (this radio)"
                            else
                                return it.serialNumber
                        }
                    override val gid: String
                        get() = it.personalGid.toString()
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Recipient Radio",
                    color = Gray
                )

                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(id = R.string.select_by_gid_label),
                        color = Green,
                        fontSize = Small,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clickable {
                                isShowGidAcquiringDialog = true
                            }
                            .padding(horizontal = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            RadioSelector(
                radios = items,
                onRadioSelected = {
                    gidNumber = it.gid
                    onSetGid(it.gid.toLong(), GidType.PRIVATE)
                }
            )
        }

        item {
            DefaultWideButton(text = "Send text file with grip") {
                val file = File(context.filesDir, "mobydick.txt")

                FileOutputStream(file).use { stream ->
                    stream.write(mobyDickText.toByteArray())
                }

                onSendFile(gidNumber, file)
            }
        }

        item {
            DefaultWideButton(text = "Send get mcu arch command") {
                onGetMCUArch()
            }
        }

        item {
            DefaultWideButton(text = "Send relay health check") {
                onSendRelayHealthCheck()
            }
        }

        item {
            pickedFirmwareFile?.let {
                val inputStream = context.contentResolver.openInputStream(it)
                val bytes = inputStream?.readBytes() ?: byteArrayOf()
                Text(text = "Selected file: ${bytes.size}", color = Gray)
                DefaultWideButton(text = "Install selected file") {
                    onInstallFile(bytes, GTFirmwareVersion(128, 0, 69))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            DefaultWideButton(text = "Select firmware file") {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        setType("*/*")
                    }
                launcher.launch(intent)
            }
        }

        item {
            DefaultWideButton(text = "Send Test private Atak Location") {

                val message = RadioMessage.Builder(isPrivate = true)
                    .withRecipient(gidNumber)
                    .build(RadioMessage.Type.LOCATION)

                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send Test broadcast Atak Location") {
                val message = RadioMessage.Builder(isPrivate = false)
                    .build(RadioMessage.Type.LOCATION)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send test broadcast chat message") {
                val message = RadioMessage.Builder(isPrivate = false)
                    .build(RadioMessage.Type.CHAT)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send test private chat message") {
                val message = RadioMessage.Builder(isPrivate = true)
                    .withRecipient(gidNumber)
                    .build(RadioMessage.Type.CHAT)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send broadcast map item") {
                val message = RadioMessage.Builder(isPrivate = false)
                    .build(RadioMessage.Type.MAP_ITEM)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send private map item") {
                val message = RadioMessage.Builder(isPrivate = true)
                    .withRecipient(gidNumber)
                    .build(RadioMessage.Type.MAP_ITEM)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send broadcast vehicle item") {
                val message = RadioMessage.Builder(isPrivate = false)
                    .build(RadioMessage.Type.VEHICLE)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send private vehicle item") {
                val message = RadioMessage.Builder(isPrivate = true)
                    .withRecipient(gidNumber)
                    .build(RadioMessage.Type.VEHICLE)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send broadcast casevac") {
                val message = RadioMessage.Builder(isPrivate = false)
                    .build(RadioMessage.Type.CASEVAC)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send private casevac") {
                val message = RadioMessage.Builder(isPrivate = true)
                    .withRecipient(gidNumber)
                    .build(RadioMessage.Type.CASEVAC)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send broadcast 9line") {
                val message = RadioMessage.Builder(isPrivate = false)
                    .build(RadioMessage.Type.NINE_LINE)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send private 9line") {
                val message = RadioMessage.Builder(isPrivate = true)
                    .withRecipient(gidNumber)
                    .build(RadioMessage.Type.NINE_LINE)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send broadcast shape") {
                val message = RadioMessage.Builder(isPrivate = false)
                    .build(RadioMessage.Type.SHAPE)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send private shape") {
                val message = RadioMessage.Builder(isPrivate = true)
                    .withRecipient(gidNumber)
                    .build(RadioMessage.Type.SHAPE)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send broadcast circle") {
                val message = RadioMessage.Builder(isPrivate = false)
                    .build(RadioMessage.Type.CIRCLE)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send private circle") {
                val message = RadioMessage.Builder(isPrivate = true)
                    .withRecipient(gidNumber)
                    .build(RadioMessage.Type.CIRCLE)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send broadcast route") {
                val message = RadioMessage.Builder(isPrivate = false)
                    .build(RadioMessage.Type.ROUTE)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send private route") {
                val message = RadioMessage.Builder(isPrivate = true)
                    .withRecipient(gidNumber)
                    .build(RadioMessage.Type.ROUTE)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send broadcast Qr") {
                val message = RadioMessage.Builder(isPrivate = false)
                    .build(RadioMessage.Type.QR)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send private Qr") {
                val message = RadioMessage.Builder(isPrivate = true)
                    .withRecipient(gidNumber)
                    .build(RadioMessage.Type.QR)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send broadcast frequency") {
                val message = RadioMessage.Builder(isPrivate = false)
                    .build(RadioMessage.Type.FREQUENCY)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send private frequency") {
                val message = RadioMessage.Builder(isPrivate = true)
                    .withRecipient(gidNumber)
                    .build(RadioMessage.Type.FREQUENCY)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send broadcast group") {
                val message = RadioMessage.Builder(isPrivate = false)
                    .build(RadioMessage.Type.GROUP)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send private group") {
                val message = RadioMessage.Builder(isPrivate = true)
                    .withRecipient(gidNumber)
                    .build(RadioMessage.Type.GROUP)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send broadcast fronthaul chat") {
                val message = RadioMessage.Builder(isPrivate = false)
                    .build(RadioMessage.Type.FRONT_HAUL)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send private fronthaul chat") {
                val message = RadioMessage.Builder(isPrivate = true)
                    .withRecipient(gidNumber)
                    .build(RadioMessage.Type.FRONT_HAUL)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Scan Channels") {
                onStartScan()
            }
        }
        item {
            DefaultWideButton(text = "Get Channel Data") {
                onGetScan()
            }
        }

        item {
            DefaultWideButton(text = "Send broadcast dnop") {
                val message = RadioMessage.Builder(isPrivate = false)
                    .build(RadioMessage.Type.DNOP)
                onSendRadioMessage(message)
            }
        }
        item {
            DefaultWideButton(text = "Send private dnop") {
                val message = RadioMessage.Builder(isPrivate = true)
                    .withRecipient(gidNumber)
                    .build(RadioMessage.Type.DNOP)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send broadcast any message") {
                val message = RadioMessage.Builder(isPrivate = false)
                    .build(RadioMessage.Type.ANY_MESSAGE)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Send private any message") {
                val message = RadioMessage.Builder(isPrivate = true)
                    .withRecipient(gidNumber)
                    .build(RadioMessage.Type.ANY_MESSAGE)
                onSendRadioMessage(message)
            }
        }

        item {
            DefaultWideButton(text = "Set network mode to spin v1") {
                onSetNetworkMacMode(1, 0, 3)
            }
        }

        item {
            DefaultWideButton(text = R.string.led_blick_action) {
                onPerformLedBlink()
            }
        }

        item {
            DefaultWideButton(text = R.string.set_gid_action) {
                onSetGid(gid, GidType.PRIVATE)
            }
        }

        item {
            DefaultWideButton(text = R.string.delete_git_action) {
                onDeleteGid(gid, GidType.PRIVATE)
            }
        }

        item {
            DefaultWideButton(text = R.string.set_token_action) {
                onSetSdkToken(MainApplication.SDK_TOKEN)
            }
        }

        item {
            DefaultWideButton(text = R.string.set_power_action_1) {
                onSetPowerAndBandwidth(GTPowerLevel.ONE_HALF, GTBandwidth.BANDWIDTH_4_54)
            }
        }

        item {
            DefaultWideButton(text = R.string.set_power_action_2) {
                onSetPowerAndBandwidth(GTPowerLevel.ONE, GTBandwidth.BANDWIDTH_7_28)
            }
        }

        item {
            DefaultWideButton(text = R.string.get_power_action) {
                onGetPowerAndBandwidth()
            }
        }

        item {
            DefaultWideButton(text = R.string.set_frequency_action) {
                onSetFrequencyChannels(
                    listOf(
                        GTFrequencyChannel(
                            frequencyHz = 149000000,
                            isControlChannel = true
                        ), GTFrequencyChannel(
                            frequencyHz = 159000000,
                            isControlChannel = false
                        )
                    )
                )
            }
        }

        item {
            DefaultWideButton(text = R.string.get_frequency_action) {
                onGetFrequencyChannels()
            }
        }

        item {
            DefaultWideButton(text = R.string.get_device_info_action) {
                onGetDeviceInfo()
            }
        }
    }
}

@Composable
fun GidAcquiringDialog(dismissAction: () -> Unit, confirmClickAction: (String) -> Unit) {
    val context = LocalContext.current
    var gidString by remember { mutableStateOf("") }

    Dialog(onDismissRequest = dismissAction) {
        Card(backgroundColor = DialogBackground) {
            Column(
                modifier = Modifier
                    .width(400.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    BoldText(text = R.string.select_by_gid_dialog_title, color = White, fontSize = Medium)
                    Spacer(modifier = Modifier.height(16.dp))

                    SimpleTextField(
                        text = gidString,
                        label = R.string.gid_hint,
                        keyboardType = KeyboardType.Number,
                        onValueChange = {
                            if (it.length <= 14) {
                                gidString = it
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Divider()

                Row(modifier = Modifier.height(56.dp)) {
                    Text(
                        text = "Cancel",
                        color = White,
                        fontSize = Small,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clickable { dismissAction() }
                            .wrapContentSize()
                    )

                    VerticalDivider()

                    Text(
                        text = "Confirm",
                        color = White,
                        fontSize = Small,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clickable {
                                if (!GIDUtils.isRandomizedGid(gidString.toLong())) {
                                    showToast(context, R.string.invalid_gid_warning)
                                } else {
                                    confirmClickAction(gidString)
                                    dismissAction()
                                }
                            }
                            .wrapContentSize()
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun RadioActionsPreview() {
    RadioActions(
        currentRadioSerial = "0123456789",
        radios = emptyList(),
        onSendRadioMessage = {},
        onStartScan = {},
        onGetScan = {},
        onSetNetworkMacMode = { _, _, _ -> },
        onPerformLedBlink = {},
        onSetGid = { _, _ -> },
        onDeleteGid = { _, _ -> },
        onSetSdkToken = {},
        onSetPowerAndBandwidth = { _, _ -> },
        onGetPowerAndBandwidth = {},
        onSetFrequencyChannels = {},
        onGetFrequencyChannels = {},
        onGetDeviceInfo = {},
        onSendFile = { _, _ -> },
        onGetMCUArch = {},
        onInstallFile = {_, _ -> },
        onSendRelayHealthCheck = {}
    )
}