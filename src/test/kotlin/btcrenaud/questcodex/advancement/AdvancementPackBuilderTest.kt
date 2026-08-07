package btcrenaud.questcodex.advancement

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdvancementPackBuilderTest {

    private fun spec(
        entryId: String,
        key: String,
        parentEntryId: String = "",
        namespace: String = "questcodex",
        background: String = "",
        title: String = """{"text":"Title"}""",
        autoGrant: Boolean = false,
    ) = AdvancementSpec(
        entryId = entryId,
        entryName = "Entry $entryId",
        namespace = namespace,
        key = key,
        parentEntryId = parentEntryId,
        icon = "minecraft:book",
        titleJson = title,
        descriptionJson = """{"text":"Description"}""",
        frame = "task",
        background = background,
        showToast = true,
        announceToChat = false,
        hidden = false,
        autoGrant = autoGrant,
    )

    private fun advancement(pack: AdvancementPackBuilder.Pack, ns: String, key: String) =
        JsonParser.parseString(pack.files.getValue("data/$ns/advancement/$key.json")).asJsonObject

    @Test
    fun `writes one file per advancement plus the pack metadata`() {
        val pack = AdvancementPackBuilder.build(listOf(spec("a", "root"), spec("b", "child", "a")))

        assertEquals(
            setOf("pack.mcmeta", "data/questcodex/advancement/root.json", "data/questcodex/advancement/child.json"),
            pack.files.keys,
        )
        assertTrue(pack.rejections.isEmpty())
    }

    @Test
    fun `a child names its parent by advancement id, not by entry id`() {
        val pack = AdvancementPackBuilder.build(listOf(spec("a", "root"), spec("b", "child", "a")))

        val child = advancement(pack, "questcodex", "child")
        assertEquals("questcodex:root", child.get("parent").asString)
        assertFalse(advancement(pack, "questcodex", "root").has("parent"))
    }

    @Test
    fun `the only criterion is impossible, so nothing grants the advancement on its own`() {
        val pack = AdvancementPackBuilder.build(listOf(spec("a", "root")))

        val criteria = advancement(pack, "questcodex", "root").getAsJsonObject("criteria")
        assertEquals(setOf(AdvancementPackBuilder.GRANTED_CRITERION), criteria.keySet())
        assertEquals(
            "minecraft:impossible",
            criteria.getAsJsonObject(AdvancementPackBuilder.GRANTED_CRITERION).get("trigger").asString,
        )
    }

    @Test
    fun `an auto-granted advancement is earned by playing, so its tab exists from the start`() {
        val pack = AdvancementPackBuilder.build(listOf(spec("a", "root", autoGrant = true)))

        val criteria = advancement(pack, "questcodex", "root").getAsJsonObject("criteria")
        assertEquals(
            "minecraft:tick",
            criteria.getAsJsonObject(AdvancementPackBuilder.GRANTED_CRITERION).get("trigger").asString,
        )
    }

    @Test
    fun `the icon uses the 1_20_5 id key rather than the removed item key`() {
        val pack = AdvancementPackBuilder.build(listOf(spec("a", "root")))

        val icon = advancement(pack, "questcodex", "root").getAsJsonObject("display").getAsJsonObject("icon")
        assertEquals("minecraft:book", icon.get("id").asString)
        assertFalse(icon.has("item"))
    }

    @Test
    fun `a background is kept on a root and dropped on a child, where the client ignores it`() {
        val sprite = "minecraft:gui/advancements/backgrounds/stone"
        val pack = AdvancementPackBuilder.build(
            listOf(spec("a", "root", background = sprite), spec("b", "child", "a", background = sprite)),
        )

        assertEquals(
            sprite,
            advancement(pack, "questcodex", "root").getAsJsonObject("display").get("background").asString,
        )
        assertFalse(advancement(pack, "questcodex", "child").getAsJsonObject("display").has("background"))
    }

    @Test
    fun `a background written as a pre 1_21_2 texture path is converted to the sprite id`() {
        // The old form parses as a resource location and then resolves to nothing, which leaves
        // the tab without a background instead of reporting anything.
        val pack = AdvancementPackBuilder.build(
            listOf(spec("a", "root", background = "minecraft:textures/gui/advancements/backgrounds/stone.png")),
        )

        assertEquals(
            "minecraft:gui/advancements/backgrounds/stone",
            advancement(pack, "questcodex", "root").getAsJsonObject("display").get("background").asString,
        )
    }

    @Test
    fun `an invalid namespace or key is rejected instead of being written`() {
        val pack = AdvancementPackBuilder.build(
            listOf(spec("a", "Root"), spec("b", "ok", namespace = "Quest Codex"), spec("c", "")),
        )

        assertEquals(listOf("a", "b", "c"), pack.rejections.map { it.entryId })
        assertEquals(setOf("pack.mcmeta"), pack.files.keys)
    }

    @Test
    fun `the second claim on an advancement id loses, and names the entry that took it`() {
        val pack = AdvancementPackBuilder.build(listOf(spec("a", "root"), spec("b", "root")))

        assertEquals(1, pack.rejections.size)
        assertEquals("b", pack.rejections.single().entryId)
        assertTrue(pack.rejections.single().reason.contains("Entry a"))
        assertTrue(pack.files.containsKey("data/questcodex/advancement/root.json"))
    }

    @Test
    fun `an advancement whose parent does not exist is dropped`() {
        val pack = AdvancementPackBuilder.build(listOf(spec("b", "child", parentEntryId = "missing")))

        assertEquals("b", pack.rejections.single().entryId)
        assertEquals(setOf("pack.mcmeta"), pack.files.keys)
    }

    @Test
    fun `a rejected parent takes its descendants with it`() {
        // 'a' is refused for its key, so 'b' has nothing to hang from and 'c' hangs from 'b'.
        val pack = AdvancementPackBuilder.build(
            listOf(spec("a", "ROOT"), spec("b", "child", "a"), spec("c", "grandchild", "b")),
        )

        assertEquals(setOf("a", "b", "c"), pack.rejections.map { it.entryId }.toSet())
        assertEquals(setOf("pack.mcmeta"), pack.files.keys)
    }

    @Test
    fun `a parent cycle is refused rather than written as an unloadable tree`() {
        val pack = AdvancementPackBuilder.build(
            listOf(spec("a", "one", parentEntryId = "b"), spec("b", "two", parentEntryId = "a")),
        )

        assertEquals(setOf("a", "b"), pack.rejections.map { it.entryId }.toSet())
        assertTrue(pack.rejections.all { it.reason.contains("loops back") })
        assertEquals(setOf("pack.mcmeta"), pack.files.keys)
    }

    @Test
    fun `a title that is not a text component is rejected before it reaches the server`() {
        val pack = AdvancementPackBuilder.build(listOf(spec("a", "root", title = "not json")))

        assertEquals("a", pack.rejections.single().entryId)
    }

    @Test
    fun `the pack declares an open ended format range instead of a single version`() {
        val pack = AdvancementPackBuilder.build(emptyList())

        val meta = JsonParser.parseString(pack.files.getValue("pack.mcmeta"))
            .asJsonObject.getAsJsonObject("pack")
        val supported = meta.getAsJsonObject("supported_formats")
        assertTrue(supported.get("max_inclusive").asInt > supported.get("min_inclusive").asInt)
        assertEquals(supported.get("min_inclusive").asInt, meta.get("pack_format").asInt)
    }

    @Test
    fun `the range is repeated in the fields each reader generation expects`() {
        // From pack format 81 on, the server refuses a pack that claims support for a newer
        // version without min_format and max_format, and falls back to a guessed pack type.
        val meta = JsonParser.parseString(
            AdvancementPackBuilder.build(emptyList()).files.getValue("pack.mcmeta"),
        ).asJsonObject.getAsJsonObject("pack")

        val supported = meta.getAsJsonObject("supported_formats")
        // [major, minor], the shape the server writes in the 'bukkit' datapack it generates.
        val min = meta.getAsJsonArray("min_format")
        val max = meta.getAsJsonArray("max_format")
        assertEquals(2, min.size())
        assertEquals(2, max.size())
        assertEquals(supported.get("min_inclusive").asInt, min[0].asInt)
        assertEquals(supported.get("max_inclusive").asInt, max[0].asInt)
        assertTrue(max[0].asInt > 81)
    }
}
