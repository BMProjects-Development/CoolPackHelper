package org.bmp.cph.client.editor

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import org.bmp.cph.config.ConfigManager
import java.util.concurrent.CompletableFuture
import kotlin.math.sin

class CurseForgeKeyScreen(
    parent: Screen,
    private val session: EditorSession,
) : EditorScreenBase(tr("curseforge.title"), parent) {
    private lateinit var keyField: EditBox
    private var remember = true

    override fun init() {
        val w = (width - 30).coerceAtMost(560)
        val x = (width - w) / 2
        keyField = EditBox(font, x, 92, w, 20, tr("curseforge.key")).also {
            it.value = ConfigManager.loadAuthorSettings().curseForgeApiKey.orEmpty()
            it.setMaxLength(512)
            it.setFormatter { value, _ -> FormattedCharSequence.forward("•".repeat(value.length), Style.EMPTY) }
            addRenderableWidget(it)
        }
        addRenderableWidget(
            TechButton.builder(rememberText()) { button -> remember = !remember; button.message = rememberText() }
                .bounds(x, 121, w, 20).build()
        )
        val half = (w - 8) / 2
        addRenderableWidget(
            TechButton.builder(tr("scan.start")) { start() }.style(TechButtonStyle.PRIMARY).bounds(x, height - 27, half, 20).build()
        )
        addRenderableWidget(TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST).bounds(x + half + 8, height - 27, half, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("curseforge.subtitle"))
        guiGraphics.drawString(font, tr("curseforge.key"), keyField.x, keyField.y - 12, 0x90A7BC, false)
        font.split(tr("curseforge.security"), (width - 40).coerceAtMost(560)).take(4).forEachIndexed { index, line ->
            guiGraphics.drawCenteredString(font, line, width / 2, 157 + index * 11, 0x74889D)
        }
    }

    private fun rememberText() = tr(if (remember) "curseforge.remember.on" else "curseforge.remember.off")

    private fun start() {
        val key = keyField.value.trim()
        if (key.isBlank()) {
            Minecraft.getInstance().let {
                SystemToast.add(it.toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, tr("curseforge.title"), tr("curseforge.key.required"))
            }
            return
        }
        ConfigManager.saveAuthorSettings(ConfigManager.loadAuthorSettings().copy(curseForgeApiKey = if (remember) key else null))
        minecraft?.setScreen(ScanScreen(previousScreen, session, ScanPlatform.CURSEFORGE, key))
    }
}

