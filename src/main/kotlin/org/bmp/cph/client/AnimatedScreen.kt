package org.bmp.cph.client

import net.minecraft.Util
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

abstract class AnimatedScreen(
    title: Component,
    protected val previousScreen: Screen,
) : Screen(title) {
    private val openedAt = Util.getMillis()
    private var closingAt: Long? = null

    protected fun transitionProgress(): Float {
        val closing = closingAt
        return if (closing == null) {
            ((Util.getMillis() - openedAt) / OPEN_DURATION.toFloat()).coerceIn(0f, 1f).easeOut()
        } else {
            (1f - (Util.getMillis() - closing) / CLOSE_DURATION.toFloat()).coerceIn(0f, 1f).easeOut()
        }
    }

    protected fun slideOffset(distance: Int = 10): Int = ((1f - transitionProgress()) * distance).roundToInt()

    protected fun animatedColor(rgb: Int): Int {
        val alpha = (transitionProgress() * 255).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (rgb and 0xFFFFFF)
    }

    override fun onClose() {
        if (closingAt == null) closingAt = Util.getMillis()
    }

    override fun tick() {
        super.tick()
        val closing = closingAt
        if (closing != null && Util.getMillis() - closing >= CLOSE_DURATION) {
            minecraft?.setScreen(previousScreen)
        }
    }

    override fun isPauseScreen(): Boolean = false

    private fun Float.easeOut(): Float = 1f - (1f - this) * (1f - this)

    private companion object {
        const val OPEN_DURATION = 220L
        const val CLOSE_DURATION = 140L
    }
}
