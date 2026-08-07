package btcrenaud.questcodex.advancement

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * A single advancement, reduced to plain values.
 *
 * Nothing here comes from Bukkit: the entry is read and converted upstream so this stage can be
 * exercised without a server. [titleJson] and [descriptionJson] are already-serialized Minecraft
 * text components, and [icon] is a resolved item id (`minecraft:diamond`).
 */
data class AdvancementSpec(
    /** Editor entry id — used to resolve [parentEntryId] and to name the entry in a rejection. */
    val entryId: String,
    val entryName: String,
    val namespace: String,
    val key: String,
    /** Entry id of the parent advancement, or empty for a root. */
    val parentEntryId: String,
    val icon: String,
    val titleJson: String,
    val descriptionJson: String,
    val frame: String,
    /** Background texture of the tab. Only a root advancement opens a tab, so only it uses one. */
    val background: String,
    val showToast: Boolean,
    val announceToChat: Boolean,
    val hidden: Boolean,
    /** Earn this one as soon as the player plays, instead of waiting for an action to award it. */
    val autoGrant: Boolean,
)

/** An advancement that will not be written, and the reason an author can act on. */
data class AdvancementRejection(val entryId: String, val entryName: String, val reason: String)

/**
 * Turns advancement specs into the exact file tree of a datapack.
 *
 * Everything Minecraft refuses at load time — a malformed key, two advancements claiming the same
 * id, a parent that no longer exists, a cycle — is refused here instead, with the offending entry
 * named. A rejected advancement is dropped; the rest of the pack is still produced, because one bad
 * entry must not cost the author every other advancement.
 */
object AdvancementPackBuilder {

    /**
     * The only criterion every generated advancement carries.
     *
     * `minecraft:impossible` can never fire on its own, so the advancement is granted exactly when
     * the server awards this criterion and never by accident.
     */
    const val GRANTED_CRITERION = "granted"

    /** Directory name of the generated pack, under `<world>/datapacks/`. */
    const val PACK_DIRECTORY = "questcodex_advancements"

    /**
     * Floor of the declared format range: 1.20.5 is the first version whose advancement icon is
     * `{"id": ...}` rather than `{"item": ...}`, which is the shape written below.
     */
    private const val MIN_PACK_FORMAT = 32

    /**
     * Ceiling of the declared range. Paper exposes no accessor for the running pack format, so the
     * pack declares an open-ended range rather than carrying a version table that would need
     * editing at every Minecraft release.
     */
    private const val MAX_PACK_FORMAT = 9999

    private const val META_FILE = "pack.mcmeta"

    private val NAMESPACE_PATTERN = Regex("[a-z0-9_.-]+")
    private val KEY_PATTERN = Regex("[a-z0-9_./-]+")

    /** Files to write, keyed by their path relative to the pack directory, and what was dropped. */
    data class Pack(val files: Map<String, String>, val rejections: List<AdvancementRejection>)

    fun build(specs: List<AdvancementSpec>): Pack {
        val rejections = mutableListOf<AdvancementRejection>()
        val accepted = LinkedHashMap<String, AdvancementSpec>() // entry id -> spec
        val claimedIds = HashMap<String, String>() // "ns:key" -> entry id that took it

        for (spec in specs) {
            val reason = syntaxProblem(spec) ?: duplicateProblem(spec, claimedIds, accepted)
            if (reason != null) {
                rejections += AdvancementRejection(spec.entryId, spec.entryName, reason)
                continue
            }
            claimedIds["${spec.namespace}:${spec.key}"] = spec.entryId
            accepted[spec.entryId] = spec
        }

        // Parent resolution runs over the accepted set only: an advancement whose parent was just
        // rejected has no tree to hang from, so it cannot be written either.
        dropUnrootable(accepted, rejections)

        val files = LinkedHashMap<String, String>()
        files[META_FILE] = packMeta()
        for (spec in accepted.values) {
            val parent = accepted[spec.parentEntryId]
            files["data/${spec.namespace}/advancement/${spec.key}.json"] = advancementJson(spec, parent)
        }
        return Pack(files, rejections)
    }

    private fun syntaxProblem(spec: AdvancementSpec): String? = when {
        spec.namespace.isBlank() || spec.key.isBlank() ->
            "namespace and key are both required."
        !NAMESPACE_PATTERN.matches(spec.namespace) ->
            "namespace '${spec.namespace}' is invalid: only a-z, 0-9, '_', '.' and '-' are allowed."
        !KEY_PATTERN.matches(spec.key) ->
            "key '${spec.key}' is invalid: only a-z, 0-9, '_', '.', '-' and '/' are allowed."
        !parses(spec.titleJson) -> "the title could not be converted to a text component."
        !parses(spec.descriptionJson) -> "the description could not be converted to a text component."
        else -> null
    }

    private fun duplicateProblem(
        spec: AdvancementSpec,
        claimedIds: Map<String, String>,
        accepted: Map<String, AdvancementSpec>,
    ): String? {
        val owner = claimedIds["${spec.namespace}:${spec.key}"] ?: return null
        val ownerName = accepted[owner]?.entryName ?: owner
        return "'${spec.namespace}:${spec.key}' is already used by '$ownerName'."
    }

