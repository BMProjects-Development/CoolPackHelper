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
        val cancelText = tr("cancel")
        val discardText = tr("unsaved.discard")
        val saveText = tr("unsaved.save")
        val buttons = listOf(
            TechButton.builder(cancelText) { minecraft?.setScreen(previousScreen) }.style(TechButtonStyle.GHOST)
                .bounds(0, 0, compactButtonWidth(cancelText), 18).build(),
            TechButton.builder(discardText) { minecraft?.setScreen(destination) }.style(TechButtonStyle.DANGER)
                .bounds(0, 0, compactButtonWidth(discardText, 76), 18).build(),
            TechButton.builder(saveText) { saveAction() }.style(TechButtonStyle.PRIMARY)
                .bounds(0, 0, compactButtonWidth(saveText, 76), 18).build(),
        )
        val gap = 5
        val available = (width - 32).coerceAtLeast(90)
        val natural = buttons.sumOf { it.width } + gap * 2
        if (natural > available) buttons.forEach { it.width = ((available - gap * 2) / 3).coerceAtLeast(26) }
        var x = width / 2 - (buttons.sumOf { it.width } + gap * 2) / 2
        buttons.forEach { button ->
            button.x = x
            button.y = height / 2 + 15
            addRenderableWidget(button)
            x += button.width + gap
        }
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics)
        val panelWidth = (width - 28).coerceAtMost(470)
        drawPanel(guiGraphics, (width - panelWidth) / 2, height / 2 - 56, (width + panelWidth) / 2, height / 2 + 46)
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
            (height - 72).coerceAtLeast(38),
            41,
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
        val back = TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("back")), 18).build()
        addFooterActions(back)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("validation.hint"))
    }
}
