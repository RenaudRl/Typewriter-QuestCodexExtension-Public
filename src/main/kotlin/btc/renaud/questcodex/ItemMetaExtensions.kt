package btc.renaud.questcodex

import org.bukkit.inventory.meta.ItemMeta

/**
 * Helper to apply legacy custom model data using the new component API introduced in
 * Minecraft 1.21.7.
 *
 * The old [ItemMeta.setCustomModelData] method was deprecated and removed, so we
 * emulate the same behaviour by setting a [org.bukkit.inventory.meta.components.CustomModelDataComponent].
 */
fun ItemMeta.setModelData(value: Int) {
    if (value == 0) {
        setCustomModelDataComponent(null)
    } else {
        val component = getCustomModelDataComponent()
        component.setFloats(listOf(value.toFloat()))
        setCustomModelDataComponent(component)
    }
}