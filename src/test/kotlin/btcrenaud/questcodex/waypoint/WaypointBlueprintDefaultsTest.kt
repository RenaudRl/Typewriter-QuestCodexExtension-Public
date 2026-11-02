package btcrenaud.questcodex.waypoint

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Typewriter processor ignores Kotlin default values and only reads `@Default`, so an entry made in
 * the web editor starts from 0 / false / the first enum constant unless the field says otherwise.
 * This reads the blueprint the processor generated and checks it against the Kotlin constructors.
 */
class WaypointBlueprintDefaultsTest {

    private val entryFields: JsonObject = run {
        // `test` depends on processResources, which copies the processor's extension.json here.
        val file = File("build/resources/main/extension.json")
        val root = JsonParser.parseString(file.readText()).asJsonObject
        root.getAsJsonArray("entries")
            .map { it.asJsonObject }
            .first { it.get("name").asString == "quest_codex_waypoint" }
            .getAsJsonObject("dataBlueprint")
            .getAsJsonObject("fields")
    }

    private fun default(field: String) = entryFields.getAsJsonObject(field).get("default")

    private fun layerDefault(layer: String, field: String) = entryFields.getAsJsonObject("layers")
        .getAsJsonObject("type").getAsJsonObject("cases").getAsJsonObject(layer)
        .getAsJsonObject("fields").getAsJsonObject(field).get("default")

    @Test
    fun `waypoint numbers start at the values that make it visible`() {
        assertEquals(12.0, default("projectionRadius").asDouble)
        assertEquals(10.0, default("nearTargetDistance").asDouble)
        assertEquals(true, default("constantApparentSize").asBoolean)
        assertEquals(1, default("refreshTicks").asInt)
    }

    @Test
    fun `waypoint scalar defaults match the Kotlin constructor`() {
        val entry = QuestCodexWaypointEntry()

        assertEquals(entry.maxTargets, default("maxTargets").asInt)
        assertEquals(entry.interpolationTicks, default("interpolationTicks").asInt)
        assertEquals(entry.adaptiveTransitionBand, default("adaptiveTransitionBand").asDouble)
        assertEquals(entry.declutterAngle, default("declutterAngle").asDouble)
        assertEquals(entry.declutterSpacing, default("declutterSpacing").asDouble)
        assertEquals(entry.nearTargetVerticalOffset, default("nearTargetVerticalOffset").asDouble)
        assertEquals(entry.targetViewDistance, default("targetViewDistance").asDouble)
        assertEquals(entry.hudVisibilityAngle, default("hudVisibilityAngle").asDouble)
        assertEquals(entry.hudForwardDistance, default("hudForwardDistance").asDouble)
        assertEquals(entry.hudVerticalOffset, default("hudVerticalOffset").asDouble)
    }

    @Test
    fun `block layer starts as a beacon on the target`() {
        assertEquals("TARGET", layerDefault("waypoint_block_layer", "placement").asString)
        assertEquals("BEACON", layerDefault("waypoint_block_layer", "material").asString)
        assertEquals(1.0, layerDefault("waypoint_block_layer", "scale").asDouble)
        assertEquals(true, layerDefault("waypoint_block_layer", "enabled").asBoolean)
    }

    @Test
    fun `a new waypoint starts with one text layer`() {
        val layers = default("layers").asJsonArray

        assertEquals(1, layers.size())
        assertEquals("waypoint_text_layer", layers[0].asJsonObject.get("case").asString)
        assertEquals(1.0, layerDefault("waypoint_text_layer", "scale").asDouble)
    }
}
