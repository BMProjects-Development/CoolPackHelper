package org.bmp.cph.client

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.fml.loading.FMLPaths
import org.bmp.cph.Cph
import org.bmp.cph.config.ConfigManager
import org.bmp.cph.config.MenuTextResolver
import org.bmp.cph.config.ModCategory
import org.bmp.cph.config.ResolvedMenuText
import org.bmp.cph.client.download.SecureDownloadScreen
import org.bmp.cph.client.editor.EditorScreenBase
import org.bmp.cph.client.editor.TechButton
import org.bmp.cph.client.editor.TechButtonStyle
import java.nio.file.Files

class MissingModsScreen(
    parent: Screen,
    private val results: List<ModCheckResult>,
    private val text: ResolvedMenuText,
    private var selectedTab: RequirementTab = RequirementTab.ALL,
    private val markPolicyOnClose: Boolean = false,
) : EditorScreenBase(Component.literal(text.title), parent) {
    private var compactHeader = false
    private var listTop = 64
    private var listBottom = 100
    private var expanded = !markPolicyOnClose || Minecraft.getInstance().window.isFullscreen
    private var modalLeft = 0
    private var modalTop = 0
    private var modalRight = 0
    private var modalBottom = 0
    private var lastFullscreen = Minecraft.getInstance().window.isFullscreen
    private var lastTitleClickAt = 0L

    override fun usesModalBackground(): Boolean = !expanded
    override fun modalScrimColor(): Int = 0x42080A0D

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        val fullscreen = minecraft.window.isFullscreen
        if (fullscreen != lastFullscreen) {
            lastFullscreen = fullscreen
            expanded = fullscreen
        }
        super.resize(minecraft, width, height)
    }

    override fun init() {
        if (!expanded) {
            initCompactWindow()
            return
        }
        compactHeader = height < 280
        listTop = if (compactHeader) 72 else 98
        val footerTop = height - 30
        listBottom = footerTop
        val listHeight = (footerTop - listTop).coerceAtLeast(40)
        val listWidth = (width - 16).coerceAtLeast(120)
        val rowWidth = (listWidth - 18).coerceIn(100, 720)
        addTabs(if (compactHeader) 47 else 73)
        val visibleResults = filteredResults()
        val list = MissingModsList(
            minecraft ?: Minecraft.getInstance(),
            listWidth,
            listHeight,
            listTop,
            rowWidth,
            listHeight < 76,
            visibleResults,
            text,
            text.languageCode,
            ::openDownload,
            ::openDetails,
            ::transitionProgress,
        )
        list.x = 8
        addRenderableWidget(list)
        addWindowControls(0, width, 1)
        addFooterButtons()
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val required = results.count { it.mod.resolvedCategory() == ModCategory.REQUIRED }
        val recommended = results.size - required
        val summary = text.summary
            .replace("{required}", required.toString())
            .replace("{recommended}", recommended.toString())
        if (!expanded) {
            drawPanel(guiGraphics, modalLeft, modalTop, modalRight, modalBottom)
            val textWidth = (modalRight - modalLeft - 72).coerceAtLeast(40)
            guiGraphics.drawString(font, font.plainSubstrByWidth(text.title, textWidth), modalLeft + 10, modalTop + 9, animatedColor(0xF0F1F3), false)
            guiGraphics.drawString(font, font.plainSubstrByWidth(summary, modalRight - modalLeft - 20), modalLeft + 10, modalTop + 27, animatedColor(0x9AA2AD), false)
            guiGraphics.fill(modalLeft + 10, modalTop + 43, modalRight - 10, modalTop + 44, animatedAlphaColor(0x5530343C))
            val preview = results.take(2)
            preview.forEachIndexed { index, result ->
                val name = "• ${result.mod.displayName()}"
                guiGraphics.drawString(
                    font,
                    font.plainSubstrByWidth(name, textWidth),
                    modalLeft + 10,
                    modalTop + 52 + index * 12,
                    animatedColor(0xC9CDD3),
                    false,
                )
            }
            val remaining = results.size - preview.size
            if (remaining > 0) {
                guiGraphics.drawString(
                    font,
                    Component.translatable("cph.requirements.more", remaining),
                    modalLeft + 10,
                    modalTop + 78,
                    animatedColor(0x7F8996),
                    false,
                )
            }
            return
        }
        drawHeader(guiGraphics, Component.literal(summary))
        if (!compactHeader) {
            font.split(Component.literal(text.description), (width - 32).coerceAtLeast(40)).take(2).forEachIndexed { index, line ->
                guiGraphics.drawCenteredString(font, line, width / 2, 49 + index * 10 + slideOffset(6), animatedColor(0x8298AD))
            }
        }
        if (filteredResults().isEmpty()) {
            val message = if (results.isEmpty()) text.allResolvedMessage else text.emptyTabMessage
            guiGraphics.drawCenteredString(font, message, width / 2, (listTop + listBottom) / 2, animatedColor(0xAFAFAF))
        }
    }

    override fun onClose() {
        if (markPolicyOnClose) ConfigManager.markMenuShown()
        super.onClose()
    }

    private fun addFooterButtons() {
        addFooterActions(
            utilityButton("↗", Component.translatable("cph.requirements.folder.hint"), ::openModsFolder),
            utilityButton("↻", Component.translatable("cph.requirements.recheck.hint"), ::recheck),
            bulkDownloadButton(),
        )
    }

    private fun initCompactWindow() {
        val modalWidth = (width - 44).coerceAtMost(340).coerceAtLeast(250)
        val modalHeight = (height - 40).coerceAtMost(150).coerceAtLeast(132)
        modalLeft = (width - modalWidth) / 2
        modalTop = (height - modalHeight) / 2 + slideOffset(8)
        modalRight = modalLeft + modalWidth
        modalBottom = modalTop + modalHeight

        addWindowControls(modalLeft, modalRight, modalTop)
        addCompactActions(
            modalLeft + 8,
            modalRight - 8,
            modalBottom - 23,
            utilityButton("↗", Component.translatable("cph.requirements.folder.hint"), ::openModsFolder),
            utilityButton("↻", Component.translatable("cph.requirements.recheck.hint"), ::recheck),
            bulkDownloadButton(compact = true),
        )
    }

    private fun addWindowControls(left: Int, right: Int, top: Int) {
        val close = utilityButton("×", Component.translatable("cph.requirements.close.hint"), ::onClose)
        close.x = right - 25
        close.y = top + 5
        addRenderableWidget(close)

        val toggle = utilityButton(
            if (expanded) "—" else "□",
            Component.translatable(if (expanded) "cph.requirements.compact.hint" else "cph.requirements.expand.hint"),
            ::toggleExpanded,
        )
        toggle.x = right - 48
        toggle.y = top + 5
        addRenderableWidget(toggle)
    }

    private fun utilityButton(glyph: String, tooltip: Component, action: () -> Unit): TechButton =
        TechButton.builder(Component.literal(glyph)) { action() }
            .style(TechButtonStyle.GHOST)
            .tooltip(Tooltip.create(tooltip))
            .bounds(0, 0, 20, 18)
            .build()

    private fun toggleExpanded() {
        expanded = !expanded
        rebuildWidgets()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val inTitle = if (expanded) {
                mouseY >= 0 && mouseY < 34 && mouseX >= 6 && mouseX < width - 54
            } else {
                mouseY >= modalTop && mouseY < modalTop + 34 && mouseX >= modalLeft + 6 && mouseX < modalRight - 54
            }
            if (inTitle) {
                val now = Util.getMillis()
                if (now - lastTitleClickAt <= 320L) {
                    lastTitleClickAt = 0L
                    toggleExpanded()
                    return true
                }
                lastTitleClickAt = now
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun bulkDownloadButton(compact: Boolean = false): TechButton {
        val label = if (compact) Component.translatable("cph.download.install", results.size)
        else Component.translatable("cph.download.install_missing")
        return TechButton.builder(label) { openBulkDownload() }
            .tooltip(Tooltip.create(Component.translatable("cph.requirements.install.hint")))
            .style(TechButtonStyle.PRIMARY).bounds(0, 0, compactButtonWidth(label, 86), 18).build().also {
                it.active = results.any { result -> result.mod.availableLinks().isNotEmpty() }
            }
    }

    private fun addTabs(y: Int) {
        val totalWidth = (width - 20).coerceAtMost(600)
        val tabWidth = (totalWidth - 8) / 3
        val startX = (width - totalWidth) / 2
        val labels = listOf(
            RequirementTab.ALL to "${text.allTab} (${results.size})",
            RequirementTab.REQUIRED to "${text.requiredTab} (${results.count { it.mod.resolvedCategory() == ModCategory.REQUIRED }})",
            RequirementTab.RECOMMENDED to "${text.recommendedTab} (${results.count { it.mod.resolvedCategory() == ModCategory.RECOMMENDED }})",
        )
        labels.forEachIndexed { index, (tab, label) ->
            val button = TechButton.builder(Component.literal(label)) {
                selectedTab = tab
                rebuildWidgets()
            }.style(if (selectedTab == tab) TechButtonStyle.PRIMARY else TechButtonStyle.GHOST)
                .bounds(startX + index * (tabWidth + 4), y, tabWidth, 18).build()
            addRenderableWidget(button)
        }
    }

    private fun openDownload(result: ModCheckResult) {
        val links = result.mod.availableLinks()
        if (links.size == 1) {
            minecraft?.setScreen(SecureDownloadScreen.forSource(this, result, links.first()))
        } else if (links.isNotEmpty()) {
            minecraft?.setScreen(DownloadSourcesScreen(this, result, links, text))
        }
    }

    private fun openBulkDownload() {
        minecraft?.setScreen(SecureDownloadScreen.forMissing(this, results))
    }

    private fun openDetails(result: ModCheckResult) {
        minecraft?.setScreen(ModDetailsScreen(this, result, text))
    }

    private fun openModsFolder() {
        try {
            val path = FMLPaths.MODSDIR.get()
            Files.createDirectories(path)
            Util.getPlatform().openPath(path)
        } catch (exception: Exception) {
            Cph.LOGGER.error("Could not open the mods directory", exception)
        }
    }

    private fun recheck() {
        ConfigManager.load()
        val language = Minecraft.getInstance().languageManager.selected
        val refreshedText = MenuTextResolver.resolve(ConfigManager.config.menu, language)
        if (ConfigManager.hasErrors()) {
            minecraft?.setScreen(ConfigErrorScreen(previousScreen, ConfigManager.validationIssues, refreshedText, markPolicyOnClose))
            return
        }

        val refreshed = MissingModDetector.findUnsatisfied(ConfigManager.config.activeModEntries())
        if (refreshed.isEmpty()) {
            minecraft?.setScreen(previousScreen)
            minecraft?.let {
                SystemToast.add(
                    it.toasts,
                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal(refreshedText.title),
                    Component.literal(refreshedText.allResolvedMessage),
                )
            }
        } else {
            minecraft?.setScreen(MissingModsScreen(previousScreen, refreshed, refreshedText, selectedTab, markPolicyOnClose))
        }
    }

    private fun filteredResults(): List<ModCheckResult> = when (selectedTab) {
        RequirementTab.ALL -> results
        RequirementTab.REQUIRED -> results.filter { it.mod.resolvedCategory() == ModCategory.REQUIRED }
        RequirementTab.RECOMMENDED -> results.filter { it.mod.resolvedCategory() == ModCategory.RECOMMENDED }
    }
}

enum class RequirementTab {
    ALL,
    REQUIRED,
    RECOMMENDED,
}
