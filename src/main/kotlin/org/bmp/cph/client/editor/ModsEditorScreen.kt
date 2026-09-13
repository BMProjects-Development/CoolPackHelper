package org.bmp.cph.client.editor

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.config.RequiredMod

class ModsEditorScreen(
    parent: Screen,
    private val session: EditorSession,
    private var page: Int = 0,
) : EditorScreenBase(tr("mods.title"), parent) {
    private var pageSize = 1
    private var visible = emptyList<Pair<Int, RequiredMod>>()
    private var rows = emptyList<IntArray>()

    override fun init() {
        val mods = session.config.activeModEntries()
        pageSize = ((height - 116) / 34).coerceAtLeast(1)
        val pages = ((mods.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        page = page.coerceIn(0, pages - 1)
        visible = mods.withIndex().drop(page * pageSize).take(pageSize).map { it.index to it.value }
        val contentWidth = (width - 24).coerceAtMost(720)
        val left = (width - contentWidth) / 2
        rows = visible.indices.map { intArrayOf(left, 48 + it * 34, contentWidth, 29) }
        visible.forEachIndexed { row, (index, mod) ->
            val bounds = rows[row]
            val deleteWidth = 24
            addRenderableWidget(
                TechButton.builder(tr("mods.edit")) { minecraft?.setScreen(ModEditorScreen(this, session, index)) }
                    .bounds(bounds[0] + bounds[2] - 82, bounds[1] + 5, 52, 20).build()
            )
            addRenderableWidget(
                TechButton.builder(Component.literal("×")) {
                    val mutable = session.config.activeModEntries().toMutableList()
                    if (index in mutable.indices) mutable.removeAt(index)
                    session.replaceMods(mutable)
                    rebuildWidgets()
                }.style(TechButtonStyle.DANGER).bounds(bounds[0] + bounds[2] - deleteWidth, bounds[1] + 5, deleteWidth, 20).build()
            )
        }

        val footerWidth = (width - 24).coerceAtMost(720)
        val footerX = (width - footerWidth) / 2
        val navigationY = height - 53
        val quarter = (footerWidth - 18) / 4
        val previous = TechButton.builder(tr("previous")) { page--; rebuildWidgets() }
            .bounds(footerX, navigationY, quarter, 20).build()
        previous.active = page > 0
        addRenderableWidget(previous)
        addRenderableWidget(
            TechButton.builder(tr("mods.add")) {
                val mutable = session.config.activeModEntries().toMutableList()
                mutable += RequiredMod(enabled = false, descriptions = linkedMapOf(), links = emptyList())
                session.replaceMods(mutable)
                minecraft?.setScreen(ModEditorScreen(this, session, mutable.lastIndex))
            }.bounds(footerX + quarter + 6, navigationY, quarter, 20).build()
        )
        addRenderableWidget(
            TechButton.builder(tr("mods.import_local")) {
                minecraft?.setScreen(LocalImportScreen(this, session))
            }.bounds(footerX + (quarter + 6) * 2, navigationY, quarter, 20).build()
        )
        val next = TechButton.builder(tr("next")) { page++; rebuildWidgets() }
            .bounds(footerX + (quarter + 6) * 3, navigationY, quarter, 20).build()
        next.active = page < pages - 1
        addRenderableWidget(next)
        addRenderableWidget(TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST).bounds(footerX, height - 27, footerWidth, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("mods.count", session.config.activeModEntries().size))
        visible.forEachIndexed { row, (_, mod) ->
            val b = rows[row]
            val hovered = mouseX in b[0]..(b[0] + b[2]) && mouseY in b[1]..(b[1] + b[3])
            drawPanel(guiGraphics, b[0], b[1], b[0] + b[2], b[1] + b[3], hovered)
            val color = if (mod.enabled == false) 0x758397 else 0xE6F4FF
            guiGraphics.drawString(font, font.plainSubstrByWidth(mod.displayName(), b[2] - 130), b[0] + 8, b[1] + 6, color, false)
            val detector = mod.modId?.takeIf(String::isNotBlank) ?: mod.filePattern.orEmpty()
            guiGraphics.drawString(font, font.plainSubstrByWidth(detector, b[2] - 130), b[0] + 8, b[1] + 17, 0x8294A8, false)
        }
        if (visible.isEmpty()) guiGraphics.drawCenteredString(font, tr("mods.empty"), width / 2, height / 2, 0x91A4B8)
    }

    override fun onClose() {
        super.onClose()
    }
}
