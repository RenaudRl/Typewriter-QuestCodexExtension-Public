package btcrenaud.questcodex.migration

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the conversion on the shape a real pre-chassis page has: several `category_menu`
 * entries carrying their own `layoutPool`, one of them already pointing at a populated
 * `open_gui`.
 */
class CategoryMenuChassisMigrationTest {

    private val logger = Logger.getLogger("test")

    private fun tempDataFolder(): File =
        Files.createTempDirectory("questcodex-migration").toFile().also { it.deleteOnExit() }

    /** A `category_menu` written against the old schema. */
    private fun legacyMenu(id: String, name: String, category: String, rows: Int, menu: String? = null) = """
        {
          "id": "$id",
          "name": "$name",
          "type": "category_menu",
          "category": "$category",
          "rows": $rows,
          "title": "<gold>$category",
          "guiType": "CUSTOM",
          "audio": { "onOpen": { "volume": 1 } },
          "sortDisplay": [ { "mode": "ALL", "label": "<yellow>All" } ],
          "mainLayoutId": "${category}_main",
          "layoutPool": [
            { "case": "simple", "value": { "id": "${category}_main", "items": [
              { "displayName": "QUEST_SLOT", "buttonType": "QUEST_SLOT",
                "buttonPrefix": "codex_button:", "x": 0, "y": 0, "count": 7, "direction": "right" }
            ] } }
          ]${if (menu == null) "" else ",\n          \"menu\": \"$menu\""}
        }
    """.trimIndent()

    private fun page(id: String, name: String, entries: List<String>): String = """
        { "id": "$id", "name": "$name", "type": "sequence", "priority": 0,
          "entries": [ ${entries.joinToString(",")} ] }
    """.trimIndent()

    private fun writePage(dir: File, id: String, content: String) {
        dir.mkdirs()
        dir.resolve("$id.json").writeText(content)
    }

    private fun readPages(dir: File): Map<String, JsonObject> =
        dir.listFiles().orEmpty()
            .filter { it.extension == "json" }
            .associate { it.nameWithoutExtension to JsonParser.parseString(it.readText()).asJsonObject }

    private fun JsonObject.entryList(): List<JsonObject> =
        getAsJsonArray("entries").map { it.asJsonObject }

    private fun JsonObject.entry(id: String): JsonObject =
        entryList().first { it.get("id").asString == id }

    @Test
    fun `a legacy menu gets an open_gui chassis on a new sequence page`() {
        val base = tempDataFolder()
        val pages = base.resolve("pages")
        writePage(
            pages, "sourcePage000001",
            page("sourcePage000001", "quests", listOf(legacyMenu("weeklyMenu00001", "weekly_quest_menu", "weeklyquest", 6))),
        )

        val converted = CategoryMenuChassisMigration.migrateOnce(base, logger)
        assertEquals(1, converted)

        val all = readPages(pages)
        assertEquals(2, all.size, "the chassis must land on a page of its own")

        val menu = all.getValue("sourcePage000001").entry("weeklyMenu00001")
        val chassisId = menu.get("menu").asString
        assertTrue(chassisId.length == 15, "chassis id must be a Typewriter identifier, was '$chassisId'")

        // The dead fields are gone; the fields the entry class still declares survive.
        assertNull(menu.get("layoutPool"))
        assertNull(menu.get("mainLayoutId"))
        assertNull(menu.get("guiType"))
        assertNull(menu.get("audio"))
        assertNotNull(menu.get("sortDisplay"))
        assertEquals(6, menu.get("rows").asInt)

        val chassisPage = all.values.first { it.get("name").asString == "questcodex_migrated_chassis" }
        assertEquals("sequence", chassisPage.get("type").asString)
        assertEquals(15, chassisPage.get("id").asString.length)
        assertTrue(chassisPage.has("entryPositions"))

        val chassis = chassisPage.entry(chassisId)
        assertEquals("open_gui", chassis.get("type").asString)
        assertEquals("weekly_quest_menu_chassis", chassis.get("name").asString)
        assertEquals("weeklyquest_main", chassis.get("mainLayoutId").asString)
        assertEquals("54", chassis.get("size").asString, "6 rows must serialize as the 54-slot size")
        assertEquals(
            "QUEST_SLOT",
            chassis.getAsJsonArray("layoutPool")[0].asJsonObject
                .getAsJsonObject("value").getAsJsonArray("items")[0].asJsonObject
                .get("buttonType").asString,
        )
    }

