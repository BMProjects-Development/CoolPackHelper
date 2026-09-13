package org.bmp.cph.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.ResolvedMenuText
import org.bmp.cph.client.download.SecureDownloadScreen
import org.bmp.cph.client.editor.EditorScreenBase
import org.bmp.cph.client.editor.TechButton
import org.bmp.cph.client.editor.TechButtonStyle
import org.bmp.cph.client.editor.RowAction
import org.bmp.cph.client.editor.RowBadge
import org.bmp.cph.client.editor.StyledActionList

class DownloadSourcesScreen(
    parent: Screen,
    private val result: ModCheckResult,
    private val links: List<DownloadLink>,
    private val text: ResolvedMenuText,
) : EditorScreenBase(Component.literal(text.sourcesTitle.replace("{mod}", result.mod.displayName())), parent) {
    override fun init() {
        val buttonWidth = (width - 32).coerceIn(100, 360)
        val x = (width - buttonWidth) / 2
        val listWidth = (width - 16).coerceAtLeast(120)
        val list = StyledActionList(
            minecraft ?: net.minecraft.client.Minecraft.getInstance(),
            listWidth,
            (height - 83).coerceAtLeast(38),
            49,
            (listWidth - 18).coerceIn(100, 620),
            40,
            links,
            titleOf = { it.displayLabel() },
            subtitleOf = { it.downloadUrl?.takeIf(String::isNotBlank) ?: it.url.orEmpty() },
            accentOf = { 0xFF62D9FF.toInt() },
            badgeOf = { RowBadge(Component.literal(it.resolvedType().name), 0xFF62D9FF.toInt()) },
            iconOf = { result.mod.iconUrl },
            actionsOf = { link ->
                listOf(
                    RowAction(
                        label = { Component.literal(text.downloadButton) },
                        width = 82,
                        style = { TechButtonStyle.PRIMARY },
                        tooltip = Component.literal(link.downloadUrl ?: link.url.orEmpty()),
                    ) { minecraft?.setScreen(SecureDownloadScreen.forSource(this, result, link)) }
                )
            },
        )
        list.x = 8
        addRenderableWidget(list)

        addRenderableWidget(
            TechButton.builder(Component.literal(text.continueButton)) { onClose() }
                .style(TechButtonStyle.GHOST)
                .bounds(x, height - 27, buttonWidth, 20)
                .build()
        )
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val indicator = text.chooseSourceButton.replace("{count}", links.size.toString())
        drawHeader(guiGraphics, Component.literal(indicator))
    }
}
