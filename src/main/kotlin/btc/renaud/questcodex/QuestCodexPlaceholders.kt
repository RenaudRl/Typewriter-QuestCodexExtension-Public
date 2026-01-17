package btc.renaud.questcodex

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.extensions.placeholderapi.PlaceholderHandler
import com.typewritermc.quest.QuestStatus
import com.typewritermc.quest.entries.QuestEntry
import org.bukkit.entity.Player

@Singleton
class QuestCodexPlaceholders : PlaceholderHandler {
    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        val p = player ?: return null
        val key = params.lowercase()
        
        // Cache all unique quests to avoid re-calculating for each total placeholder in a single request
        // Note: In a real high-performance scenario, we'd cache this longer, but for PAPI request it's okay-ish.
        // However, QuestCategoryRegistry.all() already does quite some work.
        
        return when {
            key == "total_quests" -> {
                val quests = getAllUniqueQuests()
                quests.size.toString()
            }
            key == "total_completed" -> {
                val quests = getAllUniqueQuests()
                quests.count { it.questStatus(p) == QuestStatus.COMPLETED }.toString()
            }
            key == "total_in_progress" || key == "total_inprogress" -> {
                val quests = getAllUniqueQuests()
                quests.count { it.questStatus(p) == QuestStatus.ACTIVE }.toString()
            }
            key == "total_not_started" || key == "total_notstarted" -> {
                val quests = getAllUniqueQuests()
                quests.count { it.questStatus(p) == QuestStatus.INACTIVE }.toString()
            }
            key == "total_progress" -> {
                val quests = getAllUniqueQuests()
                val completed = quests.count { it.questStatus(p) == QuestStatus.COMPLETED }
                val total = quests.size
                "$completed/$total"
            }
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
                val categoryName = rawName.replace('_', ' ')
                val category = QuestCategoryRegistry.find(categoryName) ?: return null
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

    private fun getAllUniqueQuests(): Set<QuestEntry> {
        return QuestCategoryRegistry.all().flatMap { it.allQuests() }.toSet()
    }
}

