package btc.renaud.questcodex

import org.bukkit.Bukkit
import org.bukkit.inventory.meta.ItemMeta
import java.util.List

/**
 * Helper to apply legacy custom model data using whichever API is available.
 *
 * The component based API was introduced in newer Minecraft versions
 * (1.21.7+). Older servers still rely on [ItemMeta.setCustomModelData].
 * To remain compatible we attempt the component API first and fall back
 * to the legacy method when necessary.
 */
fun ItemMeta.setModelData(value: Int) {
    val metaClass = javaClass

    // Try new component API (1.21.7+)
    val applied = runCatching {
        val componentClass = Class.forName("org.bukkit.inventory.meta.components.CustomModelDataComponent")
        val setMethod = metaClass.getMethod("setCustomModelDataComponent", componentClass)

        if (value == 0) {
            setMethod.invoke(this, null)
        } else {
            val getMethod = metaClass.getMethod("getCustomModelDataComponent")
            val component = getMethod.invoke(this) ?: componentClass.getConstructor().newInstance()
            val setFloats = componentClass.getMethod("setFloats", List::class.java)
            setFloats.invoke(component, listOf(value.toFloat()))
            setMethod.invoke(this, component)
        }
        true
    }.getOrElse { false }

    if (applied) return

    // Fallback to legacy API (<=1.21.3)
    runCatching {
        val legacy = metaClass.getMethod("setCustomModelData", Int::class.javaObjectType)
        legacy.invoke(this, value.takeIf { it != 0 })
    }.onFailure {
        runCatching {
            Bukkit.getLogger().warning("Unable to set custom model data: ${it.message}")
        }
    }
}