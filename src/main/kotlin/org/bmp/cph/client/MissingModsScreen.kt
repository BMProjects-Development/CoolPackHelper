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
    private val parent: Screen,
    private val results: List<ModCheckResult>,
    private val text: ResolvedMenuText,
) : Screen(Component.literal(text.title)) {
    private var compactHeader = false

    override fun init() {
        compactHeader = height < 220
        val listTop = if (compactHeader) 40 else 64
        val wideFooter = width >= 560
        val footerTop = height - if (wideFooter) 34 else 58
        val listHeight = (footerTop - listTop).coerceAtLeast(40)
        val listWidth = (width - 16).coerceAtLeast(120)
        val rowWidth = (listWidth - 18).coerceIn(100, 720)
        val list = MissingModsList(
            minecraft ?: Minecraft.getInstance(),
            listWidth,
            listHeight,
            listTop,
            rowWidth,
            listHeight < 76,
            results,
            text,
            ConfigManager.config.menu,
            Minecraft.getInstance().languageManager.selected,
            ::openDownload,
        )
        list.x = 8
        addRenderableWidget(list)
        addFooterButtons(wideFooter)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF)

        val required = results.count { it.mod.resolvedCategory() == ModCategory.REQUIRED }
        val recommended = results.size - required
        val summary = text.summary
            .replace("{required}", required.toString())
            .replace("{recommended}", recommended.toString())
        if (compactHeader) {
            guiGraphics.drawCenteredString(font, summary, width / 2, 26, 0xB8B8B8)
        } else {
            font.split(Component.literal(text.description), (width - 32).coerceAtLeast(40)).take(2).forEachIndexed { index, line ->
                guiGraphics.drawCenteredString(font, line, width / 2, 27 + index * 10, 0xB8B8B8)
            }
            guiGraphics.drawCenteredString(font, summary, width / 2, 51, 0xA0A0A0)
        }
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

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
            minecraft?.setScreen(ConfigErrorScreen(parent, ConfigManager.validationIssues, refreshedText))
            return
        }

        val refreshed = MissingModDetector.findUnsatisfied(ConfigManager.config.activeModEntries())
        if (refreshed.isEmpty()) {
            minecraft?.setScreen(parent)
            minecraft?.let {
                SystemToast.add(
                    it.toasts,
                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal(refreshedText.title),
                    Component.literal(refreshedText.allResolvedMessage),
                )
            }
        } else {
            minecraft?.setScreen(MissingModsScreen(parent, refreshed, refreshedText))
        }
    }
}
