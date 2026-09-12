package org.bmp.cph.client

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.fml.loading.FMLPaths
import org.bmp.cph.Cph
import org.bmp.cph.config.ConfigManager
import org.bmp.cph.config.MenuTextResolver
import org.bmp.cph.config.ModCategory
import org.bmp.cph.config.ResolvedMenuText
import org.bmp.cph.config.validHttpUri
import java.nio.file.Files

class MissingModsScreen(
    parent: Screen,
    private val results: List<ModCheckResult>,
    private val text: ResolvedMenuText,
    private var selectedTab: RequirementTab = RequirementTab.ALL,
    private val markPolicyOnClose: Boolean = false,
) : AnimatedScreen(Component.literal(text.title), parent) {
    private var compactHeader = false
    private var listTop = 64
    private var listBottom = 100

    override fun init() {
        compactHeader = height < 280
        listTop = if (compactHeader) 60 else 82
        val wideFooter = width >= 560
        val footerTop = height - if (wideFooter) 34 else 58
        listBottom = footerTop
        val listHeight = (footerTop - listTop).coerceAtLeast(40)
        val listWidth = (width - 16).coerceAtLeast(120)
        val rowWidth = (listWidth - 18).coerceIn(100, 720)
        addTabs(if (compactHeader) 36 else 58)
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
        addFooterButtons(wideFooter)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        val offset = slideOffset()
        guiGraphics.drawCenteredString(font, title, width / 2, 10 + offset, animatedColor(0xFFFFFF))

        val required = results.count { it.mod.resolvedCategory() == ModCategory.REQUIRED }
        val recommended = results.size - required
        val summary = text.summary
            .replace("{required}", required.toString())
            .replace("{recommended}", recommended.toString())
        if (compactHeader) {
            guiGraphics.drawCenteredString(font, summary, width / 2, 23 + offset, animatedColor(0xB8B8B8))
        } else {
            font.split(Component.literal(text.description), (width - 32).coerceAtLeast(40)).take(2).forEachIndexed { index, line ->
                guiGraphics.drawCenteredString(font, line, width / 2, 27 + index * 10 + offset, animatedColor(0xB8B8B8))
            }
            guiGraphics.drawCenteredString(font, summary, width / 2, 51 + offset, animatedColor(0xA0A0A0))
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

    private fun addFooterButtons(wide: Boolean) {
        if (wide) {
            val totalWidth = (width - 24).coerceAtMost(720)
            val buttonWidth = (totalWidth - 12) / 3
            val startX = (width - totalWidth) / 2
            val y = height - 27
            addRenderableWidget(button(text.recheckButton, startX, y, buttonWidth, ::recheck))
            addRenderableWidget(button(text.openModsFolderButton, startX + buttonWidth + 6, y, buttonWidth, ::openModsFolder))
            addRenderableWidget(button(text.continueButton, startX + (buttonWidth + 6) * 2, y, buttonWidth) { onClose() })
        } else {
            val totalWidth = (width - 20).coerceAtMost(400)
            val half = (totalWidth - 6) / 2
            val startX = (width - totalWidth) / 2
            addRenderableWidget(button(text.recheckButton, startX, height - 51, half, ::recheck))
            addRenderableWidget(button(text.openModsFolderButton, startX + half + 6, height - 51, half, ::openModsFolder))
            addRenderableWidget(button(text.continueButton, width / 2 - totalWidth / 2, height - 27, totalWidth) { onClose() })
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
            val button = Button.builder(Component.literal(label)) {
                selectedTab = tab
                rebuildWidgets()
            }.bounds(startX + index * (tabWidth + 4), y, tabWidth, 20).build()
            button.active = selectedTab != tab
            addRenderableWidget(button)
        }
    }

    private fun button(label: String, x: Int, y: Int, width: Int, action: () -> Unit): Button =
        Button.builder(Component.literal(label)) { action() }.bounds(x, y, width, 20).build()

    private fun openDownload(result: ModCheckResult) {
        val links = result.mod.availableLinks().filter { validHttpUri(it.url) != null }
        if (links.size == 1) {
            ConfirmLinkScreen.confirmLinkNow(this, validHttpUri(links.first().url)!!, true)
        } else if (links.isNotEmpty()) {
            minecraft?.setScreen(DownloadSourcesScreen(this, result.mod, links, text))
        }
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
