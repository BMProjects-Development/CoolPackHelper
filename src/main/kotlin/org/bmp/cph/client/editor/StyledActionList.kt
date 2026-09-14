package org.bmp.cph.client.editor

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import org.bmp.cph.client.ProjectIconCache

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
    private val iconOf: (T) -> String? = { null },
    private val actionsOf: (T) -> List<RowAction> = { emptyList() },
    private val onRowClick: ((T) -> Unit)? = null,
    private val rowClickable: (T) -> Boolean = { true },
    private val rowTooltipOf: (T) -> Component? = { null },
) : ContainerObjectSelectionList<StyledActionList<T>.Entry>(minecraft, width, height, top, rowHeight) {
    init {
        items.forEach { addEntry(Entry(it, minecraft.font)) }
    }

    override fun getRowWidth(): Int = rowWidth

    override fun getScrollbarPosition(): Int = x + width - 7

    fun replaceItems(items: List<T>, resetScroll: Boolean = true) {
        clearEntries()
        items.forEach { addEntry(Entry(it, minecraft.font)) }
        if (resetScroll) setScrollAmount(0.0)
    }

    inner class Entry(
        private val value: T,
        private val font: Font,
    ) : ContainerObjectSelectionList.Entry<Entry>() {
        private val actions = actionsOf(value)
        private val buttons = actions.map { action ->
            TechButton.builder(action.label()) { action.run() }
                .style(action.style())
                .tooltip(action.tooltip?.let(Tooltip::create))
                .bounds(0, 0, action.width, 18)
                .build()
        }
        private val rowButton = onRowClick?.takeIf { rowClickable(value) }?.let { open ->
            InvisibleRowButton(Component.literal(titleOf(value))) { open(value) }.also { button ->
                rowTooltipOf(value)?.let { button.setTooltip(Tooltip.create(it)) }
            }
        }

        override fun children(): List<GuiEventListener> = buttons + listOfNotNull(rowButton)

        override fun narratables(): List<NarratableEntry> = buttons + listOfNotNull(rowButton)

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
            drawEditorRow(guiGraphics, left, top, width, height, hovered, accent)

            val totalActionsWidth = actions.sumOf(RowAction::width) + (actions.size - 1).coerceAtLeast(0) * 4
            val badge = badgeOf(value)
            val badgeWidth = badge?.let { font.width(it.text) + 12 } ?: 0
            val rowActionWidth = if (rowButton == null) 0 else 12
            val iconUrl = iconOf(value)
            val iconSize = if (iconUrl.isNullOrBlank()) 0 else (height - 10).coerceIn(16, 30)
            val textX = left + 15 + if (iconSize == 0) 0 else iconSize + 7
            if (iconSize > 0) {
                val iconX = left + 14
                val iconY = top + (height - 2 - iconSize) / 2
                fillRoundedRect(guiGraphics, iconX, iconY, iconX + iconSize, iconY + iconSize, 4, 0xFF272A31.toInt())
                ProjectIconCache.texture(iconUrl)?.let { icon ->
                    guiGraphics.blit(
                        icon.location, iconX, iconY, iconSize, iconSize, 0f, 0f,
                        icon.width, icon.height, icon.width, icon.height,
                    )
                }
            }
            val textWidth = (width - (textX - left) - totalActionsWidth - badgeWidth - rowActionWidth - 18).coerceAtLeast(24)
            guiGraphics.drawString(font, font.plainSubstrByWidth(titleOf(value), textWidth), textX, top + 7, 0xE7F3FF, false)
            subtitleOf(value).takeIf(String::isNotBlank)?.let {
                guiGraphics.drawString(font, font.plainSubstrByWidth(it, textWidth), textX, top + 20, 0x8094A8, false)
            }
            badge?.let {
                val badgeX = left + width - totalActionsWidth - badgeWidth - 10
                fillRoundedRect(
                    guiGraphics, badgeX, top + (height - 15) / 2,
                    badgeX + badgeWidth, top + (height - 15) / 2 + 15, 4, 0xFF292C33.toInt(),
                )
                guiGraphics.drawString(font, it.text, badgeX + 6, top + (height - 8) / 2 - 3, it.color, false)
            }

            var buttonX = left + width - totalActionsWidth - 6
            buttons.forEachIndexed { actionIndex, button ->
                val action = actions[actionIndex]
                button.message = action.label()
                button.setTechStyle(action.style())
                button.active = action.enabled()
                button.x = buttonX
                button.y = top + (height - 20) / 2
                button.render(guiGraphics, mouseX, mouseY, partialTick)
                buttonX += action.width + 4
            }
            rowButton?.let { button ->
                if (hovered) {
                    guiGraphics.drawString(font, Component.literal("›"), left + width - totalActionsWidth - 13, top + height / 2 - 4, accent, false)
                }
                button.x = left
                button.y = top
                button.width = width
                button.height = height - 2
                button.render(guiGraphics, mouseX, mouseY, partialTick)
            }
        }
    }
}

internal class InvisibleRowButton(
    message: Component,
    private val action: () -> Unit,
) : AbstractButton(0, 0, 0, 0, message) {
    override fun onPress() = action()

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) = Unit

    override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
}