class ScanScreen(
    parent: Screen,
    private val session: EditorSession,
    private val platform: ScanPlatform,
    private val apiKey: String? = null,
) : EditorScreenBase(tr("scan.title", platform.displayName), parent) {
    private var started = false
    private var report: PlatformScanReport? = null
    private var page = 0
    private val selected = mutableSetOf<String>()

    override fun init() {
        if (!started) {
            started = true
            PlatformScanner.scanAsync(platform, apiKey).whenComplete { value, exception ->
                val completed = value ?: PlatformScanReport(platform, emptyList(), exception?.message ?: "Unknown error")
                Minecraft.getInstance().execute {
                    report = completed
                    completed.items.filter { it.status == PlatformMatchStatus.NOT_FOUND }.forEach { selected += it.artifact.fileName }
                    if (Minecraft.getInstance().screen === this) rebuildWidgets()
                }
            }
        }
        val completed = report ?: return
        val pageSize = ((height - 130) / 31).coerceAtLeast(1)
        val pages = ((completed.items.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        page = page.coerceIn(0, pages - 1)
        val w = (width - 24).coerceAtMost(720)
        val x = (width - w) / 2
        completed.items.drop(page * pageSize).take(pageSize).forEachIndexed { row, item ->
            val y = 61 + row * 31
            val selectable = item.status == PlatformMatchStatus.NOT_FOUND
            val marker = if (item.artifact.fileName in selected) "✓" else "+"
            val button = TechButton.builder(Component.literal(marker)) {
                if (!selected.add(item.artifact.fileName)) selected.remove(item.artifact.fileName)
                rebuildWidgets()
            }.bounds(x + w - 25, y + 3, 22, 20).build()
            button.active = selectable
            addRenderableWidget(button)
        }
        val third = (w - 12) / 3
        val navY = height - 53
        val previous = TechButton.builder(tr("previous")) { page--; rebuildWidgets() }.bounds(x, navY, third, 20).build()
        previous.active = page > 0
        addRenderableWidget(previous)
        addRenderableWidget(
            TechButton.builder(tr("scan.import", selected.size)) { importSelected() }.style(TechButtonStyle.PRIMARY)
                .bounds(x + third + 6, navY, third, 20).build()
        )
        val next = TechButton.builder(tr("next")) { page++; rebuildWidgets() }
            .bounds(x + (third + 6) * 2, navY, third, 20).build()
        next.active = page < pages - 1
        addRenderableWidget(next)
        addRenderableWidget(TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST).bounds(x, height - 27, w, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val completed = report
        if (completed == null) {
            drawHeader(guiGraphics, tr("scan.working"))
            val center = width / 2
            val pulse = ((sin(Util.getMillis() / 180.0) + 1.0) * 35).toInt()
            guiGraphics.fill(center - 72, height / 2, center + 72, height / 2 + 2, 0x335A7890)
            guiGraphics.fill(center - 72, height / 2, center - 72 + pulse * 2, height / 2 + 2, accent)
            guiGraphics.drawCenteredString(font, tr("scan.hashing"), center, height / 2 + 13, 0x91A6BA)
            return
        }
        val found = completed.items.count { it.status == PlatformMatchStatus.FOUND }
        val missing = completed.items.count { it.status == PlatformMatchStatus.NOT_FOUND }
        val unknown = completed.items.count { it.status == PlatformMatchStatus.UNKNOWN }
        drawHeader(guiGraphics, tr("scan.summary", found, missing, unknown))
        completed.error?.let {
            guiGraphics.drawCenteredString(font, font.plainSubstrByWidth(it, width - 32), width / 2, 45, 0xFF7777)
        }
        val pageSize = ((height - 130) / 31).coerceAtLeast(1)
        val w = (width - 24).coerceAtMost(720)
        val x = (width - w) / 2
        completed.items.drop(page * pageSize).take(pageSize).forEachIndexed { row, item ->
            val y = 61 + row * 31
            drawPanel(guiGraphics, x, y, x + w, y + 26)
            val (label, color) = when (item.status) {
                PlatformMatchStatus.FOUND -> tr("scan.found") to 0x69E09B
                PlatformMatchStatus.NOT_FOUND -> tr("scan.not_found") to 0xFFBE62
                PlatformMatchStatus.UNKNOWN -> tr("scan.unknown") to 0xFF7777
            }
            guiGraphics.drawString(font, font.plainSubstrByWidth(item.artifact.name, w - 165), x + 8, y + 5, 0xE7F3FF, false)
            guiGraphics.drawString(font, font.plainSubstrByWidth(item.artifact.fileName, w - 165), x + 8, y + 16, 0x75899D, false)
            guiGraphics.drawString(font, label, x + w - 31 - font.width(label), y + 9, color, false)
        }
        guiGraphics.drawCenteredString(font, tr("scan.exact_warning"), width / 2, 46, 0x75899D)
    }

    override fun onClose() {
        super.onClose()
    }

    private fun importSelected() {
        val drafts = report?.items.orEmpty()
            .filter { it.status == PlatformMatchStatus.NOT_FOUND && it.artifact.fileName in selected }
            .map { it.artifact.toDraft() }
        val added = session.addDrafts(drafts)
        Minecraft.getInstance().let {
            SystemToast.add(it.toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, tr("scan.imported"), tr("scan.imported.count", added))
        }
        onClose()
    }
}

class LocalImportScreen(
    parent: Screen,
    private val session: EditorSession,
) : EditorScreenBase(tr("import.title"), parent) {
    private var started = false
    private var artifacts: List<LocalModArtifact>? = null

    override fun init() {
        if (!started) {
            started = true
            CompletableFuture.supplyAsync(PlatformScanner::inspectModsFolder).whenComplete { result, exception ->
                Minecraft.getInstance().execute {
                    artifacts = result ?: emptyList()
                    if (Minecraft.getInstance().screen === this) rebuildWidgets()
                }
            }
        }
        val result = artifacts ?: return
        val w = (width - 30).coerceAtMost(540)
        val x = (width - w) / 2
        val half = (w - 8) / 2
        addRenderableWidget(
            TechButton.builder(tr("import.add", result.size)) {
                val added = session.addDrafts(result.map(LocalModArtifact::toDraft))
                Minecraft.getInstance().let {
                    SystemToast.add(it.toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, tr("scan.imported"), tr("scan.imported.count", added))
                }
                onClose()
            }.style(TechButtonStyle.PRIMARY).bounds(x, height - 27, half, 20).build()
        )
        addRenderableWidget(TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST).bounds(x + half + 8, height - 27, half, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr(if (artifacts == null) "import.working" else "import.ready", artifacts?.size ?: 0))
        guiGraphics.drawCenteredString(font, tr("import.hint"), width / 2, height / 2, 0x91A4B8)
    }

    override fun onClose() {
        super.onClose()
    }
}
