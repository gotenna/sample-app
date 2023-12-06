package com.gotenna.app.ui.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gotenna.app.R
import com.gotenna.app.home.*
import com.gotenna.app.ui.SimpleTopAppBar
import com.gotenna.app.ui.theme.Black
import com.gotenna.app.ui.theme.Gray
import com.gotenna.app.ui.theme.ScreenBackground
import com.gotenna.radio.sdk.common.models.radio.GidType
import com.gotenna.radio.sdk.common.models.radio.RadioModel
import com.gotenna.common.models.SendToNetwork
import com.gotenna.radio.sdk.legacy.sdk.firmware.GTFirmwareVersion
import com.gotenna.radio.sdk.legacy.sdk.frequency.GTBandwidth
import com.gotenna.radio.sdk.legacy.sdk.frequency.GTFrequencyChannel
import com.gotenna.radio.sdk.legacy.sdk.frequency.GTPowerLevel
import com.gotenna.radio.sdk.legacy.sdk.session.properties.Properties
import java.io.File
import java.io.FileOutputStream

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun DetailScreen(
    viewModel: HomeViewModel = viewModel(),
) {
    val logOutput by viewModel.logOutput.collectAsState()
    val radio by viewModel.selectedRadio.collectAsState()
    val gripFile by viewModel.gripFile.collectAsState()
    val radios by viewModel.radioModels.collectAsState()
    val isUpdatingFirmware by viewModel.isUpdatingFirmware.collectAsState()
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DetailScreen(
        radios = radios,
        logOutput = logOutput,
        radioSerialNumber = radio?.serialNumber ?: "",
        gripFile = gripFile,
        onSendFile = viewModel::sendFile,
        onSendLocation = { isPrivate, gidNumber ->
            gidNumber?.let {
                viewModel.sendLocation(isPrivate, it)
            } ?: viewModel.sendLocation(isPrivate)
        },
        onSendChat = { isPrivate, gidNumber ->
            gidNumber?.let {
                viewModel.sendChat(isPrivate, it)
            } ?: viewModel.sendChat(isPrivate)
        },
        onSendMapItem = { isPrivate, gidNumber ->
            gidNumber?.let {
                viewModel.sendMapItem(isPrivate, it)
            } ?: viewModel.sendMapItem(isPrivate)
        },
        onSendVehicle = { isPrivate, gidNumber ->
            gidNumber?.let {
                viewModel.sendVehicle(isPrivate, it)
            } ?: viewModel.sendVehicle(isPrivate)
        },
        onSendCasevac = { isPrivate, gidNumber ->
            gidNumber?.let {
                viewModel.sendCasevac(isPrivate, it)
            } ?: viewModel.sendCasevac(isPrivate)
        },
        onSend9Line = { isPrivate, gidNumber ->
            gidNumber?.let {
                viewModel.send9Line(isPrivate, it)
            } ?: viewModel.send9Line(isPrivate)
        },
        onSendShape = { isPrivate, gidNumber ->
            gidNumber?.let {
                viewModel.sendShape(isPrivate, it)
            } ?: viewModel.sendShape(isPrivate)
        },
        onSendCircle = { isPrivate, gidNumber ->
            gidNumber?.let {
                viewModel.sendCircle(isPrivate, it)
            } ?: viewModel.sendCircle(isPrivate)
        },
        onSendRoute = { isPrivate, gidNumber ->
            gidNumber?.let {
                viewModel.sendRoute(isPrivate, it)
            } ?: viewModel.sendRoute(isPrivate)
        },
        onSendQr = { isPrivate, gidNumber ->
            gidNumber?.let {
                viewModel.sendEncryptionKeyExchange(isPrivate, it)
            } ?: viewModel.sendEncryptionKeyExchange(isPrivate)
        },
        onSendFrequency = { isPrivate, gidNumber ->
            gidNumber?.let {
                viewModel.sendFrequency(isPrivate, it)
            } ?: viewModel.sendFrequency(isPrivate)
        },
        onSendGroup = { isPrivate, gidNumber ->
            gidNumber?.let {
                viewModel.sendGroup(isPrivate, it)
            } ?: viewModel.sendGroup(isPrivate)
        },
        onSendFrontHaul = { isPrivate, gidNumber ->
            gidNumber?.let {
                viewModel.sendFrontHaul(isPrivate, it)
            } ?: viewModel.sendFrontHaul(isPrivate)
        },
        onScanChannels = {
            viewModel.scanChannels()
        },
        onGetChannelData = {
            viewModel.getChannelData()
        },
        onSendDnop = { isPrivate, gidNumber ->
            gidNumber?.let {
                viewModel.sendDnop(isPrivate, it)
            } ?: viewModel.sendDnop(isPrivate)
        },
        onSendAnyMessage = { isPrivate, gidNumber ->
            gidNumber?.let {
                viewModel.sendAnyMessage(isPrivate, it)
            } ?: viewModel.sendAnyMessage(isPrivate)
        },
        onSetNetworkMacMode = viewModel::setNetworkMacMode,
        onPerformLedBlink = viewModel::performLedBlink,
        onSetGid = viewModel::setGid,
        onDeleteGid = viewModel::deleteGid,
        onSetSdkToken = viewModel::setSdkToken,
        onSetPowerAndBandwidth = viewModel::setPowerAndBandwidth,
        onGetPowerAndBandwidth = viewModel::getPowerAndBandwidth,
        onSetFrequencyChannels = viewModel::setFrequencyChannels,
        onGetFrequencyChannels = viewModel::getFrequencyChannels,
        onSetOperationMode = viewModel::setOperationMode,
        onGetDeviceInfo = viewModel::getDeviceInfo,
        onGetMcuArch = {
            viewModel.getMCUArch()
        },
        onInstallFile = viewModel::installFirmwareFile,
        isUpdatingFirmware = isUpdatingFirmware,
        onSendRelayHealthCheck = viewModel::sendRelayHealthCheck,
        onGetTetherMode = viewModel::getTetherMode,
        onSetTetherMode = viewModel::setTetherMode,
        onSetTargetGid = {
            viewModel.gidNumber = it
        },
        sendGroupInvite = {
            viewModel.sendGroupInvitation(it)
        },
        sendGroupChat = {
            viewModel.sendChatToGroup()
        }
    )
}

