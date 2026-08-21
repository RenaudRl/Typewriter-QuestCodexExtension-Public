package btcrenaud.questcodex.advancement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two halves of the conversion, run together.
 *
 * [AdvancementPackBuilderTest] feeds the builder text it wrote itself, so it never saw what the
 * serializer actually emits — and that gap is where every unformatted title was lost: the builder
 * demanded a JSON object, and text with no formatting is serialized as a bare string. These tests
 * start from what an author types.
 */
class AdvancementTitleConversionTest {

    private fun spec(title: String, description: String = "") = AdvancementSpec(
        entryId = "a",
        entryName = "Entry a",
        namespace = "questcodex",
        key = "root",
        parentEntryId = "",
        icon = "minecraft:book",
        titleJson = AdvancementDatapackService.toComponentJson(title),
        descriptionJson = AdvancementDatapackService.toComponentJson(description),
        frame = "task",
        background = "",
        showToast = true,
        announceToChat = false,
        hidden = false,
        autoGrant = false,
    )

    @Test
    fun `a title typed without any formatting keeps its advancement`() {
        val pack = AdvancementPackBuilder.build(listOf(spec("Adventures")))

        assertTrue(pack.rejections.isEmpty(), pack.rejections.joinToString { it.reason })
        assertTrue(pack.files.containsKey("data/questcodex/advancement/root.json"))
    }

    @Test
    fun `a coloured title keeps its advancement too`() {
        val pack = AdvancementPackBuilder.build(listOf(spec("<gold>Adventures", "<gray>Your first steps")))

        assertTrue(pack.rejections.isEmpty(), pack.rejections.joinToString { it.reason })
    }

    @Test
    fun `a description left blank is not a reason to drop the advancement`() {
        val pack = AdvancementPackBuilder.build(listOf(spec("Adventures", "")))

        assertTrue(pack.rejections.isEmpty(), pack.rejections.joinToString { it.reason })
    }

    @Test
    fun `a title left blank is refused, with the field named`() {
        val pack = AdvancementPackBuilder.build(listOf(spec("")))

        assertEquals(1, pack.rejections.size)
        assertTrue(pack.rejections.single().reason.contains("title"))
    }
}
