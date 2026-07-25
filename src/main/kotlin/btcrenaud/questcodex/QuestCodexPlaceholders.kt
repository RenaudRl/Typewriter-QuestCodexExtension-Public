package btcrenaud.questcodex

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.ref
import com.typewritermc.engine.paper.extensions.placeholderapi.PlaceholderHandler
import com.typewritermc.quest.QuestStatus
import com.typewritermc.quest.entries.QuestEntry
import com.typewritermc.quest.entries.questShowingObjectives
import btcrenaud.questcodex.entries.QuestCodexConfig
import btcrenaud.questcodex.tracking.QuestCodexTrackingService
import org.bukkit.entity.Player

/**
 * Placeholder handler for QuestCodex extension.
 * Registers placeholders like %typewriter_total_quests%, %typewriter_category_main_completed%, etc.
 */
@Singleton
class QuestCodexPlaceholders : PlaceholderHandler {
    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        val p = player ?: return null
        val key = params.lowercase()
        return when {
            key.startsWith("codex_tracked_") -> trackedPlaceholder(p, key.removePrefix("codex_tracked_"))
            // ── Global stats ──
            key == "total_quests" -> {
                val total = QuestCategoryRegistry.all().flatMap { it.allQuests() }.toSet().size
                total.toString()
            }
            key == "total_completed" -> {
                val quests = QuestCategoryRegistry.all().flatMap { it.allQuests() }.toSet()
                quests.count { it.questStatus(p) == QuestStatus.COMPLETED }.toString()
            }
            key == "total_in_progress" || key == "total_inprogress" -> {
                val quests = QuestCategoryRegistry.all().flatMap { it.allQuests() }.toSet()
                quests.count { it.questStatus(p) == QuestStatus.ACTIVE }.toString()
            }
            key == "total_not_started" || key == "total_notstarted" -> {
                val quests = QuestCategoryRegistry.all().flatMap { it.allQuests() }.toSet()
                quests.count { it.questStatus(p) == QuestStatus.INACTIVE }.toString()
            }
            key == "total_progress" -> {
                val quests = QuestCategoryRegistry.all().flatMap { it.allQuests() }.toSet()
                val completed = quests.count { it.questStatus(p) == QuestStatus.COMPLETED }
                val total = quests.size
                "$completed/$total"
            }
            // ── Per-category stats ──
            key.startsWith("category_") -> {
                val data = key.removePrefix("category_")
                val statusTokens = listOf(
                    "completed",
                    "in_progress",
                    "inprogress",
                    "not_started",
                    "notstarted",
                    "progress"
                )
                var rawName = data
                var status: String? = null
                for (token in statusTokens) {
                    if (data.endsWith("_${token}")) {
                        rawName = data.removeSuffix("_${token}")
                        status = token
                        break
                    }
                }
                val category = QuestCategoryRegistry.find(rawName) ?: return "0"
                val quests = category.allQuests()
                when (status) {
                    "completed" -> quests.count { it.questStatus(p) == QuestStatus.COMPLETED }.toString()
                    "in_progress", "inprogress" -> quests.count { it.questStatus(p) == QuestStatus.ACTIVE }.toString()
                    "not_started", "notstarted" -> quests.count { it.questStatus(p) == QuestStatus.INACTIVE }.toString()
                    "progress" -> {
                        val completed = quests.count { it.questStatus(p) == QuestStatus.COMPLETED }
                        "$completed/${quests.size}"
                    }
                    else -> quests.size.toString()
                }
            }
            else -> null
        }
    }

    private fun trackedPlaceholder(player: Player, key: String): String? {
        val quests = QuestCodexTrackingService.trackedQuestIds(player)
            .mapNotNull { Query.findById<QuestEntry>(it) }
        val primaryId = QuestCodexTrackingService.primaryQuestId(player).orEmpty()
        return when (key) {
            "count" -> quests.size.toString()
            "limit" -> QuestCodexConfig.maxTrackedQuests.toString()
            "multi_enabled" -> QuestCodexConfig.multiTrackingEnabled.toString()
            "has_any" -> quests.isNotEmpty().toString()
            "ids" -> quests.joinToString(",") { it.id }
            "names" -> quests.joinToString(", ") { it.display(player) }
            "names_lines" -> quests.joinToString("\n") { it.display(player) }
            "primary_id" -> primaryId
            "primary_name" -> quests.firstOrNull { it.id == primaryId }?.display(player).orEmpty()
            else -> indexedTrackedPlaceholder(player, key, quests, primaryId)
        }
    }

    private fun indexedTrackedPlaceholder(
        player: Player,
        key: String,
        quests: List<QuestEntry>,
        primaryId: String,
    ): String? {
        if (key.startsWith("has_")) {
            val questId = key.removePrefix("has_")
            return quests.any { it.id.equals(questId, ignoreCase = true) }.toString()
        }
        val match = TRACKED_INDEX.matchEntire(key) ?: return null
        val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: return null
        val quest = quests.getOrNull(index) ?: return ""
        val objectives = player.questShowingObjectives(quest.ref()).toList()
        return when (match.groupValues[2]) {
            "id" -> quest.id
            "name" -> quest.display(player)
            "status" -> quest.questStatus(player).name.lowercase()
            "primary" -> (quest.id == primaryId).toString()
            "objective_count" -> objectives.size.toString()
            "objectives" -> objectives.joinToString(", ") { it.display(player) }
            "objectives_lines" -> objectives.joinToString("\n") { it.display(player) }
            "priority" -> (objectives.maxOfOrNull { it.priorityOverride.orElse(0) } ?: 0).toString()
            else -> null
        }
    }

    private companion object {
        val TRACKED_INDEX = Regex("""(\d+)_(id|name|status|primary|objective_count|objectives|objectives_lines|priority)""")
    }
}
