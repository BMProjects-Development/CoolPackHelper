package org.bmp.cph.client

import com.mojang.blaze3d.vertex.Tesselator
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.neoforged.neoforge.client.gui.widget.ScrollPanel
import org.bmp.cph.client.download.SecureDownloadScreen
import org.bmp.cph.client.editor.EditorRect
import org.bmp.cph.client.editor.EditorScreenBase
import org.bmp.cph.client.editor.EditorTheme
import org.bmp.cph.client.editor.RowBadge
import org.bmp.cph.client.editor.RowMark
import org.bmp.cph.client.editor.StyledActionList
import org.bmp.cph.client.editor.TechButton
import org.bmp.cph.client.editor.TechButtonStyle
import org.bmp.cph.client.editor.drawRoundedOutline
import org.bmp.cph.client.editor.fillRoundedRect
import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.DownloadSourceType
import org.bmp.cph.config.ModCategory
import org.bmp.cph.config.RequiredMod
import org.bmp.cph.config.ResolvedMenuText
import org.bmp.cph.config.validHttpUri
import java.net.URI
import kotlin.math.min

class ModDetailsScreen(
    parent: Screen,
    private val result: ModCheckResult,
    private val text: ResolvedMenuText,
) : EditorScreenBase(
    Component.literal(text.detailsTitle.replace("{mod}", result.mod.displayName())),
    parent,
) {
    private var hero = EditorRect(0, 0, 0, 0)
    private var descriptionPanel = EditorRect(0, 0, 0, 0)
    private var resourcesPanel = EditorRect(0, 0, 0, 0)
    private var resources: List<DetailResource> = emptyList()

    override fun init() {
        val contentWidth = (width - 20).coerceAtLeast(220).coerceAtMost(780)
        val contentLeft = (width - contentWidth) / 2
        val contentBottom = (height - 31).coerceAtLeast(118)
        val heroHeight = if (height < 270) 58 else 70
        hero = EditorRect(contentLeft, 40, contentLeft + contentWidth, 40 + heroHeight)

        val bodyTop = hero.bottom + 7
        val bodyHeight = (contentBottom - bodyTop).coerceAtLeast(40)
        val stacked = contentWidth < 560 && bodyHeight >= 118
        if (stacked) {
            val gap = 7
            val resourceHeight = (bodyHeight * .42f).toInt().coerceAtLeast(48)
            val descriptionHeight = (bodyHeight - resourceHeight - gap).coerceAtLeast(48)
            descriptionPanel = EditorRect(contentLeft, bodyTop, contentLeft + contentWidth, bodyTop + descriptionHeight)
            resourcesPanel = EditorRect(contentLeft, descriptionPanel.bottom + gap, contentLeft + contentWidth, contentBottom)
        } else {
            val gap = 7
            val resourceWidth = if (contentWidth >= 560) {
                (contentWidth * .34f).toInt().coerceIn(210, 255)
            } else {
                (contentWidth * .42f).toInt().coerceAtLeast(92)
            }
            descriptionPanel = EditorRect(contentLeft, bodyTop, contentLeft + contentWidth - resourceWidth - gap, contentBottom)
            resourcesPanel = EditorRect(descriptionPanel.right + gap, bodyTop, contentLeft + contentWidth, contentBottom)
        }

        val detailsTop = descriptionPanel.top + 25
        val detailsHeight = (descriptionPanel.bottom - detailsTop - 5).coerceAtLeast(28)
        addRenderableWidget(
            DetailsPanel(
                minecraft ?: Minecraft.getInstance(),
                descriptionPanel.width - 4,
                detailsHeight,
                detailsTop,
                descriptionPanel.left + 2,
                detailsLines((descriptionPanel.width - 22).coerceAtLeast(40)),
            )
        )

        resources = detailResources(
            result.mod,
            result.mod.localizedDescription(text.languageCode, text.fallbackLanguage),
        )
        val resourceTop = resourcesPanel.top + 25
        val resourceHeight = resourcesPanel.bottom - resourceTop - 5
        if (resources.isNotEmpty() && resourceHeight >= 28) {
            StyledActionList(
                minecraft ?: Minecraft.getInstance(),
                resourcesPanel.width - 4,
                resourceHeight,
                resourceTop,
                (resourcesPanel.width - 12).coerceAtLeast(76),
                42,
                resources,
                titleOf = { it.title.string },
                subtitleOf = { it.shortAddress },
                accentOf = { it.brand.color },
                badgeOf = { RowBadge(Component.literal(if (it.download == null) "↗" else "↓"), it.brand.color) },
                markOf = { RowMark(Component.literal(it.brand.mark), it.brand.color) },
                onRowClick = ::openResource,
                rowTooltipOf = { Component.translatable("cph.mod_details.open.hint", it.url) },
            ).also {
                it.x = resourcesPanel.left + 2
                addRenderableWidget(it)
            }
        }

        val downloadText = Component.literal(downloadLabel())
        val downloadButton = TechButton.builder(downloadText) { openDownload() }
            .style(TechButtonStyle.PRIMARY)
            .tooltip(Tooltip.create(Component.literal(result.mod.availableLinks().joinToString("\n") { it.displayLabel() })))
            .bounds(0, 0, compactButtonWidth(downloadText, 78), 18)
            .build().also { it.active = result.mod.availableLinks().isNotEmpty() }
        val backText = Component.literal(text.backButton)
        val back = TechButton.builder(backText) { onClose() }
            .style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(backText), 18)
            .build()
        addFooterActions(back, downloadButton)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, Component.translatable("cph.mod_details.subtitle"))
        drawPanel(guiGraphics, hero.left, hero.top, hero.right, hero.bottom)
        drawPanel(guiGraphics, descriptionPanel.left, descriptionPanel.top, descriptionPanel.right, descriptionPanel.bottom)
        drawPanel(guiGraphics, resourcesPanel.left, resourcesPanel.top, resourcesPanel.right, resourcesPanel.bottom)
        renderHero(guiGraphics)
        guiGraphics.drawString(font, Component.translatable("cph.mod_details.information"), descriptionPanel.left + 10, descriptionPanel.top + 9, EditorTheme.TEXT, false)
        guiGraphics.drawString(font, Component.translatable("cph.mod_details.resources", resources.size), resourcesPanel.left + 10, resourcesPanel.top + 9, EditorTheme.TEXT, false)
        if (resources.isEmpty()) {
            guiGraphics.drawCenteredString(
                font,
                Component.translatable("cph.mod_details.resources.empty"),
                (resourcesPanel.left + resourcesPanel.right) / 2,
                (resourcesPanel.top + resourcesPanel.bottom) / 2,
                EditorTheme.TEXT_MUTED,
            )
        }
    }

    private fun renderHero(guiGraphics: GuiGraphics) {
        val iconSize = min(48, hero.height - 16).coerceAtLeast(28)
        val iconX = hero.left + 11
        val iconY = hero.top + (hero.height - iconSize) / 2
        drawRoundedOutline(guiGraphics, iconX, iconY, iconX + iconSize, iconY + iconSize, 7, EditorTheme.BORDER, 0xFF22252B.toInt())
        val icon = ProjectIconCache.texture(result.mod.iconUrl)
        if (icon != null) {
            guiGraphics.blit(icon.location, iconX + 2, iconY + 2, iconSize - 4, iconSize - 4, 0f, 0f, icon.width, icon.height, icon.width, icon.height)
        } else {
            val initial = result.mod.displayName().trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            guiGraphics.drawCenteredString(font, initial, iconX + iconSize / 2, iconY + iconSize / 2 - 4, EditorTheme.ACCENT)
        }

        val textX = iconX + iconSize + 11
        val nameWidth = (hero.right - 10 - textX).coerceAtLeast(30)
        guiGraphics.drawString(font, font.plainSubstrByWidth(result.mod.displayName(), nameWidth), textX, hero.top + 11, EditorTheme.TEXT, false)

        val category = result.mod.resolvedCategory()
        val categoryText = if (category == ModCategory.REQUIRED) text.requiredLabel else text.recommendedLabel
        val categoryColor = if (category == ModCategory.REQUIRED) 0xFFE46A6A.toInt() else 0xFFE0B85B.toInt()
        val categoryWidth = drawChip(guiGraphics, categoryText, textX, hero.top + 27, categoryColor, nameWidth)
        if (nameWidth - categoryWidth > 44) {
            drawChip(guiGraphics, requirementStatus(), textX + categoryWidth + 5, hero.top + 27, EditorTheme.ACCENT, nameWidth - categoryWidth - 5)
        }

        val identity = listOfNotNull(
            result.mod.modId?.takeIf(String::isNotBlank),
            result.mod.versionRange?.takeIf(String::isNotBlank)?.let { "v $it" },
            result.mod.authors.orEmpty().filter(String::isNotBlank).takeIf(List<*>::isNotEmpty)?.joinToString(", "),
        ).joinToString("  ·  ")
        if (identity.isNotBlank() && hero.height >= 66) {
            guiGraphics.drawString(font, font.plainSubstrByWidth(identity, nameWidth), textX, hero.top + 49, EditorTheme.TEXT_MUTED, false)
        }
    }

    private fun drawChip(guiGraphics: GuiGraphics, value: String, x: Int, y: Int, color: Int, maxWidth: Int): Int {
        val label = font.plainSubstrByWidth(value, (maxWidth - 12).coerceAtLeast(10))
        val chipWidth = (font.width(label) + 12).coerceAtMost(maxWidth)
        fillRoundedRect(guiGraphics, x, y, x + chipWidth, y + 16, 5, 0xFF24272D.toInt())
        guiGraphics.drawString(font, label, x + 6, y + 4, color, false)
        return chipWidth
    }

    private fun detailsLines(maxWidth: Int): List<DetailLine> = buildList {
        add(DetailLine(Component.translatable("cph.mod_details.description").visualOrderText, EditorTheme.ACCENT, 14))
        val description = result.mod.localizedDescription(text.languageCode, text.fallbackLanguage)
        val descriptionText = description.takeIf(String::isNotBlank)
            ?: Component.translatable("cph.mod_details.description.empty").string
        font.split(Component.literal(descriptionText), maxWidth).forEach { add(DetailLine(it, EditorTheme.TEXT, 11)) }
        add(DetailLine(Component.empty().visualOrderText, EditorTheme.TEXT, 8))
        add(DetailLine(Component.translatable("cph.mod_details.technical").visualOrderText, EditorTheme.ACCENT_PURPLE, 14))
        detailValues().forEach { value ->
            font.split(Component.literal(value), maxWidth).forEach { add(DetailLine(it, EditorTheme.TEXT_MUTED, 11)) }
        }
    }

    private fun detailValues(): List<String> = buildList {
        result.mod.modId?.takeIf(String::isNotBlank)?.let { add(text.modIdLabel.replace("{value}", it)) }
        result.installedVersion?.takeIf(String::isNotBlank)?.let { add(text.installedVersionLabel.replace("{value}", it)) }
        result.mod.versionRange?.takeIf(String::isNotBlank)?.let { add(text.requiredVersionLabel.replace("{value}", it)) }
        result.mod.authors.orEmpty().filter(String::isNotBlank).takeIf(List<*>::isNotEmpty)?.let {
            add(text.authorsLabel.replace("{value}", it.joinToString(", ")))
        }
        result.mod.license?.takeIf(String::isNotBlank)?.let { add(text.licenseLabel.replace("{value}", it)) }
    }

    private fun requirementStatus(): String = when (result.status) {
        RequirementStatus.MISSING -> text.missingStatus
        RequirementStatus.WRONG_VERSION -> text.wrongVersionStatus
            .replace("{installed}", result.installedVersion.orEmpty())
            .replace("{required}", result.mod.versionRange.orEmpty())
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

    private fun openResource(resource: DetailResource) {
        resource.download?.let {
            minecraft?.setScreen(SecureDownloadScreen.forSource(this, result, it))
            return
        }
        ConfirmLinkScreen.confirmLinkNow(this, URI.create(resource.url), true)
    }

    private class DetailsPanel(
        minecraft: Minecraft,
        width: Int,
        height: Int,
        top: Int,
        left: Int,
        private val lines: List<DetailLine>,
    ) : ScrollPanel(minecraft, width, height, top, left) {
        override fun getContentHeight(): Int = (lines.sumOf(DetailLine::height) + 10).coerceAtLeast(height - 8)

        override fun drawPanel(guiGraphics: GuiGraphics, entryRight: Int, relativeY: Int, tess: Tesselator, mouseX: Int, mouseY: Int) {
            var y = relativeY + 5
            lines.forEach { line ->
                guiGraphics.drawString(Minecraft.getInstance().font, line.text, left + 8, y, line.color, false)
                y += line.height
            }
        }

        override fun narrationPriority(): NarratableEntry.NarrationPriority = NarratableEntry.NarrationPriority.NONE

        override fun updateNarration(output: NarrationElementOutput) = Unit
    }
}

