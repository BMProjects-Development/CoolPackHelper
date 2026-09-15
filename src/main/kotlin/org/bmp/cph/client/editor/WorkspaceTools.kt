package org.bmp.cph.client.editor

import com.google.gson.Gson
import com.google.gson.JsonObject
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.network.chat.Component
import org.bmp.cph.client.curseforge.CurseForgeApiSupport
import org.bmp.cph.config.ConfigManager
import org.bmp.cph.config.MenuText
import java.net.URI
import java.util.concurrent.CompletableFuture
import kotlin.math.min
import kotlin.math.sin

internal class MenuTranslationsWindow(workspace: EditorWorkspaceScreen) : WorkspaceWindow(
    workspace, "translations", tr("translations.title"), 680, 440, 410, 285,
) {
    private val gson = Gson()
    private val working = linkedMapOf<String, JsonObject>().also { target ->
        workspace.editorSession.ensureMenu().translations.orEmpty().forEach { (locale, value) ->
            target[locale] = gson.toJsonTree(value).asJsonObject
        }
    }
    private var selectedKey: String? = working.keys.firstOrNull()
    private var localeValue = selectedKey.orEmpty()
    private var dirty = false

    override fun isDocumentDirty() = dirty

    override fun commitShortcut(): Boolean {
        apply()
        return true
    }

    override fun buildWidgets() {
        val top = bodyTop + 2
        val bottom = bodyBottom - 24
        val leftWidth = (bodyWidth * .26).toInt().coerceIn(105, 180)
        StyledActionList(
            minecraft, leftWidth, (bottom - top - 25).coerceAtLeast(38), top,
            (leftWidth - 8).coerceAtLeast(80), 34, working.entries.toList(),
            titleOf = { it.key },
            subtitleOf = { entry -> entry.value.get("title")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty() },
            accentOf = { if (it.key == selectedKey) EditorTheme.ACCENT else EditorTheme.ACCENT_PURPLE },
            onRowClick = { select(it.key) },
        ).also { it.x = bodyLeft; add(it) }
        button(tr("translations.add"), bodyLeft, bottom - 20, leftWidth, ::addLocale, TechButtonStyle.GHOST)

        val key = selectedKey
        val value = key?.let(working::get)
        if (key != null && value != null) {
            val rightX = bodyLeft + leftWidth + 7
            val rightWidth = bodyRight - rightX
            field(rightX, top, rightWidth - 26, localeValue, 32) { localeValue = it; dirty = true }
            button(Component.literal("×"), bodyRight - 22, top + 1, 21, ::removeLocale, TechButtonStyle.DANGER, tr("workspace.translation.delete.hint"))
            TranslationFieldsList(
                minecraft, rightWidth, (bottom - top - 26).coerceAtLeast(38), top + 26,
                (rightWidth - 8).coerceAtLeast(100), TEXT_FIELDS, value,
            ) { dirty = true }.also { it.x = rightX; add(it) }
        }
        button(tr("cancel"), bodyRight - 177, bodyBottom - 20, 78, { workspace.closeWindow(this) }, TechButtonStyle.GHOST)
        button(tr("workspace.apply"), bodyRight - 94, bodyBottom - 20, 90, ::apply, TechButtonStyle.PRIMARY)
    }

    override fun renderBody(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (working.isEmpty()) graphics.drawCenteredString(font, tr("translations.empty_locale"), (bodyLeft + bodyRight) / 2, (bodyTop + bodyBottom) / 2, EditorTheme.TEXT_MUTED)
    }

    private fun select(key: String) {
        commitLocaleRename()
        selectedKey = key
        localeValue = key
        rebuild()
    }

    fun focusLocale(locale: String) {
        val key = working.keys.firstOrNull { it.equals(locale, true) } ?: return
        select(key)
    }

    private fun addLocale() {
        commitLocaleRename()
        var code = "en_us"
        var suffix = 2
        while (working.containsKey(code)) code = "en_us_${suffix++}"
        working[code] = JsonObject()
        selectedKey = code
        localeValue = code
        dirty = true
        rebuild()
    }

    private fun removeLocale() {
        selectedKey?.let(working::remove)
        selectedKey = working.keys.firstOrNull()
        localeValue = selectedKey.orEmpty()
        dirty = true
        rebuild()
    }

    private fun commitLocaleRename() {
        val old = selectedKey ?: return
        val normalized = localeValue.trim().lowercase()
        if (normalized.isBlank() || normalized == old || working.containsKey(normalized)) return
        val value = working.remove(old) ?: return
        working[normalized] = value
        selectedKey = normalized
        dirty = true
    }

    private fun apply() {
        commitLocaleRename()
        workspace.editorSession.ensureMenu().translations = working.mapValues { gson.fromJson(it.value, MenuText::class.java) }.toMutableMap()
        workspace.editorSession.markDirty()
        dirty = false
        rebuild()
    }

    companion object {
        private val TEXT_FIELDS = listOf(
            "title", "description", "summary", "requiredLabel", "recommendedLabel", "missingStatus", "wrongVersionStatus",
            "downloadButton", "chooseSourceButton", "sourcesTitle", "continueButton", "recheckButton", "openModsFolderButton",
            "openConfigFolderButton", "previousButton", "nextButton", "pageIndicator", "configErrorTitle",
            "configErrorDescription", "allResolvedMessage", "allTab", "requiredTab", "recommendedTab", "detailsButton",
            "detailsTitle", "modIdLabel", "installedVersionLabel", "requiredVersionLabel", "authorsLabel", "licenseLabel",
            "projectPageLabel", "backButton", "emptyTabMessage",
        )
    }
}

