package btcrenaud.questcodex

import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.PluginManager

/**
 * Permission nodes of the Quest Codex.
 *
 * A Typewriter extension ships no `plugin.yml`, so a node that nobody declares is invisible to
 * permission plugins (no tab completion, no description) and silently falls back to operators.
 * Declaring it here makes that default explicit instead of accidental.
 */
object QuestCodexPermissions {
    /** Every `/tw codex` command. Opening a menu from an action or a button needs no permission. */
    const val OPEN = "typewriter.codex.open"

    /**
     * Operators only, as the documentation states: server owners grant the node to the groups that
     * may browse the codex. A node is a protocol constant of the command tree, not gameplay
     * content, so it is not an entry field.
     */
    val declared: Map<String, PermissionDefault> = mapOf(OPEN to PermissionDefault.OP)

    /** Declares every node on [pluginManager]; a node already declared (reload, other plugin) is kept. */
    fun register(pluginManager: PluginManager) {
        declared.forEach { (node, default) ->
            if (pluginManager.getPermission(node) == null) {
                pluginManager.addPermission(Permission(node, default))
            }
        }
    }
}
