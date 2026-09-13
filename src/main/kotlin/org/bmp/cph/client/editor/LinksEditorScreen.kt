package org.bmp.cph.client.editor

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.RequiredMod

class LinksEditorScreen(
    parent: Screen,
    private val mod: RequiredMod,
    private val onChanged: () -> Unit = {},
) : EditorScreenBase(tr("links.title", mod.displayName()), parent) {
    override fun init() {
        val links = mod.links.orEmpty()
        val contentWidth = (width - 24).coerceAtMost(650)
        val left = (width - contentWidth) / 2
        val listWidth = (width - 16).coerceAtLeast(120)
        val indexedLinks = links.withIndex().map { it.index to it.value }
        val list = StyledActionList(
            minecraft ?: net.minecraft.client.Minecraft.getInstance(),
            listWidth,
            (height - 83).coerceAtLeast(38),
            49,
            (listWidth - 18).coerceIn(100, 650),
            40,
            indexedLinks,
            titleOf = { it.second.displayLabel() },
            subtitleOf = { it.second.url.orEmpty() },
            accentOf = { 0xFF9B7BFF.toInt() },
            actionsOf = { (index, _) -> listOf(
                RowAction(label = { tr("mods.edit") }, width = 58) {
                    minecraft?.setScreen(LinkEntryEditorScreen(this, mod, index, onChanged))
                },
                RowAction(label = { Component.literal("×") }, width = 25, style = { TechButtonStyle.DANGER }) {
                    val mutable = mod.links.orEmpty().toMutableList()
                    mutable.removeAt(index)
                    mod.links = mutable
                    onChanged()
                    rebuildWidgets()
                },
            ) },
        )
        list.x = 8
        addRenderableWidget(list)
        val half = (contentWidth - 6) / 2
        addRenderableWidget(
            TechButton.builder(tr("links.add")) {
                mod.links = mod.links.orEmpty() + DownloadLink()
                minecraft?.setScreen(LinkEntryEditorScreen(this, mod, mod.links.orEmpty().lastIndex, onChanged))
            }.style(TechButtonStyle.PRIMARY).bounds(left, height - 27, half, 20).build()
        )
        addRenderableWidget(TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST).bounds(left + half + 6, height - 27, half, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("links.subtitle"))
        if (mod.links.orEmpty().isEmpty()) guiGraphics.drawCenteredString(font, tr("links.empty"), width / 2, height / 2, 0x91A4B8)
    }

    override fun onClose() {
        super.onClose()
    }
}

class LinkEntryEditorScreen(
    parent: Screen,
    private val mod: RequiredMod,
    private val index: Int,
    private val onChanged: () -> Unit = {},
) : EditorScreenBase(tr("link.title"), parent) {
    private lateinit var labelField: EditBox
    private lateinit var urlField: EditBox

    override fun init() {
        val link = mod.links.orEmpty().getOrNull(index) ?: DownloadLink()
        val w = (width - 30).coerceAtMost(600)
        val x = (width - w) / 2
        labelField = EditBox(font, x, 78, w, 20, tr("link.label")).also {
            it.value = link.label.orEmpty(); it.setMaxLength(256); addRenderableWidget(it)
        }
        urlField = EditBox(font, x, 122, w, 20, tr("link.url")).also {
            it.value = link.url.orEmpty(); it.setMaxLength(2048); addRenderableWidget(it)
        }
        addRenderableWidget(TechButton.builder(tr("save_back")) { save() }.style(TechButtonStyle.PRIMARY).bounds(x, height - 27, w, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("link.subtitle"))
        guiGraphics.drawString(font, tr("link.label"), labelField.x, labelField.y - 12, 0x90A7BC, false)
        guiGraphics.drawString(font, tr("link.url"), urlField.x, urlField.y - 12, 0x90A7BC, false)
    }

    private fun save() {
        val mutable = mod.links.orEmpty().toMutableList()
        val edited = DownloadLink(labelField.value.trim(), urlField.value.trim())
        if (index in mutable.indices) mutable[index] = edited else mutable += edited
        mod.links = mutable
        onChanged()
        onClose()
    }
}
