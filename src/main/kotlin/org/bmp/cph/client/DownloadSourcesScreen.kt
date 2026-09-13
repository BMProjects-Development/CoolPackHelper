package org.bmp.cph.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.RequiredMod
import org.bmp.cph.config.ResolvedMenuText
import org.bmp.cph.config.validHttpUri
import org.bmp.cph.client.editor.EditorScreenBase
import org.bmp.cph.client.editor.TechButton
import org.bmp.cph.client.editor.TechButtonStyle

class DownloadSourcesScreen(
    parent: Screen,
    private val mod: RequiredMod,
    private val links: List<DownloadLink>,
    private val text: ResolvedMenuText,
) : EditorScreenBase(Component.literal(text.sourcesTitle.replace("{mod}", mod.displayName())), parent) {
    private var page = 0
    private var pageSize = 1

    override fun init() {
        pageSize = ((height - 116) / 24).coerceAtLeast(1)
        val pages = pageCount()
        page = page.coerceIn(0, pages - 1)
        val buttonWidth = (width - 32).coerceIn(100, 360)
        val x = (width - buttonWidth) / 2
        val firstY = 54

        links.drop(page * pageSize).take(pageSize).forEachIndexed { index, link ->
            val uri = validHttpUri(link.url) ?: return@forEachIndexed
            addRenderableWidget(
                TechButton.builder(Component.literal(link.displayLabel())) {
                    ConfirmLinkScreen.confirmLinkNow(this, uri, true)
                }.style(TechButtonStyle.PRIMARY)
                    .bounds(x, firstY + index * 24, buttonWidth, 20)
                    .tooltip(Tooltip.create(Component.literal(uri.toString())))
                    .build()
            )
        }

        if (pages > 1) {
            val half = (buttonWidth - 6) / 2
            val previous = TechButton.builder(Component.literal(text.previousButton)) {
                page--
                rebuildWidgets()
            }.bounds(x, height - 51, half, 20).build()
            previous.active = page > 0
            addRenderableWidget(previous)
            val next = TechButton.builder(Component.literal(text.nextButton)) {
                page++
                rebuildWidgets()
            }.bounds(x + half + 6, height - 51, half, 20).build()
            next.active = page < pages - 1
            addRenderableWidget(next)
        }

        addRenderableWidget(
            TechButton.builder(Component.literal(text.continueButton)) { onClose() }
                .style(TechButtonStyle.GHOST)
                .bounds(x, height - 27, buttonWidth, 20)
                .build()
        )
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val indicator = if (pageCount() > 1) text.pageIndicator
            .replace("{current}", (page + 1).toString())
            .replace("{total}", pageCount().toString()) else mod.displayName()
        drawHeader(guiGraphics, Component.literal(indicator))
    }

    private fun pageCount(): Int = ((links.size + pageSize - 1) / pageSize).coerceAtLeast(1)
}
