package org.bmp.cph.config

import com.google.gson.annotations.SerializedName
import java.net.URI

const val CONFIG_SCHEMA_VERSION = 4

data class PackHelperConfig(
    @SerializedName("\$schema")
    var schemaUrl: String? = null,
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
            schemaUrl = "coolpackhelper.schema.json",
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

enum class LanguageMode {
    GAME,
    FIXED,
}

data class LanguageConfig(
    var mode: String? = LanguageMode.GAME.name,
    var fixedLanguage: String? = "ru_ru",
    var fallbackLanguage: String? = "en_us",
)

data class MenuConfig(
    var language: LanguageConfig? = LanguageConfig(),
    var translations: Map<String, MenuText>? = defaultTranslations(),
    // Schema v1/v2 compatibility. New configs use language.fallbackLanguage.
    var defaultLanguage: String? = null,
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
    var allTab: String? = null,
    var requiredTab: String? = null,
    var recommendedTab: String? = null,
    var detailsButton: String? = null,
    var detailsTitle: String? = null,
    var modIdLabel: String? = null,
    var installedVersionLabel: String? = null,
    var requiredVersionLabel: String? = null,
    var backButton: String? = null,
    var emptyTabMessage: String? = null,
    var validationMessages: Map<String, String>? = null,
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
    var type: String? = null,
    var projectId: String? = null,
    var versionId: String? = null,
    var fileId: String? = null,
    var downloadUrl: String? = null,
    var fileName: String? = null,
    var sizeBytes: Long? = null,
    var sha256: String? = null,
    var sha512: String? = null,
    var sha1: String? = null,
) {
    fun displayLabel(): String = label?.takeIf { it.isNotBlank() }
        ?: validHttpUri(url)?.host?.removePrefix("www.")
        ?: "Link"

    fun resolvedType(): DownloadSourceType {
        DownloadSourceType.entries.firstOrNull { it.name.equals(type, ignoreCase = true) }?.let { return it }
        val host = validHttpUri(downloadUrl ?: url)?.host?.lowercase().orEmpty()
        return when {
            host == "modrinth.com" || host.endsWith(".modrinth.com") -> DownloadSourceType.MODRINTH
            host == "curseforge.com" || host.endsWith(".curseforge.com") || host.endsWith(".forgecdn.net") -> DownloadSourceType.CURSEFORGE
            host == "github.com" || host.endsWith(".githubusercontent.com") -> DownloadSourceType.GITHUB_RELEASE
            downloadUrl != null -> DownloadSourceType.DIRECT
            else -> DownloadSourceType.PAGE
        }
    }

    fun strongestHash(): Pair<String, String>? = when {
        !sha512.isNullOrBlank() -> "SHA-512" to sha512!!.trim().lowercase()
        !sha256.isNullOrBlank() -> "SHA-256" to sha256!!.trim().lowercase()
        !sha1.isNullOrBlank() -> "SHA-1" to sha1!!.trim().lowercase()
        else -> null
    }
}

enum class DownloadSourceType {
    MODRINTH,
    CURSEFORGE,
    GITHUB_RELEASE,
    DIRECT,
    PAGE,
}

enum class DownloadTrustLevel {
    PLATFORM,
    REPOSITORY,
    UNVERIFIED,
}

data class ResolvedMenuText(
    val languageCode: String,
    val fallbackLanguage: String,
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
    val allTab: String,
    val requiredTab: String,
    val recommendedTab: String,
    val detailsButton: String,
    val detailsTitle: String,
    val modIdLabel: String,
    val installedVersionLabel: String,
    val requiredVersionLabel: String,
    val backButton: String,
    val emptyTabMessage: String,
    val validationMessages: Map<String, String>,
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
        allTab = "All",
        requiredTab = "Required",
        recommendedTab = "Recommended",
        detailsButton = "Details",
        detailsTitle = "About {mod}",
        modIdLabel = "Mod ID: {value}",
        installedVersionLabel = "Installed version: {value}",
        requiredVersionLabel = "Required version: {value}",
        backButton = "Back",
        emptyTabMessage = "There are no mods in this section.",
        validationMessages = englishValidationMessages(),
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
        allTab = "Все",
        requiredTab = "Обязательные",
        recommendedTab = "Рекомендуемые",
        detailsButton = "Подробнее",
        detailsTitle = "О моде {mod}",
        modIdLabel = "ID мода: {value}",
        installedVersionLabel = "Установленная версия: {value}",
        requiredVersionLabel = "Требуемая версия: {value}",
        backButton = "Назад",
        emptyTabMessage = "В этом разделе нет модов.",
        validationMessages = russianValidationMessages(),
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
            DownloadLink(
                label = "Modrinth",
                url = "https://modrinth.com/mod/ftb-quests",
                type = DownloadSourceType.MODRINTH.name,
                projectId = "ftb-quests",
            ),
            DownloadLink(
                label = "CurseForge",
                url = "https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge",
                type = DownloadSourceType.CURSEFORGE.name,
            ),
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
            DownloadLink(
                label = "CurseForge",
                url = "https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge",
                type = DownloadSourceType.CURSEFORGE.name,
            ),
        ),
    ),
)

