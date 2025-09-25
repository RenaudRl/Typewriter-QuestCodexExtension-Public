package btc.renaud.questcodex

import com.typewritermc.engine.paper.interaction.interactionContext
import com.typewritermc.engine.paper.utils.Sound
import com.typewritermc.engine.paper.utils.playSound
import org.bukkit.entity.Player

/** Play a configured quest codex sound if it is defined. */
fun Player.playCodexSound(sound: Sound) {
    if (!sound.isConfigured()) return
    playSound(sound, interactionContext)
}

fun Sound.isConfigured(): Boolean = soundId.namespacedKey != null
