package btcrenaud.questcodex.ui

import btcrenaud.gui.api.GuiSlot
import btcrenaud.gui.api.MenuLayout
import btcrenaud.gui.api.Viewport
import btcrenaud.gui.services.MenuSessionService

/**
 * Wraps a [MenuLayout] and injects dynamically-computed slots at render time.
 */
class AugmentedSimpleLayout(
    val inner: MenuLayout,
    val dynamicSlots: List<GuiSlot>,
    override val id: String? = null,
) : MenuLayout {

    override val innerLayout: MenuLayout? get() = inner.innerLayout

    override fun getSlots(
        session: MenuSessionService.ActiveSession,
        viewport: Viewport,
    ): List<GuiSlot> = inner.getSlots(session, viewport) + dynamicSlots

    override val virtualWidth: Int get() = inner.virtualWidth
    override val virtualHeight: Int get() = inner.virtualHeight
}
