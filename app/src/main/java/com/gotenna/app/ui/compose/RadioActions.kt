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
import com.gotenna.radio.sdk.legacy.sdk.session.properties.Properties
import java.io.File
import java.io.FileOutputStream
import kotlin.io.path.outputStream

public const val mobyDickText = "Call me Ishmael. Some years ago—never mind how long precisely—having little or no money in my purse, and nothing particular to interest me on shore, I thought I would sail about a little and see the watery part of the world. It is a way I have of driving off the spleen and regulating the circulation. Whenever I find myself growing grim about the mouth; whenever it is a damp, drizzly November in my soul; whenever I find myself involuntarily pausing before coffin warehouses, and bringing up the rear of every funeral I meet; and especially whenever my hypos get such an upper hand of me, that it requires a strong moral principle to prevent me from deliberately stepping into the street, and methodically knocking people’s hats off—then, I account it high time to get to sea as soon as I can. This is my substitute for pistol and ball. With a philosophical flourish Cato throws himself upon his sword; I quietly take to the ship. There is nothing surprising in this. If they but knew it, almost all men in their degree, some time or other, cherish very nearly the same feelings towards the ocean with me.\n" +
        "\n" +
        "There now is your insular city of the Manhattoes, belted round by wharves as Indian isles by coral reefs—commerce surrounds it with her surf. Right and left, the streets take you waterward. Its extreme downtown is the battery, where that noble mole is washed by waves, and cooled by breezes, which a few hours previous were out of sight of land. Look at the crowds of water-gazers there.\n" //+
