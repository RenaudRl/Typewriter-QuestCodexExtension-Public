package btcrenaud.questcodex.advancement

import com.typewritermc.core.entries.Query
import com.typewritermc.core.utils.launch
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.utils.Sync
import kotlinx.coroutines.Dispatchers
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.Bukkit
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Writes the advancement definitions of the pages into a datapack the extension owns, and asks the
 * server to load it.
 *
 * The pack directory belongs to this service alone: it is rewritten from the pages on every start,
 * so an advancement removed from a page disappears from the game instead of surviving as a file
 * nobody edits any more. Because a rewrite costs a datapack reload, the desired content is compared
 * with what is already on disk first — an unchanged setup reloads nothing.
 */
object AdvancementDatapackService {

    private val mm = MiniMessage.miniMessage()
    private val componentJson = GsonComponentSerializer.gson()

    fun initialize() {
        val levelRoot = resolveLevelRoot() ?: return

        val specs = mutableListOf<AdvancementSpec>()
        for (entry in Query.find<AdvancementDefinitionEntry>()) {
            val spec = toSpec(entry)
            if (spec == null) {
                // The icon is the only field Bukkit gets a say in, so it is the only one that can
                // fail here rather than in the builder.
                plugin.logger.warning(
                    "[QuestCodex] Advancement '${entry.name}' was skipped: " +
                        "${entry.icon.name} is not an item and cannot be an advancement icon."
                )
                continue
            }
            specs += spec
        }

        val pack = AdvancementPackBuilder.build(specs)
        pack.rejections.forEach {
            plugin.logger.warning("[QuestCodex] Advancement '${it.entryName}' was skipped: ${it.reason}")
        }

        // One tab in the advancement screen is one root advancement, so naming them here is the
        // only way to tell "no tab was defined" apart from "the tab was defined and is not shown".
        val rejected = pack.rejections.mapTo(HashSet()) { it.entryId }
        val tabs = specs.filter { it.parentEntryId.isBlank() && it.entryId !in rejected }
        if (tabs.isEmpty()) {
            plugin.logger.info(
                "[QuestCodex] No advancement tab is defined; a definition with an empty parent " +
                    "becomes a tab of its own."
            )
        } else {
            plugin.logger.info(
                "[QuestCodex] Advancement tab(s): " +
                    tabs.joinToString { "${it.namespace}:${it.key}" } + "."
            )
        }

        val packDir = File(File(levelRoot, "datapacks"), AdvancementPackBuilder.PACK_DIRECTORY)
        if (readPack(packDir) == pack.files) {
            plugin.logger.info(
                "[QuestCodex] ${specs.size - pack.rejections.size} advancement(s) already up to date."
            )
            return
        }

        val written = runCatching { writePack(packDir, pack.files) }
        if (written.isFailure) {
            plugin.logger.severe(
                "[QuestCodex] Could not write the advancement datapack in ${packDir.path}: " +
                    "${written.exceptionOrNull()?.message}"
            )
            return
        }

        // Datapacks are only read by the server thread, and only on a data reload.
        Dispatchers.Sync.launch {
            Bukkit.reloadData()
            // The path is part of the message: a pack written outside the level root loads nothing
            // and reports nothing, so the only way to see it is to read where it went.
            plugin.logger.info(
                "[QuestCodex] Wrote ${pack.files.size - 1} advancement(s) to ${packDir.path} " +
                    "and reloaded server data."
            )
        }
    }

    /**
     * Directory the server reads datapacks from — the level root, which holds `level.dat`.
     *
     * Not [org.bukkit.World.getWorldFolder]: that one points at the *dimension* directory
     * (`<level>/dimensions/minecraft/overworld` on a Paper server), and a pack written there is
     * silently ignored — the reload finds nothing and reports no error.
     *
     * @return null, after saying why, when the level root cannot be identified.
     */
    private fun resolveLevelRoot(): File? {
        val levelName = Bukkit.getWorlds().firstOrNull()?.name
        if (levelName == null) {
            plugin.logger.warning(
                "[QuestCodex] No world is loaded; advancement definitions were not written."
            )
            return null
        }
        val root = File(Bukkit.getWorldContainer(), levelName)
        // Writing into a directory that is not the level root costs nothing at write time and
        // everything at load time, so the layout is confirmed instead of assumed.
        if (!File(root, "level.dat").isFile) {
            plugin.logger.severe(
                "[QuestCodex] Expected the level root at ${root.path}, but it holds no level.dat; " +
                    "advancement definitions were not written."
            )
            return null
        }
        return root
    }

    /** @return null when the entry names an icon that is not an item. */
    private fun toSpec(entry: AdvancementDefinitionEntry): AdvancementSpec? {
        if (!entry.icon.isItem) return null
        return AdvancementSpec(
            entryId = entry.id,
            entryName = entry.name,
            namespace = entry.namespace.trim(),
            key = entry.key.trim(),
            parentEntryId = entry.parent.id,
            icon = entry.icon.key.asString(),
            titleJson = componentJson.serialize(mm.deserialize(entry.title)),
            descriptionJson = componentJson.serialize(mm.deserialize(entry.description)),
            frame = entry.frame.jsonValue,
            background = entry.background.trim(),
            showToast = entry.showToast,
            announceToChat = entry.announceToChat,
            hidden = entry.hidden,
            autoGrant = entry.autoGrant,
        )
    }

    /** Current pack content, in the same shape the builder produces, so the two can be compared. */
    private fun readPack(packDir: File): Map<String, String> {
        if (!packDir.isDirectory) return emptyMap()
        return packDir.walkTopDown()
            .filter { it.isFile }
            .associate { file ->
                file.relativeTo(packDir).invariantSeparatorsPath to file.readText(StandardCharsets.UTF_8)
            }
    }

    private fun writePack(packDir: File, files: Map<String, String>) {
        // The whole directory goes, including advancements that are no longer defined anywhere.
        if (packDir.exists() && !packDir.deleteRecursively()) {
            error("the previous pack directory could not be removed")
        }
        for ((path, content) in files) {
            val target = File(packDir, path)
            target.parentFile?.mkdirs()
            target.writeText(content, StandardCharsets.UTF_8)
        }
    }
}
