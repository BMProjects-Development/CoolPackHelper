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
) : EditorScreenBase(tr("links.title", mod.displayName()), parent) {
    private var page = 0

    override fun init() {
        val links = mod.links.orEmpty()
        val pageSize = ((height - 118) / 30).coerceAtLeast(1)
        val pages = ((links.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        page = page.coerceIn(0, pages - 1)
        val contentWidth = (width - 24).coerceAtMost(650)
        val left = (width - contentWidth) / 2
        links.withIndex().drop(page * pageSize).take(pageSize).forEachIndexed { row, indexed ->
            val index = indexed.index
            val link = indexed.value
            addRenderableWidget(
                TechButton.builder(Component.literal(link.displayLabel())) {
                    minecraft?.setScreen(LinkEntryEditorScreen(this, mod, index))
                }.bounds(left, 47 + row * 30, contentWidth - 30, 20).build()
            )
            addRenderableWidget(
                TechButton.builder(Component.literal("×")) {
                    val mutable = mod.links.orEmpty().toMutableList()
                    mutable.removeAt(index)
                    mod.links = mutable
                    rebuildWidgets()
                }.style(TechButtonStyle.DANGER).bounds(left + contentWidth - 24, 47 + row * 30, 24, 20).build()
            )
        }
        val quarter = (contentWidth - 18) / 4
        val navY = height - 53
        val previous = TechButton.builder(tr("previous")) { page--; rebuildWidgets() }.bounds(left, navY, quarter, 20).build()
        previous.active = page > 0
        addRenderableWidget(previous)
        addRenderableWidget(
            TechButton.builder(tr("links.add")) {
                mod.links = mod.links.orEmpty() + DownloadLink()
                minecraft?.setScreen(LinkEntryEditorScreen(this, mod, mod.links.orEmpty().lastIndex))
            }.bounds(left + quarter + 6, navY, quarter * 2 + 6, 20).build()
        )
        val next = TechButton.builder(tr("next")) { page++; rebuildWidgets() }
            .bounds(left + (quarter + 6) * 3, navY, quarter, 20).build()
        next.active = page < pages - 1
        addRenderableWidget(next)
        addRenderableWidget(TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST).bounds(left, height - 27, contentWidth, 20).build())
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
        onClose()
    }
}
