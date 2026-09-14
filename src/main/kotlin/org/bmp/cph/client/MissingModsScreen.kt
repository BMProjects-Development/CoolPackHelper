package org.bmp.cph.client

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.toasts.SystemToast
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

    override fun init() {
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
        addFooterButtons()
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val required = results.count { it.mod.resolvedCategory() == ModCategory.REQUIRED }
        val recommended = results.size - required
        val summary = text.summary
            .replace("{required}", required.toString())
            .replace("{recommended}", recommended.toString())
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
        val continueButton = button(text.continueButton, ::onClose, TechButtonStyle.GHOST)
        val folderButton = button(text.openModsFolderButton, ::openModsFolder, TechButtonStyle.GHOST)
        val recheckButton = button(text.recheckButton, ::recheck)
        addFooterActions(continueButton, folderButton, recheckButton, bulkDownloadButton())
    }

    private fun bulkDownloadButton(): TechButton {
        val label = Component.translatable("cph.download.install_missing", results.size)
        return TechButton.builder(label) { openBulkDownload() }
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

    private fun button(label: String, action: () -> Unit, style: TechButtonStyle = TechButtonStyle.SECONDARY): TechButton {
        val message = Component.literal(label)
        return TechButton.builder(message) { action() }.style(style)
            .bounds(0, 0, compactButtonWidth(message, 62), 18).build()
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