private data class DetailLine(val text: FormattedCharSequence, val color: Int, val height: Int)

private data class ResourceBrand(val name: String, val mark: String, val color: Int)

private data class DetailResource(
    val title: Component,
    val url: String,
    val brand: ResourceBrand,
    val download: DownloadLink? = null,
) {
    val shortAddress: String = runCatching {
        val uri = URI.create(url)
        buildString {
            append(uri.host?.removePrefix("www.") ?: url)
            uri.path?.takeIf { it != "/" }?.let(::append)
        }
    }.getOrDefault(url)
}

private fun detailResources(mod: RequiredMod, localizedDescription: String): List<DetailResource> = buildList {
    val links = mod.resolvedProjectLinks()
    fun addProject(key: String, url: String?, fallback: ResourceBrand) {
        val uri = validHttpUri(url) ?: return
        val brand = resourceBrand(uri.host, fallback)
        val semantic = Component.translatable("cph.project_links.$key")
        val label = when {
            key == "discord" -> Component.literal("Discord")
            brand.name.isBlank() -> semantic
            else -> Component.translatable("cph.mod_details.branded_link", brand.name, semantic)
        }
        add(DetailResource(label, uri.toString(), brand))
    }
    addProject("homepage", links.homepage, ResourceBrand("", "WEB", 0xFF58C7E8.toInt()))
    addProject("source", links.source, ResourceBrand("", "SRC", 0xFFA795C8.toInt()))
    addProject("issues", links.issues, ResourceBrand("", "!", 0xFFE0B85B.toInt()))
    addProject("wiki", links.wiki, ResourceBrand("", "W", 0xFF63C7B2.toInt()))
    addProject("discord", links.discord, ResourceBrand("Discord", "DS", 0xFF7289DA.toInt()))
    links.donations.orEmpty().forEach { donation ->
        val uri = validHttpUri(donation.url) ?: return@forEach
        val brand = resourceBrand(uri.host, ResourceBrand("", "$", 0xFFE788B7.toInt()))
        val label = donation.label?.takeIf(String::isNotBlank)?.let(Component::literal)
            ?: brand.name.takeIf(String::isNotBlank)?.let(Component::literal)
            ?: Component.translatable("cph.project_links.donation")
        add(DetailResource(label, uri.toString(), brand))
    }
    mod.availableLinks().forEach { download ->
        val target = download.downloadUrl?.takeIf(String::isNotBlank) ?: download.url
        val uri = validHttpUri(target) ?: return@forEach
        val brand = downloadBrand(download, uri.host)
        val label = Component.translatable("cph.mod_details.download_source", brand.name.ifBlank { download.displayLabel() })
        add(DetailResource(label, uri.toString(), brand, download))
    }
    DESCRIPTION_URL.findAll(localizedDescription).forEach { match ->
        val uri = validHttpUri(match.value) ?: return@forEach
        val brand = resourceBrand(uri.host, ResourceBrand("", "URL", 0xFF76AFC8.toInt()))
        val semantic = Component.translatable("cph.mod_details.description_link")
        val label = if (brand.name.isBlank()) semantic
        else Component.translatable("cph.mod_details.branded_link", brand.name, semantic)
        add(DetailResource(label, uri.toString(), brand))
    }
}.distinctBy { "${it.download != null}:${it.url}" }

