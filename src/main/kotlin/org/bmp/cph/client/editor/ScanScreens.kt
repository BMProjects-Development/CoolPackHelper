package org.bmp.cph.client.editor

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import org.bmp.cph.config.ConfigManager
import org.bmp.cph.client.curseforge.CurseForgeApiSupport
import java.net.URI
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
        keyField = StableEditBox(font, x, 92, w, 20, tr("curseforge.key")).also {
            it.value = ConfigManager.loadAuthorSettings().curseForgeApiKey.orEmpty()
            it.setMaxLength(512)
            it.setFormatter { value, _ -> FormattedCharSequence.forward("•".repeat(value.length), Style.EMPTY) }
            addRenderableWidget(it)
        }
        addRenderableWidget(
            TechButton.builder(rememberText()) { button -> remember = !remember; button.message = rememberText() }
                .style(TechButtonStyle.SECONDARY).bounds(x, 121, compactButtonWidth(rememberText(), 110, w), 18).build()
        )
        val back = TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("back")), 18).build()
        val help = TechButton.builder(tr("curseforge.key.help")) { openKeyHelp() }
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("curseforge.key.help.hint")))
            .style(TechButtonStyle.GHOST).bounds(0, 0, compactButtonWidth(tr("curseforge.key.help"), 88), 18).build()
        val start = TechButton.builder(tr("scan.start")) { start() }.style(TechButtonStyle.PRIMARY)
            .bounds(0, 0, compactButtonWidth(tr("scan.start"), 80), 18).build()
        addFooterActions(back, help, start)
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
        val key = CurseForgeApiSupport.normalizeKey(keyField.value)
        if (key.isBlank()) {
            Minecraft.getInstance().let {
                SystemToast.add(it.toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, tr("curseforge.title"), tr("curseforge.key.required"))
            }
            return
        }
        ConfigManager.saveAuthorSettings(ConfigManager.loadAuthorSettings().copy(curseForgeApiKey = if (remember) key else null))
        minecraft?.setScreen(ScanScreen(previousScreen, session, ScanPlatform.CURSEFORGE, key))
    }

    private fun openKeyHelp() {
        ConfirmLinkScreen.confirmLinkNow(this, URI.create(CurseForgeApiSupport.API_KEY_HELP_URL), true)
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
    private var filter = ScanResultFilter.NOT_FOUND
    private val selected = mutableSetOf<String>()
    private lateinit var importButton: TechButton

    override fun init() {
        if (!started) {
            started = true
            PlatformScanner.scanAsync(platform, apiKey).whenComplete { value, exception ->
                val completed = value ?: PlatformScanReport(
                    platform, emptyList(), exception?.message ?: tr("error.unknown").string,
                )
                Minecraft.getInstance().execute {
                    report = completed
                    completed.items.filter { it.status == PlatformMatchStatus.NOT_FOUND }.forEach { selected += it.artifact.fileName }
                    filter = if (completed.items.any { it.status == PlatformMatchStatus.NOT_FOUND }) ScanResultFilter.NOT_FOUND else ScanResultFilter.ALL
                    if (Minecraft.getInstance().screen === this) rebuildWidgets()
                }
            }
        }
        val completed = report ?: return
        if (completed.error != null) {
            initErrorActions()
            return
        }
        val w = (width - 24).coerceAtMost(720)
        val x = (width - w) / 2
        val counts = ScanResultFilter.entries.associateWith { candidate -> completed.items.count(candidate::accepts) }
        val tabGap = 4
        val tabWidth = (w - tabGap * 3) / 4
        ScanResultFilter.entries.forEachIndexed { index, candidate ->
            addRenderableWidget(
                TechButton.builder(tr("scan.filter.${candidate.name.lowercase()}", counts.getValue(candidate))) {
                    filter = candidate
                    rebuildWidgets()
                }.style(if (filter == candidate) TechButtonStyle.PRIMARY else TechButtonStyle.GHOST)
                    .bounds(x + index * (tabWidth + tabGap), 53, tabWidth, 18)
                    .build()
            )
        }
        val visibleItems = completed.items.filter(filter::accepts)
        val listTop = 76
        val listBottom = height - 32
        val listWidth = (width - 16).coerceAtLeast(120)
        val list = StyledActionList(
            minecraft ?: Minecraft.getInstance(),
            listWidth,
            (listBottom - listTop).coerceAtLeast(35),
            listTop,
            (listWidth - 18).coerceIn(100, 720),
            39,
            visibleItems,
            titleOf = { it.artifact.name },
            subtitleOf = { it.artifact.fileName },
            accentOf = {
                when (it.status) {
                    PlatformMatchStatus.FOUND -> 0xFF69E09B.toInt()
                    PlatformMatchStatus.NOT_FOUND -> 0xFFFFBE62.toInt()
                    PlatformMatchStatus.UNKNOWN -> 0xFFFF7777.toInt()
                }
            },
            badgeOf = {
                when (it.status) {
                    PlatformMatchStatus.FOUND -> RowBadge(tr("scan.found"), 0x69E09B)
                    PlatformMatchStatus.NOT_FOUND -> RowBadge(tr("scan.not_found"), 0xFFBE62)
                    PlatformMatchStatus.UNKNOWN -> RowBadge(tr("scan.unknown"), 0xFF7777)
                }
            },
            actionsOf = { item ->
                if (item.status != PlatformMatchStatus.NOT_FOUND) emptyList() else listOf(
                    RowAction(
                        label = { Component.literal(if (item.artifact.fileName in selected) "✓" else "+") },
                        width = 25,
                        style = {
                            if (item.artifact.fileName in selected) TechButtonStyle.PRIMARY else TechButtonStyle.GHOST
                        },
                    ) {
                        if (!selected.add(item.artifact.fileName)) selected.remove(item.artifact.fileName)
                        if (::importButton.isInitialized) {
                            importButton.message = tr("scan.import", selected.size)
                            importButton.active = selected.isNotEmpty()
                        }
                    }
                )
            },
        )
        list.x = 8
        addRenderableWidget(list)
        val importText = tr("scan.import", selected.size)
        importButton = TechButton.builder(importText) { importSelected() }.style(TechButtonStyle.PRIMARY)
            .bounds(0, 0, compactButtonWidth(importText, 84), 18).build()
        importButton.active = selected.isNotEmpty()
        val back = TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("back")), 18).build()
        addFooterActions(back, importButton)
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
        if (completed.error != null) {
            drawHeader(guiGraphics, tr("scan.failed", platform.displayName))
            val messageWidth = (width - 48).coerceAtMost(620).coerceAtLeast(80)
            font.split(Component.literal(completed.error), messageWidth).take(4).forEachIndexed { index, line ->
                guiGraphics.drawCenteredString(font, line, width / 2, height / 2 - 28 + index * 11, 0xFFFF7777.toInt())
            }
            if (platform == ScanPlatform.CURSEFORGE) {
                font.split(tr("curseforge.scan.permission"), messageWidth).take(3).forEachIndexed { index, line ->
                    guiGraphics.drawCenteredString(font, line, width / 2, height / 2 + 26 + index * 11, 0x90A7BC)
                }
            }
            return
        }
        val found = completed.items.count { it.status == PlatformMatchStatus.FOUND }
        val missing = completed.items.count { it.status == PlatformMatchStatus.NOT_FOUND }
        val unknown = completed.items.count { it.status == PlatformMatchStatus.UNKNOWN }
        drawHeader(guiGraphics, tr("scan.summary", found, missing, unknown))
        val notice = tr("scan.exact_warning")
        guiGraphics.drawCenteredString(
            font, font.plainSubstrByWidth(notice.string, (width - 32).coerceAtLeast(40)),
            width / 2, 43, 0x75899D,
        )
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

    private fun openCurseForgeHelp() {
        ConfirmLinkScreen.confirmLinkNow(this, URI.create(CurseForgeApiSupport.API_KEY_HELP_URL), true)
    }

    private fun initErrorActions() {
        val back = TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("back")), 18).build()
        val retry = TechButton.builder(tr("scan.retry")) { retry() }.style(TechButtonStyle.PRIMARY)
            .bounds(0, 0, compactButtonWidth(tr("scan.retry"), 74), 18).build()
        val actions = mutableListOf(back)
        if (platform == ScanPlatform.CURSEFORGE) {
            actions += TechButton.builder(tr("curseforge.key.change")) { changeCurseForgeKey() }
                .style(TechButtonStyle.SECONDARY)
                .bounds(0, 0, compactButtonWidth(tr("curseforge.key.change"), 82), 18).build()
            actions += TechButton.builder(tr("curseforge.key.help")) { openCurseForgeHelp() }
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("curseforge.key.help.hint")))
                .style(TechButtonStyle.GHOST)
                .bounds(0, 0, compactButtonWidth(tr("curseforge.key.help"), 88), 18).build()
        }
        actions += retry
        addFooterActions(*actions.toTypedArray())
    }

    private fun retry() {
        report = null
        started = false
        selected.clear()
        rebuildWidgets()
    }

    private fun changeCurseForgeKey() {
        minecraft?.setScreen(CurseForgeKeyScreen(previousScreen, session))
    }
}

