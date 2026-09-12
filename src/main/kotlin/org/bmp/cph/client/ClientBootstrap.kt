package org.bmp.cph.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge
import org.bmp.cph.Cph
import org.bmp.cph.config.ConfigManager
import org.bmp.cph.config.MenuTextResolver

object ClientBootstrap {
    private var handledThisLaunch = false

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onScreenOpening)
        Cph.LOGGER.info("CoolPackHelper client menu registered")
    }

    private fun onScreenOpening(event: ScreenEvent.Opening) {
        if (handledThisLaunch) return
        val newScreen = event.newScreen
        if (newScreen !is TitleScreen) return

        handledThisLaunch = true
        val missing = MissingModDetector.findMissing(ConfigManager.config.requiredMods.orEmpty())
        if (missing.isEmpty() || !ConfigManager.shouldShowOnce()) return

        val language = Minecraft.getInstance().languageManager.selected
        val text = MenuTextResolver.resolve(ConfigManager.config.menu, language)
        event.newScreen = MissingModsScreen(newScreen, missing, text)
        Cph.LOGGER.info("Showing missing mods menu for {} mod(s)", missing.size)
    }
}
