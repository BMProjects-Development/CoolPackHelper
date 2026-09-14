package org.bmp.cph.client.download

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.client.editor.EditorScreenBase
import org.bmp.cph.client.editor.RowAction
import org.bmp.cph.client.editor.RowBadge
import org.bmp.cph.client.editor.StyledActionList
import org.bmp.cph.client.editor.TechButton
import org.bmp.cph.client.editor.TechButtonStyle

class InstallationHistoryScreen(parent: Screen) :
    EditorScreenBase(Component.translatable("cph.history.title"), parent) {
    private data class Batch(val id: String, val records: List<InstallationRecord>)

    private var message: Component? = null
    private var hasHistory = false
    private var activeCount = 0

    override fun init() {
        val records = InstallationJournal.load()
        hasHistory = records.isNotEmpty()
        activeCount = records.count { !it.rolledBack }
        val batches = records
            .groupBy(InstallationRecord::batchId)
            .map { (id, records) -> Batch(id, records) }
            .sortedByDescending { it.records.maxOfOrNull(InstallationRecord::installedAt).orEmpty() }
        val contentWidth = (width - 24).coerceAtMost(720)
        val left = (width - contentWidth) / 2
        val listWidth = (width - 16).coerceAtLeast(120)
        val list = StyledActionList(
            minecraft ?: Minecraft.getInstance(), listWidth, (height - 72).coerceAtLeast(38), 41,
            (listWidth - 18).coerceIn(100, 720), 38, batches,
            titleOf = { batch -> batch.records.joinToString(", ") { it.modName } },
            subtitleOf = { batch ->
                Component.translatable(
                    "cph.history.batch_summary",
                    batch.records.size,
                    batch.records.maxOfOrNull(InstallationRecord::installedAt).orEmpty(),
                ).string
            },
            accentOf = { if (it.records.all(InstallationRecord::rolledBack)) 0xFF647386.toInt() else 0xFF62D9FF.toInt() },
            badgeOf = {
                if (it.records.all(InstallationRecord::rolledBack)) {
                    RowBadge(Component.translatable("cph.history.rolled_back"), 0xFF9AA7B5.toInt())
                } else null
            },
            actionsOf = { batch ->
                listOf(
                    RowAction(
                        label = { Component.translatable("cph.history.rollback") },
                        width = 72,
                        style = { TechButtonStyle.DANGER },
                        enabled = { batch.records.any { !it.rolledBack } },
                    ) { rollback(batch.id) },
                )
            },
        )
        list.x = 8
        addRenderableWidget(list)
        val backText = Component.translatable("cph.history.back")
        val back = TechButton.builder(backText) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(backText), 18).build()
        addFooterActions(back)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, message ?: Component.translatable("cph.history.subtitle", activeCount))
        if (!hasHistory) {
            guiGraphics.drawCenteredString(font, Component.translatable("cph.history.empty"), width / 2, height / 2, 0x91A4B8)
        }
    }

    private fun rollback(batchId: String) {
        val (restored, errors) = InstallationJournal.rollbackBatch(batchId)
        message = if (errors.isEmpty()) {
            Component.translatable("cph.download.rollback_done", restored)
        } else {
            Component.translatable("cph.download.rollback_failed", errors.size)
        }
        rebuildWidgets()
    }
}