internal class LocalImportWindow(workspace: EditorWorkspaceScreen) : WorkspaceWindow(
    workspace, "import:local", tr("import.title"), 500, 260, 320, 210,
) {
    private var started = false
    private var artifacts: List<LocalModArtifact>? = null
    private var error: String? = null

    override fun buildWidgets() {
        if (!started) start()
        val result = artifacts
        if (result != null) {
            val text = tr("import.add", result.size)
            button(text, bodyRight - 140, bodyBottom - 20, 136, ::importAll, TechButtonStyle.PRIMARY).active = result.isNotEmpty()
        }
        button(tr("close"), bodyLeft + 4, bodyBottom - 20, 74, { workspace.closeWindow(this) }, TechButtonStyle.GHOST)
    }

    override fun renderBody(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val result = artifacts
        val title = if (result == null) tr("import.working") else tr("import.ready", result.size)
        drawFittedLine(graphics, title, bodyLeft + 7, bodyTop + 8, bodyWidth - 14, EditorTheme.TEXT)
        val message = error?.let { tr("import.failed", it) } ?: tr("import.hint")
        drawWrappedText(
            graphics, message, bodyLeft + 7, bodyTop + 32, bodyWidth - 16, bodyBottom - 26,
            if (error == null) EditorTheme.TEXT_MUTED else 0xFFFF7777.toInt(), maxLines = 5,
        )
    }

    private fun start() {
        started = true
        CompletableFuture.supplyAsync(PlatformScanner::inspectModsFolder).whenComplete { value, exception ->
            Minecraft.getInstance().execute {
                artifacts = value ?: emptyList()
                error = exception?.cause?.message ?: exception?.message
                if (workspace.hasWindow(this)) rebuild()
            }
        }
    }

    private fun importAll() {
        val added = workspace.editorSession.addDrafts(artifacts.orEmpty().map(LocalModArtifact::toDraft))
        workspace.modsChanged()
        Minecraft.getInstance().let {
            SystemToast.add(it.toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, tr("scan.imported"), tr("scan.imported.count", added))
        }
        workspace.closeWindow(this)
    }
}

