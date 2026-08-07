package btcrenaud.questcodex

import com.typewritermc.core.entries.Ref
import com.typewritermc.quest.entries.QuestEntry
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the invariant the codex leaked for a while: the quest display order must be a pure
 * function of the configuration.
 *
 * It must not depend on the viewing player, on display text, or on which quests happen to be
 * filtered out — those are exactly the inputs that made the order drift as players tracked quests.
 */
class QuestOrderTest {
    private fun ref(id: String): Ref<QuestEntry> = Ref(id, QuestEntry::class)

    private fun category(name: String, vararg questIds: String): QuestCategory {
        val category = QuestCategory(name = name)
        questIds.forEach { category.quests.add(ref(it)) }
        return category
    }

    private fun QuestCategory.orderedIds(): List<String> = orderedQuestRefs().map { it.id }

    @Test
    fun `keeps declaration order when no explicit order is given`() {
        val category = category("main", "zebra", "apple", "monkey")
        // Not alphabetical: the listed order is the author's intent, and it is what must survive.
        assertEquals(listOf("zebra", "apple", "monkey"), category.orderedIds())
    }

    @Test
    fun `honours explicit orders and puts them before unordered quests`() {
        val category = category("main", "a", "b", "c", "d")
        category.questOrders["c"] = 0
        category.questOrders["a"] = 1
        assertEquals(listOf("c", "a", "b", "d"), category.orderedIds())
    }

    @Test
    fun `resolves nested quest orders against the sub-category that owns them`() {
        val parent = category("parent", "p1")
        val child = category("child", "c1", "c2")
        // The order lives in the child; ranking the flattened list against the parent alone used to
        // leave every nested quest unordered and fall back to display-name sorting.
        child.questOrders["c2"] = 0
        child.parent = parent
        parent.subCategories.add(child)

        assertEquals(listOf("p1", "c2", "c1"), parent.orderedIds())
    }

    @Test
    fun `orders sub-categories by their own order then by name`() {
        val parent = category("parent")
        val late = category("late", "l1").apply { order = 2; title = "AAA display title" }
        val early = category("early", "e1").apply { order = 1; title = "ZZZ display title" }
        listOf(late, early).forEach { it.parent = parent; parent.subCategories.add(it) }

        // Ordered by `order`, not by the display title — the title carries placeholders and
        // translations, so it cannot decide a server-wide ordering.
        assertEquals(listOf("e1", "l1"), parent.orderedIds())
    }

    @Test
    fun `filtering preserves the relative order of the survivors`() {
        val category = category("main", "a", "b", "c", "d", "e")
        category.questOrders["e"] = 0

        val all = category.orderedIds()
        // Whatever a player-dependent filter removes, the rest must keep their relative order.
        val filtered = category.orderedQuestRefs()
            .filterNot { it.id == "b" || it.id == "d" }
            .map { it.id }

        assertEquals(all.filterNot { it == "b" || it == "d" }, filtered)
        assertEquals(listOf("e", "a", "c"), filtered)
    }

    @Test
    fun `is stable across repeated resolutions`() {
        val category = category("main", "a", "b", "c")
        category.questOrders["b"] = 5
        category.questOrders["c"] = 5
        // Equal explicit orders must not tie-break randomly: the quest id decides.
        repeat(5) { assertEquals(listOf("b", "c", "a"), category.orderedIds()) }
    }

    @Test
    fun `de-duplicates a quest reachable through several categories`() {
        val parent = category("parent", "shared")
        val child = category("child", "shared", "own")
        child.parent = parent
        parent.subCategories.add(child)

        assertEquals(listOf("shared", "own"), parent.orderedIds())
    }
}
