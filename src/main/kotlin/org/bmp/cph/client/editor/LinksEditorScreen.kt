package org.bmp.cph.client.editor

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.DownloadSourceType
import org.bmp.cph.config.RequiredMod

class LinksEditorScreen(
    parent: Screen,
    private val mod: RequiredMod,
    private val onChanged: () -> Unit = {},
) : EditorScreenBase(tr("links.title", mod.displayName()), parent) {
    override fun init() {
        val links = mod.links.orEmpty()
        val contentWidth = (width - 24).coerceAtMost(650)
        val left = (width - contentWidth) / 2
        val listWidth = (width - 16).coerceAtLeast(120)
        val indexedLinks = links.withIndex().map { it.index to it.value }
        val list = StyledActionList(
            minecraft ?: net.minecraft.client.Minecraft.getInstance(),
            listWidth,
            (height - 83).coerceAtLeast(38),
            49,
            (listWidth - 18).coerceIn(100, 650),
            40,
            indexedLinks,
            titleOf = { it.second.displayLabel() },
            subtitleOf = { it.second.downloadUrl?.takeIf(String::isNotBlank) ?: it.second.url.orEmpty() },
            accentOf = { 0xFF9B7BFF.toInt() },
            badgeOf = { RowBadge(Component.literal(it.second.resolvedType().name), 0xFFB59BFF.toInt()) },
            actionsOf = { (index, _) -> listOf(
                RowAction(label = { tr("mods.edit") }, width = 58) {
                    minecraft?.setScreen(LinkEntryEditorScreen(this, mod, index, onChanged))
                },
                RowAction(label = { Component.literal("×") }, width = 25, style = { TechButtonStyle.DANGER }) {
                    val mutable = mod.links.orEmpty().toMutableList()
                    mutable.removeAt(index)
                    mod.links = mutable
                    onChanged()
                    rebuildWidgets()
                },
            ) },
        )
        list.x = 8
        addRenderableWidget(list)
        val half = (contentWidth - 6) / 2
        addRenderableWidget(
            TechButton.builder(tr("links.add")) {
                mod.links = mod.links.orEmpty() + DownloadLink()
                minecraft?.setScreen(LinkEntryEditorScreen(this, mod, mod.links.orEmpty().lastIndex, onChanged))
            }.style(TechButtonStyle.PRIMARY).bounds(left, height - 27, half, 20).build()
        )
        addRenderableWidget(TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST).bounds(left + half + 6, height - 27, half, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("links.subtitle"))
        if (mod.links.orEmpty().isEmpty()) guiGraphics.drawCenteredString(font, tr("links.empty"), width / 2, height / 2, 0x91A4B8)
    }

    override fun onClose() {
        super.onClose()
    }
}

