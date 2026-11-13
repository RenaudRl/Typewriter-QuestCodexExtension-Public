package btcrenaud.questcodex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Overrides of a quest declared in a sub-category must survive a flattened (TREE) parent menu. */
class TreeOverrideLookupTest {
    private fun display(name: String) = QuestDisplayOverrides(notStarted = QuestStateDisplayOverride(name = name))

    private fun child(parent: QuestCategory, name: String): QuestCategory {
        val c = QuestCategory(name = name)
        c.parent = parent
        parent.subCategories.add(c)
        return c
    }

    @Test
    fun `parent menu finds the override declared in a sub-category`() {
        val root = QuestCategory(name = "root")
        child(root, "child").questDisplays["q"] = display("Explorer (locked)")

        assertEquals("Explorer (locked)", root.displayOverridesFor("q")?.notStarted?.name)
    }

    @Test
    fun `override of the displayed category wins over the descendant`() {
        val root = QuestCategory(name = "root")
        root.questDisplays["q"] = display("root name")
        child(root, "child").questDisplays["q"] = display("child name")

        assertEquals("root name", root.displayOverridesFor("q")?.notStarted?.name)
    }

    @Test
    fun `lookup reaches grandchildren and returns null when nobody declares the quest`() {
        val root = QuestCategory(name = "root")
        val grand = child(child(root, "child"), "grand")
        grand.questAdditionalLore["q"] = QuestAdditionalLore(description = listOf("d"))

        assertEquals(listOf("d"), root.additionalLoreFor("q")?.description)
        assertNull(root.itemOverridesFor("q"))
    }

    @Test
    fun `a parent cycle does not loop`() {
        val a = QuestCategory(name = "a")
        val b = child(a, "b")
        b.subCategories.add(a)

        assertNull(a.displayOverridesFor("q"))
    }
}
