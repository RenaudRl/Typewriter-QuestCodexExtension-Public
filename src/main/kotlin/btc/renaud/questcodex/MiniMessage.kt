package btc.renaud.questcodex

import com.typewritermc.engine.paper.utils.asMini
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration

/** Utility to strip the italic decoration applied by Adventure by default. */
fun String.asMiniWithoutItalic(): Component =
    this.asMini().decorations(setOf(TextDecoration.ITALIC), false)

