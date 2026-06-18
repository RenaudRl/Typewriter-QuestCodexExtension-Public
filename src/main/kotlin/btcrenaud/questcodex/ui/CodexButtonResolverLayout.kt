package btcrenaud.questcodex.ui

import btcrenaud.gui.api.GenericButtonResolverLayout
import btcrenaud.gui.api.GuiSlot
import btcrenaud.gui.api.MenuLayout
import btcrenaud.gui.api.Viewport
import btcrenaud.gui.services.MenuSessionService
import btcrenaud.questcodex.navigation.CodexNavAction
import btcrenaud.questcodex.navigation.CodexNavButton
import btcrenaud.questcodex.navigation.CodexNavDefaults
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Resolves [CodexButtonType] tagged placeholders into dynamic navigation buttons.
 */
class CodexButtonResolverLayout(
    inner: MenuLayout,
    private val player: Player,
    private val navButtons: List<CodexNavButton> = emptyList(),
    override val id: String? = null,
) : MenuLayout {

    private val mm = MiniMessage.miniMessage()

    private val delegate = GenericButtonResolverLayout(
        inner = inner,
        prefix = "codex_button:",
        resolver = { type, p, _ -> resolveButton(type, p) },
        id = id,
    )

    override val innerLayout: MenuLayout? get() = delegate.innerLayout

    override fun getSlots(
        session: MenuSessionService.ActiveSession,
        viewport: Viewport,
    ): List<GuiSlot> = delegate.getSlots(session, viewport)

    override val virtualWidth: Int get() = delegate.virtualWidth
    override val virtualHeight: Int get() = delegate.virtualHeight

    private fun resolveButton(type: String, p: Player): GuiSlot? {
        val buttonType = try {
            CodexButtonType.valueOf(type)
        } catch (_: IllegalArgumentException) {
            return null
        }

        val action = buttonType.toNavAction() ?: return null

        val config = navButtons.find { it.action == action }
        val item: ItemStack = config?.item?.build(p) ?: CodexNavDefaults.defaultItem(action).build(p)
        val label: String = config?.label?.ifEmpty { null } ?: CodexNavDefaults.defaultLabel(action)

        val slotIndex = config?.slot ?: -1

        val meta = item.itemMeta
        meta.displayName(mm.deserialize(label))
        item.itemMeta = meta

        return GuiSlot(
            x = if (slotIndex >= 0) slotIndex % 9 else 0,
            y = if (slotIndex >= 0) slotIndex / 9 else 0,
            item = item,
            allowPickup = false,
            commands = listOf("codex:nav ${action.name}"),
            tag = "codex_button:${type}",
        )
    }
}

private fun CodexButtonType.toNavAction(): CodexNavAction? = when (this) {
    CodexButtonType.PAGE_NEXT -> CodexNavAction.PAGE_NEXT
    CodexButtonType.PAGE_PREV -> CodexNavAction.PAGE_PREV
    CodexButtonType.SCROLL_UP -> CodexNavAction.SCROLL_UP
    CodexButtonType.SCROLL_DOWN -> CodexNavAction.SCROLL_DOWN
    CodexButtonType.SCROLL_LEFT -> CodexNavAction.SCROLL_LEFT
    CodexButtonType.SCROLL_RIGHT -> CodexNavAction.SCROLL_RIGHT
    CodexButtonType.BACK -> CodexNavAction.BACK
    CodexButtonType.CLOSE -> CodexNavAction.CLOSE
    CodexButtonType.SORT -> CodexNavAction.SORT
    CodexButtonType.QUEST_SLOT -> null
    CodexButtonType.CATEGORY_SLOT -> null
    CodexButtonType.SORT_SLOT -> null
}
