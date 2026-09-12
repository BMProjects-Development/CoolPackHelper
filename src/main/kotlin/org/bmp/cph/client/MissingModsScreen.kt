package org.bmp.cph.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.config.RequiredMod
import org.bmp.cph.config.ResolvedMenuText
import java.net.URI

class MissingModsScreen(
    private val parent: Screen,
    private val missingMods: List<RequiredMod>,
    private val text: ResolvedMenuText,
) : Screen(Component.literal(text.title)) {
    private var page = 0
    private var pageSize = 1

    override fun init() {
        pageSize = ((height - 156) / 24).coerceIn(1, 8)
        val pageCount = pageCount()
        page = page.coerceIn(0, pageCount - 1)

        val buttonWidth = (width - 40).coerceAtMost(320)
        val x = (width - buttonWidth) / 2
        val firstButtonY = 82
        val firstIndex = page * pageSize
        val pageEntries = missingMods.drop(firstIndex).take(pageSize)

        pageEntries.forEachIndexed { index, mod ->
            val name = displayName(mod)
            val label = text.downloadButton.replace("{mod}", name)
            val url = validHttpUri(mod.downloadUrl)
            val button = Button.builder(Component.literal(label)) {
                if (url != null) ConfirmLinkScreen.confirmLinkNow(this, url, true)
            }.bounds(x, firstButtonY + index * 24, buttonWidth, 20)
                .tooltip(
                    Tooltip.create(
                        Component.literal(url?.toString() ?: "${text.invalidLink}: ${mod.downloadUrl.orEmpty()}")
                    )
                )
                .build()
            button.active = url != null
            addRenderableWidget(button)
        }

        val navigationY = height - 50
        if (pageCount > 1) {
            val navigationWidth = ((buttonWidth - 8) / 2).coerceAtMost(156)
            val previous = Button.builder(Component.literal(text.previousButton)) {
                page--
                rebuildWidgets()
            }.bounds(width / 2 - navigationWidth - 4, navigationY, navigationWidth, 20).build()
            previous.active = page > 0
            addRenderableWidget(previous)

            val next = Button.builder(Component.literal(text.nextButton)) {
                page++
                rebuildWidgets()
            }.bounds(width / 2 + 4, navigationY, navigationWidth, 20).build()
            next.active = page < pageCount - 1
            addRenderableWidget(next)
        }

        addRenderableWidget(
            Button.builder(Component.literal(text.continueButton)) { onClose() }
                .bounds(width / 2 - 100, height - 26, 200, 20)
                .build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 24, 0xFFFFFF)

        val descriptionLines = font.split(Component.literal(text.description), (width - 40).coerceAtLeast(40))
        descriptionLines.take(3).forEachIndexed { index, line ->
            guiGraphics.drawCenteredString(font, line, width / 2, 46 + index * 10, 0xB8B8B8)
        }

        if (pageCount() > 1) {
            val pageLabel = text.pageIndicator
                .replace("{current}", (page + 1).toString())
                .replace("{total}", pageCount().toString())
            guiGraphics.drawCenteredString(font, pageLabel, width / 2, height - 62, 0xA0A0A0)
        }
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    private fun pageCount(): Int = ((missingMods.size + pageSize - 1) / pageSize).coerceAtLeast(1)

    private fun displayName(mod: RequiredMod): String =
        mod.name?.takeIf { it.isNotBlank() }
            ?: mod.modId?.takeIf { it.isNotBlank() }
            ?: mod.filePattern?.takeIf { it.isNotBlank() }
            ?: "Unknown mod"

    private fun validHttpUri(value: String?): URI? = try {
        value?.trim()?.takeIf { it.isNotEmpty() }?.let(URI::create)?.takeIf {
            it.scheme.equals("https", ignoreCase = true) || it.scheme.equals("http", ignoreCase = true)
        }
    } catch (_: IllegalArgumentException) {
        null
    }
}
