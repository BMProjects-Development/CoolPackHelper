package org.bmp.cph.client.editor

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import org.bmp.cph.config.ConfigManager
import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.DownloadSourceType
import org.bmp.cph.config.RequiredMod

class ModMetadataEditorScreen(parent: Screen, private val mod: RequiredMod, private val onChanged: () -> Unit = {}) :
    EditorScreenBase(tr("metadata.title"), parent) {
    override fun init() {
        val contentWidth = (width - 24).coerceAtMost(650)
        val left = (width - contentWidth) / 2
        val listWidth = (width - 16).coerceAtLeast(120)
        val list = MetadataFieldsList(
            minecraft ?: Minecraft.getInstance(), listWidth, (height - 72).coerceAtLeast(38), 41,
            (listWidth - 18).coerceIn(100, 650), mod, onChanged,
        )
        list.x = 8
        addRenderableWidget(list)
        val back = TechButton.builder(tr("save_back")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("save_back"), 80), 18).build()
        addFooterActions(back, importButton(DownloadSourceType.MODRINTH), importButton(DownloadSourceType.CURSEFORGE))
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("metadata.subtitle"))
    }

    private fun importButton(type: DownloadSourceType): TechButton {
        val text = tr("metadata.import.${type.name.lowercase()}")
        return TechButton.builder(text) {
            minecraft?.setScreen(MetadataImportScreen(this, mod, type, onChanged))
        }.style(TechButtonStyle.SECONDARY).bounds(0, 0, compactButtonWidth(text, 90), 18).build()
    }
}

private class MetadataImportScreen(
    parent: Screen,
    private val mod: RequiredMod,
    private val type: DownloadSourceType,
    private val onChanged: () -> Unit,
) : EditorScreenBase(tr("metadata.import.title", type.name), parent) {
    private lateinit var projectField: MultiLineEditBox
    private var keyField: EditBox? = null
    private var projectValue = mod.links.orEmpty().firstOrNull { it.resolvedType() == type }
        ?.let { it.projectId?.takeIf(String::isNotBlank) ?: it.url }.orEmpty()
    private var keyValue = if (type == DownloadSourceType.CURSEFORGE) {
        ConfigManager.loadAuthorSettings().curseForgeApiKey.orEmpty()
    } else ""
    private var loading = false
    private var errorMessage: String? = null

    override fun init() {
        val contentWidth = (width - 30).coerceAtMost(580)
        val left = (width - contentWidth) / 2
        projectField = StableMultiLineEditBox(font, left, 78, contentWidth, 34, tr("metadata.import.project"), tr("metadata.import.project")).also {
            it.value = projectValue
            it.setCharacterLimit(2048)
            it.setValueListener { value -> projectValue = value.replace("\r", "").replace("\n", "") }
            addRenderableWidget(it)
        }
        if (type == DownloadSourceType.CURSEFORGE) {
            keyField = StableEditBox(font, left, 132, contentWidth, 20, tr("curseforge.key")).also {
                it.value = keyValue
                it.setMaxLength(512)
                it.setFormatter { value, _ -> FormattedCharSequence.forward("•".repeat(value.length), Style.EMPTY) }
                it.setResponder { value -> keyValue = value }
                addRenderableWidget(it)
            }
        }
        val importText = tr(if (loading) "metadata.import.loading" else "metadata.import.action")
        val import = TechButton.builder(importText) { import() }
            .style(TechButtonStyle.PRIMARY).bounds(0, 0, compactButtonWidth(importText, 90), 18).build()
        import.active = !loading
        val back = TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("back")), 18).build()
        addFooterActions(back, import)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("metadata.import.subtitle.${type.name.lowercase()}"))
        guiGraphics.drawString(font, tr("metadata.import.project.${type.name.lowercase()}"), projectField.x, projectField.y - 11, 0x90A7BC, false)
        keyField?.let { guiGraphics.drawString(font, tr("curseforge.key"), it.x, it.y - 11, 0x90A7BC, false) }
        errorMessage?.let {
            guiGraphics.drawCenteredString(font, font.plainSubstrByWidth(it, width - 32), width / 2, height - 43, 0xFFFF7777.toInt())
        }
    }

    private fun import() {
        val project = projectValue.trim()
        if (project.isBlank() || loading) return
        if (type == DownloadSourceType.CURSEFORGE) {
            val key = keyValue.trim()
            if (key.isNotBlank()) ConfigManager.saveAuthorSettings(ConfigManager.loadAuthorSettings().copy(curseForgeApiKey = key))
        }
        val existingIndex = mod.links.orEmpty().indexOfFirst { it.resolvedType() == type }
        val source = mod.links.orEmpty().getOrNull(existingIndex)?.copy()
            ?: DownloadLink(label = if (type == DownloadSourceType.MODRINTH) "Modrinth" else "CurseForge", type = type.name)
        source.type = type.name
        if (type == DownloadSourceType.MODRINTH && project.startsWith("http", ignoreCase = true)) {
            source.url = project
            source.projectId = null
        } else {
            source.projectId = project
        }
        loading = true
        errorMessage = null
        rebuildWidgets()
        ProjectMetadataService.fetchAsync(source).whenComplete { metadata, exception ->
            Minecraft.getInstance().execute {
                loading = false
                if (Minecraft.getInstance().screen !== this) return@execute
                if (metadata != null) {
                    ProjectMetadataService.apply(metadata, mod, source)
                    val links = mod.links.orEmpty().toMutableList()
                    if (existingIndex in links.indices) links[existingIndex] = source else links += source
                    mod.links = links
                    onChanged()
                    SystemToast.add(
                        Minecraft.getInstance().toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        tr("link.metadata.done"), Component.literal(metadata.name),
                    )
                    minecraft?.setScreen(previousScreen)
                } else {
                    errorMessage = exception?.cause?.message ?: exception?.message ?: "Unknown error"
                    rebuildWidgets()
                }
            }
        }
    }

    override fun onClose() {
        if (!loading) super.onClose()
    }
}

