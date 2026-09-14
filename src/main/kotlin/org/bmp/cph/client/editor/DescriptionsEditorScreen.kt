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
    private var dialog = EditorRect(0, 0, 0, 0)

    override fun usesModalBackground(): Boolean = true

    override fun init() {
        val entries = mod.descriptions.orEmpty().entries.toList()
        dialog = centeredModal(680, 350, 190)
        val listWidth = (dialog.width - 12).coerceAtLeast(120)
        val list = StyledActionList(
            minecraft ?: net.minecraft.client.Minecraft.getInstance(),
            listWidth,
            (dialog.height - 70).coerceAtLeast(38),
            dialog.top + 38,
            (listWidth - 10).coerceAtLeast(100),
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
        list.x = dialog.left + 6
        addRenderableWidget(list)
        val add = TechButton.builder(tr("descriptions.add")) {
                minecraft?.setScreen(DescriptionEntryEditorScreen(this, mod, null, onChanged))
            }.style(TechButtonStyle.PRIMARY).bounds(0, 0, compactButtonWidth(tr("descriptions.add")), 18).build()
        val back = TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("back")), 18).build()
        addCompactActions(dialog.left + 8, dialog.right - 8, dialog.bottom - 23, back, add)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawModalFrame(guiGraphics, dialog, tr("descriptions.subtitle"))
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
    private var dialog = EditorRect(0, 0, 0, 0)

    override fun usesModalBackground(): Boolean = true

    override fun init() {
        dialog = centeredModal(650, 360, 220)
        val innerLeft = dialog.left + 9
        val innerWidth = dialog.width - 18
        localeField = StableEditBox(font, innerLeft, dialog.top + 55, innerWidth, 18, tr("description.locale")).also {
            it.value = originalLocale ?: "en_us"; it.setMaxLength(32); addRenderableWidget(it)
        }
        textField = StableMultiLineEditBox(font, innerLeft, dialog.top + 91, innerWidth, (dialog.bottom - dialog.top - 124).coerceAtLeast(42), tr("description.text"), tr("description.text")).also {
            it.value = originalLocale?.let { code -> mod.descriptions.orEmpty()[code] }.orEmpty()
            it.setCharacterLimit(8192)
            addRenderableWidget(it)
        }
        val save = TechButton.builder(tr("save_back")) { save() }.style(TechButtonStyle.PRIMARY)
            .bounds(0, 0, compactButtonWidth(tr("save_back"), 90), 18).build()
        val cancel = TechButton.builder(tr("cancel")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("cancel")), 18).build()
        addCompactActions(dialog.left + 8, dialog.right - 8, dialog.bottom - 23, cancel, save)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawModalFrame(guiGraphics, dialog, tr("description.subtitle"))
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
