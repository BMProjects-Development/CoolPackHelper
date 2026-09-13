package org.bmp.cph.client.editor

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.Minecraft
import org.bmp.cph.client.ConfigIssuesList
import org.bmp.cph.config.ConfigIssue
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
    private val session: EditorSession,
    private val issues: List<ConfigIssue>,
) : EditorScreenBase(tr("validation.title"), parent) {
    private val menuText by lazy {
        MenuTextResolver.resolve(session.config.menu, Minecraft.getInstance().languageManager.selected)
    }

    override fun init() {
        val w = (width - 32).coerceAtMost(520)
        val x = (width - w) / 2
        val listWidth = (width - 16).coerceAtLeast(120)
        val list = ConfigIssuesList(
            minecraft ?: Minecraft.getInstance(),
            listWidth,
            (height - 83).coerceAtLeast(38),
            49,
            (listWidth - 18).coerceIn(100, 620),
            issues,
            menuText,
            canOpen = EditorIssueNavigator::canOpen,
            onOpen = { issue ->
                EditorIssueNavigator.destination(previousScreen, session, issue)?.let { minecraft?.setScreen(it) }
            },
            openHint = tr("validation.click_hint"),
        )
        list.x = 8
        addRenderableWidget(list)
        addRenderableWidget(TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST).bounds(x, height - 27, w, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("validation.hint"))
    }
}
