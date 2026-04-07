package btc.renaud.questcodex

import com.typewritermc.quest.entries.ObjectiveEntry
import com.typewritermc.core.entries.ref
import com.typewritermc.engine.paper.entry.triggerFor
import com.typewritermc.core.interaction.context
import org.bukkit.entity.Player
import java.lang.reflect.Method

/**
 * Helper to handle QuestPlus features optionally via reflection.
 * This allows the extension to compile and run without QuestPlus being present.
 */
object QuestPlusIntegration {

    private val hidableObjectiveClass: Class<*>? = try {
        Class.forName("btc.renaud.questplusextension.HidableObjective")
    } catch (_: ClassNotFoundException) {
        null
    }

    private val gpsObjectiveClass: Class<*>? = try {
        Class.forName("btc.renaud.questplusextension.GPSLocationObjectiveEntry")
    } catch (_: ClassNotFoundException) {
        null
    }

    /**
     * Checks if an objective should be hidden based on HidableObjective.hideObjective property.
     */
    fun isHidden(objective: ObjectiveEntry, player: Player): Boolean {
        if (hidableObjectiveClass == null || !hidableObjectiveClass.isInstance(objective)) return false
        
        return try {
            val getHideObjectiveMethod = hidableObjectiveClass.getMethod("getHideObjective")
            val varInstance = getHideObjectiveMethod.invoke(objective)
            // Var<Boolean> has a get(player) method
            val getMethod = varInstance.javaClass.getMethod("get", Player::class.java)
            getMethod.invoke(varInstance, player) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the codex lore for a GPSLocationObjectiveEntry.
     */
    fun getGpsCodexLore(objective: ObjectiveEntry, player: Player): String? {
        if (gpsObjectiveClass == null || !gpsObjectiveClass.isInstance(objective)) return null
        
        return try {
            val getCodexLoreMethod = gpsObjectiveClass.getMethod("getCodexLore")
            val varInstance = getCodexLoreMethod.invoke(objective)
            val getMethod = varInstance.javaClass.getMethod("get", Player::class.java)
            getMethod.invoke(varInstance, player) as? String
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Triggers the onCodexShiftClick action for a GPSLocationObjectiveEntry.
     */
    fun triggerGpsShiftClick(objective: ObjectiveEntry, player: Player): Boolean {
        if (gpsObjectiveClass == null || !gpsObjectiveClass.isInstance(objective)) return false
        
        return try {
            val getOnCodexShiftClickMethod = gpsObjectiveClass.getMethod("getOnCodexShiftClick")
            val refInstance = getOnCodexShiftClickMethod.invoke(objective)
            
            // Ref<Sequence> has isSet property (getter)
            val isSetMethod = refInstance.javaClass.getMethod("isSet")
            val isSet = isSetMethod.invoke(refInstance) as Boolean
            
            if (isSet) {
                // TriggerableEntry has triggerFor(player, context)
                // Actually refInstance is a Ref<Sequence>. To trigger it, we usually call triggerFor on it directly if it's a triggerable.
                // In Typewriter, Ref<T> doesn't necessarily have triggerFor.
                // Wait, in QuestCategoryListener.kt line 133: gpsObjective.onCodexShiftClick.triggerFor(player, context())
                // This means refInstance (the Ref) has an extension method or directly implements triggerFor.
                // Ref implements Triggerable if it's a Ref<Sequence>.
                
                val triggerMethod = refInstance.javaClass.methods.find { it.name == "triggerFor" && it.parameterCount == 2 }
                if (triggerMethod != null) {
                    triggerMethod.invoke(refInstance, player, context())
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}