private class MetadataFieldsList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val rowWidth: Int,
    mod: RequiredMod,
    onChanged: () -> Unit,
) : ContainerObjectSelectionList<MetadataFieldsList.FieldEntry>(minecraft, width, height, top, 40) {
    data class Field(val label: Component, val value: () -> String, val wraps: Boolean = false, val changed: (String) -> Unit)

    init {
        listOf(
            Field(tr("metadata.icon"), { mod.iconUrl.orEmpty() }, wraps = true) { mod.iconUrl = it.singleLine().clean(); onChanged() },
            Field(tr("metadata.project"), { mod.projectUrl.orEmpty() }, wraps = true) { mod.projectUrl = it.singleLine().clean(); onChanged() },
            Field(tr("metadata.authors"), { mod.authors.orEmpty().joinToString(", ") }) {
                mod.authors = it.split(',').map(String::trim).filter(String::isNotBlank).distinct().take(32)
                onChanged()
            },
            Field(tr("metadata.license"), { mod.license.orEmpty() }) { mod.license = it.clean(); onChanged() },
        ).forEach { addEntry(FieldEntry(it, minecraft.font, (rowWidth * .62).toInt().coerceAtLeast(20))) }
    }

    override fun getRowWidth(): Int = rowWidth
    override fun getScrollbarPosition(): Int = x + width - 7

    class FieldEntry(data: Field, private val font: Font, fieldWidth: Int) : Entry<FieldEntry>() {
        private val label = data.label
        private val singleLineField = if (!data.wraps) StableEditBox(font, 0, 0, fieldWidth, 20, label).also {
            it.value = data.value(); it.setMaxLength(4096); it.setResponder(data.changed)
        } else null
        private val wrappedField = if (data.wraps) StableMultiLineEditBox(font, 0, 0, fieldWidth, 32, label, label).also {
            it.setCharacterLimit(4096); it.value = data.value(); it.setValueListener(data.changed)
        } else null

        override fun children(): List<GuiEventListener> = listOfNotNull(singleLineField, wrappedField)
        override fun narratables(): List<NarratableEntry> = listOfNotNull(singleLineField, wrappedField)

        override fun render(
            guiGraphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float,
        ) {
            drawEditorRow(guiGraphics, left, top, width, height, hovered)
            val fieldWidth = singleLineField?.width ?: wrappedField?.width ?: 0
            val fieldX = left + width - fieldWidth - 7
            guiGraphics.drawString(font, font.plainSubstrByWidth(label.string, (fieldX - left - 14).coerceAtLeast(25)), left + 7, top + 15, EditorTheme.TEXT_MUTED, false)
            singleLineField?.let {
                it.x = fieldX
                it.y = top + 10
                it.render(guiGraphics, mouseX, mouseY, partialTick)
            }
            wrappedField?.let {
                it.x = fieldX
                it.y = top + 4
                it.render(guiGraphics, mouseX, mouseY, partialTick)
            }
        }
    }

    private fun String.clean(): String? = trim().takeIf(String::isNotBlank)
    private fun String.singleLine(): String = replace("\r", "").replace("\n", "")
}
