package org.bmp.cph.config

import java.net.URI

const val CONFIG_SCHEMA_VERSION = 2

data class PackHelperConfig(
    var schemaVersion: Int? = null,
    var pack: PackInfo? = null,
    var showPolicy: String? = null,
    var menu: MenuConfig? = null,
    var mods: List<RequiredMod>? = null,
    // Schema v1 compatibility. New configs use showPolicy and mods.
    var showOnlyOnce: Boolean? = null,
    var requiredMods: List<RequiredMod>? = null,
) {
    companion object {
        fun default() = PackHelperConfig(
            schemaVersion = CONFIG_SCHEMA_VERSION,
            pack = PackInfo(id = "my-pack", name = "My Modpack", version = "1.0.0"),
            showPolicy = ShowPolicy.UNTIL_RESOLVED.name,
            menu = MenuConfig.default(),
            mods = defaultExampleMods(),
        )
    }

    fun activeModEntries(): List<RequiredMod> = (mods ?: requiredMods).orEmpty()

    fun resolvedShowPolicy(): ShowPolicy =
        ShowPolicy.entries.firstOrNull { it.name.equals(showPolicy, ignoreCase = true) }
            ?: if (showOnlyOnce == true) ShowPolicy.ONCE_EVER else ShowPolicy.UNTIL_RESOLVED
}

data class PackInfo(
    var id: String? = null,
    var name: String? = null,
    var version: String? = null,
)

enum class ShowPolicy {
    UNTIL_RESOLVED,
    ONCE_PER_PACK_VERSION,
    ONCE_EVER,
    NEVER,
}

enum class ModCategory {
    REQUIRED,
    RECOMMENDED,
}

data class MenuConfig(
    var defaultLanguage: String? = "en_us",
    var translations: Map<String, MenuText>? = defaultTranslations(),
) {
    companion object {
        fun default() = MenuConfig()
    }
}

data class MenuText(
    var title: String? = null,
    var description: String? = null,
    var summary: String? = null,
    var requiredLabel: String? = null,
    var recommendedLabel: String? = null,
    var missingStatus: String? = null,
    var wrongVersionStatus: String? = null,
    var downloadButton: String? = null,
    var chooseSourceButton: String? = null,
    var sourcesTitle: String? = null,
    var continueButton: String? = null,
    var recheckButton: String? = null,
    var openModsFolderButton: String? = null,
    var openConfigFolderButton: String? = null,
    var previousButton: String? = null,
    var nextButton: String? = null,
    var pageIndicator: String? = null,
    var configErrorTitle: String? = null,
    var configErrorDescription: String? = null,
    var allResolvedMessage: String? = null,
)

