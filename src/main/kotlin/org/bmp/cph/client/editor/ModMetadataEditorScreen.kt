package org.bmp.cph.client.editor

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.config.RequiredMod

class ModMetadataEditorScreen(parent: Screen, private val mod: RequiredMod) :
    EditorScreenBase(tr("metadata.title"), parent) {
    override fun init() {
        val contentWidth = (width - 24).coerceAtMost(650)
        val left = (width - contentWidth) / 2
        val listWidth = (width - 16).coerceAtLeast(120)
        val list = MetadataFieldsList(
            minecraft ?: Minecraft.getInstance(), listWidth, (height - 83).coerceAtLeast(38), 49,
            (listWidth - 18).coerceIn(100, 650), mod,
        )
        list.x = 8
        addRenderableWidget(list)
        addRenderableWidget(
            TechButton.builder(tr("save_back")) { onClose() }.style(TechButtonStyle.PRIMARY)
                .bounds(left, height - 27, contentWidth, 20).build()
        )
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("metadata.subtitle"))
    }
}

private class MetadataFieldsList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val rowWidth: Int,
    mod: RequiredMod,
) : ContainerObjectSelectionList<MetadataFieldsList.FieldEntry>(minecraft, width, height, top, 42) {
    data class Field(val label: Component, val value: () -> String, val changed: (String) -> Unit)

    init {
        listOf(
            Field(tr("metadata.icon"), { mod.iconUrl.orEmpty() }) { mod.iconUrl = it.clean() },
            Field(tr("metadata.project"), { mod.projectUrl.orEmpty() }) { mod.projectUrl = it.clean() },
            Field(tr("metadata.authors"), { mod.authors.orEmpty().joinToString(", ") }) {
                mod.authors = it.split(',').map(String::trim).filter(String::isNotBlank).distinct().take(32)
            },
            Field(tr("metadata.license"), { mod.license.orEmpty() }) { mod.license = it.clean() },
        ).forEach { addEntry(FieldEntry(it, minecraft.font)) }
    }

    override fun getRowWidth(): Int = rowWidth
    override fun getScrollbarPosition(): Int = x + width - 7

    class FieldEntry(data: Field, private val font: Font) : Entry<FieldEntry>() {
        private val label = data.label
        private val field = EditBox(font, 0, 0, 100, 20, label).also {
            it.value = data.value()
            it.setMaxLength(4096)
            it.setResponder(data.changed)
        }

        override fun children(): List<GuiEventListener> = listOf(field)
        override fun narratables(): List<NarratableEntry> = listOf(field)

        override fun render(
            guiGraphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float,
        ) {
            guiGraphics.fill(left, top, left + width, top + height - 2, if (hovered) 0xE0222D3E.toInt() else 0xC5161D29.toInt())
            guiGraphics.fill(left, top, left + 2, top + height - 2, 0xFF62D9FF.toInt())
            guiGraphics.drawString(font, label, left + 7, top + 4, 0x90A7BC, false)
            field.x = left + 7
            field.y = top + 15
            field.width = (width - 14).coerceAtLeast(20)
            field.render(guiGraphics, mouseX, mouseY, partialTick)
        }
    }

    private fun String.clean(): String? = trim().takeIf(String::isNotBlank)
}
