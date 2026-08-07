package btcrenaud.questcodex.advancement

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.utils.launch
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.Modifier
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.entries.ActionEntry
import com.typewritermc.engine.paper.entry.entries.ActionTrigger
import com.typewritermc.engine.paper.logger
import com.typewritermc.engine.paper.utils.Sync
import kotlinx.coroutines.Dispatchers
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey

/**
 * Awards a [AdvancementDefinitionEntry] to the player, or takes it back.
 *
 * The generated advancements are granted by nothing but this action: their only criterion is
 * `minecraft:impossible`, so the player earns them exactly when a page says so.
 */
@Entry("grant_advancement", "Grant Advancement", Colors.YELLOW, "mdi:trophy-award")
class GrantAdvancementActionEntry(
    override val id: String = "",
    override val name: String = "",
    override val criteria: List<Criteria> = emptyList(),
    override val modifiers: List<Modifier> = emptyList(),
    override val triggers: List<Ref<TriggerableEntry>> = emptyList(),
    @Help("Advancement to award.")
    val advancement: Ref<AdvancementDefinitionEntry> = emptyRef(),
    @Help("Take the advancement back instead of awarding it.")
    val revoke: Boolean = false,
    @Help("Also award every advancement above this one, so the tree can never show a child earned under an unearned parent.")
    val includeParents: Boolean = true,
) : ActionEntry {
    override fun ActionTrigger.execute() {
        val definition = advancement.get() ?: return

        // Minecraft treats the parent link as decoration only: awarding a child leaves its parent
        // untouched, and the tree then shows a branch earned from nowhere. Walking the chain here
        // is what keeps what the player sees consistent with what they did.
        val chain = if (includeParents && !revoke) ancestryOf(definition) else listOf(definition)

        val keyed = chain.mapNotNull { entry ->
            val key = runCatching { NamespacedKey(entry.namespace.trim(), entry.key.trim()) }.getOrNull()
            if (key == null) {
                logger.warning(
                    "Cannot grant advancement '${entry.name}': " +
                        "'${entry.namespace}:${entry.key}' is not a valid id."
                )
                null
            } else {
                entry to key
            }
        }
        if (keyed.isEmpty()) return

        // Advancement progress is server-thread state, and the advancement itself only exists once
        // the datapack has been loaded.
        Dispatchers.Sync.launch {
            for ((entry, key) in keyed) {
                val target = Bukkit.getAdvancement(key)
                if (target == null) {
                    logger.warning(
                        "Cannot grant advancement '${entry.name}': $key is unknown to the server. " +
                            "It is written on start-up, so a definition added since then needs a restart."
                    )
                    continue
                }
                val progress = player.getAdvancementProgress(target)
                if (revoke) {
                    progress.awardedCriteria.forEach { progress.revokeCriteria(it) }
                } else {
                    progress.remainingCriteria.forEach { progress.awardCriteria(it) }
                }
            }
        }
    }

    /**
     * The advancement and everything above it, oldest first, so a root is awarded before its child
     * and the toasts arrive in the order the tree reads.
     *
     * A definition that points at itself, directly or through a loop, would otherwise spin here.
     * The pack builder already refuses to write such a chain; this guard covers the window where
     * the pages hold one and the datapack does not.
     */
    private fun ancestryOf(definition: AdvancementDefinitionEntry): List<AdvancementDefinitionEntry> {
        val chain = ArrayDeque<AdvancementDefinitionEntry>()
        val seen = mutableSetOf<String>()
        var current: AdvancementDefinitionEntry? = definition
        while (current != null && seen.add(current.id)) {
            chain.addFirst(current)
            current = current.parent.get()
        }
        return chain.toList()
    }
}