internal class ScanToolWindow(
    workspace: EditorWorkspaceScreen,
    private val platform: ScanPlatform,
) : WorkspaceWindow(workspace, "scan:${platform.name.lowercase()}", tr("scan.title", platform.displayName), 700, 430, 410, 285) {
    private enum class Filter { ALL, FOUND, NOT_FOUND, UNKNOWN }
    private var started = false
    private var report: PlatformScanReport? = null
    private var filter = Filter.NOT_FOUND
    private val selected = mutableSetOf<String>()
    private var apiKey = if (platform == ScanPlatform.CURSEFORGE) ConfigManager.loadAuthorSettings().curseForgeApiKey.orEmpty() else ""
    private var rememberKey = true

    override fun buildWidgets() {
        if (!started) {
            if (platform == ScanPlatform.CURSEFORGE && apiKey.isBlank()) buildKeyForm() else start()
            return
        }
        val completed = report ?: return
        if (completed.error != null) {
            button(tr("scan.retry"), bodyRight - 94, bodyBottom - 20, 90, ::retry, TechButtonStyle.PRIMARY)
            if (platform == ScanPlatform.CURSEFORGE) button(tr("curseforge.key.change"), bodyLeft + 4, bodyBottom - 20, 115, ::changeKey, TechButtonStyle.GHOST)
            return
        }
        val top = bodyTop + 25
        val counts = Filter.entries.associateWith { candidate -> completed.items.count { candidate.accepts(it) } }
        val tabWidth = ((bodyWidth - 6) / 4).coerceAtLeast(54)
        Filter.entries.forEachIndexed { index, value ->
            button(tr("scan.filter.${value.name.lowercase()}", counts.getValue(value)), bodyLeft + index * (tabWidth + 2), bodyTop, tabWidth, {
                filter = value; rebuild()
            }, if (filter == value) TechButtonStyle.PRIMARY else TechButtonStyle.GHOST)
        }
        val visible = completed.items.filter { filter.accepts(it) }
        StyledActionList(
            minecraft, bodyWidth, (bodyBottom - top - 26).coerceAtLeast(38), top,
            (bodyWidth - 9).coerceAtLeast(100), 39, visible,
            titleOf = { it.artifact.name }, subtitleOf = { it.artifact.fileName },
            accentOf = { when (it.status) { PlatformMatchStatus.FOUND -> 0xFF69E09B.toInt(); PlatformMatchStatus.NOT_FOUND -> 0xFFFFBE62.toInt(); PlatformMatchStatus.UNKNOWN -> 0xFFFF7777.toInt() } },
            badgeOf = { when (it.status) { PlatformMatchStatus.FOUND -> RowBadge(tr("scan.found"), 0x69E09B); PlatformMatchStatus.NOT_FOUND -> RowBadge(tr("scan.not_found"), 0xFFBE62); PlatformMatchStatus.UNKNOWN -> RowBadge(tr("scan.unknown"), 0xFF7777) } },
            actionsOf = { item -> if (item.status != PlatformMatchStatus.NOT_FOUND) emptyList() else listOf(
                RowAction(label = { Component.literal(if (item.artifact.fileName in selected) "✓" else "+") }, width = 25, style = { if (item.artifact.fileName in selected) TechButtonStyle.PRIMARY else TechButtonStyle.GHOST }) {
                    if (!selected.add(item.artifact.fileName)) selected.remove(item.artifact.fileName)
                }
            ) },
        ).also { it.x = bodyLeft; add(it) }
        val importText = tr("scan.import", selected.size)
        button(importText, bodyRight - 130, bodyBottom - 20, 126, ::importSelected, TechButtonStyle.PRIMARY).active = selected.isNotEmpty()
    }

    private fun buildKeyForm() {
        field(bodyLeft + 5, bodyTop + 30, bodyWidth - 10, apiKey, 512) { apiKey = it }
        button(tr(if (rememberKey) "curseforge.remember.on" else "curseforge.remember.off"), bodyLeft + 5, bodyTop + 57, min(190, bodyWidth - 10), {
            rememberKey = !rememberKey; rebuild()
        }, TechButtonStyle.GHOST)
        button(tr("scan.start"), bodyRight - 125, bodyBottom - 20, 121, ::start, TechButtonStyle.PRIMARY)
    }

    override fun renderBody(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!started && platform == ScanPlatform.CURSEFORGE) {
            drawFittedLine(graphics, tr("curseforge.subtitle"), bodyLeft + 6, bodyTop + 7, bodyWidth - 12)
            return
        }
        val completed = report
        if (completed == null) {
            val center = (bodyLeft + bodyRight) / 2
            val pulse = ((sin(Util.getMillis() / 180.0) + 1.0) * 35).toInt()
            graphics.fill(center - 72, (bodyTop + bodyBottom) / 2, center + 72, (bodyTop + bodyBottom) / 2 + 2, 0x335A7890)
            graphics.fill(center - 72, (bodyTop + bodyBottom) / 2, center - 72 + pulse * 2, (bodyTop + bodyBottom) / 2 + 2, EditorTheme.ACCENT)
            graphics.drawCenteredString(font, tr("scan.hashing"), center, (bodyTop + bodyBottom) / 2 + 13, EditorTheme.TEXT_MUTED)
            return
        }
        completed.error?.let { message ->
            drawFittedLine(graphics, tr("scan.failed", platform.displayName), bodyLeft + 7, bodyTop + 8, bodyWidth - 14, 0xFFFF7777.toInt())
            drawWrappedText(graphics, Component.literal(message), bodyLeft + 7, bodyTop + 31, bodyWidth - 16, bodyBottom - 26, maxLines = 5)
        }
    }

    private fun start() {
        if (platform == ScanPlatform.CURSEFORGE) {
            val normalized = CurseForgeApiSupport.normalizeKey(apiKey)
            if (normalized.isBlank()) return
            apiKey = normalized
            val settings = ConfigManager.loadAuthorSettings()
            ConfigManager.saveAuthorSettings(settings.copy(curseForgeApiKey = if (rememberKey) apiKey else null))
        }
        started = true
        report = null
        rebuild()
        PlatformScanner.scanAsync(platform, apiKey.takeIf(String::isNotBlank)).whenComplete { value, exception ->
            Minecraft.getInstance().execute {
                report = value ?: PlatformScanReport(platform, emptyList(), exception?.cause?.message ?: exception?.message ?: tr("error.unknown").string)
                selected.clear()
                report?.items.orEmpty().filter { it.status == PlatformMatchStatus.NOT_FOUND }.forEach { selected += it.artifact.fileName }
                filter = if (selected.isEmpty()) Filter.ALL else Filter.NOT_FOUND
                if (workspace.hasWindow(this)) rebuild()
            }
        }
    }

    private fun retry() {
        report = null
        started = false
        selected.clear()
        rebuild()
    }

    private fun changeKey() {
        report = null
        started = false
        apiKey = ""
        selected.clear()
        rebuild()
    }

    private fun importSelected() {
        val drafts = report?.items.orEmpty().filter { it.status == PlatformMatchStatus.NOT_FOUND && it.artifact.fileName in selected }.map { it.artifact.toDraft() }
        val added = workspace.editorSession.addDrafts(drafts)
        workspace.modsChanged()
        Minecraft.getInstance().let {
            SystemToast.add(it.toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, tr("scan.imported"), tr("scan.imported.count", added))
        }
    }

    private fun Filter.accepts(item: PlatformScanItem): Boolean = when (this) {
        Filter.ALL -> true
        Filter.FOUND -> item.status == PlatformMatchStatus.FOUND
        Filter.NOT_FOUND -> item.status == PlatformMatchStatus.NOT_FOUND
        Filter.UNKNOWN -> item.status == PlatformMatchStatus.UNKNOWN
    }
}

