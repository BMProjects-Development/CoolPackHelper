package org.bmp.cph.client

import net.minecraft.client.gui.GuiGraphics
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
import org.bmp.cph.client.editor.RowAction
import org.bmp.cph.client.editor.StyledActionList

class DownloadSourcesScreen(
    parent: Screen,
    private val mod: RequiredMod,
    private val links: List<DownloadLink>,
    private val text: ResolvedMenuText,
) : EditorScreenBase(Component.literal(text.sourcesTitle.replace("{mod}", mod.displayName())), parent) {
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
            subtitleOf = { it.url.orEmpty() },
            accentOf = { 0xFF62D9FF.toInt() },
            actionsOf = { link ->
                val uri = validHttpUri(link.url)
                listOf(
                    RowAction(
                        label = { Component.literal(text.downloadButton) },
                        width = 82,
                        style = { TechButtonStyle.PRIMARY },
                        enabled = { uri != null },
                        tooltip = uri?.let { Component.literal(it.toString()) },
                    ) { if (uri != null) ConfirmLinkScreen.confirmLinkNow(this, uri, true) }
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
