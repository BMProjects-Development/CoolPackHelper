package org.bmp.cph.client

import com.mojang.blaze3d.vertex.Tesselator
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.neoforged.neoforge.client.gui.widget.ScrollPanel
import org.bmp.cph.config.ResolvedMenuText
import org.bmp.cph.client.download.SecureDownloadScreen
import org.bmp.cph.client.editor.EditorScreenBase
import org.bmp.cph.client.editor.EditorRect
import org.bmp.cph.client.editor.RowAction
import org.bmp.cph.client.editor.RowBadge
import org.bmp.cph.client.editor.StyledActionList
import org.bmp.cph.client.editor.TechButton
import org.bmp.cph.client.editor.TechButtonStyle
import org.bmp.cph.config.RequiredMod
import org.bmp.cph.config.validHttpUri
import java.net.URI

class ModDetailsScreen(
    parent: Screen,
    private val result: ModCheckResult,
    private val text: ResolvedMenuText,
) : EditorScreenBase(
    Component.literal(text.detailsTitle.replace("{mod}", result.mod.displayName())),
    parent,
) {
    private var panelLeft = 0
    private var panelTop = 54
    private var panelWidth = 0
    private var panelBottom = 100

    override fun init() {
        val contentWidth = (width - 24).coerceIn(120, 720)
        val left = (width - contentWidth) / 2
        val top = 41
        val footerTop = height - 30
        panelLeft = left
        panelTop = top
        panelWidth = contentWidth
        panelBottom = footerTop
        addRenderableWidget(
            DetailsPanel(
                minecraft ?: Minecraft.getInstance(),
                contentWidth,
                (footerTop - top).coerceAtLeast(44),
                top,
                left,
                detailsLines(contentWidth - if (result.mod.iconUrl.isNullOrBlank()) 20 else 68),
                result.mod.iconUrl,
            )
        )

        val downloadText = Component.literal(downloadLabel())
        val downloadButton = TechButton.builder(downloadText) { openDownload() }
                .style(TechButtonStyle.PRIMARY)
                .tooltip(Tooltip.create(Component.literal(result.mod.availableLinks().joinToString("\n") { it.displayLabel() })))
                .bounds(0, 0, compactButtonWidth(downloadText, 78), 18)
                .build()
        downloadButton.active = result.mod.availableLinks().isNotEmpty()
        val backText = Component.literal(text.backButton)
        val back = TechButton.builder(backText) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(backText), 18).build()
        val projectLinks = projectLinkActions(result.mod)
        val project = projectLinks.takeIf(List<*>::isNotEmpty)?.let {
            val label = Component.translatable("cph.project_links.open", it.size)
            TechButton.builder(label) { minecraft?.setScreen(ProjectLinksScreen(this, result.mod)) }.style(TechButtonStyle.GHOST)
                .tooltip(Tooltip.create(Component.translatable("cph.project_links.open.hint")))
                .bounds(0, 0, compactButtonWidth(label, 72), 18).build()
        }
        addFooterActions(*listOfNotNull(back, project, downloadButton).toTypedArray())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics)
        drawPanel(guiGraphics, panelLeft, panelTop, panelLeft + panelWidth, panelBottom)
    }

    private fun detailsLines(maxWidth: Int): List<FormattedCharSequence> = buildList {
        val status = when (result.status) {
            RequirementStatus.MISSING -> text.missingStatus
            RequirementStatus.WRONG_VERSION -> text.wrongVersionStatus
                .replace("{installed}", result.installedVersion.orEmpty())
                .replace("{required}", result.mod.versionRange.orEmpty())
        }
        add(Component.literal(status).visualOrderText)
        result.mod.modId?.takeIf { it.isNotBlank() }?.let {
            add(Component.literal(text.modIdLabel.replace("{value}", it)).visualOrderText)
        }
        result.installedVersion?.takeIf { it.isNotBlank() }?.let {
            add(Component.literal(text.installedVersionLabel.replace("{value}", it)).visualOrderText)
        }
        result.mod.versionRange?.takeIf { it.isNotBlank() }?.let {
            add(Component.literal(text.requiredVersionLabel.replace("{value}", it)).visualOrderText)
        }
        result.mod.authors.orEmpty().filter(String::isNotBlank).takeIf { it.isNotEmpty() }?.let { authors ->
            font.split(Component.literal(text.authorsLabel.replace("{value}", authors.joinToString(", "))), maxWidth).forEach(::add)
        }
        result.mod.license?.takeIf(String::isNotBlank)?.let {
            font.split(Component.literal(text.licenseLabel.replace("{value}", it)), maxWidth).forEach(::add)
        }
        result.mod.resolvedProjectLinks().homepage?.takeIf(String::isNotBlank)?.let {
            font.split(Component.literal(text.projectPageLabel.replace("{value}", it)), maxWidth).forEach(::add)
        }
        result.mod.availableLinks().takeIf(List<*>::isNotEmpty)?.let { links ->
            links.forEach { link ->
                val target = link.downloadUrl?.takeIf(String::isNotBlank) ?: link.url.orEmpty()
                font.split(Component.literal("${link.displayLabel()}: $target"), maxWidth).forEach(::add)
            }
        }
        add(Component.empty().visualOrderText)
        val description = result.mod.localizedDescription(text.languageCode, text.fallbackLanguage)
        font.split(Component.literal(description), maxWidth.coerceAtLeast(40)).forEach {
            add(it)
        }
    }

    private fun downloadLabel(): String {
        val count = result.mod.availableLinks().size
        return if (count <= 1) text.downloadButton else text.chooseSourceButton.replace("{count}", count.toString())
    }

    private fun openDownload() {
        val links = result.mod.availableLinks()
        if (links.size == 1) {
            minecraft?.setScreen(SecureDownloadScreen.forSource(this, result, links.first()))
        } else if (links.isNotEmpty()) {
            minecraft?.setScreen(DownloadSourcesScreen(this, result, links, text))
        }
    }

    private class DetailsPanel(
        minecraft: Minecraft,
        width: Int,
        height: Int,
        top: Int,
        left: Int,
        private val lines: List<FormattedCharSequence>,
        private val iconUrl: String?,
    ) : ScrollPanel(minecraft, width, height, top, left) {
        override fun getContentHeight(): Int = (lines.size * 12 + 12).coerceAtLeast(height - 8)

        override fun drawPanel(
            guiGraphics: GuiGraphics,
            entryRight: Int,
            relativeY: Int,
            tess: Tesselator,
            mouseX: Int,
            mouseY: Int,
        ) {
            val textLeft = left + if (iconUrl.isNullOrBlank()) 8 else 56
            if (!iconUrl.isNullOrBlank()) {
                guiGraphics.fill(left + 8, relativeY + 6, left + 48, relativeY + 46, 0x80303A49.toInt())
                ProjectIconCache.texture(iconUrl)?.let { icon ->
                    guiGraphics.blit(
                        icon.location, left + 8, relativeY + 6, 40, 40, 0f, 0f,
                        icon.width, icon.height, icon.width, icon.height,
                    )
                }
            }
            lines.forEachIndexed { index, line ->
                guiGraphics.drawString(Minecraft.getInstance().font, line, textLeft, relativeY + 6 + index * 12, 0xD8D8D8, false)
            }
        }

        override fun narrationPriority(): NarratableEntry.NarrationPriority = NarratableEntry.NarrationPriority.NONE

        override fun updateNarration(output: NarrationElementOutput) = Unit
    }
}

