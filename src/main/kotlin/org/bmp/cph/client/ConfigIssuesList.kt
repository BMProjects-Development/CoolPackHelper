package org.bmp.cph.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import org.bmp.cph.client.editor.InvisibleRowButton
import org.bmp.cph.client.editor.drawEditorRow
import org.bmp.cph.config.ConfigIssue
import org.bmp.cph.config.IssueSeverity
import org.bmp.cph.config.ResolvedMenuText

class ConfigIssuesList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val rowWidth: Int,
    issues: List<ConfigIssue>,
    private val text: ResolvedMenuText,
    private val canOpen: (ConfigIssue) -> Boolean = { false },
    private val onOpen: (ConfigIssue) -> Unit = {},
    private val openHint: Component? = null,
) : ContainerObjectSelectionList<ConfigIssuesList.IssueEntry>(minecraft, width, height, top, 38) {
    init {
        issues.forEach { addEntry(IssueEntry(it, minecraft.font)) }
    }

    override fun getRowWidth(): Int = rowWidth

    override fun getScrollbarPosition(): Int = x + width - 7

    inner class IssueEntry(
        private val issue: ConfigIssue,
        private val font: Font,
    ) : ContainerObjectSelectionList.Entry<IssueEntry>() {
        private val openButton = if (canOpen(issue)) {
            InvisibleRowButton(
                Component.literal("${issue.displayPath ?: issue.path}. ${issue.localized(text)}"),
            ) { onOpen(issue) }.also { button -> openHint?.let { button.setTooltip(Tooltip.create(it)) } }
        } else null

        override fun children(): List<GuiEventListener> = listOfNotNull(openButton)

        override fun narratables(): List<NarratableEntry> = listOfNotNull(openButton)

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
            val accent = if (issue.severity == IssueSeverity.ERROR) 0xFFE46A6A.toInt() else 0xFFE0B85B.toInt()
            drawEditorRow(guiGraphics, left, top, width, height + 2, hovered, accent)
            guiGraphics.drawString(
                font,
                font.plainSubstrByWidth(issue.displayPath ?: issue.path, (width - 22).coerceAtLeast(30)),
                left + 15,
                top + 4,
                accent,
                false,
            )
            if (openButton != null && hovered) {
                guiGraphics.drawString(font, Component.literal("›"), left + width - 13, top + 4, accent, false)
            }
            font.split(Component.literal(issue.localized(text)), (width - 14).coerceAtLeast(30)).take(2).forEachIndexed { line, value ->
                guiGraphics.drawString(font, value, left + 15, top + 16 + line * 10, 0xC8C8C8, false)
            }
            openButton?.let { button ->
                button.x = left
                button.y = top
                button.width = width
                button.height = height
                button.render(guiGraphics, mouseX, mouseY, partialTick)
            }
        }
    }
}