private fun resourceBrand(hostValue: String?, fallback: ResourceBrand): ResourceBrand {
    val host = hostValue.orEmpty().lowercase()
    return when {
        host == "discord.gg" || host.endsWith(".discord.com") || host == "discord.com" -> ResourceBrand("Discord", "DS", 0xFF7289DA.toInt())
        host == "github.com" || host.endsWith(".github.com") -> ResourceBrand("GitHub", "GH", 0xFFB9C1CC.toInt())
        host == "gitlab.com" || host.endsWith(".gitlab.com") -> ResourceBrand("GitLab", "GL", 0xFFF39B54.toInt())
        host == "patreon.com" || host.endsWith(".patreon.com") -> ResourceBrand("Patreon", "P", 0xFFFF6B63.toInt())
        host == "ko-fi.com" || host.endsWith(".ko-fi.com") -> ResourceBrand("Ko-fi", "K", 0xFF5BCBEA.toInt())
        host == "buymeacoffee.com" || host.endsWith(".buymeacoffee.com") -> ResourceBrand("Buy Me a Coffee", "BMC", 0xFFFFD45C.toInt())
        host == "boosty.to" || host.endsWith(".boosty.to") -> ResourceBrand("Boosty", "B", 0xFFF28A45.toInt())
        host == "opencollective.com" || host.endsWith(".opencollective.com") -> ResourceBrand("Open Collective", "OC", 0xFF75A7FF.toInt())
        host == "paypal.com" || host.endsWith(".paypal.com") || host == "paypal.me" -> ResourceBrand("PayPal", "PP", 0xFF66A9E8.toInt())
        host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be" -> ResourceBrand("YouTube", "YT", 0xFFFF6464.toInt())
        host == "reddit.com" || host.endsWith(".reddit.com") -> ResourceBrand("Reddit", "R", 0xFFFF784D.toInt())
        host == "modrinth.com" || host.endsWith(".modrinth.com") -> ResourceBrand("Modrinth", "M", 0xFF55D991.toInt())
        host == "curseforge.com" || host.endsWith(".curseforge.com") || host.endsWith(".forgecdn.net") -> ResourceBrand("CurseForge", "CF", 0xFFF16436.toInt())
        host.contains("wiki") -> ResourceBrand("Wiki", "W", 0xFF63C7B2.toInt())
        else -> fallback
    }
}

private fun downloadBrand(link: DownloadLink, host: String?): ResourceBrand = when (link.resolvedType()) {
    DownloadSourceType.MODRINTH -> ResourceBrand("Modrinth", "M", 0xFF55D991.toInt())
    DownloadSourceType.CURSEFORGE -> ResourceBrand("CurseForge", "CF", 0xFFF16436.toInt())
    DownloadSourceType.GITHUB_RELEASE -> ResourceBrand("GitHub", "GH", 0xFFB9C1CC.toInt())
    DownloadSourceType.DIRECT -> resourceBrand(host, ResourceBrand("", "DL", 0xFF76AFC8.toInt()))
    DownloadSourceType.PAGE -> resourceBrand(host, ResourceBrand("", "WEB", 0xFF58C7E8.toInt()))
}

private val DESCRIPTION_URL = Regex("https?://[^\\s<>()]+", RegexOption.IGNORE_CASE)
