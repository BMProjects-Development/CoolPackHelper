package org.bmp.cph.client

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.Cph
import org.bmp.cph.config.ConfigIssue
import org.bmp.cph.config.ConfigManager
import org.bmp.cph.config.MenuTextResolver
import org.bmp.cph.config.ResolvedMenuText
import java.nio.file.Files
import org.bmp.cph.client.editor.EditorScreenBase
import org.bmp.cph.client.editor.EditorHubScreen
import org.bmp.cph.client.editor.EditorIssueNavigator
import org.bmp.cph.client.editor.EditorSession
import org.bmp.cph.client.editor.TechButton
import org.bmp.cph.client.editor.TechButtonStyle

class ConfigErrorScreen(
    parent: Screen,
    private val issues: List<ConfigIssue>,
    private val text: ResolvedMenuText,
    private val markPolicyOnResolvedScreen: Boolean = false,
) : EditorScreenBase(Component.literal(text.configErrorTitle), parent) {
    private val editorSession by lazy(EditorSession::open)
    private val editorRoot by lazy { EditorHubScreen(this, editorSession) }

    override fun init() {
        val listTop = 75
        val footerTop = height - 30
        val listWidth = (width - 16).coerceAtLeast(120)
        val list = ConfigIssuesList(
            minecraft ?: Minecraft.getInstance(),
            listWidth,
            (footerTop - listTop).coerceAtLeast(40),
            listTop,
            (listWidth - 18).coerceIn(100, 720),
            issues,
            text,
            canOpen = { true },
            onOpen = { issue ->
                val destination = EditorIssueNavigator.destination(editorRoot, editorSession, issue)
                if (destination == null) openConfigFolder() else minecraft?.setScreen(destination)
            },
            openHint = Component.translatable("cph.editor.validation.click_hint"),
        )
        list.x = 8
        addRenderableWidget(list)
        addFooterButtons()
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val offset = slideOffset()
        drawHeader(guiGraphics)
        font.split(Component.literal(text.configErrorDescription), (width - 32).coerceAtLeast(40)).take(2).forEachIndexed { index, line ->
            guiGraphics.drawCenteredString(font, line, width / 2, 49 + index * 10 + offset, animatedColor(0x93A7BB))
        }
    }

    private fun addFooterButtons() {
        addFooterActions(
            button(text.continueButton, { onClose() }, TechButtonStyle.GHOST),
            button(text.openConfigFolderButton, ::openConfigFolder, TechButtonStyle.GHOST),
            button(text.recheckButton, ::recheck, TechButtonStyle.PRIMARY),
        )
    }

    private fun button(label: String, action: () -> Unit, style: TechButtonStyle = TechButtonStyle.SECONDARY): TechButton {
        val message = Component.literal(label)
        return TechButton.builder(message) { action() }.style(style)
            .bounds(0, 0, compactButtonWidth(message, 62), 18).build()
    }

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
