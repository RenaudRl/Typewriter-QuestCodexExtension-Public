package btc.renaud.questcodex

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.core.extension.annotations.ContentEditor
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.content.modes.custom.HoldingItemContentMode
import com.typewritermc.engine.paper.utils.DefaultSoundId
import com.typewritermc.engine.paper.utils.Sound
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.engine.paper.utils.item.toItem
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import net.kyori.adventure.text.Component

/**
 * Template describing how an item should look inside the quest codex menus.
 */
data class ItemTemplate(
    @Help("Item used when rendering the item")
    @ContentEditor(HoldingItemContentMode::class)
    val item: Item = Item.Empty,
    @Help("Display name of the item")
    @Placeholder
    @Colored
    val name: String = "",
    @Help("Lore displayed on the item")
    @Placeholder
    @Colored
    @MultiLine
    val lore: List<String> = emptyList(),
)

/**
 * Template describing a navigation button inside a menu.
 */
data class NavigationButtonTemplate(
    @Help("Column used on the last row when no explicit slot is provided")
    val column: Int = 0,
    @Help("Explicit slot for the button. Overrides the column when set.")
    val slot: Int? = null,
    @Help("Item used when rendering the button")
    @ContentEditor(HoldingItemContentMode::class)
    val item: Item = materialItem(Material.ARROW),
    @Help("Display name of the button")
    @Placeholder
    @Colored
    val name: String = "",
    @Help("Lore displayed on the button")
    @Placeholder
    @Colored
    @MultiLine
    val lore: List<String> = emptyList(),
)

/**
 * Sort button configuration for the quest menu.
 */
data class SortButtonSettings(
    @Help("Column used on the last row when no explicit slot is provided")
    val column: Int = 4,
    @Help("Explicit slot for the button. Overrides the column when set.")
    val slot: Int? = null,
    @Help("Item used when rendering the button")
    @ContentEditor(HoldingItemContentMode::class)
    val item: Item = materialItem(Material.COMPARATOR),
    @Help("Display name of the button")
    @Placeholder
    @Colored
    val name: String = "Sort",
    @Help("Lore displayed on the button")
    @Placeholder
    @Colored
    @MultiLine
    val lore: List<String> = emptyList(),
    @Help("Prefix displayed in front of the currently selected option")
    @Placeholder
    @Colored
    val selectedPrefix: String = "\u2192 ",
    @Help("Format applied to the currently selected option")
    @Placeholder
    @Colored
    val selectedFormat: String = "<green>{prefix}{name}</green>",
    @Help("Format applied to unselected options")
    @Placeholder
    @Colored
    val unselectedFormat: String = "<white>{name}</white>",
    @Help("Label used for the \"All\" sort option")
    @Placeholder
    @Colored
    val allName: String = "All Quests",
    @Help("Label used for the \"Completed\" sort option")
    @Placeholder
    @Colored
    val completedName: String = "Completed",
    @Help("Label used for the \"In Progress\" sort option")
    @Placeholder
    @Colored
    val inProgressName: String = "In Progress",
    @Help("Label used for the \"Not Started\" sort option")
    @Placeholder
    @Colored
    val notStartedName: String = "Not Started",
)

/**
 * Item templates used for quest buttons in the quest menu.
 */
data class QuestButtonSettings(
    @Help("Appearance when a quest is completed")
    val completed: ItemTemplate = ItemTemplate(
        item = materialItem(Material.ENCHANTED_BOOK),
        name = "<green>Completed</green>",
        lore = listOf("<green>✓ Completed</green>"),
    ),
    @Help("Appearance when a quest is in progress")
    val inProgress: ItemTemplate = ItemTemplate(
        item = materialItem(Material.WRITABLE_BOOK),
        name = "<yellow>In Progress</yellow>",
        lore = listOf("<yellow>⚡ In Progress</yellow>"),
    ),
    @Help("Appearance when a quest has not started")
    val notStarted: ItemTemplate = ItemTemplate(
        item = materialItem(Material.BOOK),
        name = "<gray>Not Started</gray>",
        lore = listOf("<gray>○ Not Started</gray>"),
    ),
    @Help("Additional lore shown to track a quest")
    @Placeholder
    @Colored
    @MultiLine
    val trackLore: List<String> = listOf("<gray>Click to track</gray>"),
    @Help("Additional lore shown to untrack a quest")
    @Placeholder
    @Colored
    @MultiLine
    val untrackLore: List<String> = listOf("<gray>Click to untrack</gray>"),
)

/**
 * Button configuration for the quest menu.
 */
