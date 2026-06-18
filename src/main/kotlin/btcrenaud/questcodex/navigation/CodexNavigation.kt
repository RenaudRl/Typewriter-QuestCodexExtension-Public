package btcrenaud.questcodex.navigation

import com.typewritermc.core.extension.annotations.ContentEditor
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.engine.paper.content.modes.custom.HoldingItemContentMode
import com.typewritermc.engine.paper.utils.item.Item

enum class CodexNavAction {
    PAGE_NEXT,
    PAGE_PREV,
    SCROLL_UP,
    SCROLL_DOWN,
    SCROLL_LEFT,
    SCROLL_RIGHT,
    BACK,
    CLOSE,
    SORT,
}

data class CodexNavButton(
    @Help("What this button does when clicked.")
    val action: CodexNavAction = CodexNavAction.PAGE_NEXT,
    @Help("Inventory slot (0-53). Use -1 for automatic placement.")
    val slot: Int = -1,
    @Help("Custom item icon. Leave empty to use the default for this action.")
    @ContentEditor(HoldingItemContentMode::class)
    val item: Item? = null,
    @Help("Custom label. Leave empty to use the default for this action.")
    @Placeholder
    @Colored
    val label: String = "",
)
