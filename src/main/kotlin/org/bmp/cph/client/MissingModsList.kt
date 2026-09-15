package org.bmp.cph.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import org.bmp.cph.config.ModCategory
import org.bmp.cph.config.ResolvedMenuText
import org.bmp.cph.client.editor.TechButton
import org.bmp.cph.client.editor.TechButtonStyle
import org.bmp.cph.client.editor.InvisibleRowButton
import org.bmp.cph.client.editor.drawEditorRow
import org.bmp.cph.client.editor.fillRoundedRect

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
    if (compact || rowWidth < 460) 58 else 54,
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
        private val downloadButton = TechButton.builder(Component.literal("↓")) { onDownload(result) }
            .style(TechButtonStyle.PRIMARY)
            .tooltip(Tooltip.create(Component.literal(downloadTooltip())))
            .bounds(0, 0, 26, 18)
            .build()
        private val rowButton = InvisibleRowButton(Component.literal(narrationText())) { onDetails(result) }.also {
            it.setTooltip(Tooltip.create(Component.translatable("cph.requirements.details.hint")))
        }

        init {
            downloadButton.active = links.isNotEmpty()
        }

        override fun children(): List<GuiEventListener> = listOf(downloadButton, rowButton)

        override fun narratables(): List<NarratableEntry> = listOf(downloadButton, rowButton)

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
            drawEditorRow(guiGraphics, left, animatedTop, width, height + 2, hovered, accent)

            val categoryLabel = if (category == ModCategory.REQUIRED) text.requiredLabel else text.recommendedLabel
            val categoryWidth = font.width(categoryLabel)
            val narrow = width < 460
            val iconSize = if (result.mod.iconUrl.isNullOrBlank()) 0 else 28
            val textLeft = left + 15 + if (iconSize == 0) 0 else iconSize + 7
            if (iconSize > 0) {
                val iconY = animatedTop + 6
                fillRoundedRect(guiGraphics, left + 14, iconY, left + 14 + iconSize, iconY + iconSize, 4, 0xFF272A31.toInt())
                ProjectIconCache.texture(result.mod.iconUrl)?.let { icon ->
                    guiGraphics.blit(
                        icon.location, left + 14, iconY, iconSize, iconSize, 0f, 0f,
                        icon.width, icon.height, icon.width, icon.height,
                    )
                }
            }
            val downloadWidth = 26
            val actionsWidth = downloadWidth
            val reservedActions = actionsWidth + 15
            val nameWidth = (width - categoryWidth - 15 - (textLeft - left) - reservedActions).coerceAtLeast(30)
            val name = font.plainSubstrByWidth(result.mod.displayName(), nameWidth)
            guiGraphics.drawString(font, name, textLeft, animatedTop + 3, 0xFFFFFF, false)
            guiGraphics.drawString(font, categoryLabel, left + width - categoryWidth - 7, animatedTop + 3, accent, false)

            val status = when (result.status) {
                RequirementStatus.MISSING -> text.missingStatus
                RequirementStatus.WRONG_VERSION -> text.wrongVersionStatus
                    .replace("{installed}", result.installedVersion.orEmpty())
                    .replace("{required}", result.mod.versionRange.orEmpty())
            }
            guiGraphics.drawString(
                font,
                font.plainSubstrByWidth(status, (width - (textLeft - left) - 7).coerceAtLeast(30)),
                textLeft,
                animatedTop + 15,
                0xC8C8C8,
                false,
            )

            if (!compact && !narrow) {
                val description = result.mod.localizedDescription(languageCode, text.fallbackLanguage)
                guiGraphics.drawString(
                    font,
                    font.plainSubstrByWidth(description, (width - (textLeft - left) - actionsWidth - 18).coerceAtLeast(30)),
                    textLeft,
                    animatedTop + 28,
                    0xAFAFAF,
                    false,
                )
            }

            if (hovered) guiGraphics.drawString(font, Component.literal("›"), left + width - actionsWidth - 17, animatedTop + height / 2 - 4, accent, false)
            downloadButton.width = downloadWidth
            downloadButton.x = left + width - downloadWidth - 7
            downloadButton.y = animatedTop + (height - 20) / 2
            downloadButton.render(guiGraphics, mouseX, mouseY, partialTick)
            rowButton.x = left
            rowButton.y = animatedTop
            // Do not cover the download control: overlapping invisible widgets
            // otherwise compete for clicks and show different tooltips.
            rowButton.width = width - downloadWidth - 7
            rowButton.height = height - 2
            rowButton.render(guiGraphics, mouseX, mouseY, partialTick)
        }

        private fun downloadActionLabel(): String = if (links.size <= 1) {
            text.downloadButton.replace("{mod}", result.mod.displayName())
        } else {
            text.chooseSourceButton.replace("{count}", links.size.toString())
        }

        private fun downloadTooltip(): String = buildList {
            add(downloadActionLabel())
            addAll(links.map { it.displayLabel() })
        }.joinToString("\n")

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
