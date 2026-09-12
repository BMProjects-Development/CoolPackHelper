package org.bmp.cph.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.RequiredMod
import org.bmp.cph.config.ResolvedMenuText
import org.bmp.cph.config.validHttpUri

class DownloadSourcesScreen(
    parent: Screen,
    private val mod: RequiredMod,
    private val links: List<DownloadLink>,
    private val text: ResolvedMenuText,
) : AnimatedScreen(Component.literal(text.sourcesTitle.replace("{mod}", mod.displayName())), parent) {
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
                Button.builder(Component.literal(link.displayLabel())) {
                    ConfirmLinkScreen.confirmLinkNow(this, uri, true)
                }.bounds(x, firstY + index * 24, buttonWidth, 20)
                    .tooltip(Tooltip.create(Component.literal(uri.toString())))
                    .build()
            )
        }

        if (pages > 1) {
            val half = (buttonWidth - 6) / 2
            val previous = Button.builder(Component.literal(text.previousButton)) {
                page--
                rebuildWidgets()
            }.bounds(x, height - 51, half, 20).build()
            previous.active = page > 0
            addRenderableWidget(previous)
            val next = Button.builder(Component.literal(text.nextButton)) {
                page++
                rebuildWidgets()
            }.bounds(x + half + 6, height - 51, half, 20).build()
            next.active = page < pages - 1
            addRenderableWidget(next)
        }

        addRenderableWidget(
            Button.builder(Component.literal(text.continueButton)) { onClose() }
                .bounds(x, height - 27, buttonWidth, 20)
                .build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        val offset = slideOffset()
        guiGraphics.drawCenteredString(font, title, width / 2, 14 + offset, animatedColor(0xFFFFFF))
        if (pageCount() > 1) {
            val indicator = text.pageIndicator
                .replace("{current}", (page + 1).toString())
                .replace("{total}", pageCount().toString())
            guiGraphics.drawCenteredString(font, indicator, width / 2, 34 + offset, animatedColor(0xAFAFAF))
        }
    }

    private fun pageCount(): Int = ((links.size + pageSize - 1) / pageSize).coerceAtLeast(1)
}
