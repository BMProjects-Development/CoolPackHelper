package org.bmp.cph.client.editor

import net.minecraft.Util
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.client.AnimatedScreen
import kotlin.math.sin

internal fun tr(key: String, vararg values: Any): Component = Component.translatable("cph.editor.$key", *values)

/**
 * EditBox keeps a horizontal viewport calculated from its construction width. Editor lists must create it
 * at its final width; while unfocused we render from the beginning without destroying the user's cursor.
 */
internal class StableEditBox(
    font: Font,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component,
) : EditBox(font, x, y, width, height, message) {
    override fun setValue(value: String) {
        super.setValue(value)
        if (!isFocused) moveCursorToStart(false)
    }

    override fun setFocused(focused: Boolean) {
        super.setFocused(focused)
        if (!focused) moveCursorToStart(false)
    }
}

abstract class EditorScreenBase(
    title: Component,
    previous: Screen,
) : AnimatedScreen(title, previous) {
    protected val accent = 0xFF62D9FF.toInt()
    protected val accentPurple = 0xFF9B7BFF.toInt()
    protected val panel = 0xD0181C29.toInt()
    protected val panelHover = 0xE0242A3A.toInt()

    override fun tick() {
        super.tick()
        val client = minecraft ?: return
        val scaledWidth = client.window.guiScaledWidth
        val scaledHeight = client.window.guiScaledHeight
        if (scaledWidth != width || scaledHeight != height) resize(client, scaledWidth, scaledHeight)
    }

    final override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderEditorBackground(guiGraphics)
        renderEditorContent(guiGraphics, mouseX, mouseY, partialTick)
    }

    protected open fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) = Unit

    protected fun renderEditorBackground(guiGraphics: GuiGraphics) {
        guiGraphics.fillGradient(0, 0, width, height, 0xFF090D18.toInt(), 0xFF111629.toInt())
        val time = Util.getMillis() / 1100.0
        for (x in 0..width step 32) guiGraphics.fill(x, 0, x + 1, height, 0x0A6E91B0)
        for (y in 0..height step 32) guiGraphics.fill(0, y, width, y + 1, 0x086E91B0)
        val scanY = ((Util.getMillis() / 18L) % (height + 60) - 30).toInt()
        guiGraphics.fillGradient(0, scanY - 8, width, scanY + 8, 0x0062D9FF, 0x1262D9FF)
        val firstX = (width * .18 + sin(time) * 18).toInt()
        val secondX = (width * .82 + sin(time * .73 + 2.0) * 22).toInt()
        guiGraphics.fill(firstX - 70, -18, firstX + 70, 1, 0x5962D9FF)
        guiGraphics.fill(secondX - 90, height - 1, secondX + 90, height, 0x559B7BFF)
        guiGraphics.fill(0, 0, width, 1, 0x334B7691)
    }

    protected fun drawHeader(guiGraphics: GuiGraphics, subtitle: Component? = null) {
        val offset = slideOffset(12)
        guiGraphics.drawCenteredString(font, title, width / 2, 12 + offset, animatedColor(0xF4FAFF))
        subtitle?.let {
            guiGraphics.drawCenteredString(font, it, width / 2, 28 + offset, animatedColor(0x92A5BB))
        }
        val lineWidth = (font.width(title) + 34).coerceAtMost(width - 30)
        guiGraphics.fill(width / 2 - lineWidth / 2, 41 + offset, width / 2 + lineWidth / 2, 42 + offset, animatedColor(0x2D91B1))
        guiGraphics.fill(width / 2 - 8, 40 + offset, width / 2 + 8, 42 + offset, animatedColor(0x62D9FF))
    }

    protected fun drawPanel(guiGraphics: GuiGraphics, left: Int, top: Int, right: Int, bottom: Int, hovered: Boolean = false) {
        guiGraphics.fill(left, top, right, bottom, if (hovered) panelHover else panel)
        guiGraphics.fill(left, top, left + 1, bottom, if (hovered) accent else 0xAA405269.toInt())
        guiGraphics.fill(left, top, right, top + 1, 0x334E637F)
        guiGraphics.fill(right - 5, top, right, top + 1, if (hovered) accentPurple else 0x6656677A)
        guiGraphics.fill(left, bottom - 1, left + 5, bottom, if (hovered) accent else 0x6656677A)
    }
}

enum class TechButtonStyle {
    PRIMARY,
    SECONDARY,
    GHOST,
    DANGER,
    CARD,
}

