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
    private val onChanged: () -> Unit = {},
) : EditorScreenBase(tr("descriptions.title", mod.displayName()), parent) {
    override fun init() {
        val entries = mod.descriptions.orEmpty().entries.toList()
        val w = (width - 24).coerceAtMost(650)
        val x = (width - w) / 2
        val listWidth = (width - 16).coerceAtLeast(120)
        val list = StyledActionList(
            minecraft ?: net.minecraft.client.Minecraft.getInstance(),
            listWidth,
            (height - 72).coerceAtLeast(38),
            41,
            (listWidth - 18).coerceIn(100, 650),
            36,
            entries,
            titleOf = { it.key },
            subtitleOf = { it.value },
            accentOf = { 0xFF62D9FF.toInt() },
            actionsOf = { entry -> listOf(
                RowAction(label = { tr("mods.edit") }, width = 58) {
                    minecraft?.setScreen(DescriptionEntryEditorScreen(this, mod, entry.key, onChanged))
                },
                RowAction(label = { Component.literal("×") }, width = 25, style = { TechButtonStyle.DANGER }) {
                    mod.descriptions = mod.descriptions.orEmpty().toMutableMap().also { it.remove(entry.key) }
                    onChanged()
                    rebuildWidgets()
                },
            ) },
        )
        list.x = 8
        addRenderableWidget(list)
        val add = TechButton.builder(tr("descriptions.add")) {
                minecraft?.setScreen(DescriptionEntryEditorScreen(this, mod, null, onChanged))
            }.style(TechButtonStyle.PRIMARY).bounds(0, 0, compactButtonWidth(tr("descriptions.add")), 18).build()
        val back = TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("back")), 18).build()
        addFooterActions(back, add)
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
    private val onChanged: () -> Unit = {},
) : EditorScreenBase(tr("description.title"), parent) {
    private lateinit var localeField: EditBox
    private lateinit var textField: MultiLineEditBox

    override fun init() {
        val w = (width - 30).coerceAtMost(650)
        val x = (width - w) / 2
        localeField = StableEditBox(font, x, 70, w, 20, tr("description.locale")).also {
            it.value = originalLocale ?: "en_us"; it.setMaxLength(32); addRenderableWidget(it)
        }
        textField = StableMultiLineEditBox(font, x, 116, w, (height - 158).coerceAtLeast(42), tr("description.text"), tr("description.text")).also {
            it.value = originalLocale?.let { code -> mod.descriptions.orEmpty()[code] }.orEmpty()
            it.setCharacterLimit(8192)
            addRenderableWidget(it)
        }
        val save = TechButton.builder(tr("save_back")) { save() }.style(TechButtonStyle.PRIMARY)
            .bounds(0, 0, compactButtonWidth(tr("save_back"), 90), 18).build()
        addFooterActions(save)
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
            onChanged()
        }
        onClose()
    }
}
