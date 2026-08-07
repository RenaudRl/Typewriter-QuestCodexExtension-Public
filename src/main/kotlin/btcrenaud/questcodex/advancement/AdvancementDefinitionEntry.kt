package btcrenaud.questcodex.advancement

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.ManifestEntry
import org.bukkit.Material

/** Shape of the advancement's frame in the tree, and of its toast. */
enum class AdvancementFrame(val jsonValue: String) {
    TASK("task"),
    GOAL("goal"),
    CHALLENGE("challenge"),
}

/**
 * Defines a real Minecraft advancement.
 *
 * Every definition on the pages is written to a datapack the extension owns, so the advancement
 * appears in the vanilla advancement screen (`L`) with its own tab, tree and toast — this is not a
 * cosmetic toast. Nothing grants it on its own: the only criterion is `minecraft:impossible`, and
 * [GrantAdvancementActionEntry] is what awards it.
 *
 * Set [parent] to hang this advancement under another one; leave it empty to make it the root of a
 * tab, which is the only place [background] has an effect.
 *
 * Example:
 * ```
 * advancement_definition:
 *   namespace: "questcodex"
 *   key: "root"
 *   title: "<gold>Adventures"
 *   icon: GOLDEN_SWORD
 *   background: "minecraft:gui/advancements/backgrounds/stone"
 *
 * advancement_definition:
 *   namespace: "questcodex"
 *   key: "first_quest"
 *   title: "<green>First Steps"
 *   parent: <the root definition>
 * ```
 */
@Entry("advancement_definition", "Advancement Definition", Colors.YELLOW, "mdi:trophy")
@Tags("quest", "advancement")
class AdvancementDefinitionEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Namespace of the advancement id. Lowercase letters, digits, '_', '.' and '-' only.")
    val namespace: String = "questcodex",
    @Help("Key of the advancement id, unique within the namespace. '/' creates a folder.")
    val key: String = "",
    @Help("Advancement this one hangs under. Leave empty to make it the root of its own tab.")
    val parent: Ref<AdvancementDefinitionEntry> = emptyRef(),
    @Help("Item shown as the advancement icon.")
    val icon: Material = Material.BOOK,
    @Help("Title shown in the tree and on the toast.")
    @Colored
    val title: String = "",
    @Help("Description shown under the title in the tree.")
    @Colored
    val description: String = "",
    @Help("Frame drawn around the icon. Challenges use the ornate frame and a distinct toast.")
    val frame: AdvancementFrame = AdvancementFrame.TASK,
    @Help("Tab background sprite, e.g. 'minecraft:gui/advancements/backgrounds/stone'. Only used on a root advancement.")
    val background: String = "",
    @Help("Whether earning this advancement pops a toast on screen.")
    val showToast: Boolean = true,
    @Help("Whether earning this advancement is announced in chat.")
    val announceToChat: Boolean = false,
    @Help("Whether this advancement stays hidden in the tree until it is earned. Ignored on a root.")
    val hidden: Boolean = false,
    @Help("Earn this one as soon as the player plays, with no action. Use it on a root so its tab exists from the start.")
    val autoGrant: Boolean = false,
) : ManifestEntry