data class QuestMenuButtonSettings(
    val previous: NavigationButtonTemplate = NavigationButtonTemplate(
        column = 0,
        item = materialItem(Material.ARROW),
        name = "Previous Page",
        lore = listOf("<gray>Go to previous page</gray>"),
    ),
    val next: NavigationButtonTemplate = NavigationButtonTemplate(
        column = 8,
        item = materialItem(Material.ARROW),
        name = "Next Page",
        lore = listOf("<gray>Go to next page</gray>"),
    ),
    val back: NavigationButtonTemplate = NavigationButtonTemplate(
        column = 7,
        item = materialItem(Material.BARRIER),
        name = "Back",
        lore = listOf("<gray>Return to categories</gray>"),
    ),
    val sort: SortButtonSettings = SortButtonSettings(),
)

/**
 * Configuration applied to quest menus.
 */
data class QuestMenuSettings(
    @Help("Number of quests displayed per row when no explicit layout is provided")
    val questsPerRow: Int = 9,
    val fill: ItemTemplate = ItemTemplate(item = materialItem(Material.GRAY_STAINED_GLASS_PANE)),
    @Help("Placeholder item used when a quest slot is empty")
    val emptyQuest: ItemTemplate = ItemTemplate(
        item = materialItem(Material.GRAY_STAINED_GLASS_PANE),
        lore = listOf("<gray>No quests available</gray>"),
    ),
    val buttons: QuestMenuButtonSettings = QuestMenuButtonSettings(),
    val questButtons: QuestButtonSettings = QuestButtonSettings(),
)

/**
 * Category menu configuration shared by the main and sub menus.
 */
data class CategoryMenuSettings(
    @Help("Inventory title displayed to the player")
    @Placeholder
    @Colored
    val title: String = "Quest Categories",
    @Help("Number of rows of the inventory")
    val rows: Int = 6,
    @Help("Number of categories displayed per row when no explicit layout is provided")
    val categoriesPerRow: Int = 9,
    @Help("Default item used when a category does not define an icon")
    @ContentEditor(HoldingItemContentMode::class)
    val categoryItem: Item = materialItem(Material.BOOK),
    @Help("Default color or style applied to the category name")
    @Placeholder
    @Colored
    val categoryNameColor: String = "<yellow>",
    @Help("Whether the category name should be bold by default")
    val categoryNameBold: Boolean = false,
    @Help("Lore lines showing quest completion progress")
    @Placeholder
    @Colored
    @MultiLine
    val categoryLoreQuestCount: List<String> = listOf("<gray><completed>/<total> quests</gray>"),
    @Help("Lore shown on categories that can be opened")
    @Placeholder
    @Colored
    @MultiLine
    val categoryLore: List<String> = listOf("<gray>Click to view quests</gray>"),
    val fill: ItemTemplate = ItemTemplate(item = materialItem(Material.GRAY_STAINED_GLASS_PANE)),
    val previousButton: NavigationButtonTemplate = NavigationButtonTemplate(
        column = 0,
        item = materialItem(Material.ARROW),
        name = "Previous Page",
        lore = listOf("<gray>Go to previous page</gray>"),
    ),
    val nextButton: NavigationButtonTemplate = NavigationButtonTemplate(
        column = 8,
        item = materialItem(Material.ARROW),
        name = "Next Page",
        lore = listOf("<gray>Go to next page</gray>"),
    ),
    val infoButton: NavigationButtonTemplate = NavigationButtonTemplate(
        column = 4,
        item = materialItem(Material.PAPER),
        name = "Info",
        lore = listOf("<gray>Completed: %typewriter_total_completed%/%typewriter_total_quests%</gray>"),
    ),
    val backButton: NavigationButtonTemplate? = null,
)

/**
 * Sound configuration for the quest codex.
 */
data class SoundSettings(
    val menuOpen: Sound = defaultSound("minecraft:item.flintandsteel.use"),
    val menuSwitch: Sound = defaultSound("minecraft:item.flintandsteel.use"),
    val buttonPrevious: Sound = defaultSound("minecraft:item.flintandsteel.use"),
    val buttonNext: Sound = defaultSound("minecraft:item.flintandsteel.use"),
    val buttonBack: Sound = defaultSound("minecraft:item.flintandsteel.use"),
    val buttonSort: Sound = defaultSound("minecraft:item.flintandsteel.use"),
    val buttonCategory: Sound = defaultSound("minecraft:item.flintandsteel.use"),
    val questTrack: Sound = defaultSound("minecraft:item.flintandsteel.use"),
    val questUntrack: Sound = defaultSound("minecraft:item.flintandsteel.use"),
)

/** Default configuration used when no entry overrides it. */
object QuestCodexDefaults {
    val mainMenu: CategoryMenuSettings = CategoryMenuSettings()
    val subMenu: CategoryMenuSettings = CategoryMenuSettings(
        title = "<category>",
        backButton = NavigationButtonTemplate(
            column = 7,
            item = materialItem(Material.BARRIER),
            name = "Back",
            lore = listOf("<gray>Return to parent</gray>"),
        ),
    )
    val questMenu: QuestMenuSettings = QuestMenuSettings()
    val sounds: SoundSettings = SoundSettings()

