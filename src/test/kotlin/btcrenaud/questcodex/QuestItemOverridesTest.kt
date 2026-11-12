package btcrenaud.questcodex

import com.typewritermc.engine.paper.utils.item.components.ItemMaterialComponent
import com.typewritermc.engine.paper.utils.item.CustomItem
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.quest.QuestStatus
import org.bukkit.Material
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class QuestItemOverridesTest {

    @Test
    fun `an item saved without material falls back to the default icon`() {
        val overrides = QuestItemOverrides(
            notStarted = CustomItem(),
            inProgress = CustomItem(listOf(ItemMaterialComponent(ConstVar(Material.BOOK)))),
        )

        assertNull(overrides.itemFor(QuestStatus.INACTIVE))
        assertNotNull(overrides.itemFor(QuestStatus.ACTIVE))
    }
}
