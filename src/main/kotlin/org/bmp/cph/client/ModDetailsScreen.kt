package org.bmp.cph.client

import com.mojang.blaze3d.vertex.Tesselator
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.neoforged.neoforge.client.gui.widget.ScrollPanel
import org.bmp.cph.config.ModCategory
import org.bmp.cph.config.ResolvedMenuText
import org.bmp.cph.client.download.SecureDownloadScreen
import org.bmp.cph.client.editor.EditorScreenBase
import org.bmp.cph.client.editor.TechButton
import org.bmp.cph.client.editor.TechButtonStyle

class ModDetailsScreen(
    parent: Screen,
    private val result: ModCheckResult,
    private val text: ResolvedMenuText,
) : EditorScreenBase(
    Component.literal(text.detailsTitle.replace("{mod}", result.mod.displayName())),
    parent,
) {
    private var panelLeft = 0
    private var panelTop = 54
    private var panelWidth = 0
    private var panelBottom = 100

    override fun init() {
        val contentWidth = (width - 24).coerceIn(120, 720)
        val left = (width - contentWidth) / 2
        val top = 54
        val footerTop = height - 34
        panelLeft = left
        panelTop = top
        panelWidth = contentWidth
        panelBottom = footerTop
        addRenderableWidget(
            DetailsPanel(
                minecraft ?: Minecraft.getInstance(),
                contentWidth,
                (footerTop - top).coerceAtLeast(44),
                top,
                left,
                detailsLines(contentWidth - 20),
            )
        )

        val gap = 6
        val buttonWidth = (contentWidth - gap) / 2
        val downloadButton = TechButton.builder(Component.literal(downloadLabel())) { openDownload() }
                .style(TechButtonStyle.PRIMARY)
                .bounds(left, height - 27, buttonWidth, 20)
                .build()
        downloadButton.active = result.mod.availableLinks().isNotEmpty()
        addRenderableWidget(downloadButton)
        addRenderableWidget(
            TechButton.builder(Component.literal(text.backButton)) { onClose() }
                .style(TechButtonStyle.GHOST)
                .bounds(left + buttonWidth + gap, height - 27, buttonWidth, 20)
                .build()
        )
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics)
        drawPanel(guiGraphics, panelLeft, panelTop, panelLeft + panelWidth, panelBottom)
        val category = result.mod.resolvedCategory()
        val categoryText = if (category == ModCategory.REQUIRED) text.requiredLabel else text.recommendedLabel
        val color = if (category == ModCategory.REQUIRED) 0xE46A6A else 0xE0B85B
        val labelWidth = font.width(categoryText) + 14
        guiGraphics.fill(width / 2 - labelWidth / 2, 29 + slideOffset(6), width / 2 + labelWidth / 2, 42 + slideOffset(6), animatedColor(0x172231))
        guiGraphics.drawCenteredString(font, categoryText, width / 2, 31 + slideOffset(6), animatedColor(color))
    }

    private fun detailsLines(maxWidth: Int): List<FormattedCharSequence> = buildList {
        val status = when (result.status) {
            RequirementStatus.MISSING -> text.missingStatus
            RequirementStatus.WRONG_VERSION -> text.wrongVersionStatus
                .replace("{installed}", result.installedVersion.orEmpty())
                .replace("{required}", result.mod.versionRange.orEmpty())
        }
        add(Component.literal(status).visualOrderText)
        result.mod.modId?.takeIf { it.isNotBlank() }?.let {
            add(Component.literal(text.modIdLabel.replace("{value}", it)).visualOrderText)
        }
        result.installedVersion?.takeIf { it.isNotBlank() }?.let {
            add(Component.literal(text.installedVersionLabel.replace("{value}", it)).visualOrderText)
        }
        result.mod.versionRange?.takeIf { it.isNotBlank() }?.let {
            add(Component.literal(text.requiredVersionLabel.replace("{value}", it)).visualOrderText)
        }
        add(Component.empty().visualOrderText)
        val description = result.mod.localizedDescription(text.languageCode, text.fallbackLanguage)
        font.split(Component.literal(description), maxWidth.coerceAtLeast(40)).forEach {
            add(it)
        }
    }

    private fun downloadLabel(): String {
        val count = result.mod.availableLinks().size
        return if (count <= 1) text.downloadButton else text.chooseSourceButton.replace("{count}", count.toString())
    }

    private fun openDownload() {
        val links = result.mod.availableLinks()
        if (links.size == 1) {
            minecraft?.setScreen(SecureDownloadScreen.forSource(this, result, links.first()))
        } else if (links.isNotEmpty()) {
            minecraft?.setScreen(DownloadSourcesScreen(this, result, links, text))
        }
    }

    private class DetailsPanel(
        minecraft: Minecraft,
        width: Int,
        height: Int,
        top: Int,
        left: Int,
        private val lines: List<FormattedCharSequence>,
    ) : ScrollPanel(minecraft, width, height, top, left) {
        override fun getContentHeight(): Int = (lines.size * 12 + 12).coerceAtLeast(height - 8)

        override fun drawPanel(
            guiGraphics: GuiGraphics,
            entryRight: Int,
            relativeY: Int,
            tess: Tesselator,
            mouseX: Int,
            mouseY: Int,
        ) {
            lines.forEachIndexed { index, line ->
                guiGraphics.drawString(Minecraft.getInstance().font, line, left + 8, relativeY + 6 + index * 12, 0xD8D8D8, false)
            }
        }

        override fun narrationPriority(): NarratableEntry.NarrationPriority = NarratableEntry.NarrationPriority.NONE

        override fun updateNarration(output: NarrationElementOutput) = Unit
    }
}