    @Test
    fun `rows are raised to what the root layout needs, never lowered`() {
        val base = tempDataFolder()
        val pages = base.resolve("pages")
        // A 6-row chassis authored with rows=4: the bottom band would be clipped away, and the
        // generated size would agree with the undersized value so nothing would report it.
        val undersized = """
            { "id": "weeklyMenu00001", "name": "weekly_quest_menu", "type": "category_menu",
              "category": "weeklyquest", "rows": 4, "mainLayoutId": "weekly_main",
              "layoutPool": [
                { "case": "frame", "value": { "id": "weekly_main", "frames": [
                  { "id": "deco", "x": 0, "y": 0, "width": 9, "height": 6, "layoutId": "weekly_deco" },
                  { "id": "scroll", "x": 1, "y": 1, "width": 7, "height": 4, "layoutId": "weekly_scroll" } ] } },
                { "case": "simple", "value": { "id": "weekly_content", "items": [
                  { "buttonType": "QUEST_SLOT", "x": 0, "y": 7, "count": 7, "direction": "right" } ] } }
              ] }
        """.trimIndent()
        writePage(pages, "sourcePage000001", page("sourcePage000001", "quests", listOf(undersized)))

        CategoryMenuChassisMigration.migrateOnce(base, logger)

        val all = readPages(pages)
        val menu = all.getValue("sourcePage000001").entry("weeklyMenu00001")
        assertEquals(6, menu.get("rows").asInt, "the frame reaches row 5, so 6 rows are needed")
        val chassis = all.values.first { it.get("name").asString == "questcodex_migrated_chassis" }
            .entry(menu.get("menu").asString)
        assertEquals("54", chassis.get("size").asString)
    }

    @Test
    fun `a menu already wired to a populated gui is left alone`() {
        val base = tempDataFolder()
        val pages = base.resolve("pages")
        val gui = """
            { "id": "dailyGui0000001", "name": "open_daily_quest_gui", "type": "open_gui",
              "size": "36", "mainLayoutId": "dailyquestmenu",
              "layoutPool": [ { "case": "simple", "value": { "id": "dailyquestmenu", "items": [] } } ] }
        """.trimIndent()
        writePage(
            pages, "sourcePage000001",
            page(
                "sourcePage000001", "quests",
                listOf(legacyMenu("dailyMenu000001", "daily_quest_menu", "dailyquest", 4, menu = "dailyGui0000001"), gui),
            ),
        )
        val before = pages.resolve("sourcePage000001.json").readText()

        assertEquals(0, CategoryMenuChassisMigration.migrateOnce(base, logger))
        assertEquals(before, pages.resolve("sourcePage000001.json").readText())
        assertEquals(1, readPages(pages).size, "no chassis page when nothing was converted")
    }

    @Test
    fun `an empty referenced gui adopts the legacy layout instead of gaining a twin`() {
        val base = tempDataFolder()
        val pages = base.resolve("pages")
        val emptyGui = """
            { "id": "emptyGui0000001", "name": "empty_chassis", "type": "open_gui", "layoutPool": [] }
        """.trimIndent()
        writePage(
            pages, "sourcePage000001",
            page(
                "sourcePage000001", "quests",
                listOf(legacyMenu("uniqueMenu00001", "unique_quest_menu", "uniquequest", 6, menu = "emptyGui0000001"), emptyGui),
            ),
        )

        CategoryMenuChassisMigration.migrateOnce(base, logger)

        val page = readPages(pages).getValue("sourcePage000001")
        assertEquals("emptyGui0000001", page.entry("uniqueMenu00001").get("menu").asString)
        assertNull(page.entry("uniqueMenu00001").get("layoutPool"))
        val gui = page.entry("emptyGui0000001")
        assertEquals("uniquequest_main", gui.get("mainLayoutId").asString)
        assertEquals(1, gui.getAsJsonArray("layoutPool").size())
    }

