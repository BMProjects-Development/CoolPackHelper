package org.bmp.cph.client.editor

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.components.Tooltip
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

        val wide = width >= 520
        val listWidth = if (wide) (width * .31).toInt().coerceIn(170, 220) else (width - 16).coerceAtLeast(120)
        val list = EditorHubNavigationList(
            minecraft ?: Minecraft.getInstance(), listWidth, (height - 72).coerceAtLeast(38), 41,
            (listWidth - 12).coerceAtLeast(100), cards,
        )
        list.x = 8
        addRenderableWidget(list)

        val close = TechButton.builder(tr("close")) { closeEditor() }
            .style(TechButtonStyle.GHOST).bounds(0, 0, compactButtonWidth(tr("close")), 18).build()
        val save = TechButton.builder(tr("save")) { save(); Unit }
            .style(TechButtonStyle.PRIMARY).bounds(0, 0, compactButtonWidth(tr("save"), 78), 18).build()
        addFooterActions(close, save)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr(if (session.dirty) "subtitle.unsaved" else "subtitle"))
        if (width < 520) return

        val navWidth = (width * .31).toInt().coerceIn(170, 220)
        val left = navWidth + 17
        val right = width - 9
        val top = 41
        val bottom = height - 34
        drawPanel(guiGraphics, left, top, right, bottom)

        val pack = session.config.pack
        val packName = pack?.name?.takeIf(String::isNotBlank) ?: tr("hub.untitled").string
        val lineWidth = (right - left - 24).coerceAtLeast(30)
        guiGraphics.drawString(font, font.plainSubstrByWidth(packName, lineWidth), left + 12, top + 12, EditorTheme.TEXT, false)
        val identity = listOfNotNull(pack?.id?.takeIf(String::isNotBlank), pack?.version?.takeIf(String::isNotBlank)).joinToString("  ·  ")
        if (identity.isNotEmpty()) guiGraphics.drawString(font, font.plainSubstrByWidth(identity, lineWidth), left + 12, top + 26, EditorTheme.TEXT_MUTED, false)

        val mods = session.config.activeModEntries().size
        val errors = ConfigValidator.validate(session.config).count { it.severity == IssueSeverity.ERROR }
        val statsTop = top + 52
        guiGraphics.fill(left + 12, statsTop - 8, right - 12, statsTop - 7, EditorTheme.BORDER_SOFT)
        guiGraphics.drawString(font, tr("hub.mods", mods), left + 12, statsTop, EditorTheme.TEXT_MUTED, false)
        val state = tr(if (errors == 0) "hub.ready" else "hub.errors", errors)
        guiGraphics.drawString(font, state, left + 12, statsTop + 16, if (errors == 0) 0xFF8DAA96.toInt() else 0xFFC58B91.toInt(), false)

        val hintY = (bottom - 34).coerceAtLeast(statsTop + 38)
        font.split(tr("hub.hint"), lineWidth).take(3).forEachIndexed { index, line ->
            guiGraphics.drawString(font, line, left + 12, hintY + index * 11, EditorTheme.TEXT_MUTED, false)
        }
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

private class EditorHubNavigationList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val rowWidth: Int,
    cards: List<EditorHubCard>,
) : ContainerObjectSelectionList<EditorHubNavigationList.NavigationEntry>(minecraft, width, height, top, 27) {
    init {
        cards.forEach { addEntry(NavigationEntry(it, rowWidth)) }
    }

    override fun getRowWidth(): Int = rowWidth
    override fun getScrollbarPosition(): Int = x + width - 7

    class NavigationEntry(card: EditorHubCard, width: Int) : Entry<NavigationEntry>() {
        private val button = TechButton.builder(Component.literal("${card.glyph}  ").append(card.title)) { card.action() }
            .tooltip(Tooltip.create(card.description))
            .style(TechButtonStyle.GHOST)
            .bounds(0, 0, width, 23)
            .build()

        override fun children(): List<GuiEventListener> = listOf(button)
        override fun narratables(): List<NarratableEntry> = listOf(button)

        override fun render(
            guiGraphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float,
        ) {
            button.x = left
            button.y = top + 1
            button.width = width
            button.render(guiGraphics, mouseX, mouseY, partialTick)
        }
    }
}
