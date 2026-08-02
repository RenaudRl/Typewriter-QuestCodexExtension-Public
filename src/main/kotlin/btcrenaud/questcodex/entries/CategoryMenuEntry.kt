package btcrenaud.questcodex.entries

import btcrenaud.gui.OpenGuiActionEntry
import btcrenaud.gui.api.MenuViewData
import btcrenaud.gui.api.MenuViewSupport
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.Modifier
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.entries.ActionEntry
import com.typewritermc.engine.paper.entry.entries.ActionTrigger
import com.typewritermc.engine.paper.utils.item.Item

/**
 * Defines how the sort button displays for a given sort mode.
 */
data class SortDisplayConfig(
    @Help("Sort mode this display applies to.")
    val mode: SortModeConfig = SortModeConfig.ALL,
    @Help("Item shown when this sort mode is active.")
    val item: Item? = null,
    @Help("Label shown when this sort mode is active. Supports MiniMessage.")
    @Placeholder
    @Colored
    val label: String = "",
    @Help("Additional lore lines. Supports MiniMessage and PlaceholderAPI.")
    @Placeholder
    @Colored
    val lore: List<String> = emptyList(),
)

enum class SortModeConfig {
    ALL,
    NOT_STARTED,
    ACTIVE,
    COMPLETED,
}

/**
 * Configures the menu for a specific quest category via declarative layout pools.
 *
 * Place a "SORT_SLOT" placeholder in your layout pool to have the sort button
 * automatically injected at that position with per-mode display customization.
 */
@Entry("category_menu", "Category Menu Configuration", Colors.BLUE, "mdi:view-grid")
class CategoryMenuEntry(
    override val id: String = "",
    override val name: String = "",
    override val criteria: List<Criteria> = emptyList(),
    override val modifiers: List<Modifier> = emptyList(),
    override val triggers: List<Ref<TriggerableEntry>> = emptyList(),
    @Help("Category this menu configuration applies to. Leave empty for main menu.")
    val category: String = "",
    @Help("Menu title shown to the player. Supports MiniMessage and PlaceholderAPI.")
    @Placeholder
    @Colored
    val title: String = "",
    @Help("Number of inventory rows (3-6).")
    val rows: Int = 4,
    @Help("GUI entry providing the layout pool, main layout, type and audio configuration.")
    val menu: Ref<OpenGuiActionEntry> = emptyRef(),
    @Help("Per-mode display overrides for the sort button. Define one entry per sort mode.")
    val sortDisplay: List<SortDisplayConfig> = emptyList(),
    @Help("Addressable screens of this menu.")
    val views: List<MenuViewData> = emptyList(),
    @Help("View opened first. Empty = the first declared view.")
    val defaultViewId: String? = null,
    @Help("MiniMessage separator inserted between breadcrumb segments by the {breadcrumb} title token.")
    @Colored
    val breadcrumbSeparator: String = MenuViewSupport.DEFAULT_BREADCRUMB_SEPARATOR,
    @Help("Shared OpenGui chassis whose layout pool and views are inherited before this menu's own values.")
    val baseMenuId: String = "",
    @Help("Push the previous view onto history when switching tabs.")
    val pushHistoryOnViewSwitch: Boolean = false,
) : ActionEntry {

    override fun ActionTrigger.execute() {
        if (category.isBlank()) {
            btcrenaud.questcodex.QuestCodexInitializer.openMainMenu(player)
        } else {
            btcrenaud.questcodex.QuestCodexInitializer.openCategoryMenu(player, category)
        }
    }

    private val resolvedMenu: OpenGuiActionEntry? get() = menu.get()
    val layoutPool get() = resolvedMenu?.layoutPool ?: emptyList()
    val mainLayoutId: String get() = resolvedMenu?.mainLayoutId.orEmpty()
    val guiType get() = resolvedMenu?.guiType ?: btcrenaud.gui.GuiType.CUSTOM
    val audio get() = resolvedMenu?.audio ?: btcrenaud.gui.GuiAudioData()
    val usesLayoutPool: Boolean get() = layoutPool.isNotEmpty() && mainLayoutId.isNotBlank()

    /** Gets the display config for a given sort mode, falling back to defaults. */
    fun sortDisplayFor(mode: SortModeConfig): SortDisplayConfig? = sortDisplay.find { it.mode == mode }
}