private enum class ScanResultFilter {
    ALL,
    FOUND,
    NOT_FOUND,
    UNKNOWN;

    fun accepts(item: PlatformScanItem): Boolean = when (this) {
        ALL -> true
        FOUND -> item.status == PlatformMatchStatus.FOUND
        NOT_FOUND -> item.status == PlatformMatchStatus.NOT_FOUND
        UNKNOWN -> item.status == PlatformMatchStatus.UNKNOWN
    }
}

class LocalImportScreen(
    parent: Screen,
    private val session: EditorSession,
) : EditorScreenBase(tr("import.title"), parent) {
    private var started = false
    private var artifacts: List<LocalModArtifact>? = null
    private var errorMessage: String? = null

    override fun init() {
        if (!started) {
            started = true
            CompletableFuture.supplyAsync(PlatformScanner::inspectModsFolder).whenComplete { result, exception ->
                Minecraft.getInstance().execute {
                    artifacts = result ?: emptyList()
                    errorMessage = exception?.cause?.message ?: exception?.message
                    if (Minecraft.getInstance().screen === this) rebuildWidgets()
                }
            }
        }
        val result = artifacts ?: return
        val w = (width - 30).coerceAtMost(540)
        val x = (width - w) / 2
        val importText = tr("import.add", result.size)
        val import = TechButton.builder(importText) {
                val added = session.addDrafts(result.map(LocalModArtifact::toDraft))
                Minecraft.getInstance().let {
                    SystemToast.add(it.toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, tr("scan.imported"), tr("scan.imported.count", added))
                }
                onClose()
            }.style(TechButtonStyle.PRIMARY).bounds(0, 0, compactButtonWidth(importText, 84), 18).build()
        import.active = result.isNotEmpty()
        val back = TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("back")), 18).build()
        addFooterActions(back, import)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr(if (artifacts == null) "import.working" else "import.ready", artifacts?.size ?: 0))
        val message = errorMessage?.let { tr("import.failed", it) } ?: tr("import.hint")
        font.split(message, (width - 40).coerceAtLeast(40)).take(3).forEachIndexed { index, line ->
            guiGraphics.drawCenteredString(font, line, width / 2, height / 2 + index * 10, if (errorMessage == null) 0x91A4B8 else 0xFFFF7777.toInt())
        }
    }

    override fun onClose() {
        super.onClose()
    }
}
