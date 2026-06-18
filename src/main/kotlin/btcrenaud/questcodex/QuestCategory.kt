package btcrenaud.questcodex

import com.typewritermc.core.entries.Ref
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.entry.matches
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.engine.paper.utils.item.CustomItem
import com.typewritermc.engine.paper.utils.item.components.ItemMaterialComponent
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.quest.entries.QuestEntry
import com.typewritermc.quest.QuestStatus
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import com.typewritermc.engine.paper.utils.asMini
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration

/**
 * Represents a collection of quests grouped under a single category.
 */
data class QuestCategory(
    val name: String,
    var title: String = name,
    var icon: Item = CustomItem(),
    var rows: Int = 4,
    var order: Int = 0,
    var activeCriteria: List<Criteria> = emptyList(),
    var completedCriteria: List<Criteria> = emptyList(),
    var blockedMessage: List<String> = emptyList(),
    var activeMessage: List<String> = emptyList(),
    var completedMessage: List<String> = emptyList(),
    var hideWhenLocked: Boolean = false,
    var parent: QuestCategory? = null,
    val subCategories: MutableList<QuestCategory> = mutableListOf(),
    val quests: MutableList<Ref<QuestEntry>> = mutableListOf(),
    val questOrders: MutableMap<String, Int> = mutableMapOf(),
    val questItems: MutableMap<String, QuestItemOverrides> = mutableMapOf(),
    val questDisplays: MutableMap<String, QuestDisplayOverrides> = mutableMapOf(),
    val questAdditionalLore: MutableMap<String, QuestAdditionalLore> = mutableMapOf(),
)

data class QuestItemOverrides(
    val notStarted: Item = CustomItem(),
    val inProgress: Item = CustomItem(),
    val completed: Item = CustomItem(),
) {
    fun hasOverrides(): Boolean =
        notStarted != Item.Empty || inProgress != Item.Empty || completed != Item.Empty

    fun itemFor(status: QuestStatus): Item = when (status) {
        QuestStatus.INACTIVE -> notStarted
        QuestStatus.ACTIVE -> inProgress
        QuestStatus.COMPLETED -> completed
    }

    fun overrideWith(other: QuestItemOverrides): QuestItemOverrides = QuestItemOverrides(
        notStarted = if (other.notStarted != Item.Empty) other.notStarted else notStarted,
        inProgress = if (other.inProgress != Item.Empty) other.inProgress else inProgress,
        completed = if (other.completed != Item.Empty) other.completed else completed,
    )
}

data class QuestStateDisplayOverride(
    val name: String = "",
    val lore: List<String> = emptyList(),
    val hideObjectives: Boolean = false,
    val hideQuest: Boolean = false,
) {
    fun hasOverrides(): Boolean =
        name.isNotBlank() || lore.isNotEmpty() || hideObjectives || hideQuest

    fun overrideWith(other: QuestStateDisplayOverride): QuestStateDisplayOverride = QuestStateDisplayOverride(
        name = if (other.name.isNotBlank()) other.name else name,
        lore = if (other.lore.isNotEmpty()) other.lore else lore,
        hideObjectives = hideObjectives || other.hideObjectives,
        hideQuest = hideQuest || other.hideQuest,
    )
}

data class QuestDisplayOverrides(
    val notStarted: QuestStateDisplayOverride = QuestStateDisplayOverride(),
    val inProgress: QuestStateDisplayOverride = QuestStateDisplayOverride(),
    val completed: QuestStateDisplayOverride = QuestStateDisplayOverride(),
) {
    fun hasOverrides(): Boolean =
        notStarted.hasOverrides() || inProgress.hasOverrides() || completed.hasOverrides()

    fun overrideWith(other: QuestDisplayOverrides): QuestDisplayOverrides = QuestDisplayOverrides(
        notStarted = notStarted.overrideWith(other.notStarted),
        inProgress = inProgress.overrideWith(other.inProgress),
        completed = completed.overrideWith(other.completed),
    )

    fun state(status: QuestStatus): QuestStateDisplayOverride = when (status) {
        QuestStatus.INACTIVE -> notStarted
        QuestStatus.ACTIVE -> inProgress
        QuestStatus.COMPLETED -> completed
    }
}

data class QuestAdditionalLore(
    val notStarted: List<String> = emptyList(),
    val inProgress: List<String> = emptyList(),
    val completed: List<String> = emptyList(),
) {
    fun hasContent(): Boolean =
        notStarted.isNotEmpty() || inProgress.isNotEmpty() || completed.isNotEmpty()

    fun overrideWith(other: QuestAdditionalLore): QuestAdditionalLore = QuestAdditionalLore(
        notStarted = if (other.notStarted.isNotEmpty()) other.notStarted else notStarted,
        inProgress = if (other.inProgress.isNotEmpty()) other.inProgress else inProgress,
        completed = if (other.completed.isNotEmpty()) other.completed else completed,
    )

    fun forStatus(status: QuestStatus): List<String> = when (status) {
        QuestStatus.INACTIVE -> notStarted
        QuestStatus.ACTIVE -> inProgress
        QuestStatus.COMPLETED -> completed
    }
}