object MenuTextResolver {
    private val builtIn = MenuConfig.default()

    fun resolve(menu: MenuConfig?, gameLanguageCode: String): ResolvedMenuText {
        val configured = menu ?: builtIn
        val translations = configured.translations.orEmpty()
        val languageSettings = configured.language
        val mode = LanguageMode.entries.firstOrNull { it.name.equals(languageSettings?.mode, ignoreCase = true) }
            ?: LanguageMode.GAME
        val selectedLanguage = if (mode == LanguageMode.FIXED) {
            languageSettings?.fixedLanguage?.takeIf { it.isNotBlank() } ?: gameLanguageCode
        } else {
            gameLanguageCode
        }.lowercase()
        val fallbackLanguage = languageSettings?.fallbackLanguage?.takeIf { it.isNotBlank() }
            ?: configured.defaultLanguage?.takeIf { it.isNotBlank() }
            ?: "en_us"
        val candidates = languageCandidates(selectedLanguage, fallbackLanguage)
        val sources = buildList {
            candidates.forEach { candidate ->
                translations.entries.firstOrNull { it.key.equals(candidate, ignoreCase = true) }?.value?.let(::add)
            }
            candidates.forEach { candidate ->
                builtIn.translations.orEmpty().entries.firstOrNull { it.key.equals(candidate, ignoreCase = true) }?.value?.let(::add)
            }
            builtIn.translations.orEmpty()["en_us"]?.let(::add)
        }.distinct()

        fun pick(fallback: String, selector: (MenuText) -> String?): String =
            sources.firstNotNullOfOrNull { selector(it)?.takeIf(String::isNotBlank) } ?: fallback

        val validationCodes = englishValidationMessages().keys + russianValidationMessages().keys +
            sources.flatMap { it.validationMessages.orEmpty().keys }
        val validationMessages = validationCodes.associateWith { code ->
            sources.firstNotNullOfOrNull { it.validationMessages.orEmpty()[code]?.takeIf(String::isNotBlank) }
                ?: englishValidationMessages()[code]
                ?: code
        }

        return ResolvedMenuText(
            languageCode = selectedLanguage,
            fallbackLanguage = fallbackLanguage.lowercase(),
            title = pick("Modpack requirements") { it.title },
            description = pick("Some mods are missing or have an unsupported version.") { it.description },
            summary = pick("Required: {required} · Recommended: {recommended}") { it.summary },
            requiredLabel = pick("Required") { it.requiredLabel },
            recommendedLabel = pick("Recommended") { it.recommendedLabel },
            missingStatus = pick("Not installed") { it.missingStatus },
            wrongVersionStatus = pick("Installed: {installed} · Required: {required}") { it.wrongVersionStatus },
            downloadButton = pick("Download") { it.downloadButton },
            chooseSourceButton = pick("Download · {count} sources") { it.chooseSourceButton },
            sourcesTitle = pick("Download {mod}") { it.sourcesTitle },
            continueButton = pick("Continue") { it.continueButton },
            recheckButton = pick("Check again") { it.recheckButton },
            openModsFolderButton = pick("Open mods folder") { it.openModsFolderButton },
            openConfigFolderButton = pick("Open config folder") { it.openConfigFolderButton },
            previousButton = pick("Previous") { it.previousButton },
            nextButton = pick("Next") { it.nextButton },
            pageIndicator = pick("Page {current} of {total}") { it.pageIndicator },
            configErrorTitle = pick("CoolPackHelper config error") { it.configErrorTitle },
            configErrorDescription = pick("Fix the following settings and click Check again.") { it.configErrorDescription },
            allResolvedMessage = pick("All configured mods are installed.") { it.allResolvedMessage },
            allTab = pick("All") { it.allTab },
            requiredTab = pick("Required") { it.requiredTab },
            recommendedTab = pick("Recommended") { it.recommendedTab },
            detailsButton = pick("Details") { it.detailsButton },
            detailsTitle = pick("About {mod}") { it.detailsTitle },
            modIdLabel = pick("Mod ID: {value}") { it.modIdLabel },
            installedVersionLabel = pick("Installed version: {value}") { it.installedVersionLabel },
            requiredVersionLabel = pick("Required version: {value}") { it.requiredVersionLabel },
            backButton = pick("Back") { it.backButton },
            emptyTabMessage = pick("There are no mods in this section.") { it.emptyTabMessage },
            validationMessages = validationMessages,
        )
    }
}

