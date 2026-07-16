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
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** Render-time content for an indexed dynamic slot: the icon to show and the click commands. */
data class DynamicSlotContent(
    val item: ItemStack,
    val commands: List<String>,
)

/**
 * Resolves [CodexButtonType] tagged placeholders into dynamic slots at render time.
 *
 * Indexed markers (`QUEST_SLOT#<n>` / `CATEGORY_SLOT#<n>`) are produced by the
 * initializer before layout parsing, so they live inside the layout tree and follow
 * scrollable/paginated/frame viewports like any other slot. This resolver swaps each
 * visible marker for the n-th quest/category content at the marker's final on-screen
 * position. Markers whose index has no content render as empty slots.
 *
 * Navigation tags (BACK, CLOSE, SCROLL_*, ...) resolve to nav buttons that keep the
 * tagged slot's position unless an explicit slot is configured in [navButtons].
 */
class CodexButtonResolverLayout(
    inner: MenuLayout,
    private val player: Player,
    private val navButtons: List<CodexNavButton> = emptyList(),
    private val dynamicProvider: ((Int) -> DynamicSlotContent?)? = null,
    override val id: String? = null,
) : MenuLayout {

    private val mm = MiniMessage.miniMessage()

    private val delegate = GenericButtonResolverLayout(
        inner = inner,
        prefix = "codex_button:",
        resolver = { type, p, slot -> resolveButton(type, p, slot) },
        id = id,
    )

    override val innerLayout: MenuLayout? get() = delegate.innerLayout

    override fun getSlots(
        session: MenuSessionService.ActiveSession,
        viewport: Viewport,
    ): List<GuiSlot> = delegate.getSlots(session, viewport)

    override val virtualWidth: Int get() = delegate.virtualWidth
    override val virtualHeight: Int get() = delegate.virtualHeight

    private fun resolveButton(type: String, p: Player, original: GuiSlot): GuiSlot? {
        // Indexed dynamic markers: "QUEST_SLOT#<n>" / "CATEGORY_SLOT#<n>".
        val hashIndex = type.indexOf('#')
        if (hashIndex >= 0) {
            val base = type.take(hashIndex)
            if (base != CodexButtonType.QUEST_SLOT.name && base != CodexButtonType.CATEGORY_SLOT.name) return null
            val index = type.substring(hashIndex + 1).toIntOrNull() ?: return null
            val content = dynamicProvider?.invoke(index)
                ?: return GuiSlot(x = original.x, y = original.y, item = ItemStack(Material.AIR), allowPickup = false)
            return GuiSlot(
                x = original.x,
                y = original.y,
                item = content.item,
                allowPickup = false,
                commands = content.commands,
                tag = original.tag,
            )
        }

        val buttonType = try {
            CodexButtonType.valueOf(type)
        } catch (_: IllegalArgumentException) {
            return null
        }

        // Un-indexed placeholder markers (wrong menu kind or leftovers) are hidden.
        if (buttonType == CodexButtonType.QUEST_SLOT ||
            buttonType == CodexButtonType.CATEGORY_SLOT ||
            buttonType == CodexButtonType.SORT_SLOT
        ) {
            return GuiSlot(x = original.x, y = original.y, item = ItemStack(Material.AIR), allowPickup = false)
        }

        val action = buttonType.toNavAction() ?: return null

        val config = navButtons.find { it.action == action }
        val item: ItemStack = config?.item?.build(p) ?: CodexNavDefaults.defaultItem(action).build(p)
        val label: String = config?.label?.ifEmpty { null } ?: CodexNavDefaults.defaultLabel(action)

        val slotIndex = config?.slot ?: -1

        val meta = item.itemMeta
        meta.displayName(mm.deserialize(label))
        item.itemMeta = meta

        // An explicit configured slot wins; otherwise keep the tagged slot's position.
        return GuiSlot(
            x = if (slotIndex >= 0) slotIndex % 9 else original.x,
            y = if (slotIndex >= 0) slotIndex / 9 else original.y,
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