/**
 * Registry for quest categories.
 */
object QuestCategoryRegistry {
    private val categories: MutableMap<String, QuestCategory> = mutableMapOf()

    fun register(
        name: String,
        title: String = name,
        icon: Item = CustomItem(),
        rows: Int = 4,
        parent: String = "",
        order: Int = 0,
        activeCriteria: List<Criteria> = emptyList(),
        completedCriteria: List<Criteria> = emptyList(),
        blockedMessage: List<String> = emptyList(),
        activeMessage: List<String> = emptyList(),
        completedMessage: List<String> = emptyList(),
        hideWhenLocked: Boolean = false,
    ): QuestCategory {
        val key = name.lowercase()
        val category = categories.getOrPut(key) { QuestCategory(name) }
        category.title = title
        category.rows = rows.coerceIn(3, 6)
        if (!icon.isEffectivelyEmpty()) {
            category.icon = icon
        }
        if (parent.isNotBlank()) {
            val parentCategory = find(parent) ?: ensure(parent)
            category.parent = parentCategory
            if (!parentCategory.subCategories.contains(category)) {
                parentCategory.subCategories.add(category)
            }
        }
        category.order = order
        category.activeCriteria = activeCriteria
        category.completedCriteria = completedCriteria
        category.blockedMessage = blockedMessage
        category.activeMessage = activeMessage
        category.completedMessage = completedMessage
        category.hideWhenLocked = hideWhenLocked
        return category
    }

    private fun ensure(name: String): QuestCategory {
        val key = name.lowercase()
        return categories.getOrPut(key) { QuestCategory(name) }
    }

    fun find(name: String): QuestCategory? = categories[name.lowercase()]

    fun addQuest(
        categoryName: String,
        questRef: Ref<QuestEntry>,
        quest: QuestEntry,
        order: Int? = null,
        overrides: QuestItemOverrides? = null,
        displayOverrides: QuestDisplayOverrides? = null,
        additionalLore: QuestAdditionalLore? = null,
    ) {
        val category = ensure(categoryName)
        if (questRef.id.isNotBlank() && !category.quests.contains(questRef)) {
            category.quests.add(questRef)
        }
        if (order != null) {
            category.questOrders[quest.id] = order
        } else {
            category.questOrders.remove(quest.id)
        }
        overrides?.takeIf { it.hasOverrides() }?.let { newOverrides ->
            val existing = category.questItems[quest.id]
            category.questItems[quest.id] = existing?.overrideWith(newOverrides) ?: newOverrides
        }
        displayOverrides?.takeIf { it.hasOverrides() }?.let { newOverrides ->
            val existing = category.questDisplays[quest.id]
            category.questDisplays[quest.id] = existing?.overrideWith(newOverrides) ?: newOverrides
        }
        if (additionalLore != null && additionalLore.hasContent()) {
            category.questAdditionalLore[quest.id] = additionalLore
        }
    }

    fun all(): Collection<QuestCategory> = categories.values.sortedWith(
        compareBy({ if (it.order == 0) Int.MAX_VALUE else it.order }, { it.title })
    )

    fun roots(): Collection<QuestCategory> = categories.values.filter { it.parent == null }.sortedWith(
        compareBy({ if (it.order == 0) Int.MAX_VALUE else it.order }, { it.title })
    )

    fun clear() {
        categories.clear()
    }
}

fun QuestCategory.allQuests(): List<QuestEntry> =
    (quests.mapNotNull { it.get() } + subCategories.flatMap { it.allQuests() }).distinct()

enum class CategoryStatus {
    BLOCKED,
    IN_PROGRESS,
    COMPLETED,
}

fun QuestCategory.categoryStatus(player: Player): CategoryStatus {
    if (completedCriteria.isNotEmpty() && completedCriteria.matches(player)) return CategoryStatus.COMPLETED
    if (activeCriteria.isNotEmpty() && !activeCriteria.matches(player)) return CategoryStatus.BLOCKED
    return CategoryStatus.IN_PROGRESS
}

// ── Extension functions ──

fun String.asMiniWithoutItalic(): Component =
    try {
        asMini().decoration(TextDecoration.ITALIC, false)
    } catch (_: Exception) {
        Component.text(this)
    }

fun Item.isEffectivelyEmpty(): Boolean =
    this == Item.Empty || (this is CustomItem && (components.filterIsInstance<ItemMaterialComponent>().firstOrNull()?.material ?: Material.AIR) == Material.AIR)
