package org.bmp.cph.client.editor

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.Minecraft
import org.bmp.cph.config.ConfigIssue
import org.bmp.cph.config.ConfigManager
import org.bmp.cph.config.MenuTextResolver

class UnsavedChangesScreen(
    parent: Screen,
    private val destination: Screen,
    private val saveAction: () -> Unit,
) : EditorScreenBase(tr("unsaved.title"), parent) {
    override fun init() {
        val buttonWidth = (width - 40).coerceAtMost(360)
        val x = (width - buttonWidth) / 2
        val y = height / 2 - 2
        addRenderableWidget(TechButton.builder(tr("unsaved.save")) { saveAction() }.style(TechButtonStyle.PRIMARY).bounds(x, y, buttonWidth, 20).build())
        addRenderableWidget(TechButton.builder(tr("unsaved.discard")) { minecraft?.setScreen(destination) }.style(TechButtonStyle.DANGER).bounds(x, y + 26, buttonWidth, 20).build())
        addRenderableWidget(TechButton.builder(tr("cancel")) { minecraft?.setScreen(previousScreen) }.style(TechButtonStyle.GHOST).bounds(x, y + 52, buttonWidth, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics)
        font.split(tr("unsaved.message"), (width - 40).coerceAtMost(520)).forEachIndexed { index, line ->
            guiGraphics.drawCenteredString(font, line, width / 2, height / 2 - 42 + index * 11, 0xAFC0D1)
        }
    }
}

class EditorValidationScreen(
    parent: Screen,
    private val issues: List<ConfigIssue>,
) : EditorScreenBase(tr("validation.title"), parent) {
    private var page = 0
    private val menuText by lazy {
        MenuTextResolver.resolve(ConfigManager.config.menu, Minecraft.getInstance().languageManager.selected)
    }

    override fun init() {
        val pageCount = ((issues.size + 4) / 5).coerceAtLeast(1)
        page = page.coerceIn(0, pageCount - 1)
        val w = (width - 32).coerceAtMost(520)
        val x = (width - w) / 2
        if (pageCount > 1) {
            val half = (w - 6) / 2
            val previous = TechButton.builder(tr("previous")) { page--; rebuildWidgets() }.bounds(x, height - 53, half, 20).build()
            previous.active = page > 0
            addRenderableWidget(previous)
            val next = TechButton.builder(tr("next")) { page++; rebuildWidgets() }.bounds(x + half + 6, height - 53, half, 20).build()
            next.active = page < pageCount - 1
            addRenderableWidget(next)
        }
        addRenderableWidget(TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST).bounds(x, height - 27, w, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("validation.hint"))
        val w = (width - 32).coerceAtMost(620)
        val x = (width - w) / 2
        issues.drop(page * 5).take(5).forEachIndexed { index, issue ->
            val y = 50 + index * 35
            drawPanel(guiGraphics, x, y, x + w, y + 29)
            guiGraphics.drawString(font, issue.path, x + 8, y + 5, 0xFF7777, false)
            guiGraphics.drawString(font, font.plainSubstrByWidth(issue.localized(menuText), w - 16), x + 8, y + 17, 0xB8C3CE, false)
        }
    }
}