internal class TranslationToolWindow(
    workspace: EditorWorkspaceScreen,
    private val mod: org.bmp.cph.config.RequiredMod,
    ownerKey: String,
    private val onApplied: () -> Unit,
) : WorkspaceWindow(workspace, "translation:$ownerKey", tr("translation.title"), 600, 390, 390, 300) {
    private var provider = TranslationProvider.fromId(ConfigManager.loadAuthorSettings().translationProvider)
    private var source = mod.descriptions.orEmpty().keys.firstOrNull { it.equals("en_us", true) }
        ?: mod.descriptions.orEmpty().keys.firstOrNull().orEmpty()
    private var target = Minecraft.getInstance().languageManager.selected.takeUnless { it.equals(source, true) } ?: "ru_ru"
    private var endpoint = ConfigManager.loadAuthorSettings().translationEndpoint ?: TranslationService.DEFAULT_ENDPOINT
    private var apiKey = ConfigManager.loadAuthorSettings().translationApiKeys.orEmpty()[provider.id].orEmpty()
    private var rememberKey = ConfigManager.loadAuthorSettings().translationRememberApiKey ?: false
    private var loading = false
    private var result: String? = null
    private var error: String? = null

    override fun isDocumentDirty(): Boolean = result != null

    override fun commitShortcut(): Boolean {
        if (result == null) return false
        apply()
        return true
    }

    override fun buildWidgets() {
        val labelWidth = (bodyWidth * .29).toInt().coerceIn(105, 155)
        val x = bodyLeft + labelWidth
        val w = bodyRight - x
        var y = bodyTop + 2
        button(tr("translation.provider.${provider.id}"), x, y, w - 27, ::cycleProvider, TechButtonStyle.GHOST, tr("translation.provider.hint"))
        button(Component.literal("?"), bodyRight - 22, y, 21, ::openHelp, TechButtonStyle.GHOST, tr("translation.provider.details.${provider.id}")); y += 25
        field(x, y, w - 27, source, 32) { source = it }
        button(Component.literal("…"), bodyRight - 22, y + 1, 21, {
            workspace.openWindow(LanguagePickerWindow(workspace, source, "$key:source") { source = it; rebuild() }, center = true)
        }, TechButtonStyle.GHOST, tr("translation.source.choose")); y += 25
        field(x, y, w - 27, target, 32) { target = it }
        button(Component.literal("…"), bodyRight - 22, y + 1, 21, {
            workspace.openWindow(LanguagePickerWindow(workspace, target, "$key:target") { target = it; rebuild() }, center = true)
        }, TechButtonStyle.GHOST, tr("translation.target.choose")); y += 25
        if (provider == TranslationProvider.LIBRE_TRANSLATE) {
            field(x, y, w, endpoint, 2048) { endpoint = it }
            y += 25
        }
        if (provider.usesApiKey) {
            field(x, y, w, apiKey, 512) { apiKey = it }
            y += 25
            button(tr(if (rememberKey) "translation.remember_key.on" else "translation.remember_key.off"), x, y, w, {
                rememberKey = !rememberKey; rebuild()
            }, TechButtonStyle.GHOST, tr("translation.remember_key.hint"))
            y += 23
        }
        result?.let { translated ->
            StableMultiLineEditBox(font, bodyLeft + 4, y + 13, bodyWidth - 8, (bodyBottom - y - 39).coerceAtLeast(35), tr("translation.preview"), tr("translation.preview")).also {
                it.value = translated
                it.setCharacterLimit(8192)
                it.setValueListener { value -> result = value }
                add(it)
            }
        }
        button(tr("cancel"), bodyLeft + 4, bodyBottom - 20, 74, { workspace.closeWindow(this) }, TechButtonStyle.GHOST)
        val action = button(tr(if (loading) "translation.loading" else "translation.action"), bodyRight - 190, bodyBottom - 20, 94, ::translate, TechButtonStyle.SECONDARY, tr("translation.privacy"))
        action.active = !loading && mod.descriptions.orEmpty().values.any(String::isNotBlank)
        if (result != null) button(tr("translation.save"), bodyRight - 91, bodyBottom - 20, 87, ::apply, TechButtonStyle.PRIMARY)
    }

    override fun renderBody(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val labels = buildList {
            add("translation.provider")
            add("translation.source")
            add("translation.target")
            if (provider == TranslationProvider.LIBRE_TRANSLATE) add("translation.endpoint")
            if (provider.usesApiKey) add("translation.api_key")
        }
        var y = bodyTop + 8
        labels.forEach { label ->
            graphics.drawString(font, font.plainSubstrByWidth(tr(label).string, (bodyWidth * .27).toInt()), bodyLeft + 5, y, EditorTheme.TEXT_MUTED, false)
            y += 25
        }
        if (result != null) drawFittedLine(graphics, tr("translation.preview"), bodyLeft + 5, y + if (provider.usesApiKey) 23 else 0, bodyWidth - 10)
        error?.let { message ->
            drawWrappedText(graphics, Component.literal(message), bodyLeft + 8, bodyBottom - 57, bodyWidth - 18, bodyBottom - 24, 0xFFFF7777.toInt(), 10, 3)
        }
    }

    private fun cycleProvider() {
        provider = TranslationProvider.entries[(provider.ordinal + 1) % TranslationProvider.entries.size]
        apiKey = ConfigManager.loadAuthorSettings().translationApiKeys.orEmpty()[provider.id].orEmpty()
        result = null
        error = null
        rebuild()
    }

    private fun translate() {
        if (loading) return
        val text = mod.descriptions.orEmpty().entries.firstOrNull { it.key.equals(source.trim(), true) }?.value.orEmpty()
        val normalizedEndpoint = if (provider == TranslationProvider.LIBRE_TRANSLATE) TranslationService.normalizeEndpoint(endpoint) ?: endpoint.trim() else endpoint.trim()
        persistSettings(normalizedEndpoint)
        loading = true
        result = null
        error = null
        rebuild()
        TranslationService.translateAsync(provider, normalizedEndpoint, apiKey.trim(), text, source.trim(), target.trim()).whenComplete { value, exception ->
            Minecraft.getInstance().execute {
                loading = false
                if (value != null) result = value else error = exception?.cause?.message ?: exception?.message ?: tr("error.unknown").string
                if (workspace.hasWindow(this)) rebuild()
            }
        }
    }

    private fun persistSettings(normalizedEndpoint: String) {
        val settings = ConfigManager.loadAuthorSettings()
        val keys = settings.translationApiKeys.orEmpty().toMutableMap()
        if (rememberKey && provider.usesApiKey && apiKey.isNotBlank()) keys[provider.id] = apiKey.trim() else keys.remove(provider.id)
        ConfigManager.saveAuthorSettings(settings.copy(
            translationProvider = provider.id,
            translationEndpoint = normalizedEndpoint,
            translationApiKeys = keys.takeIf(Map<*, *>::isNotEmpty),
            translationRememberApiKey = rememberKey,
            translationApiKey = null,
        ))
    }

    private fun apply() {
        val code = target.trim().lowercase()
        val text = result?.trim().orEmpty()
        if (code.isBlank() || text.isBlank()) return
        mod.descriptions = mod.descriptions.orEmpty().toMutableMap().also { it[code] = text }
        onApplied()
        workspace.closeWindow(this)
    }

    private fun openHelp() {
        ConfirmLinkScreen.confirmLinkNow(workspace, URI.create(provider.helpUrl), true)
    }
}

