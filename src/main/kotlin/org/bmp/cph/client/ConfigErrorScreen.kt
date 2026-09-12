package org.bmp.cph.client

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.Cph
import org.bmp.cph.config.ConfigIssue
import org.bmp.cph.config.ConfigManager
import org.bmp.cph.config.MenuTextResolver
import org.bmp.cph.config.ResolvedMenuText
import java.nio.file.Files

class ConfigErrorScreen(
    parent: Screen,
    private val issues: List<ConfigIssue>,
    private val text: ResolvedMenuText,
    private val markPolicyOnResolvedScreen: Boolean = false,
) : AnimatedScreen(Component.literal(text.configErrorTitle), parent) {
    override fun init() {
        val wide = width >= 560
        val listTop = 52
        val footerTop = height - if (wide) 34 else 58
        val listWidth = (width - 16).coerceAtLeast(120)
        val list = ConfigIssuesList(
            minecraft ?: Minecraft.getInstance(),
            listWidth,
            (footerTop - listTop).coerceAtLeast(40),
            listTop,
            (listWidth - 18).coerceIn(100, 720),
            issues,
            text,
        )
        list.x = 8
        addRenderableWidget(list)
        addFooterButtons(wide)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        val offset = slideOffset()
        guiGraphics.drawCenteredString(font, title, width / 2, 12 + offset, animatedColor(0xE46A6A))
        font.split(Component.literal(text.configErrorDescription), (width - 32).coerceAtLeast(40)).take(2).forEachIndexed { index, line ->
            guiGraphics.drawCenteredString(font, line, width / 2, 29 + index * 10 + offset, animatedColor(0xC8C8C8))
        }
    }

    private fun addFooterButtons(wide: Boolean) {
        if (wide) {
            val totalWidth = (width - 24).coerceAtMost(720)
            val buttonWidth = (totalWidth - 12) / 3
            val startX = (width - totalWidth) / 2
            addRenderableWidget(button(text.recheckButton, startX, height - 27, buttonWidth, ::recheck))
            addRenderableWidget(button(text.openConfigFolderButton, startX + buttonWidth + 6, height - 27, buttonWidth, ::openConfigFolder))
            addRenderableWidget(button(text.continueButton, startX + (buttonWidth + 6) * 2, height - 27, buttonWidth) { onClose() })
        } else {
            val totalWidth = (width - 20).coerceAtMost(400)
            val half = (totalWidth - 6) / 2
            val startX = (width - totalWidth) / 2
            addRenderableWidget(button(text.recheckButton, startX, height - 51, half, ::recheck))
            addRenderableWidget(button(text.openConfigFolderButton, startX + half + 6, height - 51, half, ::openConfigFolder))
            addRenderableWidget(button(text.continueButton, startX, height - 27, totalWidth) { onClose() })
        }
    }

    private fun button(label: String, x: Int, y: Int, width: Int, action: () -> Unit): Button =
        Button.builder(Component.literal(label)) { action() }.bounds(x, y, width, 20).build()

    private fun openConfigFolder() {
        try {
            Files.createDirectories(ConfigManager.configDirectory)
            Util.getPlatform().openPath(ConfigManager.configDirectory)
        } catch (exception: Exception) {
            Cph.LOGGER.error("Could not open the config directory", exception)
        }
    }

    private fun recheck() {
        ConfigManager.load()
        val language = Minecraft.getInstance().languageManager.selected
        val refreshedText = MenuTextResolver.resolve(ConfigManager.config.menu, language)
        if (ConfigManager.hasErrors()) {
            minecraft?.setScreen(ConfigErrorScreen(previousScreen, ConfigManager.validationIssues, refreshedText, markPolicyOnResolvedScreen))
            return
        }

        val missing = MissingModDetector.findUnsatisfied(ConfigManager.config.activeModEntries())
        if (missing.isEmpty()) {
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
            minecraft?.setScreen(MissingModsScreen(previousScreen, missing, refreshedText, markPolicyOnClose = markPolicyOnResolvedScreen))
        }
    }
}
