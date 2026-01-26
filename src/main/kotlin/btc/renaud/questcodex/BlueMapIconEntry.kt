package btc.renaud.questcodex

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.core.utils.point.Position
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.quest.entries.QuestEntry

@Entry(
    "bluemap_icon",
    "Links a quest to a BlueMap icon",
    Colors.BLUE,
    "mdi:map-marker"
)
class BlueMapIconEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("The quest to mark on the map")
    val quest: Ref<QuestEntry> = emptyRef(),
    
    @Help("Path to the icon image in BlueMap assets (e.g. 'assets/marker.png')")
    val iconPath: String = "assets/marker.png",
    @Help("Label for the marker (supports HTML). If empty, defaults to Quest Name.")
    val label: String = "",
    @Help("Detailed description shown in the popup (supports HTML). Use <objectives> to list objectives.")
    @MultiLine
    val description: String = "",
    
    @Help("Location of the marker using Typewriter Position format (e.g. 'world; 100; 64; 100')")
    val location: Var<Position> = ConstVar(Position.ORIGIN)
) : ManifestEntry
