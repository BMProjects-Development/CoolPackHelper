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
import org.bmp.cph.config.ModCategory
import org.bmp.cph.config.RequiredMod

class ModEditorScreen(
    parent: Screen,
    private val session: EditorSession,
    private val index: Int,
    private val working: RequiredMod = session.config.activeModEntries().getOrNull(index)?.copyForEditor() ?: RequiredMod(),
) : EditorScreenBase(tr("mod.title"), parent) {
    override fun init() {
        val contentWidth = (width - 24).coerceAtMost(650)
        val left = (width - contentWidth) / 2
        val listWidth = (width - 16).coerceAtLeast(120)
        val list = ModEditorContentList(
            minecraft ?: Minecraft.getInstance(), listWidth, (height - 83).coerceAtLeast(38), 49,
            (listWidth - 18).coerceIn(100, 650), working,
            openDescriptions = { minecraft?.setScreen(DescriptionsEditorScreen(this, working)) },
            openLinks = { minecraft?.setScreen(LinksEditorScreen(this, working)) },
            openMetadata = { minecraft?.setScreen(ModMetadataEditorScreen(this, working)) },
        )
        list.x = 8
        addRenderableWidget(list)
        addRenderableWidget(
            TechButton.builder(tr("save_back")) { saveAndClose() }.style(TechButtonStyle.PRIMARY)
                .bounds(left, height - 27, contentWidth, 20).build()
        )
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("mod.subtitle"))
    }

    private fun saveAndClose() {
        val mods = session.config.activeModEntries().toMutableList()
        if (index in mods.indices) mods[index] = working else mods += working
        session.replaceMods(mods)
        onClose()
    }
}

private class ModEditorContentList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val rowWidth: Int,
    private val mod: RequiredMod,
    openDescriptions: () -> Unit,
    openLinks: () -> Unit,
    openMetadata: () -> Unit,
) : ContainerObjectSelectionList<ModEditorContentList.EditorEntry>(minecraft, width, height, top, 42) {
    init {
        addEntry(FieldEntry(tr("mod.name"), mod.name.orEmpty(), minecraft.font) { mod.name = it.trim() })
        addEntry(FieldEntry(tr("mod.id"), mod.modId.orEmpty(), minecraft.font) { mod.modId = it.trim() })
        addEntry(FieldEntry(tr("mod.version"), mod.versionRange.orEmpty(), minecraft.font) { mod.versionRange = it.trim() })
        addEntry(FieldEntry(tr("mod.pattern"), mod.filePattern.orEmpty(), minecraft.font) { mod.filePattern = it.trim() })
        addEntry(
            ActionEntry(
                listOf(
                    Action(
                        label = { tr(if (mod.enabled == false) "mod.disabled" else "mod.enabled") },
                        style = { if (mod.enabled == false) TechButtonStyle.GHOST else TechButtonStyle.PRIMARY },
                    ) { mod.enabled = mod.enabled == false },
                    Action(label = { tr("mod.category", tr("category.${mod.resolvedCategory().name.lowercase()}")) }) {
                        mod.category = if (mod.resolvedCategory() == ModCategory.REQUIRED) {
                            ModCategory.RECOMMENDED.name
                        } else ModCategory.REQUIRED.name
                    },
                )
            )
        )
        addEntry(
            ActionEntry(
                listOf(
                    Action(label = { tr("mod.descriptions", mod.descriptions.orEmpty().size) }, run = openDescriptions),
                    Action(label = { tr("mod.links", mod.links.orEmpty().size) }, run = openLinks),
                    Action(label = { tr("mod.metadata") }, run = openMetadata),
                )
            )
        )
    }

    override fun getRowWidth(): Int = rowWidth
    override fun getScrollbarPosition(): Int = x + width - 7

    abstract class EditorEntry : Entry<EditorEntry>()

    class FieldEntry(
        private val label: Component,
        value: String,
        private val font: Font,
        changed: (String) -> Unit,
    ) : EditorEntry() {
        private val field = EditBox(font, 0, 0, 100, 20, label).also {
            it.value = value
            it.setMaxLength(4096)
            it.setResponder(changed)
        }

        override fun children(): List<GuiEventListener> = listOf(field)
        override fun narratables(): List<NarratableEntry> = listOf(field)

        override fun render(
            guiGraphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float,
        ) {
            rowBackground(guiGraphics, top, left, width, height, hovered)
            guiGraphics.drawString(font, label, left + 7, top + 4, 0x90A7BC, false)
            field.x = left + 7
            field.y = top + 15
            field.width = (width - 14).coerceAtLeast(20)
            field.render(guiGraphics, mouseX, mouseY, partialTick)
        }
    }

    data class Action(
        val label: () -> Component,
        val style: () -> TechButtonStyle = { TechButtonStyle.SECONDARY },
        val run: () -> Unit,
    )

    class ActionEntry(private val actions: List<Action>) : EditorEntry() {
        private val buttons = actions.map { action ->
            TechButton.builder(action.label()) { action.run() }.style(action.style()).bounds(0, 0, 80, 20).build()
        }

        override fun children(): List<GuiEventListener> = buttons
        override fun narratables(): List<NarratableEntry> = buttons

        override fun render(
            guiGraphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float,
        ) {
            rowBackground(guiGraphics, top, left, width, height, hovered)
            val gap = 5
            val buttonWidth = ((width - 14 - gap * (buttons.size - 1)) / buttons.size).coerceAtLeast(24)
            buttons.forEachIndexed { buttonIndex, button ->
                val action = actions[buttonIndex]
                button.message = action.label()
                button.setTechStyle(action.style())
                button.x = left + 7 + buttonIndex * (buttonWidth + gap)
                button.y = top + (height - 22) / 2
                button.width = buttonWidth
                button.render(guiGraphics, mouseX, mouseY, partialTick)
            }
        }
    }

    companion object {
        private fun rowBackground(guiGraphics: GuiGraphics, top: Int, left: Int, width: Int, height: Int, hovered: Boolean) {
            guiGraphics.fill(left, top, left + width, top + height - 2, if (hovered) 0xE0222D3E.toInt() else 0xC5161D29.toInt())
            guiGraphics.fill(left, top, left + 2, top + height - 2, 0xFF62D9FF.toInt())
            guiGraphics.fill(left + 2, top, left + width, top + 1, 0x4A5A708A)
            guiGraphics.fill(left + 2, top + height - 3, left + width, top + height - 2, 0x334D6077)
        }
    }
}
