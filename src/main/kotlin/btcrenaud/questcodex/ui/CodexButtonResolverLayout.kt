package btcrenaud.questcodex.ui

import btcrenaud.gui.api.GenericButtonResolverLayout
import btcrenaud.gui.api.GuiSlot
import btcrenaud.gui.api.MenuLayout
import btcrenaud.gui.api.Viewport
import btcrenaud.gui.services.MenuSessionService
import btcrenaud.questcodex.navigation.CodexNavAction
import btcrenaud.questcodex.navigation.CodexNavDefaults
import net.kyori.adventure.text.format.TextDecoration
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
 * Navigation tags (BACK, CLOSE, SCROLL_*, ...) resolve to nav buttons at the tagged
 * slot's position. The author-configured icon, name and lore on that slot are kept
 * verbatim; the built-in default icon + label is used only when the slot has no
 * configured item.
 */
class CodexButtonResolverLayout(
    inner: MenuLayout,
    private val player: Player,
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
            if (base != CodexButtonType.QUEST_SLOT.name &&
                base != CodexButtonType.TRACKED_QUEST_SLOT.name &&
                base != CodexButtonType.CATEGORY_SLOT.name
            ) return null
            val index = type.substring(hashIndex + 1).toIntOrNull() ?: return null
            val content = dynamicProvider?.invoke(index)
            if (content == null) {
                // A tracked marker doubles as its fully author-configurable EMPTY state.
                // It remains inert, so lore such as "Click to stop tracking" is never
                // attached by the extension when no quest occupies this position.
                if (base == CodexButtonType.TRACKED_QUEST_SLOT.name) {
                    return original.copy(
                        allowPickup = false,
                        isGhost = false,
                        commands = emptyList(),
                        triggers = emptyList(),
                        modifiers = emptyList(),
                        interactions = emptyMap(),
                        input = null,
                        storage = null,
                        onClick = null,
                    )
                }
                return GuiSlot(x = original.x, y = original.y, item = ItemStack(Material.AIR), allowPickup = false)
            }
            return original.copy(
                item = content.item,
                allowPickup = false,
                isGhost = false,
                commands = content.commands,
                triggers = emptyList(),
                modifiers = emptyList(),
                interactions = emptyMap(),
                input = null,
                storage = null,
                onClick = null,
            )
        }

        val buttonType = try {
            CodexButtonType.valueOf(type)
        } catch (_: IllegalArgumentException) {
            return null
        }

        // Un-indexed placeholder markers (wrong menu kind or leftovers) are hidden.
        if (buttonType == CodexButtonType.QUEST_SLOT ||
            buttonType == CodexButtonType.TRACKED_QUEST_SLOT ||
            buttonType == CodexButtonType.CATEGORY_SLOT ||
            buttonType == CodexButtonType.SORT_SLOT
        ) {
            return GuiSlot(x = original.x, y = original.y, item = ItemStack(Material.AIR), allowPickup = false)
        }

        val action = buttonType.toNavAction() ?: return null

        // Full customization: when the tagged slot carries an author-configured icon
        // (optionally with a name and lore), keep it verbatim and only attach the nav
        // behavior. Fall back to the built-in default icon + label only when the slot
        // has no configured item (STRUCTURE_VOID placeholder built by GuiSlotBuilder).
        val configured = original.item
        val item: ItemStack = if (configured.type != Material.STRUCTURE_VOID && !configured.type.isAir) {
            configured.clone()
        } else {
            val fallback = CodexNavDefaults.defaultItem(action).build(p)
            val meta = fallback.itemMeta
            meta.displayName(
                mm.deserialize(CodexNavDefaults.defaultLabel(action))
                    .decoration(TextDecoration.ITALIC, false)
            )
            fallback.itemMeta = meta
            fallback
        }

        return GuiSlot(
            x = original.x,
            y = original.y,
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
    CodexButtonType.TRACKED_QUEST_SLOT -> null
    CodexButtonType.CATEGORY_SLOT -> null
    CodexButtonType.SORT_SLOT -> null
}