@Composable
fun DetailScreen(
    radios: List<RadioModel>,
    logOutput: String,
    radioSerialNumber: String,
    gripFile: SendToNetwork.GripFile?,
    onSendFile: (String, File) -> Unit,
    onSendLocation: (Boolean, String?) -> Unit,
    onSetNetworkMacMode: (Int, Int, Int) -> Unit,
    onPerformLedBlink: () -> Unit,
    onSendChat: (Boolean, String?) -> Unit,
    onSendMapItem: (Boolean, String?) -> Unit,
    onSendVehicle: (Boolean, String?) -> Unit,
    onSendCasevac: (Boolean, String?) -> Unit,
    onSend9Line: (Boolean, String?) -> Unit,
    onSendShape: (Boolean, String?) -> Unit,
    onSendCircle: (Boolean, String?) -> Unit,
    onSendRoute: (Boolean, String?) -> Unit,
    onSendQr: (Boolean, String?) -> Unit,
    onSendFrequency: (Boolean, String?) -> Unit,
    onSendGroup: (Boolean, String?) -> Unit,
    onSendFrontHaul: (Boolean, String?) -> Unit,
    onScanChannels: () -> Unit,
    onGetChannelData: () -> Unit,
    onSendDnop: (Boolean, String?) -> Unit,
    onSendAnyMessage: (Boolean, String?) -> Unit,
    onSetGid: (Long, GidType) -> Unit,
    onDeleteGid: (Long, GidType) -> Unit,
    onSetSdkToken: (String) -> Unit,
    onSetPowerAndBandwidth: (GTPowerLevel, GTBandwidth) -> Unit,
    onGetPowerAndBandwidth: () -> Unit,
    onSetFrequencyChannels: (List<GTFrequencyChannel>) -> Unit,
    onGetFrequencyChannels: () -> Unit,
    onSetOperationMode: (Properties.GTOperationMode) -> Unit,
    onGetDeviceInfo: () -> Unit,
    onGetMcuArch: () -> Unit,
    onInstallFile: (ByteArray, GTFirmwareVersion) -> Unit,
    isUpdatingFirmware: Boolean,
    onSendRelayHealthCheck: () -> Unit,
    onGetTetherMode: () -> Unit,
    onSetTetherMode: (Boolean, Int) -> Unit,
    onSetTargetGid: (Long) -> Unit,
    sendGroupInvite: (Long) -> Unit,
    sendGroupChat: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
    ) {
        SimpleTopAppBar(
            text = "S/N: $radioSerialNumber",
            backgroundColor = Black,
            actions = {
                IconButton(onClick = onPerformLedBlink) {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        painter = painterResource(id = R.drawable.led),
                        contentDescription = "Blink LED",
                        tint = Color.White
                    )
                }
            }
        )

        Text(
            text = logOutput,
            modifier = Modifier
                .heightIn(300.dp, 300.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            color = Gray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        if (gripFile?.fileName?.isBlank() == false) {
            val file = File(context.filesDir, gripFile.fileName)
            FileOutputStream(file).use { stream ->
                stream.write(gripFile.data)
            }
        }

        Spacer(
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(Color.LightGray)
        )

        RadioActions(
            currentRadioSerial = radioSerialNumber,
            radios = radios,
            onSendFile = onSendFile,
            onSendRadioMessage = { msg ->
                 when (msg.type) {
                     RadioMessage.Type.LOCATION -> onSendLocation(msg.isPrivate, msg.gid)
                     RadioMessage.Type.CHAT -> onSendChat(msg.isPrivate, msg.gid)
                     RadioMessage.Type.MAP_ITEM -> onSendMapItem(msg.isPrivate, msg.gid)
                     RadioMessage.Type.VEHICLE -> onSendVehicle(msg.isPrivate, msg.gid)
                     RadioMessage.Type.CASEVAC -> onSendCasevac(msg.isPrivate, msg.gid)
                     RadioMessage.Type.NINE_LINE -> onSend9Line(msg.isPrivate, msg.gid)
                     RadioMessage.Type.SHAPE -> onSendShape(msg.isPrivate, msg.gid)
                     RadioMessage.Type.CIRCLE -> onSendCircle(msg.isPrivate, msg.gid)
                     RadioMessage.Type.ROUTE -> onSendRoute(msg.isPrivate, msg.gid)
                     RadioMessage.Type.QR -> onSendQr(msg.isPrivate, msg.gid)
                     RadioMessage.Type.FREQUENCY -> onSendFrequency(msg.isPrivate, msg.gid)
                     RadioMessage.Type.GROUP -> onSendGroup(msg.isPrivate, msg.gid)
                     RadioMessage.Type.FRONT_HAUL -> onSendFrontHaul(msg.isPrivate, msg.gid)
                     RadioMessage.Type.DNOP -> onSendDnop(msg.isPrivate, msg.gid)
                     RadioMessage.Type.ANY_MESSAGE -> onSendAnyMessage(msg.isPrivate, msg.gid)
                 }
            },
            onStartScan = onScanChannels,
            onGetScan = onGetChannelData,
            onSetNetworkMacMode = onSetNetworkMacMode,
            onPerformLedBlink = onPerformLedBlink,
            onSetGid = onSetGid,
            onDeleteGid = onDeleteGid,
            onSetSdkToken = onSetSdkToken,
            onSetPowerAndBandwidth = onSetPowerAndBandwidth,
            onGetPowerAndBandwidth = onGetPowerAndBandwidth,
            onSetFrequencyChannels = onSetFrequencyChannels,
            onGetFrequencyChannels = onGetFrequencyChannels,
            onSetOperationMode = onSetOperationMode,
            onGetDeviceInfo = onGetDeviceInfo,
            onGetMCUArch = {
                onGetMcuArch()
            },
            onInstallFile = onInstallFile,
            isUpdatingFirmware = isUpdatingFirmware,
            onSendRelayHealthCheck = onSendRelayHealthCheck,
            onGetTetherMode = onGetTetherMode,
            onSetTetherMode = onSetTetherMode,
            onSetTargetGid = onSetTargetGid,
            sendGroupInvite = sendGroupInvite,
            sendGroupChat = sendGroupChat
        )

    }
}

