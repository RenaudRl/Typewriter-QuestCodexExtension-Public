package btcrenaud.questcodex.ui

/**
 * The kinds of codex menu, which decide the dynamic markers each one fills.
 *
 * A category menu carries both: `QUEST_SLOT` markers list the category's quests and
 * `CATEGORY_SLOT` markers list its direct sub-categories, so a category can nest without limit
 * and still hold quests of its own.
 */
enum class CodexMenuKind(val questMarker: CodexButtonType?, val categoryMarker: CodexButtonType?) {
    MAIN(questMarker = null, categoryMarker = CodexButtonType.CATEGORY_SLOT),
    CATEGORY(questMarker = CodexButtonType.QUEST_SLOT, categoryMarker = CodexButtonType.CATEGORY_SLOT),
    TRACKED(questMarker = CodexButtonType.TRACKED_QUEST_SLOT, categoryMarker = null);

    /** Every marker type this menu kind fills, quests first. */
    val markers: List<CodexButtonType> get() = listOfNotNull(questMarker, categoryMarker)

    companion object {
        /** Kind of the `category_menu` entry whose `category` field is [category]. */
        fun of(category: String): CodexMenuKind = when {
            category.isBlank() -> MAIN
            category.equals("@tracked", ignoreCase = true) -> TRACKED
            else -> CATEGORY
        }
    }
}

/**
 * One kind of marker in a menu: [entryCount] entries shown through [markerCount] marker slots.
 *
 * A group that fits in its markers is *pinned*: it shows the same entries on every page. Only a
 * group that overflows pages. A menu listing a few sub-categories above a long quest list thus
 * keeps its sub-category buttons visible while the quests are paged, and a single PAGE_NEXT /
 * PAGE_PREV pair still drives everything (see [pageCountOf]).
 */
data class MarkerGroup(val entryCount: Int, val markerCount: Int) {
    val pageCount: Int
        get() = if (markerCount <= 0) 1 else ((entryCount + markerCount - 1) / markerCount).coerceAtLeast(1)

    /** Index of the first entry shown on [page]. */
    fun offsetOn(page: Int): Int = if (pageCount <= 1) 0 else page * markerCount
}

/** Pages needed so that every group's overflow is reachable: the longest group decides. */
fun pageCountOf(groups: List<MarkerGroup>): Int = groups.maxOfOrNull { it.pageCount } ?: 1

/** Entry displayed by the [markerIndex]-th marker of [group] on [page]; null when that marker is empty. */
fun <T> List<T>.entryAt(group: MarkerGroup, page: Int, markerIndex: Int): T? =
    getOrNull(group.offsetOn(page) + markerIndex)