    @Test
    fun `re-scanning on every boot converts nothing twice`() {
        val base = tempDataFolder()
        val pages = base.resolve("pages")
        writePage(
            pages, "sourcePage000001",
            page("sourcePage000001", "quests", listOf(legacyMenu("weeklyMenu00001", "weekly_quest_menu", "weeklyquest", 6))),
        )

        assertEquals(1, CategoryMenuChassisMigration.migrateOnce(base, logger))
        val afterFirst = readPages(pages)

        // The scan runs on every boot; the per-entry stamp is what stops a second conversion.
        assertEquals(0, CategoryMenuChassisMigration.migrateOnce(base, logger))
        assertEquals(0, CategoryMenuChassisMigration.migrateOnce(base, logger))
        assertFalse(base.resolve(".questcodex-chassis-v1").exists(), "the one-shot marker must be gone")

        val afterThird = readPages(pages)
        assertEquals(afterFirst.keys, afterThird.keys, "a re-scan must not create a second chassis page")
        assertEquals(afterFirst.getValue("sourcePage000001"), afterThird.getValue("sourcePage000001"))
    }

    @Test
    fun `a legacy page imported after a first empty run is still converted`() {
        // The regression this locks down: a one-shot marker was written even when the first scan
        // found nothing, so every page imported afterwards was skipped forever — silently, and on
        // exactly the installation the migration was written for.
        val base = tempDataFolder()
        val pages = base.resolve("pages")
        writePage(pages, "clean00000000001", page("clean00000000001", "clean", emptyList()))

        assertEquals(0, CategoryMenuChassisMigration.migrateOnce(base, logger))

        writePage(
            pages, "sourcePage000001",
            page("sourcePage000001", "quests", listOf(legacyMenu("weeklyMenu00001", "weekly_quest_menu", "weeklyquest", 6))),
        )

        assertEquals(1, CategoryMenuChassisMigration.migrateOnce(base, logger))
        val menu = readPages(pages).getValue("sourcePage000001").entry("weeklyMenu00001")
        assertTrue(menu.get("menu").asString.isNotBlank(), "the late import must get a chassis too")
    }

    @Test
    fun `staging and pages share one chassis page id`() {
        val base = tempDataFolder()
        val entry = legacyMenu("weeklyMenu00001", "weekly_quest_menu", "weeklyquest", 6)
        writePage(base.resolve("pages"), "sourcePage000001", page("sourcePage000001", "quests", listOf(entry)))
        writePage(base.resolve("staging"), "sourcePage000001", page("sourcePage000001", "quests", listOf(entry)))

        CategoryMenuChassisMigration.migrateOnce(base, logger)

        val publishedNew = readPages(base.resolve("pages")).keys - "sourcePage000001"
        val stagingNew = readPages(base.resolve("staging")).keys - "sourcePage000001"
        assertEquals(1, publishedNew.size)
        assertEquals(publishedNew, stagingNew, "publishing staging must not delete the published chassis page")
    }

    @Test
    fun `originals are backed up before being rewritten`() {
        val base = tempDataFolder()
        val pages = base.resolve("pages")
        writePage(
            pages, "sourcePage000001",
            page("sourcePage000001", "quests", listOf(legacyMenu("weeklyMenu00001", "weekly_quest_menu", "weeklyquest", 6))),
        )
        val original = pages.resolve("sourcePage000001.json").readText()

        CategoryMenuChassisMigration.migrateOnce(base, logger)

        val backup = base.resolve("backup/questcodex-chassis-v1").walkTopDown()
            .firstOrNull { it.isFile && it.name == "sourcePage000001.json" }
        assertNotNull(backup, "the pre-migration page must be recoverable")
        assertEquals(original, backup.readText())
        assertFalse(original == pages.resolve("sourcePage000001.json").readText())
    }
}
