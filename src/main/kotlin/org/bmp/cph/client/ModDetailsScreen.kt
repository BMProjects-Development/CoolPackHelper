package org.bmp.cph.client

import com.mojang.blaze3d.vertex.Tesselator
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.neoforged.neoforge.client.gui.widget.ScrollPanel
import org.bmp.cph.config.ModCategory
import org.bmp.cph.config.ResolvedMenuText
import org.bmp.cph.config.validHttpUri

class ModDetailsScreen(
    parent: Screen,
    private val result: ModCheckResult,
    private val text: ResolvedMenuText,
) : AnimatedScreen(
    Component.literal(text.detailsTitle.replace("{mod}", result.mod.displayName())),
    parent,
) {
    override fun init() {
        val contentWidth = (width - 24).coerceIn(120, 720)
        val left = (width - contentWidth) / 2
        val top = 54
        val footerTop = height - 34
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
        addRenderableWidget(
            Button.builder(Component.literal(downloadLabel())) { openDownload() }
                .bounds(left, height - 27, buttonWidth, 20)
                .build()
        )
        addRenderableWidget(
            Button.builder(Component.literal(text.backButton)) { onClose() }
                .bounds(left + buttonWidth + gap, height - 27, buttonWidth, 20)
                .build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        val offset = slideOffset()
        guiGraphics.drawCenteredString(font, title, width / 2, 11 + offset, animatedColor(0xFFFFFF))
        val category = result.mod.resolvedCategory()
        val categoryText = if (category == ModCategory.REQUIRED) text.requiredLabel else text.recommendedLabel
        val color = if (category == ModCategory.REQUIRED) 0xE46A6A else 0xE0B85B
        guiGraphics.drawCenteredString(font, categoryText, width / 2, 29 + offset, animatedColor(color))
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
        val links = result.mod.availableLinks().filter { validHttpUri(it.url) != null }
        if (links.size == 1) {
            ConfirmLinkScreen.confirmLinkNow(this, validHttpUri(links.first().url)!!, true)
        } else if (links.isNotEmpty()) {
            minecraft?.setScreen(DownloadSourcesScreen(this, result.mod, links, text))
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
