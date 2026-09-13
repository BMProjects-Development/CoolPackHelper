package org.bmp.cph.client.editor

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.config.RequiredMod

class DescriptionsEditorScreen(
    parent: Screen,
    private val mod: RequiredMod,
    private var page: Int = 0,
) : EditorScreenBase(tr("descriptions.title", mod.displayName()), parent) {
    override fun init() {
        val entries = mod.descriptions.orEmpty().entries.toList()
        val pageSize = ((height - 118) / 30).coerceAtLeast(1)
        val pages = ((entries.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        page = page.coerceIn(0, pages - 1)
        val visible = entries.drop(page * pageSize).take(pageSize)
        val w = (width - 24).coerceAtMost(650)
        val x = (width - w) / 2
        visible.forEachIndexed { row, entry ->
            val y = 47 + row * 30
            addRenderableWidget(
                TechButton.builder(Component.literal("${entry.key} · ${font.plainSubstrByWidth(entry.value, w - 145)}")) {
                    minecraft?.setScreen(DescriptionEntryEditorScreen(this, mod, entry.key))
                }.bounds(x, y, w - 30, 20).build()
            )
            addRenderableWidget(
                TechButton.builder(Component.literal("×")) {
                    mod.descriptions = mod.descriptions.orEmpty().toMutableMap().also { it.remove(entry.key) }
                    rebuildWidgets()
                }.style(TechButtonStyle.DANGER).bounds(x + w - 24, y, 24, 20).build()
            )
        }
        val quarter = (w - 18) / 4
        val navY = height - 53
        val previous = TechButton.builder(tr("previous")) { page--; rebuildWidgets() }.bounds(x, navY, quarter, 20).build()
        previous.active = page > 0
        addRenderableWidget(previous)
        addRenderableWidget(
            TechButton.builder(tr("descriptions.add")) {
                minecraft?.setScreen(DescriptionEntryEditorScreen(this, mod, null))
            }.bounds(x + quarter + 6, navY, quarter * 2 + 6, 20).build()
        )
        val next = TechButton.builder(tr("next")) { page++; rebuildWidgets() }
            .bounds(x + (quarter + 6) * 3, navY, quarter, 20).build()
        next.active = page < pages - 1
        addRenderableWidget(next)
        addRenderableWidget(TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST).bounds(x, height - 27, w, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("descriptions.subtitle"))
        if (mod.descriptions.orEmpty().isEmpty()) guiGraphics.drawCenteredString(font, tr("descriptions.empty"), width / 2, height / 2, 0x91A4B8)
    }

    override fun onClose() {
        super.onClose()
    }
}

class DescriptionEntryEditorScreen(
    parent: Screen,
    private val mod: RequiredMod,
    private val originalLocale: String?,
) : EditorScreenBase(tr("description.title"), parent) {
    private lateinit var localeField: EditBox
    private lateinit var textField: MultiLineEditBox

    override fun init() {
        val w = (width - 30).coerceAtMost(650)
        val x = (width - w) / 2
        localeField = EditBox(font, x, 70, w, 20, tr("description.locale")).also {
            it.value = originalLocale ?: "en_us"; it.setMaxLength(32); addRenderableWidget(it)
        }
        textField = MultiLineEditBox(font, x, 116, w, (height - 158).coerceAtLeast(42), tr("description.text"), tr("description.text")).also {
            it.value = originalLocale?.let { code -> mod.descriptions.orEmpty()[code] }.orEmpty()
            it.setCharacterLimit(8192)
            addRenderableWidget(it)
        }
        addRenderableWidget(TechButton.builder(tr("save_back")) { save() }.style(TechButtonStyle.PRIMARY).bounds(x, height - 27, w, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("description.subtitle"))
        guiGraphics.drawString(font, tr("description.locale"), localeField.x, localeField.y - 12, 0x90A7BC, false)
        guiGraphics.drawString(font, tr("description.text"), textField.x, textField.y - 12, 0x90A7BC, false)
    }

    private fun save() {
        val locale = localeField.value.trim().lowercase()
        if (locale.isNotBlank()) {
            val mutable = mod.descriptions.orEmpty().toMutableMap()
            originalLocale?.let(mutable::remove)
            mutable[locale] = textField.value
            mod.descriptions = mutable
        }
        onClose()
    }
}
