package btcrenaud.questcodex.ui

import btcrenaud.gui.editor.api.ButtonTypeContribution
import btcrenaud.gui.editor.api.ButtonTypeInfo
import btcrenaud.gui.editor.api.ButtonTypeRegistry

/**
 * The codex button types as the GUI web editor offers them (OmniGUI 0.21 or later).
 *
 * Derived from [CodexButtonType], the enum [CodexButtonResolverLayout] parses, so the editor lists exactly
 * what the resolver understands. The labels are editor vocabulary, not player text.
 */
internal object CodexButtonTypes {
    const val EXTENSION_ID = "QuestCodex"

    // The codex menu configuration, and the plain `open_gui` grids of the quest screens, which carry the same markers.
    private val menuEntryTypes = setOf("category_menu", "open_gui")

    val contribution = ButtonTypeContribution(
        extensionId = EXTENSION_ID,
        displayName = "Quest Codex",
        prefix = CodexButtonResolverLayout.PREFIX,
        types = CodexButtonType.entries.map(::infoOf),
        appliesTo = { context -> context.entryType in menuEntryTypes },
    )

    fun register() = ButtonTypeRegistry.register(contribution)

    fun unregister() = ButtonTypeRegistry.unregister(EXTENSION_ID)

    // Exhaustive on purpose: a new CodexButtonType does not compile until it is described here.
    private fun infoOf(type: CodexButtonType): ButtonTypeInfo = when (type) {
        CodexButtonType.PAGE_NEXT ->
            ButtonTypeInfo(type.name, "Next page", "Goes to the next page.", category = "navigation")
        CodexButtonType.PAGE_PREV ->
            ButtonTypeInfo(type.name, "Previous page", "Goes to the previous page.", category = "navigation")
        CodexButtonType.SCROLL_UP ->
            ButtonTypeInfo(type.name, "Scroll up", "Scrolls one row up.", category = "navigation")
        CodexButtonType.SCROLL_DOWN ->
            ButtonTypeInfo(type.name, "Scroll down", "Scrolls one row down.", category = "navigation")
        CodexButtonType.SCROLL_LEFT ->
            ButtonTypeInfo(type.name, "Scroll left", "Scrolls one column left.", category = "navigation")
        CodexButtonType.SCROLL_RIGHT ->
            ButtonTypeInfo(type.name, "Scroll right", "Scrolls one column right.", category = "navigation")
        CodexButtonType.BACK ->
            ButtonTypeInfo(type.name, "Back", "Returns to the previous menu.", category = "navigation")
        CodexButtonType.CLOSE ->
            ButtonTypeInfo(type.name, "Close", "Closes the menu.", category = "navigation")
        CodexButtonType.SORT ->
            ButtonTypeInfo(type.name, "Sort", "Changes how the quests are sorted.", category = "action")
        CodexButtonType.QUEST_SLOT ->
            ButtonTypeInfo(type.name, "Quest slot", "Dynamic slot showing one quest.", repeatable = true, indexed = true, category = "dynamic")
        CodexButtonType.TRACKED_QUEST_SLOT ->
            ButtonTypeInfo(type.name, "Tracked quest slot", "Dynamic slot showing one currently tracked quest.", repeatable = true, indexed = true, category = "dynamic")
        CodexButtonType.CATEGORY_SLOT ->
            ButtonTypeInfo(type.name, "Category slot", "Dynamic slot showing one category.", repeatable = true, indexed = true, category = "dynamic")
        CodexButtonType.SORT_SLOT ->
            ButtonTypeInfo(type.name, "Sort slot", "Slot of the sort button, displayed per sort mode.", category = "dynamic")
    }
}
