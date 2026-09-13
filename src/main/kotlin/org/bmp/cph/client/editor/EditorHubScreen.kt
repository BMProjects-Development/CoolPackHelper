package org.bmp.cph.client.editor

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.client.MissingModDetector
import org.bmp.cph.client.MissingModsScreen
import org.bmp.cph.client.download.InstallationHistoryScreen
import org.bmp.cph.config.ConfigValidator
import org.bmp.cph.config.IssueSeverity
import org.bmp.cph.config.MenuTextResolver

class EditorHubScreen(
    parent: Screen,
    private val session: EditorSession = EditorSession.open(),
) : EditorScreenBase(tr("title"), parent) {
    private data class Card(
        val glyph: String,
        val title: Component,
        val description: Component,
        val action: () -> Unit,
    )

    private val cards = mutableListOf<Card>()
    private var cardBounds = emptyList<IntArray>()

    override fun init() {
        cards.clear()
        cards += Card("◇", tr("requirements"), tr("requirements.hint")) {
            previewRequirements()
        }
        cards += Card("⚙", tr("general"), tr("general.hint")) {
            minecraft?.setScreen(GeneralEditorScreen(this, session))
        }
        cards += Card("▦", tr("mods"), tr("mods.hint")) {
            minecraft?.setScreen(ModsEditorScreen(this, session))
        }
        cards += Card("文", tr("translations"), tr("translations.hint")) {
            minecraft?.setScreen(MenuTranslationsScreen(this, session))
        }
        cards += Card("M", tr("scan.modrinth"), tr("scan.modrinth.hint")) {
            minecraft?.setScreen(ScanScreen(this, session, ScanPlatform.MODRINTH))
        }
        cards += Card("C", tr("scan.curseforge"), tr("scan.curseforge.hint")) {
            minecraft?.setScreen(CurseForgeKeyScreen(this, session))
        }
        cards += Card("↶", tr("history"), tr("history.hint")) {
            minecraft?.setScreen(InstallationHistoryScreen(this))
        }

        val columns = if (width >= 620 || height < 360) 2 else 1
        val gap = 8
        val contentWidth = (width - 24).coerceAtMost(760)
        val cardWidth = (contentWidth - gap * (columns - 1)) / columns
        val availableHeight = height - 102
        val rows = (cards.size + columns - 1) / columns
        val cardHeight = ((availableHeight - gap * (rows - 1)) / rows).coerceIn(44, 72)
        val left = (width - contentWidth) / 2
        val top = 48
        cardBounds = cards.mapIndexed { index, _ ->
            val col = index % columns
            val row = index / columns
            intArrayOf(left + col * (cardWidth + gap), top + row * (cardHeight + gap), cardWidth, cardHeight)
        }
        cardBounds.forEachIndexed { index, bound ->
            addRenderableWidget(
                TechButton.builder(Component.literal("${cards[index].glyph}  ").append(cards[index].title)) { cards[index].action() }
                    .subtitle(cards[index].description)
                    .style(TechButtonStyle.CARD)
                    .bounds(bound[0], bound[1], bound[2], bound[3])
                    .build()
            )
        }

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
