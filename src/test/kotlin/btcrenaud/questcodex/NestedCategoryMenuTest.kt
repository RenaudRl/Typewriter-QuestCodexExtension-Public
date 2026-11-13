package btcrenaud.questcodex

import btcrenaud.questcodex.ui.CodexButtonType
import btcrenaud.questcodex.ui.CodexMenuKind
import btcrenaud.questcodex.ui.MarkerGroup
import btcrenaud.questcodex.ui.entryAt
import btcrenaud.questcodex.ui.pageCountOf
import com.typewritermc.core.entries.Ref
import com.typewritermc.quest.entries.QuestEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure rules behind unlimited category nesting: what each marker type lists, whether a category menu
 * flattens its tree, which menu draws a category, and how mixed markers page.
 */
class NestedCategoryMenuTest {
    private fun ref(id: String): Ref<QuestEntry> = Ref(id, QuestEntry::class)

    private fun category(name: String, parent: QuestCategory? = null, order: Int = 0, vararg questIds: String): QuestCategory {
        val category = QuestCategory(name = name, order = order)
        questIds.forEach { category.quests.add(ref(it)) }
        if (parent != null) {
            category.parent = parent
            parent.subCategories.add(category)
        }
        return category
    }

    private fun List<Ref<QuestEntry>>.ids() = map { it.id }

    // ── Which quests a category menu lists ──

    @Test
    fun `own scope lists only the quests declared in the category`() {
        val root = category("root", null, 0, "r1")
        category("child", root, 0, "c1")

        assertEquals(listOf("r1"), root.questRefs(QuestScope.OWN).ids())
    }

    @Test
    fun `tree scope keeps flattening sub-category quests into the parent`() {
        val root = category("root", null, 0, "r1")
        category("child", root, 0, "c1")

        assertEquals(listOf("r1", "c1"), root.questRefs(QuestScope.TREE).ids())
    }

    @Test
    fun `own scope still honours explicit quest orders`() {
        val root = category("root", null, 0, "a", "b", "c")
        root.questOrders["c"] = 0

        assertEquals(listOf("c", "a", "b"), root.questRefs(QuestScope.OWN).ids())
    }

    @Test
    fun `direct sub-categories are ordered by order then name and exclude grandchildren`() {
        val root = category("root")
        category("zeta", root, 1)
        category("alpha", root, 2)
        val noOrder = category("beta", root, 0)
        category("grandchild", noOrder)

        // order == 0 means "unordered" and sorts last, like root categories in the main menu.
        assertEquals(listOf("zeta", "alpha", "beta"), root.orderedSubCategories().map { it.name })
    }

    // ── Which menu draws a category ──

    @Test
    fun `a category with its own menu uses it`() {
        val root = category("root")
        val child = category("child", root)

        assertEquals("child", child.nearestInLineage { it.name in setOf("root", "child") }?.name)
    }

    @Test
    fun `a category without a menu falls back to the nearest ancestor that has one`() {
        val root = category("root")
        val mid = category("mid", root)
        val leaf = category("leaf", category("deep", mid))

        assertEquals("root", leaf.nearestInLineage { it.name == "root" }?.name)
        assertEquals("mid", leaf.nearestInLineage { it.name in setOf("root", "mid") }?.name)
    }

    @Test
    fun `no ancestor menu resolves to null`() {
        val leaf = category("leaf", category("root"))

        assertNull(leaf.nearestInLineage { false })
    }

    @Test
    fun `a parent cycle ends the lookup instead of looping`() {
        val a = category("a")
        val b = category("b", a)
        a.parent = b

        assertNull(a.nearestInLineage { false })
    }

    @Test
    fun `a sub-category cycle ends the quest flattening instead of overflowing`() {
        val a = category("a", null, 0, "qa")
        val b = category("b", a, 0, "qb")
        b.subCategories.add(a)

        assertEquals(listOf("qa", "qb"), a.questRefs(QuestScope.TREE).ids())
    }

    // ── Marker types per menu kind ──

    @Test
    fun `menu kind is derived from the category field`() {
        assertEquals(CodexMenuKind.MAIN, CodexMenuKind.of(""))
        assertEquals(CodexMenuKind.TRACKED, CodexMenuKind.of("@Tracked"))
        assertEquals(CodexMenuKind.CATEGORY, CodexMenuKind.of("main_quests"))
    }

    @Test
    fun `a category menu fills quest and sub-category markers while the main menu fills only categories`() {
        assertEquals(
            listOf(CodexButtonType.QUEST_SLOT, CodexButtonType.CATEGORY_SLOT),
            CodexMenuKind.CATEGORY.markers,
        )
        assertEquals(listOf(CodexButtonType.CATEGORY_SLOT), CodexMenuKind.MAIN.markers)
        assertEquals(listOf(CodexButtonType.TRACKED_QUEST_SLOT), CodexMenuKind.TRACKED.markers)
    }

    // ── Paging of mixed markers ──

    @Test
    fun `a group that fits stays pinned on every page`() {
        val subs = listOf("s1", "s2")
        val group = MarkerGroup(entryCount = 2, markerCount = 3)

        assertEquals("s1", subs.entryAt(group, page = 0, markerIndex = 0))
        assertEquals("s1", subs.entryAt(group, page = 4, markerIndex = 0))
        assertNull(subs.entryAt(group, page = 0, markerIndex = 2))
    }

    @Test
    fun `an overflowing group pages by marker count`() {
        val quests = (1..5).map { "q$it" }
        val group = MarkerGroup(entryCount = 5, markerCount = 2)

        assertEquals(3, group.pageCount)
        assertEquals("q3", quests.entryAt(group, page = 1, markerIndex = 0))
        assertEquals("q5", quests.entryAt(group, page = 2, markerIndex = 0))
        assertNull(quests.entryAt(group, page = 2, markerIndex = 1))
    }

    @Test
    fun `the longest group decides the page count`() {
        val quests = MarkerGroup(entryCount = 10, markerCount = 4)
        val subs = MarkerGroup(entryCount = 2, markerCount = 4)

        assertEquals(3, pageCountOf(listOf(quests, subs)))
        assertEquals(1, pageCountOf(listOf(MarkerGroup(0, 4), MarkerGroup(0, 0))))
    }

    @Test
    fun `a group without markers adds no page`() {
        assertEquals(1, MarkerGroup(entryCount = 50, markerCount = 0).pageCount)
        assertEquals(1, pageCountOf(emptyList()))
    }
}