private fun englishValidationMessages(): Map<String, String> = mapOf(
    "schema_newer" to "Schema {value} is newer than supported schema {supported}.",
    "unknown_field" to "Unknown field '{field}'. Check its spelling.",
    "both_mod_lists" to "Use either 'mods' or legacy 'requiredMods', not both.",
    "unknown_policy" to "Unknown display policy '{value}'.",
    "unknown_language_mode" to "Unknown language mode '{value}'.",
    "fixed_language_required" to "Select fixedLanguage when language mode is FIXED.",
    "pack_id_required" to "A pack id is required by ONCE_PER_PACK_VERSION.",
    "pack_version_required" to "A pack version is required by ONCE_PER_PACK_VERSION.",
    "missing_translation" to "No translation exists for '{value}'; fallback text will be used.",
    "empty_entry" to "This entry has no name or detector.",
    "missing_detector" to "Set modId or filePattern.",
    "unknown_category" to "Unknown category '{value}'.",
    "duplicate_mod_id" to "Duplicate mod id '{value}'.",
    "version_requires_mod_id" to "Version checks require modId; this range will be ignored.",
    "invalid_version_range" to "Invalid version range '{value}': {details}",
    "missing_links" to "Add at least one download link.",
    "invalid_url" to "Only a valid HTTP or HTTPS URL is allowed.",
    "unknown_source_type" to "Unknown download source type '{value}'.",
    "invalid_file_size" to "Expected file size must be greater than zero.",
    "invalid_hash" to "The configured {algorithm} hash has an invalid length or contains non-hexadecimal characters.",
    "missing_integrity_hash" to "Add SHA-256 or SHA-512 before enabling automatic downloads from this direct source.",
    "missing_link_label" to "The website domain will be used as the link label.",
    "empty_config" to "The config file is empty.",
    "parse_error" to "Could not parse the config: {details}",
    "save_error" to "Could not save the config: {details}",
)

private fun russianValidationMessages(): Map<String, String> = mapOf(
    "schema_newer" to "Версия схемы {value} новее поддерживаемой версии {supported}.",
    "unknown_field" to "Неизвестное поле '{field}'. Проверьте написание.",
    "both_mod_lists" to "Используйте либо 'mods', либо устаревшее 'requiredMods', но не оба поля.",
    "unknown_policy" to "Неизвестная политика показа '{value}'.",
    "unknown_language_mode" to "Неизвестный режим языка '{value}'.",
    "fixed_language_required" to "Укажите fixedLanguage для режима FIXED.",
    "pack_id_required" to "Для ONCE_PER_PACK_VERSION требуется ID сборки.",
    "pack_version_required" to "Для ONCE_PER_PACK_VERSION требуется версия сборки.",
    "missing_translation" to "Перевод для '{value}' отсутствует; будет использован резервный текст.",
    "empty_entry" to "У записи отсутствуют название и способ обнаружения.",
    "missing_detector" to "Укажите modId или filePattern.",
    "unknown_category" to "Неизвестная категория '{value}'.",
    "duplicate_mod_id" to "ID мода '{value}' указан несколько раз.",
    "version_requires_mod_id" to "Для проверки версии требуется modId; диапазон будет проигнорирован.",
    "invalid_version_range" to "Некорректный диапазон версий '{value}': {details}",
    "missing_links" to "Добавьте хотя бы одну ссылку для скачивания.",
    "invalid_url" to "Разрешены только корректные HTTP- или HTTPS-ссылки.",
    "unknown_source_type" to "Неизвестный тип источника загрузки: '{value}'.",
    "invalid_file_size" to "Ожидаемый размер файла должен быть больше нуля.",
    "invalid_hash" to "Хеш {algorithm} имеет неверную длину или содержит недопустимые символы.",
    "missing_integrity_hash" to "Для автоматической загрузки с прямого источника укажите SHA-256 или SHA-512.",
    "missing_link_label" to "В качестве подписи будет использован домен сайта.",
    "empty_config" to "Файл конфигурации пуст.",
    "parse_error" to "Не удалось прочитать конфиг: {details}",
    "save_error" to "Не удалось сохранить конфиг: {details}",
)

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
