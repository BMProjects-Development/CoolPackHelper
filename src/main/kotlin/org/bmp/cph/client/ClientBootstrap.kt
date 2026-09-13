package org.bmp.cph.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.neoforged.fml.ModList
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.client.gui.ModListScreen
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge
import org.bmp.cph.Cph
import org.bmp.cph.client.editor.EditorHubScreen
import org.bmp.cph.client.editor.TechButton
import org.bmp.cph.client.editor.TechButtonStyle
import org.bmp.cph.client.editor.tr
import org.bmp.cph.config.ConfigManager
import org.bmp.cph.config.MenuTextResolver

object ClientBootstrap {
    private var handledThisLaunch = false

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onScreenOpening)
        NeoForge.EVENT_BUS.addListener(::onScreenInitialized)
        ModList.get().getModContainerById(Cph.ID).ifPresent { container ->
            container.registerExtensionPoint(
                IConfigScreenFactory::class.java,
                IConfigScreenFactory { _, parent -> EditorHubScreen(parent) },
            )
        }
        Cph.LOGGER.info("CoolPackHelper client menu registered")
    }

    private fun onScreenInitialized(event: ScreenEvent.Init.Post) {
        val screen = event.screen
        if (screen !is ModListScreen) return
        val x = (screen.width / 3 + 12).coerceAtLeast(112)
        val totalWidth = (screen.width - x - 8).coerceAtMost(430)
        val buttonWidth = (totalWidth - 6) / 2
        val y = screen.height - 52
        event.addListener(
            TechButton.builder(tr("requirements")) {
                Minecraft.getInstance().setScreen(createRequirementsScreen(screen))
            }.style(TechButtonStyle.GHOST).bounds(x, y, buttonWidth, 20).build()
        )
        event.addListener(
            TechButton.builder(tr("title")) {
                Minecraft.getInstance().setScreen(EditorHubScreen(screen))
            }.style(TechButtonStyle.PRIMARY).bounds(x + buttonWidth + 6, y, buttonWidth, 20).build()
        )
    }

    fun createRequirementsScreen(parent: Screen): Screen {
        ConfigManager.load()
        val language = Minecraft.getInstance().languageManager.selected
        val text = MenuTextResolver.resolve(ConfigManager.config.menu, language)
        if (ConfigManager.hasErrors()) return ConfigErrorScreen(parent, ConfigManager.validationIssues, text)
        val results = MissingModDetector.findUnsatisfied(ConfigManager.config.activeModEntries())
        return MissingModsScreen(parent, results, text)
    }

    private fun onScreenOpening(event: ScreenEvent.Opening) {
        if (handledThisLaunch) return
        val newScreen = event.newScreen
        if (newScreen !is TitleScreen) return

        handledThisLaunch = true
        val language = Minecraft.getInstance().languageManager.selected
        val text = MenuTextResolver.resolve(ConfigManager.config.menu, language)
        if (ConfigManager.hasErrors()) {
            event.newScreen = ConfigErrorScreen(newScreen, ConfigManager.validationIssues, text, true)
            Cph.LOGGER.warn("Showing CoolPackHelper config error screen")
            return
        }

        val missing = MissingModDetector.findUnsatisfied(ConfigManager.config.activeModEntries())
        if (missing.isEmpty() || !ConfigManager.shouldShowMenu()) return

        event.newScreen = MissingModsScreen(newScreen, missing, text, markPolicyOnClose = true)
        Cph.LOGGER.info("Showing missing mods menu for {} mod(s)", missing.size)
    }
}
