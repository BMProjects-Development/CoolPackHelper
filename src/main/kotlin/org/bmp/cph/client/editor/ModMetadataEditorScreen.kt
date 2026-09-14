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
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import org.bmp.cph.config.ConfigManager
import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.DownloadSourceType
import org.bmp.cph.config.RequiredMod
import org.bmp.cph.client.curseforge.CurseForgeApiSupport
import java.net.URI

class ModMetadataEditorScreen(parent: Screen, private val mod: RequiredMod, private val onChanged: () -> Unit = {}) :
    EditorScreenBase(tr("metadata.title"), parent) {
    private var dialog = EditorRect(0, 0, 0, 0)

    override fun usesModalBackground(): Boolean = true

    override fun init() {
        dialog = centeredModal(680, 350, 210)
        val listWidth = (dialog.width - 12).coerceAtLeast(120)
        val list = MetadataFieldsList(
            minecraft ?: Minecraft.getInstance(), listWidth, (dialog.height - 70).coerceAtLeast(38), dialog.top + 38,
            (listWidth - 10).coerceAtLeast(100), mod, onChanged,
        )
        list.x = dialog.left + 6
        addRenderableWidget(list)
        val back = TechButton.builder(tr("save_back")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("save_back"), 80), 18).build()
        addCompactActions(
            dialog.left + 8, dialog.right - 8, dialog.bottom - 23,
            back, importButton(DownloadSourceType.MODRINTH), importButton(DownloadSourceType.CURSEFORGE),
        )
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawModalFrame(guiGraphics, dialog, tr("metadata.subtitle"))
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
    private var projectField: MultiLineEditBox? = null
    private var keyField: EditBox? = null
    private var projectValue = mod.links.orEmpty().firstOrNull { it.resolvedType() == type }
        ?.let { it.projectId?.takeIf(String::isNotBlank) ?: it.url }.orEmpty()
    private var keyValue = if (type == DownloadSourceType.CURSEFORGE) {
        ConfigManager.loadAuthorSettings().curseForgeApiKey.orEmpty()
    } else ""
    private var loading = false
    private var errorMessage: String? = null
    private var previewMetadata: ProjectMetadata? = null
    private var previewSource: DownloadLink? = null
    private var previewExistingIndex = -1
    private var dialog = EditorRect(0, 0, 0, 0)

    override fun usesModalBackground(): Boolean = true

    override fun init() {
        dialog = centeredModal(590, 270, 210)
        val contentWidth = dialog.width - 18
        val left = dialog.left + 9
        val preview = previewMetadata
        if (preview == null) {
            projectField = StableMultiLineEditBox(font, left, dialog.top + 62, contentWidth, 34, tr("metadata.import.project"), tr("metadata.import.project")).also {
            it.value = projectValue
            it.setCharacterLimit(2048)
            it.setValueListener { value -> projectValue = value.replace("\r", "").replace("\n", "") }
            addRenderableWidget(it)
            }
        }
        if (preview == null && type == DownloadSourceType.CURSEFORGE) {
            keyField = StableEditBox(font, left, dialog.top + 116, contentWidth, 18, tr("curseforge.key")).also {
                it.value = keyValue
                it.setMaxLength(512)
                it.setFormatter { value, _ -> FormattedCharSequence.forward("•".repeat(value.length), Style.EMPTY) }
                it.setResponder { value -> keyValue = value }
                addRenderableWidget(it)
            }
        }
        val back = TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("back")), 18).build()
        val actions = mutableListOf(back)
        if (preview != null) {
            val fill = tr("metadata.import.fill_empty")
            val replace = tr("metadata.import.replace")
            actions += TechButton.builder(fill) { applyPreview(MetadataApplyMode.FILL_EMPTY) }
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("metadata.import.fill_empty.hint")))
                .style(TechButtonStyle.SECONDARY).bounds(0, 0, compactButtonWidth(fill, 92), 18).build()
            actions += TechButton.builder(replace) { applyPreview(MetadataApplyMode.REPLACE) }
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("metadata.import.replace.hint")))
                .style(TechButtonStyle.PRIMARY).bounds(0, 0, compactButtonWidth(replace, 92), 18).build()
        } else if (type == DownloadSourceType.CURSEFORGE) {
            actions += TechButton.builder(tr("curseforge.key.help")) { openKeyHelp() }
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("curseforge.key.help.hint")))
                .style(TechButtonStyle.GHOST)
                .bounds(0, 0, compactButtonWidth(tr("curseforge.key.help"), 88), 18).build()
        }
        if (preview == null) {
            val importText = tr(if (loading) "metadata.import.loading" else "metadata.import.action")
            val import = TechButton.builder(importText) { import() }
                .style(TechButtonStyle.PRIMARY).bounds(0, 0, compactButtonWidth(importText, 90), 18).build()
            import.active = !loading
            actions += import
        }
        addCompactActions(dialog.left + 8, dialog.right - 8, dialog.bottom - 23, *actions.toTypedArray())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawModalFrame(guiGraphics, dialog, tr("metadata.import.subtitle.${type.name.lowercase()}"))
        projectField?.let { guiGraphics.drawString(font, tr("metadata.import.project.${type.name.lowercase()}"), it.x, it.y - 11, 0x90A7BC, false) }
        keyField?.let { guiGraphics.drawString(font, tr("curseforge.key"), it.x, it.y - 11, 0x90A7BC, false) }
        previewMetadata?.let { metadata ->
            val links = metadata.projectLinks
            val linkCount = listOf(links.homepage, links.source, links.issues, links.wiki, links.discord)
                .count { !it.isNullOrBlank() } + links.donations.orEmpty().count { !it.url.isNullOrBlank() }
            val lines = listOf(
                tr("metadata.import.preview.name", metadata.name),
                tr("metadata.import.preview.authors", metadata.authors.joinToString(", ").ifBlank { "—" }),
                tr("metadata.import.preview.license", metadata.license ?: "—"),
                tr("metadata.import.preview.links", linkCount),
                tr("metadata.import.preview.description", Component.translatable(if (metadata.summary.isNullOrBlank()) "options.off" else "options.on")),
            )
            val previewLeft = dialog.left + 9
            val previewTop = dialog.top + 44
            val previewRight = dialog.right - 9
            lines.forEachIndexed { index, line ->
                val rowTop = previewTop + index * 28
                drawEditorRow(guiGraphics, previewLeft, rowTop, previewRight - previewLeft, 27, false)
                val clipped = font.split(line, (previewRight - previewLeft - 18).coerceAtLeast(40)).firstOrNull()
                clipped?.let {
                    guiGraphics.drawString(
                        font, it, previewLeft + 9, rowTop + 9,
                        if (index == 0) EditorTheme.TEXT else EditorTheme.TEXT_MUTED, false,
                    )
                }
            }
        }
        errorMessage?.let {
            font.split(Component.literal(it), (dialog.width - 24).coerceAtLeast(40)).take(2).forEachIndexed { index, line ->
                guiGraphics.drawCenteredString(font, line, width / 2, dialog.bottom - 51 + index * 10, 0xFFFF7777.toInt())
            }
        }
    }

    private fun import() {
        val project = projectValue.trim()
        if (project.isBlank() || loading) return
        if (type == DownloadSourceType.CURSEFORGE) {
            val key = CurseForgeApiSupport.normalizeKey(keyValue)
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
                    previewMetadata = metadata
                    previewSource = source
                    previewExistingIndex = existingIndex
                    rebuildWidgets()
                } else {
                    errorMessage = exception?.cause?.message ?: exception?.message ?: tr("error.unknown").string
                    rebuildWidgets()
                }
            }
        }
    }

    private fun applyPreview(mode: MetadataApplyMode) {
        val metadata = previewMetadata ?: return
        val source = previewSource ?: return
        ProjectMetadataService.apply(
            metadata, mod, source, "en_us", mode,
        )
        val links = mod.links.orEmpty().toMutableList()
        if (previewExistingIndex in links.indices) links[previewExistingIndex] = source else links += source
        mod.links = links
        onChanged()
        SystemToast.add(
            Minecraft.getInstance().toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
            tr("link.metadata.done"), Component.literal(metadata.name),
        )
        minecraft?.setScreen(previousScreen)
    }

    private fun openKeyHelp() {
        ConfirmLinkScreen.confirmLinkNow(this, URI.create(CurseForgeApiSupport.API_KEY_HELP_URL), true)
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
        val projectLinks = mod.resolvedProjectLinks().also {
            mod.projectLinks = it
            mod.projectUrl = null
        }
        listOf(
            Field(tr("metadata.icon"), { mod.iconUrl.orEmpty() }, wraps = true) { mod.iconUrl = it.singleLine().clean(); onChanged() },
            Field(tr("metadata.project"), { projectLinks.homepage.orEmpty() }, wraps = true) { projectLinks.homepage = it.singleLine().clean(); onChanged() },
            Field(tr("metadata.source"), { projectLinks.source.orEmpty() }, wraps = true) { projectLinks.source = it.singleLine().clean(); onChanged() },
            Field(tr("metadata.issues"), { projectLinks.issues.orEmpty() }, wraps = true) { projectLinks.issues = it.singleLine().clean(); onChanged() },
            Field(tr("metadata.wiki"), { projectLinks.wiki.orEmpty() }, wraps = true) { projectLinks.wiki = it.singleLine().clean(); onChanged() },
            Field(tr("metadata.discord"), { projectLinks.discord.orEmpty() }, wraps = true) { projectLinks.discord = it.singleLine().clean(); onChanged() },
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