class LinkEntryEditorScreen(
    parent: Screen,
    private val mod: RequiredMod,
    private val index: Int,
    private val onChanged: () -> Unit = {},
) : EditorScreenBase(tr("link.title"), parent) {
    private val working = (mod.links.orEmpty().getOrNull(index) ?: DownloadLink()).copy()
    private var metadataLoading = false

    override fun init() {
        val w = (width - 30).coerceAtMost(600)
        val x = (width - w) / 2
        val metadataWidth = (w * 0.38).toInt().coerceAtLeast(100)
        val typeWidth = w - metadataWidth - 6
        addRenderableWidget(
            TechButton.builder(tr("link.type", working.resolvedType().name)) { cycleType() }
                .style(TechButtonStyle.SECONDARY).bounds(x, 49, typeWidth, 20).build()
        )
        val metadata = TechButton.builder(tr(if (metadataLoading) "link.metadata.loading" else "link.metadata")) { loadMetadata() }
            .style(TechButtonStyle.PRIMARY).bounds(x + typeWidth + 6, 49, metadataWidth, 20).build()
        metadata.active = !metadataLoading && working.resolvedType() in setOf(DownloadSourceType.MODRINTH, DownloadSourceType.CURSEFORGE)
        addRenderableWidget(metadata)
        val listWidth = (width - 16).coerceAtLeast(120)
        val list = DownloadSourceFieldsList(
            minecraft ?: Minecraft.getInstance(), listWidth, (height - 116).coerceAtLeast(38), 78,
            (listWidth - 18).coerceIn(100, 600), working,
        )
        list.x = 8
        addRenderableWidget(list)
        addRenderableWidget(TechButton.builder(tr("save_back")) { save() }.style(TechButtonStyle.PRIMARY).bounds(x, height - 27, w, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("link.subtitle"))
    }

    private fun cycleType() {
        val types = DownloadSourceType.entries
        val next = (types.indexOf(working.resolvedType()) + 1) % types.size
        working.type = types[next].name
        rebuildWidgets()
    }

    private fun loadMetadata() {
        if (metadataLoading) return
        metadataLoading = true
        rebuildWidgets()
        ProjectMetadataService.fetchAsync(working).whenComplete { value, exception ->
            Minecraft.getInstance().execute {
                metadataLoading = false
                if (value != null) {
                    ProjectMetadataService.apply(value, mod, working)
                    commitWorking()
                    onChanged()
                    SystemToast.add(
                        Minecraft.getInstance().toasts,
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        tr("link.metadata.done"),
                        Component.literal(value.name),
                    )
                } else {
                    SystemToast.add(
                        Minecraft.getInstance().toasts,
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        tr("link.metadata.failed"),
                        Component.literal(exception?.cause?.message ?: exception?.message ?: "Unknown error"),
                    )
                }
                if (Minecraft.getInstance().screen === this) rebuildWidgets()
            }
        }
    }

    override fun onClose() {
        if (!metadataLoading) super.onClose()
    }

    private fun save() {
        commitWorking()
        onChanged()
        onClose()
    }

    private fun commitWorking() {
        val mutable = mod.links.orEmpty().toMutableList()
        if (index in mutable.indices) mutable[index] = working else mutable += working
        mod.links = mutable
    }
}

private class DownloadSourceFieldsList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val rowWidth: Int,
    link: DownloadLink,
) : ContainerObjectSelectionList<DownloadSourceFieldsList.FieldEntry>(minecraft, width, height, top, 54) {
    data class Field(
        val label: Component,
        val value: () -> String,
        val maxLength: Int = 2048,
        val wraps: Boolean = false,
        val changed: (String) -> Unit,
    )

    init {
        val fields = listOf(
            Field(tr("link.label"), { link.label.orEmpty() }, 256) { link.label = it.clean() },
            Field(tr("link.url"), { link.url.orEmpty() }, wraps = true) { link.url = it.singleLine().clean() },
            Field(tr("link.project_id"), { link.projectId.orEmpty() }, 256) { link.projectId = it.clean() },
            Field(tr("link.version_id"), { link.versionId.orEmpty() }, 256) { link.versionId = it.clean() },
            Field(tr("link.file_id"), { link.fileId.orEmpty() }, 256) { link.fileId = it.clean() },
            Field(tr("link.download_url"), { link.downloadUrl.orEmpty() }, wraps = true) { link.downloadUrl = it.singleLine().clean() },
            Field(tr("link.file_name"), { link.fileName.orEmpty() }, 256) { link.fileName = it.clean() },
            Field(tr("link.size"), { link.sizeBytes?.toString().orEmpty() }, 24) { link.sizeBytes = it.trim().toLongOrNull() },
            Field(tr("link.sha256"), { link.sha256.orEmpty() }, 64) { link.sha256 = it.clean() },
            Field(tr("link.sha512"), { link.sha512.orEmpty() }, 128) { link.sha512 = it.clean() },
            Field(tr("link.sha1"), { link.sha1.orEmpty() }, 40) { link.sha1 = it.clean() },
        )
        fields.forEach { addEntry(FieldEntry(it, minecraft.font, (rowWidth - 14).coerceAtLeast(20))) }
    }

    override fun getRowWidth(): Int = rowWidth
    override fun getScrollbarPosition(): Int = x + width - 7

    class FieldEntry(fieldData: Field, private val font: Font, fieldWidth: Int) : Entry<FieldEntry>() {
        private val label = fieldData.label
        private val singleLineField = if (!fieldData.wraps) StableEditBox(font, 0, 0, fieldWidth, 20, label).also {
            it.value = fieldData.value(); it.setMaxLength(fieldData.maxLength); it.setResponder(fieldData.changed)
        } else null
        private val wrappedField = if (fieldData.wraps) MultiLineEditBox(font, 0, 0, fieldWidth, 32, label, label).also {
            it.setCharacterLimit(fieldData.maxLength); it.value = fieldData.value(); it.setValueListener(fieldData.changed)
        } else null

        override fun children(): List<GuiEventListener> = listOfNotNull(singleLineField, wrappedField)
        override fun narratables(): List<NarratableEntry> = listOfNotNull(singleLineField, wrappedField)

        override fun render(
            guiGraphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float,
        ) {
            guiGraphics.fill(left, top, left + width, top + height - 2, if (hovered) 0xE0222D3E.toInt() else 0xC5161D29.toInt())
            guiGraphics.fill(left, top, left + 2, top + height - 2, 0xFF9B7BFF.toInt())
            guiGraphics.drawString(font, label, left + 7, top + 4, 0x90A7BC, false)
            singleLineField?.let {
                it.x = left + 7
                it.y = top + 15
                it.render(guiGraphics, mouseX, mouseY, partialTick)
            }
            wrappedField?.let {
                it.x = left + 7
                it.y = top + 15
                it.render(guiGraphics, mouseX, mouseY, partialTick)
            }
        }
    }

    private fun String.clean(): String? = trim().takeIf(String::isNotBlank)
    private fun String.singleLine(): String = replace("\r", "").replace("\n", "")
}
