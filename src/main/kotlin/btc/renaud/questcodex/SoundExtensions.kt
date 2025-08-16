package btc.renaud.questcodex

import com.typewritermc.engine.paper.utils.playSound
import org.bukkit.entity.Player

/**
 * Helper utilities for playing sounds from snippet configuration.
 * Accepts both namespaced keys (e.g. "minecraft:item.flintandsteel.use")
 * and Bukkit sound enum names (e.g. "ITEM_FLINTANDSTEEL_USE").
 */
fun Player.playCodexSound(sound: String) = playSound(sound.toSoundKey())

fun String.toSoundKey(): String {
    val lower = lowercase()
    return if (":" in lower) lower else "minecraft:${lower.replace('_', '.')}"
}