    private fun parses(json: String): Boolean =
        runCatching { JsonParser.parseString(json) }
            .getOrNull()
            ?.isJsonObject == true

    /**
     * Removes every advancement that cannot reach a root, in one pass per removal.
     *
     * Two shapes end up here for the same reason — the chain never terminates: a parent that is not
     * in the accepted set, and a cycle where each parent exists but the walk returns to where it
     * started. Both are reported with the shape that was actually found, since the fix differs.
     */
    private fun dropUnrootable(
        accepted: MutableMap<String, AdvancementSpec>,
        rejections: MutableList<AdvancementRejection>,
    ) {
        val toDrop = mutableListOf<Pair<AdvancementSpec, String>>()
        for (spec in accepted.values) {
            val seen = mutableSetOf(spec.entryId)
            var current = spec
            while (current.parentEntryId.isNotBlank()) {
                val parent = accepted[current.parentEntryId]
                if (parent == null) {
                    toDrop += spec to "its parent advancement no longer exists."
                    break
                }
                if (!seen.add(parent.entryId)) {
                    toDrop += spec to "its parent chain loops back on itself."
                    break
                }
                current = parent
            }
        }
        for ((spec, reason) in toDrop) {
            accepted.remove(spec.entryId)
            rejections += AdvancementRejection(spec.entryId, spec.entryName, reason)
        }
    }

    /**
     * Declares the range three times over, because the field that carries it was replaced twice.
     *
     * `pack_format` alone is what pre-1.20.2 reads; `supported_formats` is what 1.20.2 added; and
     * from pack format 81 on, a pack claiming to support a newer version is rejected outright
     * unless it also carries `min_format`/`max_format` — the server says so and falls back to a
     * guessed pack type, which is not something to rely on. Unknown fields are ignored by every
     * one of those readers, so writing all three is what makes one file work everywhere.
     *
     * The last pair is written as `[major, minor]` rather than a bare number, because that is the
     * shape the server itself writes in the `bukkit` datapack it generates next to this one.
     */
    private fun packMeta(): String {
        val supported = JsonObject().apply {
            addProperty("min_inclusive", MIN_PACK_FORMAT)
            addProperty("max_inclusive", MAX_PACK_FORMAT)
        }
        val pack = JsonObject().apply {
            addProperty("pack_format", MIN_PACK_FORMAT)
            add("supported_formats", supported)
            add("min_format", versionedFormat(MIN_PACK_FORMAT))
            add("max_format", versionedFormat(MAX_PACK_FORMAT))
            add("description", JsonParser.parseString("""{"text":"QuestCodex advancements"}"""))
        }
        return JsonObject().apply { add("pack", pack) }.toString()
    }

    /**
     * Accepts a tab background written either way, and emits the one the client resolves.
     *
     * Minecraft now names the background as a plain sprite id — `minecraft:gui/advancements/
     * backgrounds/stone`, which vanilla's own `story/root.json` uses — and prepends `textures/`
     * and `.png` itself. The older full path is still a syntactically valid resource location, so
     * it is accepted without complaint and then resolves to nothing, which is exactly the failure
     * that leaves a tab unrendered. Every tutorial written before 1.21.2 teaches the old form, so
     * it is converted rather than refused.
     */
    private fun normalizeBackground(raw: String): String {
        val separator = raw.indexOf(':')
        val namespace = if (separator > 0) raw.substring(0, separator + 1) else ""
        val path = raw.substring(separator + 1).removePrefix("textures/").removeSuffix(".png")
        return namespace + path
    }

    /** A pack format as the `[major, minor]` pair the current metadata schema expects. */
    private fun versionedFormat(major: Int): JsonArray = JsonArray().apply {
        add(major)
        add(0)
    }

    private fun advancementJson(spec: AdvancementSpec, parent: AdvancementSpec?): String {
        val display = JsonObject().apply {
            add("icon", JsonObject().apply { addProperty("id", spec.icon) })
            add("title", JsonParser.parseString(spec.titleJson))
            add("description", JsonParser.parseString(spec.descriptionJson))
            addProperty("frame", spec.frame)
            addProperty("show_toast", spec.showToast)
            addProperty("announce_to_chat", spec.announceToChat)
            addProperty("hidden", spec.hidden)
            // A background opens a tab, and only a root advancement has one. Writing it on a child
            // is silently ignored by the client, so it is dropped here to keep the file honest.
            if (parent == null && spec.background.isNotBlank()) {
                addProperty("background", normalizeBackground(spec.background))
            }
        }

        // `impossible` never fires, so an action is the only thing that can award the advancement.
        // `tick` fires on the player's first tick, which is what makes a tab exist before anyone
        // has earned anything — the state a root advancement is normally in.
        val trigger = if (spec.autoGrant) "minecraft:tick" else "minecraft:impossible"
        val criteria = JsonObject().apply {
            add(GRANTED_CRITERION, JsonObject().apply { addProperty("trigger", trigger) })
        }

        return JsonObject().apply {
            add("display", display)
            add("criteria", criteria)
            // Named explicitly so the advancement stays granted by that one criterion even if a
            // future edit adds another.
            add("requirements", JsonArray().apply {
                add(JsonArray().apply { add(GRANTED_CRITERION) })
            })
            if (parent != null) addProperty("parent", "${parent.namespace}:${parent.key}")
        }.toString()
    }
}
