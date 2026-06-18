package btcrenaud.questcodex.entries

import btcrenaud.gui.GuiAudioData
import btcrenaud.gui.GuiType
import btcrenaud.gui.LayoutData
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.engine.paper.entry.ManifestEntry
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
    @Help("Category this menu configuration applies to. Leave empty for main menu.")
    val category: String = "",
    @Help("Menu title shown to the player. Supports MiniMessage and PlaceholderAPI.")
    @Placeholder
    @Colored
    val title: String = "",
    @Help("Number of inventory rows (3-6).")
    val rows: Int = 4,
    @Help("GUI type. Use CUSTOM for fully custom layouts.")
    val guiType: GuiType = GuiType.CUSTOM,
    @Help("Audio configuration overrides for this menu.")
    val audio: GuiAudioData = GuiAudioData(),
    @Help("Layout pool for declarative menu design.")
    val layoutPool: List<LayoutData> = emptyList(),
    @Help("ID of the main layout within the layout pool to display.")
    val mainLayoutId: String = "",
    @Help("Per-mode display overrides for the sort button. Define one entry per sort mode.")
    val sortDisplay: List<SortDisplayConfig> = emptyList(),
) : ManifestEntry {

    val usesLayoutPool: Boolean get() = layoutPool.isNotEmpty() && mainLayoutId.isNotBlank()

    /** Gets the display config for a given sort mode, falling back to defaults. */
    fun sortDisplayFor(mode: SortModeConfig): SortDisplayConfig? = sortDisplay.find { it.mode == mode }
}
