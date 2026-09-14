package org.bmp.cph.client.editor

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.client.AnimatedScreen
import kotlin.math.min
import kotlin.math.sqrt

internal fun tr(key: String, vararg values: Any): Component = Component.translatable("cph.editor.$key", *values)

internal object EditorTheme {
    const val BACKGROUND_TOP = 0xFF111216.toInt()
    const val BACKGROUND_BOTTOM = 0xFF0B0C0F.toInt()
    const val TOP_BAR = 0xF516181D.toInt()
    const val SURFACE = 0xF0191B20.toInt()
    const val SURFACE_HOVER = 0xF023262D.toInt()
    const val BORDER = 0xFF30343C.toInt()
    const val BORDER_SOFT = 0xB0262930.toInt()
    const val TEXT = 0xFFE7E9ED.toInt()
    const val TEXT_MUTED = 0xFF989DA8.toInt()
    const val ACCENT = 0xFF76AFC8.toInt()
    const val ACCENT_PURPLE = 0xFFA795C8.toInt()
    const val HEADER_HEIGHT = 36
    const val FOOTER_HEIGHT = 28
    const val CONTROL_HEIGHT = 18
}

data class EditorRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

internal fun fillRoundedRect(
    graphics: GuiGraphics,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    radius: Int,
    color: Int,
) {
    if (right <= left || bottom <= top) return
    val r = min(radius.coerceAtLeast(0), min((right - left) / 2, (bottom - top) / 2))
    if (r == 0) {
        graphics.fill(left, top, right, bottom, color)
        return
    }
    graphics.fill(left + r, top, right - r, bottom, color)
    graphics.fill(left, top + r, right, bottom - r, color)
    for (row in 0 until r) {
        val dy = r - row - 0.5
        val inset = (r - sqrt((r * r - dy * dy).coerceAtLeast(0.0))).toInt().coerceIn(0, r)
        graphics.fill(left + inset, top + row, right - inset, top + row + 1, color)
        graphics.fill(left + inset, bottom - row - 1, right - inset, bottom - row, color)
    }
}

internal fun drawRoundedOutline(
    graphics: GuiGraphics,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    radius: Int,
    color: Int,
    innerColor: Int,
) {
    fillRoundedRect(graphics, left, top, right, bottom, radius, color)
    fillRoundedRect(graphics, left + 1, top + 1, right - 1, bottom - 1, (radius - 1).coerceAtLeast(0), innerColor)
}

internal fun drawEditorRow(
    graphics: GuiGraphics,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    hovered: Boolean,
    accent: Int? = null,
) {
    val background = if (hovered) EditorTheme.SURFACE_HOVER else EditorTheme.SURFACE
    graphics.fill(left, top, left + width, top + height - 1, background)
    graphics.fill(left, top + height - 2, left + width, top + height - 1, EditorTheme.BORDER_SOFT)
    accent?.let { graphics.fill(left, top + 5, left + 2, top + height - 6, it) }
}

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
    init {
        isBordered = false
        setTextShadow(false)
    }

    override fun setValue(value: String) {
        super.setValue(value)
        if (!isFocused) moveCursorToStart(false)
    }

    override fun setFocused(focused: Boolean) {
        super.setFocused(focused)
        if (!focused) moveCursorToStart(false)
    }

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val clipLeft = x + 3
        val clipTop = y + 2
        val clipRight = x + width - 3
        val clipBottom = y + height - 2
        val border = if (isFocused) EditorTheme.ACCENT else EditorTheme.BORDER
        drawRoundedOutline(graphics, x, y, x + width, y + height, 4, border, 0xFF14161A.toInt())
        val originalX = x
        val originalY = y
        val originalWidth = width
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom)
        try {
            x += 5
            // Unbordered vanilla EditBox anchors text to its top edge. Our
            // custom field still needs the same optical centering as a
            // bordered vanilla control.
            y += ((height - 8) / 2).coerceAtLeast(0)
            width = (width - 10).coerceAtLeast(4)
            super.renderWidget(graphics, mouseX, mouseY, partialTick)
        } finally {
            x = originalX
            y = originalY
            width = originalWidth
            graphics.disableScissor()
        }
    }
}

internal class StableMultiLineEditBox(
    font: Font,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    placeholder: Component,
    message: Component,
) : MultiLineEditBox(font, x, y, width, height, placeholder, message) {
    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val border = if (isFocused) EditorTheme.ACCENT else EditorTheme.BORDER
        drawRoundedOutline(graphics, x, y, x + width, y + height, 4, border, 0xFF14161A.toInt())
        // AbstractScrollWidget clips at the outermost pixel. Tighten that
        // viewport so glyphs, selection and the cursor cannot touch or cross
        // the rounded top and bottom borders.
        graphics.enableScissor(x + 2, y + 3, x + width - 2, y + height - 3)
        try {
            super.renderWidget(graphics, mouseX, mouseY, partialTick)
        } finally {
            graphics.disableScissor()
        }
    }

    override fun renderBackground(graphics: GuiGraphics) = Unit

    override fun renderBorder(graphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) = Unit
}

