package btcrenaud.questcodex.migration

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * Moves a `category_menu`'s layout out of the entry and into a real `open_gui` entry.
 *
 * The layout pool, its main layout, the GUI type and the audio config used to live on the
 * `category_menu` itself. They now live in an `open_gui` entry that the menu references through
 * its `menu` field, and the entry no longer declares those fields at all — so a page authored
 * against the old schema keeps a layout nothing reads. The menu still *looks* configured in the
 * panel while rendering nothing, and opening it does nothing at all: that is the failure this
 * migration removes.
 *
 * The conversion runs on the page files, before any of it reaches the entry classes, because
 * that is the only place the dropped fields still exist.
 */
object CategoryMenuChassisMigration {

    /** Stamped on every converted entry so a re-scan can never convert it twice. */
    const val SCHEMA_VERSION = 1
    private const val SCHEMA_FIELD = "_questCodexSchema"

    /**
     * Left over from a one-shot marker that gated the whole scan. It was written even when the
     * scan found nothing, so any page imported afterwards was never converted — the exact
     * situation this migration exists to fix. Idempotence comes from the per-entry
     * [SCHEMA_FIELD] stamp instead, which costs one boot-time parse and can never go stale.
     * The file is deleted on sight so an old installation stops carrying a dead flag.
     */
    private const val OBSOLETE_MARKER_FILE = ".questcodex-chassis-v1"

    private const val NEW_PAGE_NAME = "questcodex_migrated_chassis"

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val random = SecureRandom()
    private const val ID_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    /** A Typewriter identifier: 15 characters, letters and digits, as the panel generates them. */
    private fun newId(): String =
        (1..15).map { ID_ALPHABET[random.nextInt(ID_ALPHABET.length)] }.joinToString("")

    /** What one directory's conversion produced. */
    private data class DirectoryPlan(
        val directory: File,
        /** Source pages to rewrite, by page file. */
        val rewritten: Map<File, JsonObject>,
        /** `open_gui` entries to publish on the new chassis page. */
        val chassisEntries: List<JsonObject>,
        /** Page whose `version` field is worth copying onto the new page. */
        val versionSample: String?,
    )

    /**
     * Runs the conversion at most once per installation.
     *
     * @return the number of `category_menu` entries given a chassis, always 0 once the marker exists.
     */
    fun migrateOnce(baseDirectory: File, logger: Logger): Int {
        runCatching { baseDirectory.resolve(OBSOLETE_MARKER_FILE).takeIf { it.isFile }?.delete() }

        return runCatching { migrate(baseDirectory, logger) }
            .getOrElse {
                // A failed conversion must not take the extension down with it: the pages are
                // untouched unless every write succeeded, so the next start simply retries.
                logger.warning("[QuestCodex] Chassis migration aborted, pages left untouched: ${it.message}")
                0
            }
    }