private data class ProjectLinkAction(val label: Component, val url: String)

private fun projectLinkActions(mod: RequiredMod): List<ProjectLinkAction> = buildList {
    val links = mod.resolvedProjectLinks()
    fun addLink(key: String, url: String?) {
        validHttpUri(url)?.let { add(ProjectLinkAction(Component.translatable("cph.project_links.$key"), it.toString())) }
    }
    addLink("homepage", links.homepage)
    addLink("source", links.source)
    addLink("issues", links.issues)
    addLink("wiki", links.wiki)
    addLink("discord", links.discord)
    links.donations.orEmpty().forEach { donation ->
        validHttpUri(donation.url)?.let {
            add(ProjectLinkAction(
                donation.label?.takeIf(String::isNotBlank)?.let(Component::literal)
                    ?: Component.translatable("cph.project_links.donation"),
                it.toString(),
            ))
        }
    }
}.distinctBy { it.url }

private class ProjectLinksScreen(parent: Screen, private val mod: RequiredMod) :
    EditorScreenBase(Component.translatable("cph.project_links.title", mod.displayName()), parent) {
    private var dialog = EditorRect(0, 0, 0, 0)

    override fun usesModalBackground(): Boolean = true

    override fun init() {
        dialog = centeredModal(620, 330, 170)
        val actions = projectLinkActions(mod)
        val listWidth = (dialog.width - 12).coerceAtLeast(120)
        val list = StyledActionList(
            minecraft ?: Minecraft.getInstance(),
            listWidth,
            (dialog.height - 70).coerceAtLeast(38),
            dialog.top + 38,
            (listWidth - 10).coerceAtLeast(100),
            36,
            actions,
            titleOf = { it.label.string },
            subtitleOf = { it.url },
            accentOf = { 0xFF58C7E8.toInt() },
            badgeOf = { RowBadge(Component.literal("↗"), 0xFF78D8F4.toInt()) },
            actionsOf = { action -> listOf(
                RowAction(label = { Component.translatable("cph.project_links.open_action") }, width = 58) {
                    ConfirmLinkScreen.confirmLinkNow(this, URI.create(action.url), true)
                },
            ) },
        )
        list.x = dialog.left + 6
        addRenderableWidget(list)
        val back = TechButton.builder(Component.translatable("cph.editor.back")) { onClose() }
            .style(TechButtonStyle.GHOST).bounds(0, 0, 72, 18).build()
        addCompactActions(dialog.left + 8, dialog.right - 8, dialog.bottom - 23, back)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawModalFrame(guiGraphics, dialog, Component.translatable("cph.project_links.subtitle"))
    }
}
