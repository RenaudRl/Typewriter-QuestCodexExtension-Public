package btc.renaud.questcodex

import com.flowpowered.math.vector.Vector2i
import com.typewritermc.core.entries.Query
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.entry.entries.get
import com.typewritermc.quest.entries.ObjectiveEntry
import de.bluecolored.bluemap.api.BlueMapAPI
import de.bluecolored.bluemap.api.markers.MarkerSet
import de.bluecolored.bluemap.api.markers.POIMarker
import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.logging.Level

object BlueMapIntegrationService {
    private const val MARKER_SET_ID = "quests"
    private const val MARKER_SET_LABEL = "Quests"

    fun initialize() {
        if (!Bukkit.getPluginManager().isPluginEnabled("BlueMap")) {
            return
        }

        BlueMapAPI.onEnable { api ->
            plugin.logger.info("[QuestCodex] Enabling BlueMap integration...")
            try {
                // Initial update only (Static markers)
                updateMarkers(api)
            } catch (e: Exception) {
                plugin.logger.log(Level.SEVERE, "[QuestCodex] Failed to update BlueMap markers", e)
            }
        }
    }

    private fun updateMarkers(api: BlueMapAPI) {
        val sequence = Query.find<BlueMapIconEntry>()
        if (sequence.none()) {
            plugin.logger.info("[QuestCodex] No BlueMapIconEntry found.")
            return
        }

        // 1. Resolve all locations
        // We use .get(null) because BlueMap markers are global (no player context).
        // If the location uses variables (e.g. for world name), they must resolve globally (e.g. Server variables).
        val entriesByWorld = sequence.toList()
            .mapNotNull { entry -> 
                val pos = entry.location.get(null)
                if (pos != null) {
                    // Convert Typewriter Position to Bukkit Location
                    // Position stores World object which wraps the identifier
                    val worldIdentifier = pos.world.identifier
                    val bukkitWorld = Bukkit.getWorld(worldIdentifier)
                    if (bukkitWorld != null) {
                        entry to Location(bukkitWorld, pos.x, pos.y, pos.z)
                    } else {
                        null
                    }
                } else null 
            }
            .groupBy { it.second.world!! }

        if (entriesByWorld.isEmpty()) {
            plugin.logger.info("[QuestCodex] Found entries but no valid locations resolved.")
            return
        }

        plugin.logger.info("[QuestCodex] Updating markers for ${entriesByWorld.size} worlds.")

        // 2. Iterate each Bukkit world
        entriesByWorld.forEach { (bukkitWorld, pairs) ->
            // 3. Get BlueMap World
            val blueMapWorldOptional = api.getWorld(bukkitWorld)
            if (!blueMapWorldOptional.isPresent) {
                 plugin.logger.warning("[QuestCodex] No BlueMap world found for Bukkit world: ${bukkitWorld.name}")
                 return@forEach
            }
            val blueMapWorld = blueMapWorldOptional.get()
            
            // 4. Update all maps for this world
            blueMapWorld.maps.forEach { map ->
                val markerSet = map.markerSets.getOrDefault(MARKER_SET_ID, MarkerSet.builder()
                    .label(MARKER_SET_LABEL)
                    .build())
                
                markerSet.markers.clear()

                pairs.forEach { (entry, location) ->
                    try {
                        val markerBuilder = POIMarker.builder()
                            .position(location.x, location.y, location.z)
                        
                        // Label
                        var finalLabel = entry.label
                        if (finalLabel.isBlank()) {
                            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                            finalLabel = entry.quest.get()?.displayName?.get(null) ?: entry.name
                        }
                        markerBuilder.label(finalLabel)
                        
                        // Description Resolution
                        var description = entry.description
                        if (description.isNotBlank()) {
                            // Parse <objectives> placeholder
                            if (description.contains("<objectives>") || description.contains("<objective:")) {
                                val questRef = entry.quest
                                if (questRef.get() != null) {
                                    // Query objectives for this quest
                                    // We find all ObjectiveEntry where quest reference matches
                                    val objectives = Query.findWhere<ObjectiveEntry> { it.quest == questRef }
                                        .toList()
                                        // Removed sorting by priority to avoid unresolved reference
                                    
                                    // Replace <objectives> with full list (Global context display)
                                    if (description.contains("<objectives>")) {
                                        val listStr = objectives.joinToString("<br>") { obj ->
                                            "- " + (obj.display.get(null) ?: "Unknown Objective")
                                        }
                                        description = description.replace("<objectives>", listStr)
                                    }
                                    
                                    // Replace <objective:N> (1-based index)
                                    // Regex for <objective:(\d+)>
                                    val pattern = "<objective:(\\d+)>".toRegex()
                                    description = pattern.replace(description) { matchResult ->
                                        val index = matchResult.groupValues[1].toIntOrNull() ?: 0
                                        if (index > 0 && index <= objectives.size) {
                                             objectives[index - 1].display.get(null) ?: ""
                                        } else {
                                            "Invalid Objective Index"
                                        }
                                    }
                                }
                            }
                            
                            markerBuilder.detail(description)
                        }
                        
                        // Icon
                        if (entry.iconPath.isNotBlank()) {
                            markerBuilder.icon(entry.iconPath, Vector2i(16, 32)) 
                        }
                        
                        markerSet.put(entry.id, markerBuilder.build())
                    } catch (e: Exception) {
                         plugin.logger.warning("[QuestCodex] Error creating marker ${entry.id}: ${e.message}")
                    }
                }
                
                map.markerSets.put(MARKER_SET_ID, markerSet)
                plugin.logger.info("[QuestCodex] Added ${pairs.size} markers on map '${map.id}'")
            }
        }
    }
}
