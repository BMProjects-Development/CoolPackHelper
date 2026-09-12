package org.bmp.cph.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.network.chat.Component
import org.bmp.cph.config.ConfigIssue
import org.bmp.cph.config.IssueSeverity

class ConfigIssuesList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val rowWidth: Int,
    issues: List<ConfigIssue>,
) : ObjectSelectionList<ConfigIssuesList.IssueEntry>(minecraft, width, height, top, 38) {
    init {
        issues.forEach { addEntry(IssueEntry(it, minecraft.font)) }
    }

    override fun getRowWidth(): Int = rowWidth

    override fun getScrollbarPosition(): Int = x + width - 7

    inner class IssueEntry(
        private val issue: ConfigIssue,
        private val font: Font,
    ) : ObjectSelectionList.Entry<IssueEntry>() {
        override fun getNarration(): Component = Component.literal("${issue.severity}: ${issue.path}. ${issue.message}")

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
            guiGraphics.fill(left, top, left + width, top + height, if (hovered) 0xAA303030.toInt() else 0x88303030.toInt())
            guiGraphics.fill(left, top, left + 2, top + height, accent)
            guiGraphics.drawString(
                font,
                font.plainSubstrByWidth("${issue.severity}: ${issue.path}", (width - 14).coerceAtLeast(30)),
                left + 7,
                top + 4,
                accent,
                false,
            )
            font.split(Component.literal(issue.message), (width - 14).coerceAtLeast(30)).take(2).forEachIndexed { line, value ->
                guiGraphics.drawString(font, value, left + 7, top + 16 + line * 10, 0xC8C8C8, false)
            }
        }
    }
}