abstract class EditorScreenBase(
    title: Component,
    previous: Screen,
) : AnimatedScreen(title, previous) {
    protected val accent = EditorTheme.ACCENT
    protected val accentPurple = EditorTheme.ACCENT_PURPLE
    protected val panel = EditorTheme.SURFACE
    protected val panelHover = EditorTheme.SURFACE_HOVER

    override fun tick() {
        super.tick()
        val client = minecraft ?: return
        val scaledWidth = client.window.guiScaledWidth
        val scaledHeight = client.window.guiScaledHeight
        if (scaledWidth != width || scaledHeight != height) resize(client, scaledWidth, scaledHeight)
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        // Modal screens render their parent themselves. Keep that parent at the
        // current GUI size as well, otherwise a window/fullscreen switch leaves
        // the title panorama and its widgets rendered in the old viewport.
        previousScreen.resize(minecraft, width, height)
        super.resize(minecraft, width, height)
    }

    protected open fun usesModalBackground(): Boolean = false

    final override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (usesModalBackground()) {
            // The parent remains visible behind the modal, but it must not receive
            // hover coordinates. Otherwise its hidden widgets can render a second
            // tooltip over different parts of the modal's controls.
            previousScreen.render(guiGraphics, OFFSCREEN_MOUSE, OFFSCREEN_MOUSE, partialTick)
            guiGraphics.flush()
            renderBlurredBackground(partialTick)
            guiGraphics.fill(0, 0, width, height, animatedAlphaColor(modalScrimColor()))
        } else {
            renderEditorBackground(guiGraphics)
        }
        renderEditorContent(guiGraphics, mouseX, mouseY, partialTick)
    }

    protected open fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) = Unit

    protected open fun modalScrimColor(): Int = 0x66080A0D

    protected fun renderEditorBackground(guiGraphics: GuiGraphics) {
        guiGraphics.fillGradient(0, 0, width, height, EditorTheme.BACKGROUND_TOP, EditorTheme.BACKGROUND_BOTTOM)
        guiGraphics.fill(0, 0, width, EditorTheme.HEADER_HEIGHT, EditorTheme.TOP_BAR)
        guiGraphics.fill(0, EditorTheme.HEADER_HEIGHT - 1, width, EditorTheme.HEADER_HEIGHT, EditorTheme.BORDER_SOFT)
        guiGraphics.fill(0, height - EditorTheme.FOOTER_HEIGHT, width, height, 0xD7101115.toInt())
        guiGraphics.fill(0, height - EditorTheme.FOOTER_HEIGHT, width, height - EditorTheme.FOOTER_HEIGHT + 1, EditorTheme.BORDER_SOFT)
        val time = Util.getMillis() / 90L
        val particleTop = EditorTheme.HEADER_HEIGHT
        val particleHeight = (height - EditorTheme.HEADER_HEIGHT - EditorTheme.FOOTER_HEIGHT).coerceAtLeast(1)
        repeat(10) { index ->
            val particleX = ((index * 137L + time * (index % 3 + 1)) % (width + 40) - 20).toInt()
            val particleY = particleTop + ((index * 83L + time / (index % 2 + 2)) % particleHeight).toInt()
            guiGraphics.fill(particleX, particleY, particleX + 1, particleY + 1, 0x163F5662)
        }
    }

    protected fun drawHeader(guiGraphics: GuiGraphics, subtitle: Component? = null) {
        val offset = slideOffset(8)
        val left = 11
        val titleWidth = (width - 22).coerceAtLeast(30)
        guiGraphics.drawString(font, font.plainSubstrByWidth(title.string, titleWidth), left, 7 + offset, animatedColor(EditorTheme.TEXT), false)
        subtitle?.let {
            guiGraphics.drawString(font, font.plainSubstrByWidth(it.string, titleWidth), left, 20 + offset, animatedColor(EditorTheme.TEXT_MUTED), false)
        }
    }

    protected fun drawPanel(guiGraphics: GuiGraphics, left: Int, top: Int, right: Int, bottom: Int, hovered: Boolean = false) {
        drawRoundedOutline(guiGraphics, left, top, right, bottom, 6, if (hovered) 0xFF454A54.toInt() else EditorTheme.BORDER, if (hovered) panelHover else panel)
    }

    protected fun centeredModal(maxWidth: Int = 650, maxHeight: Int = 340, minimumHeight: Int = 180): EditorRect {
        val modalWidth = (width - 24).coerceAtMost(maxWidth).coerceAtLeast(220)
        val modalHeight = (height - 24).coerceAtMost(maxHeight).coerceAtLeast(minimumHeight)
        val left = (width - modalWidth) / 2
        val top = (height - modalHeight) / 2 + slideOffset(7)
        return EditorRect(left, top, left + modalWidth, top + modalHeight)
    }

    protected fun drawModalFrame(guiGraphics: GuiGraphics, bounds: EditorRect, subtitle: Component? = null) {
        drawPanel(guiGraphics, bounds.left, bounds.top, bounds.right, bounds.bottom)
        guiGraphics.drawString(font, font.plainSubstrByWidth(title.string, (bounds.width - 20).coerceAtLeast(40)), bounds.left + 10, bounds.top + 9, EditorTheme.TEXT, false)
        subtitle?.let {
            guiGraphics.drawString(font, font.plainSubstrByWidth(it.string, (bounds.width - 20).coerceAtLeast(40)), bounds.left + 10, bounds.top + 21, EditorTheme.TEXT_MUTED, false)
        }
    }

    protected fun compactButtonWidth(message: Component, minimum: Int = 58, maximum: Int = 150): Int =
        (font.width(message) + 18).coerceIn(minimum, maximum)

    protected fun addFooterActions(vararg buttons: TechButton) {
        addCompactActions(8, width - 10, height - 23, *buttons)
    }

    protected fun addCompactActions(left: Int, right: Int, y: Int, vararg buttons: TechButton) {
        val gap = 5
        val available = (right - left).coerceAtLeast(40)
        var totalWidth = buttons.sumOf { it.width } + gap * (buttons.size - 1).coerceAtLeast(0)
        if (totalWidth > available && buttons.isNotEmpty()) {
            val compactWidth = ((available - gap * (buttons.size - 1)) / buttons.size).coerceAtLeast(24)
            buttons.forEach { it.width = compactWidth }
            totalWidth = buttons.sumOf { it.width } + gap * (buttons.size - 1).coerceAtLeast(0)
        }
        var buttonX = (right - totalWidth).coerceAtLeast(left)
        buttons.forEach { button ->
            button.x = buttonX
            button.y = y
            button.height = EditorTheme.CONTROL_HEIGHT
            addRenderableWidget(button)
            buttonX += button.width + gap
        }
    }

    private companion object {
        const val OFFSCREEN_MOUSE = -10_000
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
            TechButtonStyle.PRIMARY -> Triple(0xEC1C2025.toInt(), 0xFF282E34.toInt(), EditorTheme.ACCENT)
            TechButtonStyle.DANGER -> Triple(0xEC1C1D21.toInt(), 0xFF2B2428.toInt(), 0xFFB96E78.toInt())
            TechButtonStyle.GHOST -> Triple(0x0017191E, 0xF0202328.toInt(), 0xFF656B74.toInt())
            TechButtonStyle.CARD -> Triple(0xF0191B20.toInt(), 0xFF22252A.toInt(), 0xFF737982.toInt())
            TechButtonStyle.SECONDARY -> Triple(0xEC1C1F24.toInt(), 0xFF272B31.toInt(), 0xFF6F757E.toInt())
        }
        val background = blend(colors.first, colors.second, hoverProgress)
        val accentColor = if (active) colors.third else 0xFF526070.toInt()
        val outline = withAlpha(accentColor, if (isHoveredOrFocused) 145 else when (style) {
            TechButtonStyle.PRIMARY -> 82
            TechButtonStyle.DANGER -> 62
            TechButtonStyle.GHOST -> 18
            else -> 40
        })
        drawRoundedOutline(guiGraphics, x, y, x + width, y + height, if (style == TechButtonStyle.CARD) 6 else 4, outline, background)

        val minecraft = net.minecraft.client.Minecraft.getInstance()
        val font = minecraft.font
        val textColor = if (!active) 0xFF666B74.toInt() else when (style) {
            TechButtonStyle.PRIMARY -> 0xFFDDE8EC.toInt()
            TechButtonStyle.DANGER -> 0xFFE0B4B9.toInt()
            else -> EditorTheme.TEXT
        }
        if (style == TechButtonStyle.CARD) {
            val textX = x + 12
            guiGraphics.drawString(font, font.plainSubstrByWidth(message.string, width - 26), textX, y + 10, textColor, false)
            subtitle?.let { secondary ->
                font.split(secondary, (width - 26).coerceAtLeast(20)).take(if (height >= 58) 2 else 1).forEachIndexed { index, line ->
                    guiGraphics.drawString(font, line, textX, y + 26 + index * 10, if (active) EditorTheme.TEXT_MUTED else 0xFF5D6169.toInt(), false)
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
            it.setTooltip(tooltip ?: Tooltip.create(message))
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