data class RequiredMod(
    var enabled: Boolean? = true,
    var category: String? = ModCategory.REQUIRED.name,
    var name: String? = null,
    var modId: String? = null,
    var versionRange: String? = null,
    var filePattern: String? = null,
    var description: String? = null,
    var descriptions: Map<String, String>? = null,
    var links: List<DownloadLink>? = null,
    // Schema v1 compatibility. New configs use links.
    var downloadUrl: String? = null,
) {
    fun resolvedCategory(): ModCategory =
        ModCategory.entries.firstOrNull { it.name.equals(category, ignoreCase = true) } ?: ModCategory.REQUIRED

    fun displayName(): String = name?.takeIf { it.isNotBlank() }
        ?: modId?.takeIf { it.isNotBlank() }
        ?: filePattern?.takeIf { it.isNotBlank() }
        ?: "Unknown mod"

    fun availableLinks(): List<DownloadLink> {
        val configured = links.orEmpty()
        if (configured.isNotEmpty()) return configured
        return downloadUrl?.takeIf { it.isNotBlank() }?.let {
            listOf(DownloadLink(label = "Download", url = it))
        }.orEmpty()
    }

    fun localizedDescription(languageCode: String, defaultLanguage: String): String {
        val values = descriptions.orEmpty()
        for (candidate in languageCandidates(languageCode, defaultLanguage)) {
            values.entries.firstOrNull { it.key.equals(candidate, ignoreCase = true) }
                ?.value?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return description.orEmpty()
    }
}

data class DownloadLink(
    var label: String? = null,
    var url: String? = null,
) {
    fun displayLabel(): String = label?.takeIf { it.isNotBlank() }
        ?: validHttpUri(url)?.host?.removePrefix("www.")
        ?: "Link"
}

data class ResolvedMenuText(
    val title: String,
    val description: String,
    val summary: String,
    val requiredLabel: String,
    val recommendedLabel: String,
    val missingStatus: String,
    val wrongVersionStatus: String,
    val downloadButton: String,
    val chooseSourceButton: String,
    val sourcesTitle: String,
    val continueButton: String,
    val recheckButton: String,
    val openModsFolderButton: String,
    val openConfigFolderButton: String,
    val previousButton: String,
    val nextButton: String,
    val pageIndicator: String,
    val configErrorTitle: String,
    val configErrorDescription: String,
    val allResolvedMessage: String,
)

private fun defaultTranslations(): Map<String, MenuText> = linkedMapOf(
    "en_us" to MenuText(
        title = "Modpack requirements",
        description = "Some mods are missing or have an unsupported version.",
        summary = "Required: {required} · Recommended: {recommended}",
        requiredLabel = "Required",
        recommendedLabel = "Recommended",
        missingStatus = "Not installed",
        wrongVersionStatus = "Installed: {installed} · Required: {required}",
        downloadButton = "Download",
        chooseSourceButton = "Download · {count} sources",
        sourcesTitle = "Download {mod}",
        continueButton = "Continue",
        recheckButton = "Check again",
        openModsFolderButton = "Open mods folder",
        openConfigFolderButton = "Open config folder",
        previousButton = "Previous",
        nextButton = "Next",
        pageIndicator = "Page {current} of {total}",
        configErrorTitle = "CoolPackHelper config error",
        configErrorDescription = "Fix the following settings and click Check again.",
        allResolvedMessage = "All configured mods are installed.",
    ),
    "ru_ru" to MenuText(
        title = "Требования сборки",
        description = "Некоторые моды отсутствуют или имеют неподдерживаемую версию.",
        summary = "Обязательных: {required} · Рекомендуемых: {recommended}",
        requiredLabel = "Обязательный",
        recommendedLabel = "Рекомендуемый",
        missingStatus = "Не установлен",
        wrongVersionStatus = "Установлена: {installed} · Требуется: {required}",
        downloadButton = "Скачать",
        chooseSourceButton = "Скачать · источников: {count}",
        sourcesTitle = "Скачать {mod}",
        continueButton = "Продолжить",
        recheckButton = "Проверить снова",
        openModsFolderButton = "Открыть папку mods",
        openConfigFolderButton = "Открыть папку config",
        previousButton = "Назад",
        nextButton = "Далее",
        pageIndicator = "Страница {current} из {total}",
        configErrorTitle = "Ошибка конфига CoolPackHelper",
        configErrorDescription = "Исправьте настройки ниже и нажмите «Проверить снова».",
        allResolvedMessage = "Все настроенные моды установлены.",
    ),
)

private fun defaultExampleMods(): List<RequiredMod> = listOf(
    RequiredMod(
        enabled = false,
        category = ModCategory.REQUIRED.name,
        name = "FTB Quests",
        modId = "ftbquests",
        filePattern = "ftb-quests-*.jar",
        descriptions = linkedMapOf(
            "en_us" to "Adds the quest book used by this modpack.",
            "ru_ru" to "Добавляет книгу заданий, используемую этой сборкой.",
        ),
        links = listOf(
            DownloadLink("CurseForge", "https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge"),
            DownloadLink("Modrinth", "https://modrinth.com/mod/ftb-quests"),
        ),
    ),
    RequiredMod(
        enabled = false,
        category = ModCategory.RECOMMENDED.name,
        name = "FTB Teams",
        modId = "ftbteams",
        filePattern = "ftb-teams-*.jar",
        descriptions = linkedMapOf(
            "en_us" to "Adds team support for shared quest progress.",
            "ru_ru" to "Добавляет команды и общий прогресс выполнения заданий.",
        ),
        links = listOf(
            DownloadLink("CurseForge", "https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge"),
        ),
    ),
)

object MenuTextResolver {
    private val builtIn = MenuConfig.default()

    fun resolve(menu: MenuConfig?, languageCode: String): ResolvedMenuText {
        val configured = menu ?: builtIn
        val translations = configured.translations.orEmpty()
        val defaultLanguage = configured.defaultLanguage?.lowercase().orEmpty()
        val selected = languageCandidates(languageCode, defaultLanguage).firstNotNullOfOrNull { candidate ->
            translations.entries.firstOrNull { it.key.equals(candidate, ignoreCase = true) }?.value
        }
        val fallback = translations.entries.firstOrNull { it.key.equals(defaultLanguage, ignoreCase = true) }?.value
            ?: translations.entries.firstOrNull { it.key.equals("en_us", ignoreCase = true) }?.value
        val builtInSelected = languageCandidates(languageCode, defaultLanguage).firstNotNullOfOrNull { candidate ->
            builtIn.translations.orEmpty().entries.firstOrNull { it.key.equals(candidate, ignoreCase = true) }?.value
        }
        val builtInFallback = builtIn.translations.orEmpty()["en_us"] ?: MenuText()

        return ResolvedMenuText(
            title = value(selected?.title, fallback?.title, builtInSelected?.title, builtInFallback.title, "Modpack requirements"),
            description = value(selected?.description, fallback?.description, builtInSelected?.description, builtInFallback.description, "Some mods are missing or have an unsupported version."),
            summary = value(selected?.summary, fallback?.summary, builtInSelected?.summary, builtInFallback.summary, "Required: {required} · Recommended: {recommended}"),
            requiredLabel = value(selected?.requiredLabel, fallback?.requiredLabel, builtInSelected?.requiredLabel, builtInFallback.requiredLabel, "Required"),
            recommendedLabel = value(selected?.recommendedLabel, fallback?.recommendedLabel, builtInSelected?.recommendedLabel, builtInFallback.recommendedLabel, "Recommended"),
            missingStatus = value(selected?.missingStatus, fallback?.missingStatus, builtInSelected?.missingStatus, builtInFallback.missingStatus, "Not installed"),
            wrongVersionStatus = value(selected?.wrongVersionStatus, fallback?.wrongVersionStatus, builtInSelected?.wrongVersionStatus, builtInFallback.wrongVersionStatus, "Installed: {installed} · Required: {required}"),
            downloadButton = value(selected?.downloadButton, fallback?.downloadButton, builtInSelected?.downloadButton, builtInFallback.downloadButton, "Download"),
            chooseSourceButton = value(selected?.chooseSourceButton, fallback?.chooseSourceButton, builtInSelected?.chooseSourceButton, builtInFallback.chooseSourceButton, "Download · {count} sources"),
            sourcesTitle = value(selected?.sourcesTitle, fallback?.sourcesTitle, builtInSelected?.sourcesTitle, builtInFallback.sourcesTitle, "Download {mod}"),
            continueButton = value(selected?.continueButton, fallback?.continueButton, builtInSelected?.continueButton, builtInFallback.continueButton, "Continue"),
            recheckButton = value(selected?.recheckButton, fallback?.recheckButton, builtInSelected?.recheckButton, builtInFallback.recheckButton, "Check again"),
            openModsFolderButton = value(selected?.openModsFolderButton, fallback?.openModsFolderButton, builtInSelected?.openModsFolderButton, builtInFallback.openModsFolderButton, "Open mods folder"),
            openConfigFolderButton = value(selected?.openConfigFolderButton, fallback?.openConfigFolderButton, builtInSelected?.openConfigFolderButton, builtInFallback.openConfigFolderButton, "Open config folder"),
            previousButton = value(selected?.previousButton, fallback?.previousButton, builtInSelected?.previousButton, builtInFallback.previousButton, "Previous"),
            nextButton = value(selected?.nextButton, fallback?.nextButton, builtInSelected?.nextButton, builtInFallback.nextButton, "Next"),
            pageIndicator = value(selected?.pageIndicator, fallback?.pageIndicator, builtInSelected?.pageIndicator, builtInFallback.pageIndicator, "Page {current} of {total}"),
            configErrorTitle = value(selected?.configErrorTitle, fallback?.configErrorTitle, builtInSelected?.configErrorTitle, builtInFallback.configErrorTitle, "CoolPackHelper config error"),
            configErrorDescription = value(selected?.configErrorDescription, fallback?.configErrorDescription, builtInSelected?.configErrorDescription, builtInFallback.configErrorDescription, "Fix the following settings and click Check again."),
            allResolvedMessage = value(selected?.allResolvedMessage, fallback?.allResolvedMessage, builtInSelected?.allResolvedMessage, builtInFallback.allResolvedMessage, "All configured mods are installed."),
        )
    }

    private fun value(vararg candidates: String?): String = candidates.firstNotNullOf { it?.takeIf(String::isNotBlank) }
}

internal fun languageCandidates(languageCode: String, defaultLanguage: String): List<String> = buildList {
    val normalized = languageCode.lowercase()
    add(normalized)
    normalized.substringBefore('_').takeIf { it != normalized }?.let(::add)
    defaultLanguage.lowercase().takeIf { it.isNotBlank() }?.let(::add)
    add("en_us")
}.distinct()

fun validHttpUri(value: String?): URI? = try {
    value?.trim()?.takeIf { it.isNotEmpty() }?.let(URI::create)?.takeIf {
        (it.scheme.equals("https", ignoreCase = true) || it.scheme.equals("http", ignoreCase = true)) && !it.host.isNullOrBlank()
    }
} catch (_: IllegalArgumentException) {
    null
}
