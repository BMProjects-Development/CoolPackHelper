package org.bmp.cph.client.editor

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import org.bmp.cph.config.ModCategory
import org.bmp.cph.config.RequiredMod

class ModEditorScreen(
    parent: Screen,
    private val session: EditorSession,
    private val index: Int,
    private val working: RequiredMod = session.config.activeModEntries().getOrNull(index)?.copyForEditor() ?: RequiredMod(),
) : EditorScreenBase(tr("mod.title"), parent) {
    private lateinit var nameField: EditBox
    private lateinit var idField: EditBox
    private lateinit var versionField: EditBox
    private lateinit var patternField: EditBox
    private var labels = emptyList<Pair<EditBox, String>>()

    override fun init() {
        val contentWidth = (width - 24).coerceAtMost(650)
        val left = (width - contentWidth) / 2
        val twoColumns = width >= 430 || height < 290
        val fieldWidth = if (twoColumns) (contentWidth - 10) / 2 else contentWidth
        val right = left + contentWidth - fieldWidth
        nameField = field(left, 60, fieldWidth, working.name.orEmpty())
        idField = field(if (twoColumns) right else left, if (twoColumns) 60 else 98, fieldWidth, working.modId.orEmpty())
        versionField = field(left, if (twoColumns) 98 else 136, fieldWidth, working.versionRange.orEmpty())
        patternField = field(if (twoColumns) right else left, if (twoColumns) 98 else 174, fieldWidth, working.filePattern.orEmpty())
        labels = listOf(
            nameField to "mod.name",
            idField to "mod.id",
            versionField to "mod.version",
            patternField to "mod.pattern",
        )

        val actionY = if (twoColumns) 136 else 212
        val half = (contentWidth - 8) / 2
        val enabled = TechButton.builder(enabledText()) { button ->
            working.enabled = working.enabled == false
            button.message = enabledText()
        }.bounds(left, actionY, half, 20).build()
        addRenderableWidget(enabled)
        val category = TechButton.builder(categoryText()) { button ->
            working.category = if (working.resolvedCategory() == ModCategory.REQUIRED) ModCategory.RECOMMENDED.name else ModCategory.REQUIRED.name
            button.message = categoryText()
        }.bounds(left + half + 8, actionY, half, 20).build()
        addRenderableWidget(category)
        val third = (contentWidth - 12) / 3
        addRenderableWidget(
            TechButton.builder(tr("mod.descriptions", working.descriptions.orEmpty().size)) {
                commitFields()
                minecraft?.setScreen(DescriptionsEditorScreen(this, working))
            }.bounds(left, actionY + 27, third, 20).build()
        )
        addRenderableWidget(
            TechButton.builder(tr("mod.links", working.links.orEmpty().size)) {
                commitFields()
                minecraft?.setScreen(LinksEditorScreen(this, working))
            }.bounds(left + third + 6, actionY + 27, third, 20).build()
        )
        addRenderableWidget(
            TechButton.builder(tr("mod.metadata")) {
                commitFields()
                minecraft?.setScreen(ModMetadataEditorScreen(this, working))
            }.bounds(left + (third + 6) * 2, actionY + 27, third, 20).build()
        )
        addRenderableWidget(
            TechButton.builder(tr("save_back")) { saveAndClose() }.style(TechButtonStyle.PRIMARY)
                .bounds(left, height - 27, contentWidth, 20).build()
        )
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("mod.subtitle"))
        labels.forEach { (field, key) -> guiGraphics.drawString(font, tr(key), field.x, field.y - 11, 0x90A7BC, false) }
    }

    override fun onClose() {
        super.onClose()
    }

    private fun field(x: Int, y: Int, width: Int, value: String): EditBox = EditBox(font, x, y, width, 20, tr("field")).also {
        it.setMaxLength(1024)
        it.value = value
        addRenderableWidget(it)
    }

    private fun enabledText() = tr(if (working.enabled == false) "mod.disabled" else "mod.enabled")

    private fun categoryText() = tr("mod.category", tr("category.${working.resolvedCategory().name.lowercase()}"))

    private fun commitFields() {
        working.name = nameField.value.trim()
        working.modId = idField.value.trim()
        working.versionRange = versionField.value.trim()
        working.filePattern = patternField.value.trim()
    }

    private fun saveAndClose() {
        commitFields()
        val mods = session.config.activeModEntries().toMutableList()
        if (index in mods.indices) mods[index] = working else mods += working
        session.replaceMods(mods)
        onClose()
    }
}