    private fun migrate(baseDirectory: File, logger: Logger): Int {
        // Staging and pages are converted independently but share one page id, so publishing
        // staging cannot delete a chassis page that only exists on the published side.
        val chassisPageId = newId()

        val directories = listOf("staging", "pages")
            .map { baseDirectory.resolve(it) }
            .filter { it.isDirectory }
        val plans = directories.mapNotNull { planFor(it, logger) }
        if (plans.isEmpty()) {
            // Saying nothing here is what made a skipped migration indistinguishable from a
            // migration that ran and found nothing — the difference took a whole session to pin down.
            logger.info(
                "[QuestCodex] Chassis migration: nothing to convert in " +
                    directories.joinToString(", ") { "${it.name}/" }.ifEmpty { "<no page directory>" } +
                    "."
            )
            return 0
        }

        val backupRoot = baseDirectory.resolve(
            "backup/questcodex-chassis-v1/${timestampFormat.format(LocalDateTime.now())}"
        )
        plans.forEach { plan ->
            plan.rewritten.keys.forEach { file ->
                val target = backupRoot.resolve(plan.directory.name).resolve(file.name)
                target.parentFile.mkdirs()
                Files.copy(file.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
            }
        }

        plans.forEach { plan ->
            plan.rewritten.forEach { (file, page) -> writeAtomically(file, page.toString()) }
            writeAtomically(
                plan.directory.resolve("$chassisPageId.json"),
                chassisPage(chassisPageId, plan.chassisEntries, plan.versionSample).toString(),
            )
        }

        val converted = plans.sumOf { it.chassisEntries.size }
        logger.warning(
            "[QuestCodex] Migrated $converted category_menu entrie(s) to the open_gui chassis schema. " +
                "Their layouts now live on the new page '$NEW_PAGE_NAME' (id $chassisPageId). " +
                "Backup: ${backupRoot.path}. Typewriter had already read the old files, so run " +
                "`/typewriter reload` once for the converted menus to replace what is held in memory."
        )
        return converted
    }

    /** Converts one directory in memory; nothing is written until every directory has planned. */
    private fun planFor(directory: File, logger: Logger): DirectoryPlan? {
        val pages = directory.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.mapNotNull { file ->
                val json = runCatching { JsonParser.parseString(file.readText()).asJsonObject }
                    .getOrElse {
                        logger.warning("[QuestCodex] Skipping invalid page ${file.path}: ${it.message}")
                        null
                    }
                json?.let { file to it }
            }
            .orEmpty()
        if (pages.isEmpty()) return null

        // Resolving `menu` needs every entry in the directory, not just the current page.
        val entriesById = pages.flatMap { (_, page) -> page.entries() }
            .mapNotNull { entry -> entry.stringOrNull("id")?.let { it to entry } }
            .toMap()

        val rewritten = mutableMapOf<File, JsonObject>()
        val chassisEntries = mutableListOf<JsonObject>()
        val usedNames = entriesById.values.mapNotNull { it.stringOrNull("name") }.toMutableSet()

        pages.forEach { (file, page) ->
            var pageChanged = false
            page.entries().forEach { entry ->
                if (!isCategoryMenu(entry) || entry.schemaVersion() == SCHEMA_VERSION) return@forEach
                val legacyPool = entry.get("layoutPool")?.takeIf { it.isJsonArray && it.asJsonArray.size() > 0 }
                    ?: return@forEach

                val label = entry.stringOrNull("name") ?: entry.stringOrNull("id") ?: "?"
                val menuId = entry.stringOrNull("menu").orEmpty()
                val target = entriesById[menuId]

                when {
                    // No chassis yet: build one from the legacy layout and wire it up.
                    menuId.isBlank() -> {
                        val chassis = buildChassis(entry, legacyPool.asJsonArray, uniqueName(label, usedNames))
                        chassisEntries += chassis
                        entry.addProperty("menu", chassis.get("id").asString)
                        entry.stripLegacyChassisFields()
                        entry.addProperty(SCHEMA_FIELD, SCHEMA_VERSION)
                        pageChanged = true
                    }

                    // A chassis is referenced but empty: fill it instead of creating a second one.
                    target != null && !target.hasLayout() -> {
                        target.adoptLayoutFrom(entry, legacyPool.asJsonArray)
                        entry.stripLegacyChassisFields()
                        entry.addProperty(SCHEMA_FIELD, SCHEMA_VERSION)
                        pageChanged = true
                        // The chassis lives on its own page; mark that page for rewriting too.
                        pages.firstOrNull { (_, p) -> p.entries().any { it === target } }
                            ?.let { (targetFile, targetPage) -> rewritten[targetFile] = targetPage }
                    }

                    // Two competing layouts. The referenced GUI is what renders, so touching the
                    // legacy one would silently change what players see: report it and stop.
                    target != null -> logger.warning(
                        "[QuestCodex] category_menu '$label' carries a legacy layout AND references " +
                            "GUI '$menuId', which has its own layout. The GUI wins and the legacy " +
                            "layout is ignored. Nothing was changed: merge the two by hand in the panel."
                    )

                    else -> logger.warning(
                        "[QuestCodex] category_menu '$label' references GUI '$menuId', which does not " +
                            "exist. Its legacy layout was left in place; point `menu` at a real " +
                            "open_gui entry, or clear the field to have it converted on the next start."
                    )
                }
            }
            if (pageChanged) rewritten[file] = page
        }

        if (chassisEntries.isEmpty() && rewritten.isEmpty()) return null
        return DirectoryPlan(
            directory = directory,
            rewritten = rewritten,
            chassisEntries = chassisEntries,
            versionSample = pages.firstNotNullOfOrNull { (_, page) -> page.stringOrNull("version") },
        )
    }

    /** A fresh `open_gui` entry carrying [entry]'s legacy layout. */
    private fun buildChassis(entry: JsonObject, pool: JsonArray, name: String): JsonObject {
        val chassis = JsonObject()
        chassis.addProperty("id", newId())
        chassis.addProperty("name", name)
        chassis.addProperty(entry.typeKey(), "open_gui")
        chassis.add("criteria", JsonArray())
        chassis.add("modifiers", JsonArray())
        chassis.add("triggers", JsonArray())
        // The title stays on the category_menu: the codex builds the inventory title from it, and
        // duplicating it here would leave two sources for one string.
        chassis.addProperty("title", "")
        chassis.adoptLayout(entry, pool)
        return chassis
    }

    /** Copies the layout, size, GUI type and audio of a legacy [entry] onto this `open_gui`. */
    private fun JsonObject.adoptLayoutFrom(entry: JsonObject, pool: JsonArray) = adoptLayout(entry, pool)

    private fun JsonObject.adoptLayout(entry: JsonObject, pool: JsonArray) {
        add("layoutPool", pool.deepCopy())
        entry.get("mainLayoutId")?.let { add("mainLayoutId", it.deepCopy()) }
        entry.get("storagePool")?.let { add("storagePool", it.deepCopy()) }
        entry.get("guiType")?.let { add("guiType", it.deepCopy()) }
        entry.get("audio")?.let { add("audio", it.deepCopy()) }
        // `rows` stays authoritative on the category_menu — the codex sizes the inventory from it.
        // Mirroring it here keeps the GUI openable on its own and matching what the codex draws.
        //
        // It is also raised to what the layout actually needs: a menu authored with too few rows
        // loses its bottom band (back button, sort, pagination) to clipping, and because the size
        // written here would agree with the undersized `rows`, nothing downstream would ever
        // report it. Never lowered — extra empty rows are a deliberate choice.
        val declared = entry.get("rows")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asInt
            ?: 6
        val rows = maxOf(declared, rowsRequiredBy(entry, pool)).coerceIn(1, 6)
        if (rows != declared) entry.addProperty("rows", rows)
        addProperty("size", (rows * 9).toString())
        addProperty("_questCodexChassis", true)
    }

    /**
     * Removes the fields the entry class no longer declares.
     *
     * Left in place, they are dead weight that keeps making the entry look configured; `views`,
     * `baseMenuId`, `defaultViewId`, `breadcrumbSeparator` and `sortDisplay` are NOT removed —
     * those are still real `category_menu` fields.
     */
    private fun JsonObject.stripLegacyChassisFields() {
        listOf("layoutPool", "mainLayoutId", "storagePool", "guiType", "audio").forEach(::remove)
    }

    /**
     * Rows the ROOT layout occupies.
     *
     * Only the root sizes the inventory: a scrollable's inner layout lives on a virtual grid that
     * gets clipped into its frame, so counting its rows would ask for an inventory taller than the
     * six rows Minecraft can open.
     */
    private fun rowsRequiredBy(entry: JsonObject, pool: JsonArray): Int {
        val mainLayoutId = entry.stringOrNull("mainLayoutId") ?: return 1
        val root = pool.filter(JsonElement::isJsonObject)
            .map { it.asJsonObject }
            .firstNotNullOfOrNull { layout ->
                layout.get("value")?.takeIf { it.isJsonObject }?.asJsonObject
                    ?.takeIf { it.stringOrNull("id") == mainLayoutId }
            } ?: return 1

        fun ints(array: String, key: String, extent: String?): List<Int> =
            root.get(array)?.takeIf { it.isJsonArray }?.asJsonArray
                ?.filter(JsonElement::isJsonObject)
                ?.map { it.asJsonObject }
                ?.map { child ->
                    val start = child.get(key)?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
                    val size = extent?.let { child.get(it)?.takeIf { v -> v.isJsonPrimitive }?.asInt } ?: 1
                    start + size
                }
                .orEmpty()

        return (ints("frames", "y", "height") + ints("items", "y", null)).maxOrNull() ?: 1
    }

    private fun JsonObject.hasLayout(): Boolean =
        get("layoutPool")?.takeIf { it.isJsonArray }?.asJsonArray?.size()?.let { it > 0 } == true

    /** The page holding the generated chassis entries. */
    private fun chassisPage(id: String, entries: List<JsonObject>, version: String?): JsonObject {
        val page = JsonObject()
        page.addProperty("id", id)
        page.addProperty("name", NEW_PAGE_NAME)
        // `open_gui` and `category_menu` are both action entries, so a sequence page holds them.
        page.addProperty("type", "sequence")
        page.addProperty("priority", 0)
        page.add("entries", JsonArray().apply { entries.forEach(::add) })
        page.add("entryPositions", JsonObject())
        if (version != null) page.addProperty("version", version)
        return page
    }

    private fun isCategoryMenu(entry: JsonObject): Boolean =
        entry.stringOrNull("blueprintId") == "category_menu" || entry.stringOrNull("type") == "category_menu"

    /** Pages written by the panel use `type`; some tooling writes `blueprintId`. Mirror the source. */
    private fun JsonObject.typeKey(): String = if (has("blueprintId")) "blueprintId" else "type"

    private fun JsonObject.schemaVersion(): Int? = get(SCHEMA_FIELD)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.asInt

    private fun JsonObject.entries(): List<JsonObject> =
        get("entries")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.filter(JsonElement::isJsonObject)
            ?.map { it.asJsonObject }
            .orEmpty()

    private fun JsonObject.stringOrNull(key: String): String? = get(key)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString
        ?.takeIf { it.isNotBlank() }

    private fun uniqueName(base: String, used: MutableSet<String>): String {
        val sanitized = base.lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_')
            .ifBlank { "category_menu" }
        var candidate = "${sanitized}_chassis"
        var suffix = 2
        while (!used.add(candidate)) candidate = "${sanitized}_chassis_${suffix++}"
        return candidate
    }

    private fun writeAtomically(file: File, content: String) {
        file.parentFile?.mkdirs()
        val temporary = file.toPath().resolveSibling("${file.name}.questcodex-v1.tmp")
        Files.writeString(temporary, content, StandardCharsets.UTF_8)
        try {
            Files.move(
                temporary,
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
