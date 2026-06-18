package btcrenaud.questcodex.navigation

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

    val label: Map<CodexNavAction, String> = mapOf(
        CodexNavAction.PAGE_NEXT to "<yellow>Suivant",
        CodexNavAction.PAGE_PREV to "<yellow>Precedent",
        CodexNavAction.SCROLL_UP to "<white>Haut",
        CodexNavAction.SCROLL_DOWN to "<white>Bas",
        CodexNavAction.SCROLL_LEFT to "<white>Gauche",
        CodexNavAction.SCROLL_RIGHT to "<white>Droite",
        CodexNavAction.BACK to "<red>Retour",
        CodexNavAction.CLOSE to "<red>Fermer",
        CodexNavAction.SORT to "<yellow>Trier",
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
    fun defaultLabel(action: CodexNavAction): String = label[action] ?: action.name
    fun defaultSound(action: CodexNavAction): Sound? = sound[action]
}

private fun defaultSound(id: String): Sound = Sound(DefaultSoundId(id))
