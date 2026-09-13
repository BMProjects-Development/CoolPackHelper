package org.bmp.cph.client.editor

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.config.RequiredMod

class ModsEditorScreen(
    parent: Screen,
    private val session: EditorSession,
) : EditorScreenBase(tr("mods.title"), parent) {
    override fun init() {
        val mods = session.config.activeModEntries()
        val contentWidth = (width - 24).coerceAtMost(720)
        val left = (width - contentWidth) / 2
        val listWidth = (width - 16).coerceAtLeast(120)
        val listTop = 49
        val listBottom = height - 60
        val indexedMods = mods.withIndex().map { it.index to it.value }
        val list = StyledActionList(
            minecraft ?: net.minecraft.client.Minecraft.getInstance(),
            listWidth,
            (listBottom - listTop).coerceAtLeast(38),
            listTop,
            (listWidth - 18).coerceIn(100, 720),
            42,
            indexedMods,
            titleOf = { it.second.displayName() },
            subtitleOf = { it.second.modId?.takeIf(String::isNotBlank) ?: it.second.filePattern.orEmpty() },
            accentOf = { (_, mod) ->
                when {
                    mod.enabled == false -> 0xFF647386.toInt()
                    mod.resolvedCategory() == org.bmp.cph.config.ModCategory.REQUIRED -> 0xFFE46A6A.toInt()
                    else -> 0xFFE0B85B.toInt()
                }
            },
            actionsOf = { (index, _) ->
                listOf(
                    RowAction(label = { tr("mods.edit") }, width = 58) {
                        minecraft?.setScreen(ModEditorScreen(this, session, index))
                    },
                    RowAction(label = { Component.literal("×") }, width = 25, style = { TechButtonStyle.DANGER }) {
                        val mutable = session.config.activeModEntries().toMutableList()
                        if (index in mutable.indices) mutable.removeAt(index)
                        session.replaceMods(mutable)
                        rebuildWidgets()
                    },
                )
            },
        )
        list.x = 8
        addRenderableWidget(list)

        val footerWidth = (width - 24).coerceAtMost(720)
        val footerX = (width - footerWidth) / 2
        val navigationY = height - 53
        val half = (footerWidth - 6) / 2
        addRenderableWidget(
            TechButton.builder(tr("mods.add")) {
                val mutable = session.config.activeModEntries().toMutableList()
                mutable += RequiredMod(enabled = false, descriptions = linkedMapOf(), links = emptyList())
                session.replaceMods(mutable)
                minecraft?.setScreen(ModEditorScreen(this, session, mutable.lastIndex))
            }.style(TechButtonStyle.PRIMARY).bounds(footerX, navigationY, half, 20).build()
        )
        addRenderableWidget(
            TechButton.builder(tr("mods.import_local")) {
                minecraft?.setScreen(LocalImportScreen(this, session))
            }.bounds(footerX + half + 6, navigationY, half, 20).build()
        )
        addRenderableWidget(TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST).bounds(footerX, height - 27, footerWidth, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("mods.count", session.config.activeModEntries().size))
        if (session.config.activeModEntries().isEmpty()) guiGraphics.drawCenteredString(font, tr("mods.empty"), width / 2, height / 2, 0x91A4B8)
    }

    override fun onClose() {
        super.onClose()
    }
}