/*
        "\n" +
        "Circumambulate the city of a dreamy Sabbath afternoon. Go from Corlears Hook to Coenties Slip, and from thence, by Whitehall, northward. What do you see?—Posted like silent sentinels all around the town, stand thousands upon thousands of mortal men fixed in ocean reveries. Some leaning against the spiles; some seated upon the pier-heads; some looking over the bulwarks of ships from China; some high aloft in the rigging, as if striving to get a still better seaward peep. But these are all landsmen; of week days pent up in lath and plaster—tied to counters, nailed to benches, clinched to desks. How then is this? Are the green fields gone? What do they here?\n" +
        "\n" +
        "But look! here come more crowds, pacing straight for the water, and seemingly bound for a dive. Strange! Nothing will content them but the extremest limit of the land; loitering under the shady lee of yonder warehouses will not suffice. No. They must get just as nigh the water as they possibly can without falling in. And there they stand—miles of them—leagues. Inlanders all, they come from lanes and alleys, streets and avenues—north, east, south, and west. Yet here they all unite. Tell me, does the magnetic virtue of the needles of the compasses of all those ships attract them thither?\n" +
        "\n" +
        "Once more. Say you are in the country; in some high land of lakes. Take almost any path you please, and ten to one it carries you down in a dale, and leaves you there by a pool in the stream. There is magic in it. Let the most absent-minded of men be plunged in his deepest reveries—stand that man on his legs, set his feet a-going, and he will infallibly lead you to water, if water there be in all that region. Should you ever be athirst in the great American desert, try this experiment, if your caravan happen to be supplied with a metaphysical professor. Yes, as every one knows, meditation and water are wedded for ever.\n" +
        "\n" +
        "But here is an artist. He desires to paint you the dreamiest, shadiest, quietest, most enchanting bit of romantic landscape in all the valley of the Saco. What is the chief element he employs? There stand his trees, each with a hollow trunk, as if a hermit and a crucifix were within; and here sleeps his meadow, and there sleep his cattle; and up from yonder cottage goes a sleepy smoke. Deep into distant woodlands winds a mazy way, reaching to overlapping spurs of mountains bathed in their hill-side blue. But though the picture lies thus tranced, and though this pine-tree shakes down its sighs like leaves upon this shepherd’s head, yet all were vain, unless the shepherd’s eye were fixed upon the magic stream before him. Go visit the Prairies in June, when for scores on scores of miles you wade knee-deep among Tiger-lilies—what is the one charm wanting?—Water—there is not a drop of water there! Were Niagara but a cataract of sand, would you travel your thousand miles to see it? Why did the poor poet of Tennessee, upon suddenly receiving two handfuls of silver, deliberate whether to buy him a coat, which he sadly needed, or invest his money in a pedestrian trip to Rockaway Beach? Why is almost every robust healthy boy with a robust healthy soul in him, at some time or other crazy to go to sea? Why upon your first voyage as a passenger, did you yourself feel such a mystical vibration, when first told that you and your ship were now out of sight of land? Why did the old Persians hold the sea holy? Why did the Greeks give it a separate deity, and own brother of Jove? Surely all this is not without meaning. And still deeper the meaning of that story of Narcissus, who because he could not grasp the tormenting, mild image he saw in the fountain, plunged into it and was drowned. But that same image, we ourselves see in all rivers and oceans. It is the image of the ungraspable phantom of life; and this is the key to it all.\n" +
        "\n" +
        "Now, when I say that I am in the habit of going to sea whenever I begin to grow hazy about the eyes, and begin to be over conscious of my lungs, I do not mean to have it inferred that I ever go to sea as a passenger. For to go as a passenger you must needs have a purse, and a purse is but a rag unless you have something in it. Besides, passengers get sea-sick—grow quarrelsome—don’t sleep of nights—do not enjoy themselves much, as a general thing;—no, I never go as a passenger; nor, though I am something of a salt, do I ever go to sea as a Commodore, or a Captain, or a Cook. I abandon the glory and distinction of such offices to those who like them. For my part, I abominate all honorable respectable toils, trials, and tribulations of every kind whatsoever. It is quite as much as I can do to take care of myself, without taking care of ships, barques, brigs, schooners, and what not. And as for going as cook,—though I confess there is considerable glory in that, a cook being a sort of officer on ship-board—yet, somehow, I never fancied broiling fowls;—though once broiled, judiciously buttered, and judgmatically salted and peppered, there is no one who will speak more respectfully, not to say reverentially, of a broiled fowl than I will. It is out of the idolatrous dotings of the old Egyptians upon broiled ibis and roasted river horse, that you see the mummies of those creatures in their huge bake-houses the pyramids.\n" +
        "\n" +
        "No, when I go to sea, I go as a simple sailor, right before the mast, plumb down into the forecastle, aloft there to the royal mast-head. True, they rather order me about some, and make me jump from spar to spar, like a grasshopper in a May meadow. And at first, this sort of thing is unpleasant enough. It touches one’s sense of honor, particularly if you come of an old established family in the land, the Van Rensselaers, or Randolphs, or Hardicanutes. And more than all, if just previous to putting your hand into the tar-pot, you have been lording it as a country schoolmaster, making the tallest boys stand in awe of you. The transition is a keen one, I assure you, from a schoolmaster to a sailor, and requires a strong decoction of Seneca and the Stoics to enable you to grin and bear it. But even this wears off in time.\n" +
        "\n" +
        "What of it, if some old hunks of a sea-captain orders me to get a broom and sweep down the decks? What does that indignity amount to, weighed, I mean, in the scales of the New Testament? Do you think the archangel Gabriel thinks anything the less of me, because I promptly and respectfully obey that old hunks in that particular instance? Who ain’t a slave? Tell me that. Well, then, however the old sea-captains may order me about—however they may thump and punch me about, I have the satisfaction of knowing that it is all right; that everybody else is one way or other served in much the same way—either in a physical or metaphysical point of view, that is; and so the universal thump is passed round, and all hands should rub each other’s shoulder-blades, and be content.\n" +
        "\n" +
        "Again, I always go to sea as a sailor, because they make a point of paying me for my trouble, whereas they never pay passengers a single penny that I ever heard of. On the contrary, passengers themselves must pay. And there is all the difference in the world between paying and being paid. The act of paying is perhaps the most uncomfortable infliction that the two orchard thieves entailed upon us. But being paid,—what will compare with it? The urbane activity with which a man receives money is really marvellous, considering that we so earnestly believe money to be the root of all earthly ills, and that on no account can a monied man enter heaven. Ah! how cheerfully we consign ourselves to perdition!\n" +
        "\n" +
        "Finally, I always go to sea as a sailor, because of the wholesome exercise and pure air of the fore-castle deck. For as in this world, head winds are far more prevalent than winds from astern (that is, if you never violate the Pythagorean maxim), so for the most part the Commodore on the quarter-deck gets his atmosphere at second hand from the sailors on the forecastle. He thinks he breathes it first; but not so. In much the same way do the commonalty lead their leaders in many other things, at the same time that the leaders little suspect it. But wherefore it was that after having repeatedly smelt the sea as a merchant sailor, I should now take it into my head to go on a whaling voyage; this the invisible police officer of the Fates, who has the constant surveillance of me, and secretly dogs me, and influences me in some unaccountable way—he can better answer than any one else. And, doubtless, my going on this whaling voyage, formed part of the grand programme of Providence that was drawn up a long time ago. It came in as a sort of brief interlude and solo between more extensive performances. I take it that this part of the bill must have run something like this:\n" +
        "\n" +
        "“Grand Contested Election for the Presidency of the United States. “WHALING VOYAGE BY ONE ISHMAEL. “BLOODY BATTLE IN AFFGHANISTAN.”\n" +
        "\n" +
        "Though I cannot tell why it was exactly that those stage managers, the Fates, put me down for this shabby part of a whaling voyage, when others were set down for magnificent parts in high tragedies, and short and easy parts in genteel comedies, and jolly parts in farces—though I cannot tell why this was exactly; yet, now that I recall all the circumstances, I think I can see a little into the springs and motives which being cunningly presented to me under various disguises, induced me to set about performing the part I did, besides cajoling me into the delusion that it was a choice resulting from my own unbiased freewill and discriminating judgment."
*/

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
    onSetFrequencyChannelsAdvanced: () -> Unit,
    onGetFrequencyChannels: () -> Unit,
    onSetOperationMode: (Properties.GTOperationMode) -> Unit,
    onGetDeviceInfo: () -> Unit,
    onGetMCUArch: () -> Unit,
    onInstallFile: (ByteArray, GTFirmwareVersion) -> Unit,
    isUpdatingFirmware: Boolean,
    onSendRelayHealthCheck: () -> Unit,
    onGetTetherMode: () -> Unit,
    onSetTetherMode: (Boolean, Int) -> Unit,
    onSetTargetGid: (Long) -> Unit,
    sendGroupInvite: (Long) -> Unit,
    sendGroupChat: () -> Unit,
    navigateToComms: () -> Unit,
    onSendGripFile: (File, String) -> Unit
) {
    val context = LocalContext.current
    val gid: Long = GIDUtils.generateSerialGid(currentRadioSerial)
    var gidNumber by remember { mutableStateOf("0") }
    var isShowGidAcquiringDialog by remember { mutableStateOf(false) }
    var pickedFirmwareFile by remember { mutableStateOf<Uri?>(null) }
    var pickedGripFile by remember {
        mutableStateOf<Uri?>(null)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pickedFirmwareFile = it.data?.data
    }
    val gripFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pickedGripFile = it.data?.data
    }

    if (isShowGidAcquiringDialog) {
        GidAcquiringDialog(
            dismissAction = { isShowGidAcquiringDialog = false },
            confirmClickAction = {
                gidNumber = it
                onSetTargetGid.invoke(it.toLong())
            }
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
            DefaultWideButton(text = "Comms") {
                navigateToComms()
            }
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
            pickedGripFile?.let {
                val tempFile = kotlin.io.path.createTempFile(suffix = ".jpeg")
                context.contentResolver.openInputStream(it).use { input ->
                    tempFile.outputStream().use { output ->
                        input?.copyTo(output)
                    }
                }
                val file = tempFile.toFile()
                Text(text = "Selected file size: ${file.length()}bytes uri: ${it.lastPathSegment}", color = Gray)
                DefaultWideButton(text = "Send grip file") {
                    onSendGripFile(file, gidNumber)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            DefaultWideButton(text = "Select grip file") {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        setType("*/*")
                    }
                gripFileLauncher.launch(intent)
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
                DefaultWideButton(text = if (isUpdatingFirmware) "Cancel installing" else "Install selected file") {
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
            DefaultWideButton(text = "Send group invite") {
                sendGroupInvite(gidNumber.toLong())
            }
        }

        item {
            DefaultWideButton(text = "Send group chat") {
                sendGroupChat.invoke()
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
            DefaultWideButton(text = R.string.set_power_action_1_7_28) {
                onSetPowerAndBandwidth(GTPowerLevel.ONE, GTBandwidth.BANDWIDTH_7_28)
            }
        }

        item {
            DefaultWideButton(text = R.string.set_power_action_0_5_11_80) {
                onSetPowerAndBandwidth(GTPowerLevel.ONE_HALF, GTBandwidth.BANDWIDTH_11_8)
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
            DefaultWideButton(text = "Set Frequency (Advanced)") {
                onSetFrequencyChannelsAdvanced()
            }
        }
        item {
            DefaultWideButton(text = "Set over-water frequencies") {
                onSetFrequencyChannels(
                    listOf(
                        GTFrequencyChannel(448.000, true),
                        GTFrequencyChannel(458.000, true),
                        GTFrequencyChannel(468.000, true),
                        GTFrequencyChannel(450.000, false),
                        GTFrequencyChannel(452.000, false),
                        GTFrequencyChannel(454.000, false),
                        GTFrequencyChannel(456.000, false),
                        GTFrequencyChannel(460.000, false),
                        GTFrequencyChannel(462.000, false),
                        GTFrequencyChannel(464.000, false),
                        GTFrequencyChannel(466.000, false),
                        GTFrequencyChannel(470.000, false),
                        GTFrequencyChannel(472.000, false),
                        GTFrequencyChannel(474.000, false),
                        GTFrequencyChannel(476.000, false),
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

        item {
            DefaultWideButton(text = "Set Op Mode to RELAY") {
                onSetOperationMode(Properties.GTOperationMode.RELAY)
            }
        }

        item {
            DefaultWideButton(text = "Get Tether Mode") {
                onGetTetherMode()
            }
        }

        item {
            DefaultWideButton(text = "Enable Tether Mode") {
                onSetTetherMode(true, 20)
            }
        }

        item {
            DefaultWideButton(text = "Disable Tether Mode") {
                onSetTetherMode(false, 0)
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
                            if (it.length <= 15) {
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
        onSetFrequencyChannelsAdvanced = {},
        onGetFrequencyChannels = {},
        onSetOperationMode = {},
        onGetDeviceInfo = {},
        onSendFile = { _, _ -> },
        onGetMCUArch = {},
        onInstallFile = {_, _ -> },
        isUpdatingFirmware = false,
        onSendRelayHealthCheck = {},
        onGetTetherMode = {},
        onSetTetherMode = {_, _ -> },
        onSetTargetGid = {},
        sendGroupChat = {},
        sendGroupInvite = {},
        navigateToComms = {},
        onSendGripFile = { _, _ -> }
    )
}
