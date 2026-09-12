package org.bmp.cph.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import org.bmp.cph.config.ModCategory
import org.bmp.cph.config.ResolvedMenuText

class MissingModsList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val rowWidth: Int,
    private val compact: Boolean,
    results: List<ModCheckResult>,
    private val text: ResolvedMenuText,
    private val languageCode: String,
    private val onDownload: (ModCheckResult) -> Unit,
    private val onDetails: (ModCheckResult) -> Unit,
    private val animationProgress: () -> Float,
) : ContainerObjectSelectionList<MissingModsList.ModEntry>(
    minecraft,
    width,
    height,
    top,
    if (compact) 52 else 82,
) {
    init {
        results.forEach { addEntry(ModEntry(it, minecraft.font)) }
    }

    override fun getRowWidth(): Int = rowWidth

    override fun getScrollbarPosition(): Int = x + width - 7

    inner class ModEntry(
        private val result: ModCheckResult,
        private val font: Font,
    ) : ContainerObjectSelectionList.Entry<ModEntry>() {
        private val links = result.mod.availableLinks()
        private val downloadButton = Button.builder(Component.literal(buttonLabel())) { onDownload(result) }
            .tooltip(Tooltip.create(Component.literal(links.joinToString("\n") { it.displayLabel() })))
            .bounds(0, 0, 160, 20)
            .build()
        private val detailsButton = Button.builder(Component.literal(text.detailsButton)) { onDetails(result) }
            .createNarration { Component.literal(narrationText()) }
            .bounds(0, 0, 160, 20)
            .build()

        override fun children(): List<GuiEventListener> = listOf(detailsButton, downloadButton)

        override fun narratables(): List<NarratableEntry> = listOf(detailsButton, downloadButton)

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
            val animatedTop = top + ((1f - animationProgress()) * 10).toInt()
            val category = result.mod.resolvedCategory()
            val accent = if (category == ModCategory.REQUIRED) 0xFFE46A6A.toInt() else 0xFFE0B85B.toInt()
            val background = if (hovered) 0xAA303030.toInt() else 0x88303030.toInt()
            guiGraphics.fill(left, animatedTop, left + width, animatedTop + height, background)
            guiGraphics.fill(left, animatedTop, left + 2, animatedTop + height, accent)

            val categoryLabel = if (category == ModCategory.REQUIRED) text.requiredLabel else text.recommendedLabel
            val categoryWidth = font.width(categoryLabel)
            val nameWidth = (width - categoryWidth - 22).coerceAtLeast(30)
            val name = font.plainSubstrByWidth(result.mod.displayName(), nameWidth)
            guiGraphics.drawString(font, name, left + 7, animatedTop + 3, 0xFFFFFF, false)
            guiGraphics.drawString(font, categoryLabel, left + width - categoryWidth - 7, animatedTop + 3, accent, false)

            val status = when (result.status) {
                RequirementStatus.MISSING -> text.missingStatus
                RequirementStatus.WRONG_VERSION -> text.wrongVersionStatus
                    .replace("{installed}", result.installedVersion.orEmpty())
                    .replace("{required}", result.mod.versionRange.orEmpty())
            }
            guiGraphics.drawString(
                font,
                font.plainSubstrByWidth(status, (width - 14).coerceAtLeast(30)),
                left + 7,
                animatedTop + 15,
                0xC8C8C8,
                false,
            )

            if (!compact) {
                val description = result.mod.localizedDescription(languageCode, text.fallbackLanguage)
                font.split(Component.literal(description), (width - 14).coerceAtLeast(30)).take(2).forEachIndexed { line, value ->
                    guiGraphics.drawString(font, value, left + 7, animatedTop + 29 + line * 10, 0xAFAFAF, false)
                }
            }

            val totalButtonWidth = (width - 14).coerceAtMost(390)
            val buttonWidth = (totalButtonWidth - 4) / 2
            downloadButton.width = buttonWidth
            detailsButton.width = buttonWidth
            detailsButton.x = left + width - totalButtonWidth - 7
            detailsButton.y = animatedTop + if (compact) 28 else 56
            downloadButton.x = detailsButton.x + buttonWidth + 4
            downloadButton.y = detailsButton.y
            detailsButton.render(guiGraphics, mouseX, mouseY, partialTick)
            downloadButton.render(guiGraphics, mouseX, mouseY, partialTick)
        }

        private fun buttonLabel(): String = if (links.size <= 1) {
            text.downloadButton.replace("{mod}", result.mod.displayName())
        } else {
            text.chooseSourceButton.replace("{count}", links.size.toString())
        }

        private fun narrationText(): String {
            val category = if (result.mod.resolvedCategory() == ModCategory.REQUIRED) text.requiredLabel else text.recommendedLabel
            val status = if (result.status == RequirementStatus.MISSING) text.missingStatus else text.wrongVersionStatus
                .replace("{installed}", result.installedVersion.orEmpty())
                .replace("{required}", result.mod.versionRange.orEmpty())
            val description = result.mod.localizedDescription(languageCode, text.fallbackLanguage)
            return listOf(result.mod.displayName(), category, status, description).filter(String::isNotBlank).joinToString(". ")
        }
    }
}
