package org.bmp.cph.client.editor

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component

data class RowBadge(val text: Component, val color: Int)

data class RowAction(
    val label: () -> Component,
    val width: Int = 66,
    val style: () -> TechButtonStyle = { TechButtonStyle.SECONDARY },
    val enabled: () -> Boolean = { true },
    val tooltip: Component? = null,
    val run: () -> Unit,
)

class StyledActionList<T>(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val rowWidth: Int,
    private val rowHeight: Int,
    items: List<T>,
    private val titleOf: (T) -> String,
    private val subtitleOf: (T) -> String = { "" },
    private val accentOf: (T) -> Int = { 0xFF62D9FF.toInt() },
    private val badgeOf: (T) -> RowBadge? = { null },
    private val actionsOf: (T) -> List<RowAction> = { emptyList() },
) : ContainerObjectSelectionList<StyledActionList<T>.Entry>(minecraft, width, height, top, rowHeight) {
    init {
        items.forEach { addEntry(Entry(it, minecraft.font)) }
    }

    override fun getRowWidth(): Int = rowWidth

    override fun getScrollbarPosition(): Int = x + width - 7

    inner class Entry(
        private val value: T,
        private val font: Font,
    ) : ContainerObjectSelectionList.Entry<Entry>() {
        private val actions = actionsOf(value)
        private val buttons = actions.map { action ->
            TechButton.builder(action.label()) { action.run() }
                .style(action.style())
                .tooltip(action.tooltip?.let(Tooltip::create))
                .bounds(0, 0, action.width, 20)
                .build()
        }

        override fun children(): List<GuiEventListener> = buttons

        override fun narratables(): List<NarratableEntry> = buttons

        override fun render(
            guiGraphics: GuiGraphics,
            index: Int,
            top: Int,
            left: Int,
            width: Int,
            height: Int,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            partialTick: Float,
        ) {
            val accent = accentOf(value)
            guiGraphics.fill(left, top, left + width, top + height - 2, if (hovered) 0xE0222D3E.toInt() else 0xC5161D29.toInt())
            guiGraphics.fill(left, top, left + 2, top + height - 2, accent)
            guiGraphics.fill(left + 2, top, left + width, top + 1, 0x4A5A708A)
            guiGraphics.fill(left + width - 1, top + 1, left + width, top + height - 2, 0x334D6077)
            guiGraphics.fill(left + 2, top + height - 3, left + width, top + height - 2, 0x334D6077)

            val totalActionsWidth = actions.sumOf(RowAction::width) + (actions.size - 1).coerceAtLeast(0) * 4
            val badge = badgeOf(value)
            val badgeWidth = badge?.let { font.width(it.text) + 12 } ?: 0
            val textWidth = (width - totalActionsWidth - badgeWidth - 26).coerceAtLeast(24)
            guiGraphics.drawString(font, font.plainSubstrByWidth(titleOf(value), textWidth), left + 8, top + 7, 0xE7F3FF, false)
            subtitleOf(value).takeIf(String::isNotBlank)?.let {
                guiGraphics.drawString(font, font.plainSubstrByWidth(it, textWidth), left + 8, top + 20, 0x8094A8, false)
            }
            badge?.let {
                val badgeX = left + width - totalActionsWidth - badgeWidth - 10
                guiGraphics.fill(badgeX, top + (height - 15) / 2, badgeX + badgeWidth, top + (height - 15) / 2 + 15, 0x80202A38.toInt())
                guiGraphics.drawString(font, it.text, badgeX + 6, top + (height - 8) / 2 - 3, it.color, false)
            }

            var buttonX = left + width - totalActionsWidth - 6
            buttons.forEachIndexed { actionIndex, button ->
                val action = actions[actionIndex]
                button.message = action.label()
                button.setTechStyle(action.style())
                button.active = action.enabled()
                button.x = buttonX
                button.y = top + (height - 22) / 2
                button.render(guiGraphics, mouseX, mouseY, partialTick)
                buttonX += action.width + 4
            }
        }
    }
}
