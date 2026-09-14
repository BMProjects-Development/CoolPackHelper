package org.bmp.cph.client.editor

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.EditBox
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
    private var dialog = EditorRect(0, 0, 0, 0)

    override fun usesModalBackground(): Boolean = true

    override fun init() {
        val links = mod.links.orEmpty()
        dialog = centeredModal(680, 350, 190)
        val listWidth = (dialog.width - 12).coerceAtLeast(120)
        val indexedLinks = links.withIndex().map { it.index to it.value }
        val list = StyledActionList(
            minecraft ?: net.minecraft.client.Minecraft.getInstance(),
            listWidth,
            (dialog.height - 70).coerceAtLeast(38),
            dialog.top + 38,
            (listWidth - 10).coerceAtLeast(100),
            36,
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
        list.x = dialog.left + 6
        addRenderableWidget(list)
        val add = TechButton.builder(tr("links.add")) {
                minecraft?.setScreen(LinkEntryEditorScreen(this, mod, mod.links.orEmpty().size, onChanged))
            }.style(TechButtonStyle.PRIMARY)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("links.add.hint")))
            .bounds(0, 0, compactButtonWidth(tr("links.add")), 18).build()
        val back = TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("back")), 18).build()
        addCompactActions(dialog.left + 8, dialog.right - 8, dialog.bottom - 23, back, add)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawModalFrame(guiGraphics, dialog, tr("links.subtitle"))
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
    private var showAdvanced = false
    private var dialogLeft = 0
    private var dialogTop = 0
    private var dialogRight = 0
    private var dialogBottom = 0

    override fun usesModalBackground(): Boolean = true

    override fun init() {
        val bounds = centeredModal(620, 330, 180)
        val w = bounds.width
        val h = bounds.height
        val x = bounds.left
        val y = bounds.top
        dialogLeft = x
        dialogTop = y
        dialogRight = x + w
        dialogBottom = y + h
        val typeText = tr("link.type", working.resolvedType().name)
        val metadataText = tr(if (metadataLoading) "link.metadata.loading" else "link.metadata")
        val typeWidth = compactButtonWidth(typeText, 120, 240)
        val metadataWidth = compactButtonWidth(metadataText, 92, 180)
        addRenderableWidget(
            TechButton.builder(typeText) { cycleType() }
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("link.type.hint")))
                .style(TechButtonStyle.SECONDARY).bounds(x + 8, y + 34, typeWidth, 18).build()
        )
        val metadata = TechButton.builder(metadataText) { loadMetadata() }
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("link.metadata.hint")))
            .style(TechButtonStyle.SECONDARY).bounds(x + typeWidth + 13, y + 34, metadataWidth, 18).build()
        metadata.active = !metadataLoading && working.resolvedType() in setOf(DownloadSourceType.MODRINTH, DownloadSourceType.CURSEFORGE)
        addRenderableWidget(metadata)
        val listWidth = (w - 12).coerceAtLeast(120)
        val listTop = y + 58
        val list = DownloadSourceFieldsList(
            minecraft ?: Minecraft.getInstance(), listWidth, (dialogBottom - listTop - 31).coerceAtLeast(38), listTop,
            (listWidth - 10).coerceAtLeast(100), working, showAdvanced,
        )
        list.x = x + 6
        addRenderableWidget(list)
        val advancedText = tr(if (showAdvanced) "link.advanced.hide" else "link.advanced.show")
        val advanced = TechButton.builder(advancedText) {
                showAdvanced = !showAdvanced
                rebuildWidgets()
            }.tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("link.advanced.hint")))
            .style(TechButtonStyle.GHOST).bounds(0, 0, compactButtonWidth(advancedText, 90), 18).build()
        val cancel = TechButton.builder(tr("cancel")) { onClose() }
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("link.cancel.hint")))
            .style(TechButtonStyle.GHOST).bounds(0, 0, compactButtonWidth(tr("cancel")), 18).build()
        val save = TechButton.builder(tr("save_back")) { save() }.style(TechButtonStyle.PRIMARY)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("link.save.hint")))
            .bounds(0, 0, compactButtonWidth(tr("save_back"), 90), 18).build()
        addCompactActions(dialogLeft + 8, dialogRight - 8, dialogBottom - 23, advanced, cancel, save)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawPanel(guiGraphics, dialogLeft, dialogTop, dialogRight, dialogBottom)
        guiGraphics.drawString(font, title, dialogLeft + 10, dialogTop + 9, EditorTheme.TEXT, false)
        guiGraphics.drawString(
            font,
            font.plainSubstrByWidth(tr("link.subtitle").string, (dialogRight - dialogLeft - 20).coerceAtLeast(40)),
            dialogLeft + 10,
            dialogTop + 21,
            EditorTheme.TEXT_MUTED,
            false,
        )
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
    advanced: Boolean,
) : ContainerObjectSelectionList<DownloadSourceFieldsList.FieldEntry>(minecraft, width, height, top, 40) {
    data class Field(
        val key: String,
        val label: Component,
        val value: () -> String,
        val maxLength: Int = 2048,
        val wraps: Boolean = false,
        val changed: (String) -> Unit,
    )

    init {
        val fields = listOf(
            Field("label", tr("link.label"), { link.label.orEmpty() }, 256) { link.label = it.clean() },
            Field("url", tr("link.url"), { link.url.orEmpty() }, wraps = true) { link.url = it.singleLine().clean() },
            Field("projectId", tr("link.project_id"), { link.projectId.orEmpty() }, 256) { link.projectId = it.clean() },
            Field("versionId", tr("link.version_id"), { link.versionId.orEmpty() }, 256) { link.versionId = it.clean() },
            Field("fileId", tr("link.file_id"), { link.fileId.orEmpty() }, 256) { link.fileId = it.clean() },
            Field("downloadUrl", tr("link.download_url"), { link.downloadUrl.orEmpty() }, wraps = true) { link.downloadUrl = it.singleLine().clean() },
            Field("fileName", tr("link.file_name"), { link.fileName.orEmpty() }, 256) { link.fileName = it.clean() },
            Field("sizeBytes", tr("link.size"), { link.sizeBytes?.toString().orEmpty() }, 24) { link.sizeBytes = it.trim().toLongOrNull() },
            Field("sha256", tr("link.sha256"), { link.sha256.orEmpty() }, 64) { link.sha256 = it.clean() },
            Field("sha512", tr("link.sha512"), { link.sha512.orEmpty() }, 128) { link.sha512 = it.clean() },
            Field("sha1", tr("link.sha1"), { link.sha1.orEmpty() }, 40) { link.sha1 = it.clean() },
        )
        val basic = when (link.resolvedType()) {
            DownloadSourceType.MODRINTH -> setOf("label", "projectId", "url")
            DownloadSourceType.CURSEFORGE -> setOf("label", "projectId")
            DownloadSourceType.GITHUB_RELEASE -> setOf("label", "url", "downloadUrl", "sha256")
            DownloadSourceType.DIRECT -> setOf("label", "url", "downloadUrl", "sha256")
            DownloadSourceType.PAGE -> setOf("label", "url")
        }
        fields.filter { advanced || it.key in basic }.forEach {
            addEntry(FieldEntry(it, minecraft.font, (rowWidth * .62).toInt().coerceAtLeast(20)))
        }
    }

    override fun getRowWidth(): Int = rowWidth
    override fun getScrollbarPosition(): Int = x + width - 7

    class FieldEntry(fieldData: Field, private val font: Font, fieldWidth: Int) : Entry<FieldEntry>() {
        private val label = fieldData.label
        private val singleLineField = if (!fieldData.wraps) StableEditBox(font, 0, 0, fieldWidth, 20, label).also {
            it.value = fieldData.value(); it.setMaxLength(fieldData.maxLength); it.setResponder(fieldData.changed)
        } else null
        private val wrappedField = if (fieldData.wraps) StableMultiLineEditBox(font, 0, 0, fieldWidth, 32, label, label).also {
            it.setCharacterLimit(fieldData.maxLength); it.value = fieldData.value(); it.setValueListener(fieldData.changed)
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
