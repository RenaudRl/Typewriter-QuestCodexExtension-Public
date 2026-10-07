package btcrenaud.questcodex.navigation

import btcrenaud.questcodex.entries.QuestCodexConfig
import com.typewritermc.engine.paper.utils.item.CustomItem
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.engine.paper.utils.item.components.ItemMaterialComponent
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.utils.DefaultSoundId
import com.typewritermc.engine.paper.utils.Sound
import org.bukkit.Material

object CodexNavDefaults {

    val item: Map<CodexNavAction, Item> = mapOf(
        CodexNavAction.PAGE_NEXT to CustomItem(listOf(ItemMaterialComponent(ConstVar(Material.ARROW)))),
        CodexNavAction.PAGE_PREV to CustomItem(listOf(ItemMaterialComponent(ConstVar(Material.ARROW)))),
        CodexNavAction.SCROLL_UP to CustomItem(listOf(ItemMaterialComponent(ConstVar(Material.ARROW)))),
        CodexNavAction.SCROLL_DOWN to CustomItem(listOf(ItemMaterialComponent(ConstVar(Material.ARROW)))),
        CodexNavAction.SCROLL_LEFT to CustomItem(listOf(ItemMaterialComponent(ConstVar(Material.ARROW)))),
        CodexNavAction.SCROLL_RIGHT to CustomItem(listOf(ItemMaterialComponent(ConstVar(Material.ARROW)))),
        CodexNavAction.BACK to CustomItem(listOf(ItemMaterialComponent(ConstVar(Material.ARROW)))),
        CodexNavAction.CLOSE to CustomItem(listOf(ItemMaterialComponent(ConstVar(Material.BARRIER)))),
        CodexNavAction.SORT to CustomItem(listOf(ItemMaterialComponent(ConstVar(Material.HOPPER)))),
    )

    val sound: Map<CodexNavAction, Sound> = mapOf(
        CodexNavAction.PAGE_NEXT to defaultSound("minecraft:item.flintandsteel.use"),
        CodexNavAction.PAGE_PREV to defaultSound("minecraft:item.flintandsteel.use"),
        CodexNavAction.SCROLL_UP to defaultSound("minecraft:item.flintandsteel.use"),
        CodexNavAction.SCROLL_DOWN to defaultSound("minecraft:item.flintandsteel.use"),
        CodexNavAction.SCROLL_LEFT to defaultSound("minecraft:item.flintandsteel.use"),
        CodexNavAction.SCROLL_RIGHT to defaultSound("minecraft:item.flintandsteel.use"),
        CodexNavAction.BACK to defaultSound("minecraft:item.flintandsteel.use"),
        CodexNavAction.CLOSE to defaultSound("minecraft:item.flintandsteel.use"),
        CodexNavAction.SORT to defaultSound("minecraft:item.flintandsteel.use"),
    )

    fun defaultItem(action: CodexNavAction): Item = item[action] ?: CustomItem()

    /** Label of the built-in button for [action]: the `navLabels` of the `quest_codex` entry. */
    fun defaultLabel(action: CodexNavAction): String = QuestCodexConfig.navLabels.labelFor(action)

    fun defaultSound(action: CodexNavAction): Sound? = sound[action]
}

private fun defaultSound(id: String): Sound = Sound(DefaultSoundId(id))
