package org.bmp.cph.client.editor

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.client.MissingModDetector
import org.bmp.cph.client.MissingModsScreen
import org.bmp.cph.client.download.InstallationHistoryScreen
import org.bmp.cph.config.ConfigValidator
import org.bmp.cph.config.IssueSeverity
import org.bmp.cph.config.MenuTextResolver

private data class EditorHubCard(
    val glyph: String,
    val title: Component,
    val description: Component,
    val action: () -> Unit,
)

class EditorHubScreen(
    parent: Screen,
    private val session: EditorSession = EditorSession.open(),
) : EditorScreenBase(tr("title"), parent) {
    private val cards = mutableListOf<EditorHubCard>()

    override fun init() {
        cards.clear()
        cards += EditorHubCard("◇", tr("requirements"), tr("requirements.hint")) {
            previewRequirements()
        }
        cards += EditorHubCard("⚙", tr("general"), tr("general.hint")) {
            minecraft?.setScreen(GeneralEditorScreen(this, session))
        }
        cards += EditorHubCard("▦", tr("mods"), tr("mods.hint")) {
            minecraft?.setScreen(ModsEditorScreen(this, session))
        }
        cards += EditorHubCard("文", tr("translations"), tr("translations.hint")) {
            minecraft?.setScreen(MenuTranslationsScreen(this, session))
        }
        cards += EditorHubCard("M", tr("scan.modrinth"), tr("scan.modrinth.hint")) {
            minecraft?.setScreen(ScanScreen(this, session, ScanPlatform.MODRINTH))
        }
        cards += EditorHubCard("C", tr("scan.curseforge"), tr("scan.curseforge.hint")) {
            minecraft?.setScreen(CurseForgeKeyScreen(this, session))
        }
        cards += EditorHubCard("↶", tr("history"), tr("history.hint")) {
            minecraft?.setScreen(InstallationHistoryScreen(this))
        }

        val columns = if (width >= 620) 2 else 1
        val listWidth = (width - 16).coerceAtLeast(120)
        val list = EditorHubCardsList(
            minecraft ?: Minecraft.getInstance(), listWidth, (height - 83).coerceAtLeast(38), 49,
            (listWidth - 18).coerceIn(100, 760), columns, cards,
        )
        list.x = 8
        addRenderableWidget(list)

        val footerWidth = (width - 24).coerceAtMost(500)
        val half = (footerWidth - 8) / 2
        val footerX = (width - footerWidth) / 2
        addRenderableWidget(
            TechButton.builder(tr("save")) { save(); Unit }
                .style(TechButtonStyle.PRIMARY)
                .bounds(footerX, height - 28, half, 20).build()
        )
        addRenderableWidget(
            TechButton.builder(tr("close")) { closeEditor() }
                .style(TechButtonStyle.GHOST)
                .bounds(footerX + half + 8, height - 28, half, 20).build()
        )
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr(if (session.dirty) "subtitle.unsaved" else "subtitle"))
    }

    override fun onClose() = closeEditor()

    private fun save(): Boolean {
        val issues = session.save()
        val errors = issues.filter { it.severity == IssueSeverity.ERROR }
        if (errors.isNotEmpty()) {
            minecraft?.setScreen(EditorValidationScreen(this, session, errors))
            return false
        }
        Minecraft.getInstance().let {
            SystemToast.add(it.toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, tr("saved"), tr("saved.hint"))
        }
        rebuildWidgets()
        return true
    }

    private fun previewRequirements() {
        val issues = ConfigValidator.validate(session.config).filter { it.severity == IssueSeverity.ERROR }
        if (issues.isNotEmpty()) {
            minecraft?.setScreen(EditorValidationScreen(this, session, issues))
            return
        }
        val language = Minecraft.getInstance().languageManager.selected
        val text = MenuTextResolver.resolve(session.config.menu, language)
        val results = MissingModDetector.findUnsatisfied(session.config.activeModEntries())
        minecraft?.setScreen(MissingModsScreen(this, results, text))
    }

    private fun closeEditor() {
        if (session.dirty) minecraft?.setScreen(UnsavedChangesScreen(this, previousScreen) {
            if (save()) minecraft?.setScreen(previousScreen)
        })
        else super.onClose()
    }
}

private class EditorHubCardsList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val rowWidth: Int,
    columns: Int,
    cards: List<EditorHubCard>,
) : ContainerObjectSelectionList<EditorHubCardsList.CardRow>(minecraft, width, height, top, 76) {
    init {
        cards.chunked(columns).forEach { addEntry(CardRow(it)) }
    }

    override fun getRowWidth(): Int = rowWidth
    override fun getScrollbarPosition(): Int = x + width - 7

    class CardRow(cards: List<EditorHubCard>) : Entry<CardRow>() {
        private val entries = cards.map { card ->
            TechButton.builder(Component.literal("${card.glyph}  ").append(card.title)) { card.action() }
                .subtitle(card.description)
                .style(TechButtonStyle.CARD)
                .bounds(0, 0, 100, 68)
                .build()
        }

        override fun children(): List<GuiEventListener> = entries
        override fun narratables(): List<NarratableEntry> = entries

        override fun render(
            guiGraphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float,
        ) {
            val gap = 8
            val cardWidth = ((width - gap * (entries.size - 1)) / entries.size).coerceAtLeast(40)
            entries.forEachIndexed { cardIndex, button ->
                button.x = left + cardIndex * (cardWidth + gap)
                button.y = top + 2
                button.width = cardWidth
                button.height = (height - 8).coerceAtLeast(38)
                button.render(guiGraphics, mouseX, mouseY, partialTick)
            }
        }
    }
}
