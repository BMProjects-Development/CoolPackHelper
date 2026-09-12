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
import org.bmp.cph.config.MenuConfig
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
    private val menuConfig: MenuConfig?,
    private val languageCode: String,
    private val onDownload: (ModCheckResult) -> Unit,
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

        override fun children(): List<GuiEventListener> = listOf(downloadButton)

        override fun narratables(): List<NarratableEntry> = listOf(downloadButton)

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
            val category = result.mod.resolvedCategory()
            val accent = if (category == ModCategory.REQUIRED) 0xFFE46A6A.toInt() else 0xFFE0B85B.toInt()
            val background = if (hovered) 0xAA303030.toInt() else 0x88303030.toInt()
            guiGraphics.fill(left, top, left + width, top + height, background)
            guiGraphics.fill(left, top, left + 2, top + height, accent)

            val categoryLabel = if (category == ModCategory.REQUIRED) text.requiredLabel else text.recommendedLabel
            val categoryWidth = font.width(categoryLabel)
            val nameWidth = (width - categoryWidth - 22).coerceAtLeast(30)
            val name = font.plainSubstrByWidth(result.mod.displayName(), nameWidth)
            guiGraphics.drawString(font, name, left + 7, top + 3, 0xFFFFFF, false)
            guiGraphics.drawString(font, categoryLabel, left + width - categoryWidth - 7, top + 3, accent, false)

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
                top + 15,
                0xC8C8C8,
                false,
            )

            if (!compact) {
                val defaultLanguage = menuConfig?.defaultLanguage.orEmpty()
                val description = result.mod.localizedDescription(languageCode, defaultLanguage)
                font.split(Component.literal(description), (width - 14).coerceAtLeast(30)).take(2).forEachIndexed { line, value ->
                    guiGraphics.drawString(font, value, left + 7, top + 29 + line * 10, 0xAFAFAF, false)
                }
            }

            val buttonWidth = (width - 14).coerceAtMost(190)
            downloadButton.width = buttonWidth
            downloadButton.x = left + width - buttonWidth - 7
            downloadButton.y = top + if (compact) 28 else 56
            downloadButton.render(guiGraphics, mouseX, mouseY, partialTick)
        }

        private fun buttonLabel(): String = if (links.size <= 1) {
            text.downloadButton.replace("{mod}", result.mod.displayName())
        } else {
            text.chooseSourceButton.replace("{count}", links.size.toString())
        }
    }
}