    fun settingsEntry(): QuestCodexSettingsEntry = QuestCodexSettingsEntry(
        id = "quest_codex_settings_defaults",
        name = "Quest Codex Defaults",
        mainMenu = mainMenu,
        subMenu = subMenu,
        questMenu = questMenu,
        sounds = sounds,
    )
}

/** Holds the active quest codex configuration. */
object QuestCodexConfig {
    var mainMenu: CategoryMenuSettings = QuestCodexDefaults.mainMenu
        private set
    var subMenu: CategoryMenuSettings = QuestCodexDefaults.subMenu
        private set
    var questMenu: QuestMenuSettings = QuestCodexDefaults.questMenu
        private set
    var sounds: SoundSettings = QuestCodexDefaults.sounds
        private set

    /** Reset configuration to default values. */
    fun reset() {
        mainMenu = QuestCodexDefaults.mainMenu
        subMenu = QuestCodexDefaults.subMenu
        questMenu = QuestCodexDefaults.questMenu
        sounds = QuestCodexDefaults.sounds
    }

    /** Apply settings from a Typewriter entry. */
    fun apply(entry: QuestCodexSettingsEntry) {
        mainMenu = entry.mainMenu
        subMenu = entry.subMenu
        questMenu = entry.questMenu
        sounds = entry.sounds
    }
}

/**
 * Entry allowing administrators to override quest codex settings directly from Typewriter.
 */
@Entry(
    "quest_codex_settings",
    "Configures quest codex menus and sounds",
    Colors.RED,
    "mdi:book-cog",
)
class QuestCodexSettingsEntry(
    override val id: String = "",
    override val name: String = "",
    val mainMenu: CategoryMenuSettings = QuestCodexDefaults.mainMenu,
    val subMenu: CategoryMenuSettings = QuestCodexDefaults.subMenu,
    val questMenu: QuestMenuSettings = QuestCodexDefaults.questMenu,
    val sounds: SoundSettings = QuestCodexDefaults.sounds,
) : ManifestEntry

fun NavigationButtonTemplate.resolveSlot(rows: Int, inventorySize: Int): Int {
    val normalizedSlot = slot
    if (normalizedSlot != null && normalizedSlot in 0 until inventorySize) {
        return normalizedSlot
    }
    val bottomRowStart = (rows - 1).coerceAtLeast(0) * 9
    val target = bottomRowStart + column.coerceIn(0, 8)
    return target.coerceIn(0, (inventorySize - 1).coerceAtLeast(0))
}

fun SortButtonSettings.resolveSlot(rows: Int, inventorySize: Int): Int {
    val normalizedSlot = slot
    if (normalizedSlot != null && normalizedSlot in 0 until inventorySize) {
        return normalizedSlot
    }
    val bottomRowStart = (rows - 1).coerceAtLeast(0) * 9
    val target = bottomRowStart + column.coerceIn(0, 8)
    return target.coerceIn(0, (inventorySize - 1).coerceAtLeast(0))
}

fun NavigationButtonTemplate.toItemTemplate(): ItemTemplate =
    ItemTemplate(item = item, name = name, lore = lore)

fun SortButtonSettings.toItemTemplate(): ItemTemplate =
    ItemTemplate(item = item, name = name, lore = lore)

fun ItemTemplate.baseItem(player: Player, fallbackMaterial: Material = Material.GRAY_STAINED_GLASS_PANE): ItemStack =
    if (item != Item.Empty) item.build(player) else ItemStack(fallbackMaterial)

fun ItemTemplate.buildItem(
    player: Player,
    fallbackMaterial: Material = Material.GRAY_STAINED_GLASS_PANE,
    nameOverride: Component? = null,
    loreOverride: List<Component>? = null,
): ItemStack {
    val base = baseItem(player, fallbackMaterial)
    val displayNameComponent = nameOverride ?: name.parsePlaceholders(player).asMiniWithoutItalic()
    val loreComponents = loreOverride ?: lore.flatMap { it.split("\n") }
        .map { it.parsePlaceholders(player).asMiniWithoutItalic() }
    val resolvedMeta = base.itemMeta ?: Bukkit.getItemFactory().getItemMeta(base.type)
    if (resolvedMeta == null) {
        plugin.logger.warning(
            "[QuestCodex] Unable to resolve item meta for ${base.type} while building item template " +
                "for player ${player.name}. Returning base stack without additional formatting."
        )
        return base
    }
    resolvedMeta.displayName(displayNameComponent)
    resolvedMeta.lore(loreComponents)
    base.itemMeta = resolvedMeta
    return base
}

private fun materialItem(material: Material): Item = ItemStack(material).toItem()

private fun defaultSound(id: String): Sound = Sound(DefaultSoundId(id))
