package org.bmp.cph.client.download

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.client.ModCheckResult
import org.bmp.cph.client.editor.EditorScreenBase
import org.bmp.cph.client.editor.RowAction
import org.bmp.cph.client.editor.RowBadge
import org.bmp.cph.client.editor.StyledActionList
import org.bmp.cph.client.editor.TechButton
import org.bmp.cph.client.editor.TechButtonStyle
import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.DownloadTrustLevel
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.math.roundToInt

class SecureDownloadScreen private constructor(
    parent: Screen,
    private val requests: List<DownloadRequest>,
    private val bulk: Boolean,
) : EditorScreenBase(Component.translatable("cph.download.title"), parent) {
    private var resolutionStarted = false
    private var resolutions: List<ResolvedDownload>? = null
    private var running = false
    private var progress: DownloadProgress? = null
    private var results: List<InstallResult>? = null
    private var batchId: String? = null
    private var rollbackMessage: Component? = null

    override fun init() {
        if (!resolutionStarted) beginResolution()
        val resolved = resolutions ?: return
        val contentWidth = (width - 24).coerceAtMost(720)
        val left = (width - contentWidth) / 2
        val listWidth = (width - 16).coerceAtLeast(120)
        val footerTop = height - 30
        val listTop = 64
        val list = StyledActionList(
            minecraft ?: Minecraft.getInstance(),
            listWidth,
            (footerTop - listTop).coerceAtLeast(38),
            listTop,
            (listWidth - 18).coerceIn(100, 720),
            38,
            resolved,
            titleOf = { it.mod.displayName() },
            subtitleOf = { value -> resultFor(value)?.message ?: resolutionSubtitle(value) },
            accentOf = { value -> resultFor(value)?.let { if (it.success) 0xFF69E09B.toInt() else 0xFFFF6B78.toInt() } ?: trustColor(value) },
            badgeOf = { value ->
                resultFor(value)?.let {
                    if (it.success) RowBadge(Component.translatable("cph.download.status.installed"), 0xFF69E09B.toInt())
                    else RowBadge(Component.translatable("cph.download.status.failed"), 0xFFFF6B78.toInt())
                } ?: RowBadge(trustLabel(value), trustColor(value) and 0xFFFFFF)
            },
            iconOf = { it.mod.iconUrl },
            actionsOf = { value ->
                val actions = mutableListOf<RowAction>()
                if (bulk && value.status == DownloadResolutionStatus.READY && value.trust != DownloadTrustLevel.PLATFORM) {
                    actions += RowAction(
                        label = { Component.translatable("cph.download.review") },
                        width = 66,
                        style = { TechButtonStyle.SECONDARY },
                    ) { minecraft?.setScreen(SecureDownloadScreen(this, listOf(DownloadRequest(value.mod, value.source)), false)) }
                }
                val page = value.pageUri
                if (page != null) actions += RowAction(
                        label = { Component.translatable("cph.download.open_page") },
                        width = 74,
                        style = { TechButtonStyle.GHOST },
                    ) { ConfirmLinkScreen.confirmLinkNow(this, page, true) }
                actions
            },
        )
        list.x = 8
        addRenderableWidget(list)

        when {
            running -> Unit
            results == null -> addInstallFooter()
            else -> addResultFooter()
        }
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val resolved = resolutions
        val completed = results
        val subtitle = when {
            resolved == null -> Component.translatable("cph.download.resolving")
            running -> Component.translatable("cph.download.installing")
            completed != null -> Component.translatable(
                "cph.download.completed",
                completed.count(InstallResult::success),
                completed.count { !it.success },
            )
            bulk -> Component.translatable("cph.download.review_bulk")
            else -> Component.translatable("cph.download.review_single")
        }
        drawHeader(guiGraphics, subtitle)
        if (resolved == null) drawActivity(guiGraphics)
        if (running) drawProgress(guiGraphics)
        if (resolved != null && !running) {
            val noticeKey = when {
                bulk -> "cph.download.notice.bulk"
                resolved.any { it.trust == DownloadTrustLevel.UNVERIFIED } -> "cph.download.notice.unverified"
                resolved.any { it.trust == DownloadTrustLevel.REPOSITORY } -> "cph.download.notice.repository"
                else -> "cph.download.notice.platform"
            }
            guiGraphics.drawCenteredString(font, Component.translatable(noticeKey), width / 2, 51, 0x9EB1C4)
        }
        rollbackMessage?.let { guiGraphics.drawCenteredString(font, it, width / 2, height - 55, 0xAFC0D1) }
    }

    override fun onClose() {
        if (!running) super.onClose()
    }

    private fun beginResolution() {
        resolutionStarted = true
        val futures = requests.map(DownloadResolver::resolveAsync)
        CompletableFuture.allOf(*futures.toTypedArray()).whenComplete { _, _ ->
            val values = futures.mapIndexed { index, future ->
                try {
                    future.join()
                } catch (exception: Exception) {
                    ResolvedDownload(
                        requests[index].mod,
                        requests[index].source ?: DownloadLink(),
                        (requests[index].source ?: DownloadLink()).resolvedType(),
                        DownloadTrustLevel.UNVERIFIED,
                        DownloadResolutionStatus.FAILED,
                        message = exception.cause?.message ?: exception.message
                            ?: Component.translatable("cph.download.error.resolution_failed").string,
                    )
                }
            }
            Minecraft.getInstance().execute {
                resolutions = values
                if (Minecraft.getInstance().screen === this) rebuildWidgets()
            }
        }
    }

    private fun addInstallFooter() {
        val installable = installableDownloads()
        val installText = Component.translatable("cph.download.install", installable.size)
        val install = TechButton.builder(installText) { installAll() }
            .style(TechButtonStyle.PRIMARY)
            .bounds(0, 0, compactButtonWidth(installText, 76), 18)
            .build()
        install.active = installable.isNotEmpty()
        val cancelText = Component.translatable("cph.download.cancel")
        val cancel = TechButton.builder(cancelText) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(cancelText), 18).build()
        addFooterActions(cancel, install)
    }

    private fun addResultFooter() {
        val batch = batchId
        val successful = results.orEmpty().any(InstallResult::success)
        val rollbackText = Component.translatable("cph.download.rollback")
        val rollback = TechButton.builder(rollbackText) { rollback() }
            .style(TechButtonStyle.DANGER)
            .bounds(0, 0, compactButtonWidth(rollbackText, 78), 18)
            .build()
        rollback.active = successful && batch != null
        val folderText = Component.translatable("cph.download.open_mods")
        val folder = TechButton.builder(folderText) { openModsFolder() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(folderText, 76), 18).build()
        val closeText = Component.translatable("cph.download.close")
        val close = TechButton.builder(closeText) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(closeText), 18).build()
        val exitText = Component.translatable("cph.download.exit_restart")
        val exit = TechButton.builder(exitText) { minecraft?.stop() }
            .style(TechButtonStyle.PRIMARY)
            .bounds(0, 0, compactButtonWidth(exitText, 82), 18)
            .build()
        exit.active = successful
        addFooterActions(close, rollback, folder, exit)
    }

    private fun installAll() {
        val downloads = installableDownloads()
        if (downloads.isEmpty() || running) return
        running = true
        results = null
        batchId = UUID.randomUUID().toString()
        rebuildWidgets()
        CompletableFuture.supplyAsync {
            downloads.map { download ->
                SecureDownloadManager.install(download, batchId!!) { value ->
                    Minecraft.getInstance().execute { progress = value }
                }
            }
        }.whenComplete { values, exception ->
            Minecraft.getInstance().execute {
                results = values ?: downloads.map {
                    InstallResult(
                        it, false, message = exception?.message
                            ?: Component.translatable("cph.download.error.installation_failed").string,
                    )
                }
                running = false
                progress = null
                if (Minecraft.getInstance().screen === this) rebuildWidgets()
            }
        }
    }

    private fun rollback() {
        val batch = batchId ?: return
        val (restored, errors) = InstallationJournal.rollbackBatch(batch)
        rollbackMessage = if (errors.isEmpty()) {
            Component.translatable("cph.download.rollback_done", restored)
        } else {
            Component.translatable("cph.download.rollback_failed", errors.size)
        }
        batchId = null
        rebuildWidgets()
    }

    private fun installableDownloads(): List<ResolvedDownload> = resolutions.orEmpty().filter {
        it.status == DownloadResolutionStatus.READY && (!bulk || it.trust == DownloadTrustLevel.PLATFORM)
    }

    private fun resultFor(download: ResolvedDownload): InstallResult? = results.orEmpty().firstOrNull { it.download == download }

    private fun resolutionSubtitle(value: ResolvedDownload): String = when (value.status) {
        DownloadResolutionStatus.READY -> listOfNotNull(
            value.source.displayLabel(),
            value.fileName,
            value.sizeBytes?.let(::formatBytes),
            value.strongestHash()?.first,
        ).joinToString(" · ")
        DownloadResolutionStatus.PAGE_ONLY -> value.message ?: Component.translatable("cph.download.page_only").string
        DownloadResolutionStatus.FAILED -> value.message ?: Component.translatable("cph.download.failed").string
    }

    private fun trustLabel(value: ResolvedDownload): Component = when {
        value.status == DownloadResolutionStatus.FAILED -> Component.translatable("cph.download.status.failed")
        value.status == DownloadResolutionStatus.PAGE_ONLY -> Component.translatable("cph.download.status.page")
        value.trust == DownloadTrustLevel.PLATFORM -> Component.translatable("cph.download.trust.platform")
        value.trust == DownloadTrustLevel.REPOSITORY -> Component.translatable("cph.download.trust.repository")
        else -> Component.translatable("cph.download.trust.unverified")
    }

    private fun trustColor(value: ResolvedDownload): Int = when {
        value.status == DownloadResolutionStatus.FAILED -> 0xFFFF6B78.toInt()
        value.status == DownloadResolutionStatus.PAGE_ONLY -> 0xFF8499AF.toInt()
        value.trust == DownloadTrustLevel.PLATFORM -> 0xFF69E09B.toInt()
        value.trust == DownloadTrustLevel.REPOSITORY -> 0xFFFFBE62.toInt()
        else -> 0xFFFF7777.toInt()
    }

    private fun drawActivity(guiGraphics: GuiGraphics) {
        val center = width / 2
        val pulse = ((kotlin.math.sin(Util.getMillis() / 180.0) + 1.0) * 36).roundToInt()
        guiGraphics.fill(center - 72, height / 2, center + 72, height / 2 + 2, 0x335A7890)
        guiGraphics.fill(center - 72, height / 2, center - 72 + pulse * 2, height / 2 + 2, accent)
    }

    private fun drawProgress(guiGraphics: GuiGraphics) {
        val current = progress ?: return
        val barWidth = (width - 48).coerceAtMost(620)
        val left = (width - barWidth) / 2
        val total = current.totalBytes
        val ratio = if (total == null || total <= 0) 0.15 else (current.downloadedBytes.toDouble() / total).coerceIn(0.0, 1.0)
        guiGraphics.fill(left, 47, left + barWidth, 51, 0x66344455)
        guiGraphics.fill(left, 47, left + (barWidth * ratio).roundToInt(), 51, accent)
        guiGraphics.drawCenteredString(
            font,
            "${current.modName} · ${formatBytes(current.downloadedBytes)}${total?.let { " / ${formatBytes(it)}" }.orEmpty()}",
            width / 2,
            53,
            0x91A7BC,
        )
    }

    private fun openModsFolder() {
        val directory = net.neoforged.fml.loading.FMLPaths.MODSDIR.get()
        java.nio.file.Files.createDirectories(directory)
        Util.getPlatform().openPath(directory)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KiB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    companion object {
        fun forSource(parent: Screen, result: ModCheckResult, source: DownloadLink): SecureDownloadScreen =
            SecureDownloadScreen(parent, listOf(DownloadRequest(result.mod, source)), bulk = false)

        fun forMissing(parent: Screen, results: List<ModCheckResult>): SecureDownloadScreen =
            SecureDownloadScreen(parent, results.map { DownloadRequest(it.mod) }, bulk = true)
    }
}