class TechButton private constructor(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component,
    private val subtitle: Component?,
    private var style: TechButtonStyle,
    private val narration: ((TechButton) -> Component)?,
    private val action: (TechButton) -> Unit,
) : AbstractButton(x, y, width, height, message) {
    private var hoverProgress = 0f

    override fun onPress() = action(this)

    fun setTechStyle(value: TechButtonStyle) {
        style = value
    }

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val target = if (isHoveredOrFocused && active) 1f else 0f
        hoverProgress += (target - hoverProgress) * 0.28f
        if (kotlin.math.abs(target - hoverProgress) < .01f) hoverProgress = target

        val colors = when (style) {
            TechButtonStyle.PRIMARY -> Triple(0xE1286884.toInt(), 0xF0388DB1.toInt(), 0xFF62D9FF.toInt())
            TechButtonStyle.DANGER -> Triple(0xB13A2028.toInt(), 0xE65B2B38.toInt(), 0xFFFF6B78.toInt())
            TechButtonStyle.GHOST -> Triple(0x64151B28, 0xA0253042.toInt(), 0xFF8499AF.toInt())
            TechButtonStyle.CARD -> Triple(0xD0171D2A.toInt(), 0xED222E42.toInt(), 0xFF62D9FF.toInt())
            TechButtonStyle.SECONDARY -> Triple(0xB51D2635.toInt(), 0xE02A384B.toInt(), 0xFF9B7BFF.toInt())
        }
        val background = blend(colors.first, colors.second, hoverProgress)
        val accentColor = if (active) colors.third else 0xFF526070.toInt()
        guiGraphics.fill(x, y, x + width, y + height, background)
        val outline = withAlpha(accentColor, if (isHoveredOrFocused) 210 else if (style == TechButtonStyle.DANGER) 145 else 72)
        guiGraphics.fill(x, y, x + width, y + 1, outline)
        guiGraphics.fill(x, y + height - 1, x + width, y + height, outline)
        guiGraphics.fill(x, y + 1, x + 1, y + height - 1, outline)
        guiGraphics.fill(x + width - 1, y + 1, x + width, y + height - 1, outline)
        when (style) {
            TechButtonStyle.CARD -> guiGraphics.fill(x, y, x + 3, y + height, accentColor)
            TechButtonStyle.PRIMARY -> guiGraphics.fill(x + 1, y + height - 2, x + width - 1, y + height, accentColor)
            TechButtonStyle.DANGER -> {
                guiGraphics.fill(x, y, x + width, y + 1, accentColor)
                guiGraphics.fill(x, y + height - 1, x + width, y + height, accentColor)
                guiGraphics.fill(x, y, x + 1, y + height, accentColor)
                guiGraphics.fill(x + width - 1, y, x + width, y + height, accentColor)
            }
            else -> Unit
        }

        val minecraft = net.minecraft.client.Minecraft.getInstance()
        val font = minecraft.font
        val textColor = if (active) 0xFFF4FAFF.toInt() else 0xFF687484.toInt()
        if (style == TechButtonStyle.CARD) {
            val textX = x + 13
            guiGraphics.drawString(font, font.plainSubstrByWidth(message.string, width - 26), textX, y + 10, textColor, false)
            subtitle?.let { secondary ->
                font.split(secondary, (width - 26).coerceAtLeast(20)).take(if (height >= 58) 2 else 1).forEachIndexed { index, line ->
                    guiGraphics.drawString(font, line, textX, y + 26 + index * 10, if (active) 0xFF91A7BC.toInt() else 0xFF5D6875.toInt(), false)
                }
            }
            if (isHoveredOrFocused && active) guiGraphics.drawString(font, Component.literal("›"), x + width - 16, y + height / 2 - 4, accentColor, false)
        } else {
            renderScrollingString(guiGraphics, font, 7, textColor)
        }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        val custom = narration?.invoke(this)
        if (custom == null) defaultButtonNarrationText(output)
        else output.add(NarratedElementType.TITLE, custom)
    }

    class Builder internal constructor(
        private val message: Component,
        private val action: (TechButton) -> Unit,
    ) {
        private var x = 0
        private var y = 0
        private var width = 150
        private var height = 20
        private var subtitle: Component? = null
        private var style = TechButtonStyle.SECONDARY
        private var tooltip: Tooltip? = null
        private var narration: ((TechButton) -> Component)? = null

        fun bounds(x: Int, y: Int, width: Int, height: Int) = apply {
            this.x = x; this.y = y; this.width = width; this.height = height
        }

        fun subtitle(value: Component?) = apply { subtitle = value }

        fun style(value: TechButtonStyle) = apply { style = value }

        fun tooltip(value: Tooltip?) = apply { tooltip = value }

        fun createNarration(value: (TechButton) -> Component) = apply { narration = value }

        fun build(): TechButton = TechButton(x, y, width, height, message, subtitle, style, narration, action).also {
            tooltip?.let(it::setTooltip)
        }
    }

    companion object {
        fun builder(message: Component, action: (TechButton) -> Unit): Builder = Builder(message, action)
    }

    private fun blend(from: Int, to: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val a = from ushr shift and 0xFF
            val b = to ushr shift and 0xFF
            return (a + (b - a) * amount).toInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)
}