@Preview
@Composable
fun DetailScreenPreview() {
    DetailScreen(
        radios = emptyList(),
        logOutput = "Log output",
        radioSerialNumber = "GT-1234567890",
        gripFile = null,
        onSendFile = { _, _ -> },
        onSendLocation = { _, _ -> },
        onSendChat = { _, _ -> },
        onSendMapItem = { _, _ -> },
        onSendVehicle = { _, _ -> },
        onSendCasevac = { _, _ -> },
        onSend9Line = { _, _ -> },
        onSendShape = { _, _ -> },
        onSendCircle = { _, _ -> },
        onSendRoute = { _, _ -> },
        onSendQr = { _, _ -> },
        onSendFrequency = { _, _ -> },
        onSendGroup = { _, _ -> },
        onSendFrontHaul = { _, _ -> },
        onScanChannels = {},
        onGetChannelData = {},
        onSendDnop = { _, _ -> },
        onSendAnyMessage = { _, _ -> },
        onSetNetworkMacMode = { _, _, _ -> },
        onPerformLedBlink = { },
        onSetGid = { _, _ -> },
        onDeleteGid = { _, _ -> },
        onSetSdkToken = { },
        onSetPowerAndBandwidth = { _, _ -> },
        onGetPowerAndBandwidth = { },
        onSetFrequencyChannels = { },
        onGetFrequencyChannels = { },
        onSetOperationMode = { },
        onGetDeviceInfo = { },
        onGetMcuArch = {},
        onInstallFile = {_, _ -> },
        isUpdatingFirmware = false,
        onSendRelayHealthCheck = {},
        onGetTetherMode = {},
        onSetTetherMode = {_, _ -> },
        onSetTargetGid = {},
        sendGroupInvite = {},
        sendGroupChat = {}
    )
}