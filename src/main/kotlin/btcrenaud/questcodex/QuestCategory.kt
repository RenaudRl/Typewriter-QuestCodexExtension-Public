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

    /** Null when the state's item has no material: the editor saves an untouched item that way, and it renders as air. */
    fun itemFor(status: QuestStatus): Item? = when (status) {
        QuestStatus.INACTIVE -> notStarted
        QuestStatus.ACTIVE -> inProgress
        QuestStatus.COMPLETED -> completed
    }.takeUnless { it.isEffectivelyEmpty() }

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
    val description: List<String> = emptyList(),
    val notStarted: List<String> = emptyList(),
    val inProgress: List<String> = emptyList(),
    val completed: List<String> = emptyList(),
) {
    fun hasContent(): Boolean =
        description.isNotEmpty() || notStarted.isNotEmpty() || inProgress.isNotEmpty() || completed.isNotEmpty()

    fun overrideWith(other: QuestAdditionalLore): QuestAdditionalLore = QuestAdditionalLore(
        description = if (other.description.isNotEmpty()) other.description else description,
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
        // Merge, never erase: several assignment entries may list the same quest, and only some of
        // them carry an `orders` list. Clearing on `order == null` let a later entry silently drop
        // an order set by an earlier one, which is one way the display order drifted between reloads.
        if (order != null) {
            category.questOrders[quest.id] = order
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

    fun all(): Collection<QuestCategory> = categories.values.sortedWith(CATEGORY_ORDER)

    fun roots(): Collection<QuestCategory> =
        categories.values.filter { it.parent == null }.sortedWith(CATEGORY_ORDER)

    fun clear() {
        categories.clear()
    }
}

/**
 * Total order over categories: explicit [QuestCategory.order] first, then the category *name*.
 *
 * The tiebreak used to be `title`, which is display text — it carries placeholders and translations,
 * so the same server could order its categories differently depending on who was looking.
 */
internal val CATEGORY_ORDER: Comparator<QuestCategory> =
    compareBy({ if (it.order == 0) Int.MAX_VALUE else it.order }, { it.name })

/**
 * Rank of a quest inside the category that owns it.
 *
 * Quests carrying an explicit order come first, in that order. The rest keep their *declaration
 * position* — the position of the ref in [QuestCategory.quests], which follows assignment-entry
 * order — instead of collapsing into a single `Int.MAX_VALUE` bucket sorted by display name.
 */
private fun QuestCategory.rankOf(questId: String, position: Int): Pair<Int, Int> {
    val explicit = questOrders[questId]
    return if (explicit != null) 0 to explicit else 1 to position
}

/**
 * Quest refs of this category tree in their stable display order.
 *
 * Resolved against the category that actually *owns* each quest: a quest declared in a sub-category
 * has its order in that sub-category's [QuestCategory.questOrders], so ranking the flattened list
 * against the parent alone left every nested quest unordered.
 *
 * The result depends on configuration only — never on the viewing player — so filtering it later
 * (visibility, sort mode, tracking) can remove entries but can never reshuffle the survivors.
 */
fun QuestCategory.orderedQuestRefs(): List<Ref<QuestEntry>> = orderedQuestRefs(mutableSetOf())

// `visited` stops a misconfigured parent cycle instead of overflowing the stack at render.
private fun QuestCategory.orderedQuestRefs(visited: MutableSet<String>): List<Ref<QuestEntry>> {
    if (!visited.add(name.lowercase())) return emptyList()
    val nested = orderedSubCategories().flatMap { it.orderedQuestRefs(visited) }
    return (ownOrderedQuestRefs() + nested).distinctBy { it.id }
}

/** Quest refs declared directly in this category (sub-categories excluded), in display order. */
fun QuestCategory.ownOrderedQuestRefs(): List<Ref<QuestEntry>> = quests
    .filter { it.id.isNotBlank() }
    .mapIndexed { position, ref -> ref to rankOf(ref.id, position) }
    .sortedWith(compareBy({ it.second.first }, { it.second.second }, { it.first.id }))
    .map { it.first }

/** Direct sub-categories in the same stable order the main menu uses for root categories. */
fun QuestCategory.orderedSubCategories(): List<QuestCategory> = subCategories.sortedWith(CATEGORY_ORDER)

/**
 * First category of the lineage — this one, then its parent, then its grandparent… — that
 * satisfies [predicate]; null when none does.
 *
 * Used to give a category without its own menu the menu of its nearest ancestor. A parent cycle
 * (a misconfigured `parent`) ends the walk instead of looping.
 */
fun QuestCategory.nearestInLineage(predicate: (QuestCategory) -> Boolean): QuestCategory? {
    val seen = mutableSetOf<String>()
    var current: QuestCategory? = this
    while (current != null && seen.add(current.name.lowercase())) {
        if (predicate(current)) return current
        current = current.parent
    }
    return null
}

/** Which quests a category menu lists. */
enum class QuestScope {
    /** Only the quests declared in the category: its sub-categories are reachable through their own buttons. */
    OWN,

    /** The quests of the whole category tree, flattened into one list (menus without sub-category buttons). */
    TREE,
}

fun QuestCategory.questRefs(scope: QuestScope): List<Ref<QuestEntry>> = when (scope) {
    QuestScope.OWN -> ownOrderedQuestRefs()
    QuestScope.TREE -> orderedQuestRefs()
}

/**
 * First non-null [pick] over this category, then its sub-categories depth-first in display order.
 *
 * Rule for a flattened (TREE) menu: an assignment made in the category being shown wins, otherwise
 * the one of the descendant that declares the quest applies. Without this fallback a quest declared
 * in a sub-category lost its per-state names/items/lore as soon as its parent's menu listed it.
 */
fun <T> QuestCategory.firstInTree(pick: (QuestCategory) -> T?): T? = firstInTree(mutableSetOf(), pick)

private fun <T> QuestCategory.firstInTree(visited: MutableSet<String>, pick: (QuestCategory) -> T?): T? {
    if (!visited.add(name.lowercase())) return null
    pick(this)?.let { return it }
    for (sub in orderedSubCategories()) sub.firstInTree(visited, pick)?.let { return it }
    return null
}

fun QuestCategory.itemOverridesFor(questId: String): QuestItemOverrides? = firstInTree { it.questItems[questId] }

fun QuestCategory.displayOverridesFor(questId: String): QuestDisplayOverrides? = firstInTree { it.questDisplays[questId] }

fun QuestCategory.additionalLoreFor(questId: String): QuestAdditionalLore? = firstInTree { it.questAdditionalLore[questId] }

fun QuestCategory.allQuests(): List<QuestEntry> = orderedQuestRefs().mapNotNull { it.get() }

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
