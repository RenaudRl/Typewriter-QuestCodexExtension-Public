package btcrenaud.questcodex

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.*
import com.typewritermc.core.utils.point.Position
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.quest.entries.QuestEntry

/**
 * Defines an icon on the BlueMap associated with a quest.
 */
@Entry(
    "bluemap_icon",
    "Adds an icon to BlueMap for a specific quest",
    Colors.BLUE,
    "mdi:map-marker"
)
class BlueMapIconEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Quest associated with this icon")
    val quest: Ref<QuestEntry> = emptyRef(),
    @Help("Icon ID to use on BlueMap (e.g., '0; 64; 100')")
    val iconPath: String = "",
    @Help("Label displayed on the map")
    @Placeholder
    @Colored
    val label: String = "",
    @Help("Detailed description shown in the popup (supports HTML). Use <objectives> as a placeholder for quest objectives.")
    @Placeholder
    @Colored
    @MultiLine
    val description: String = "",
    @Help("Location of the icon on the map")
    val location: Var<Position> = ConstVar(Position.ORIGIN)
) : ManifestEntry