internal class LanguagePickerWindow(
    workspace: EditorWorkspaceScreen,
    private val currentCode: String,
    ownerKey: String,
    private val onSelected: (String) -> Unit,
) : WorkspaceWindow(workspace, "language-picker:$ownerKey", tr("language_picker.title"), 560, 390, 340, 250) {
    private val languages = Minecraft.getInstance().languageManager.languages.map { (code, info) ->
        MinecraftLanguageChoice(code, info.name, info.region, info.bidirectional)
    }
    private var query = ""
    private lateinit var list: StyledActionList<MinecraftLanguageChoice>

    override fun buildWidgets() {
        field(bodyLeft + 3, bodyTop + 2, bodyWidth - 6, query, 128) { value ->
            query = value
            if (::list.isInitialized) list.replaceItems(filterMinecraftLanguages(languages, query), resetScroll = false)
        }.setHint(tr("language_picker.search"))
        list = StyledActionList(
            minecraft, bodyWidth, (bodyHeight - 29).coerceAtLeast(38), bodyTop + 27,
            (bodyWidth - 9).coerceAtLeast(100), 36, filterMinecraftLanguages(languages, query),
            titleOf = { it.name },
            subtitleOf = { buildString { append(it.code); if (it.region.isNotBlank()) append(" · ").append(it.region); if (it.bidirectional) append(" · RTL") } },
            accentOf = { if (it.code.equals(currentCode, true)) EditorTheme.ACCENT else 0xFF737982.toInt() },
            badgeOf = { if (it.code.equals(currentCode, true)) RowBadge(Component.literal("✓"), EditorTheme.ACCENT) else null },
            onRowClick = { onSelected(it.code); workspace.closeWindow(this) },
            rowTooltipOf = { tr("language_picker.select", it.name, it.code) },
        ).also { it.x = bodyLeft; add(it) }
    }

    override fun renderBody(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (filterMinecraftLanguages(languages, query).isEmpty()) graphics.drawCenteredString(font, tr("language_picker.empty"), (bodyLeft + bodyRight) / 2, bodyTop + 58, EditorTheme.TEXT_MUTED)
    }
}
