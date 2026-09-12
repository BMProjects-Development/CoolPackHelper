package org.bmp.cph.config

const val CONFIG_SCHEMA_VERSION = 1

data class PackHelperConfig(
    var schemaVersion: Int? = CONFIG_SCHEMA_VERSION,
    var showOnlyOnce: Boolean? = false,
    var menu: MenuConfig? = MenuConfig.default(),
    var requiredMods: List<RequiredMod>? = defaultExampleMods(),
)

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
    var downloadButton: String? = null,
    var continueButton: String? = null,
    var previousButton: String? = null,
    var nextButton: String? = null,
    var pageIndicator: String? = null,
    var invalidLink: String? = null,
)

data class RequiredMod(
    var enabled: Boolean? = true,
    var name: String? = null,
    var modId: String? = null,
    var filePattern: String? = null,
    var downloadUrl: String? = null,
)

data class ResolvedMenuText(
    val title: String,
    val description: String,
    val downloadButton: String,
    val continueButton: String,
    val previousButton: String,
    val nextButton: String,
    val pageIndicator: String,
    val invalidLink: String,
)

private fun defaultTranslations(): Map<String, MenuText> = linkedMapOf(
    "en_us" to MenuText(
        title = "Required mods are missing",
        description = "Install the following mods, then restart the game.",
        downloadButton = "Download {mod}",
        continueButton = "Continue",
        previousButton = "Previous",
        nextButton = "Next",
        pageIndicator = "Page {current} of {total}",
        invalidLink = "The download link is invalid",
    ),
    "ru_ru" to MenuText(
        title = "Не установлены обязательные моды",
        description = "Установите следующие моды, затем перезапустите игру.",
        downloadButton = "Скачать {mod}",
        continueButton = "Продолжить",
        previousButton = "Назад",
        nextButton = "Далее",
        pageIndicator = "Страница {current} из {total}",
        invalidLink = "Некорректная ссылка для скачивания",
    ),
)

private fun defaultExampleMods(): List<RequiredMod> = listOf(
    RequiredMod(
        enabled = false,
        name = "FTB Quests",
        modId = "ftbquests",
        filePattern = "ftb-quests-*.jar",
        downloadUrl = "https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge",
    ),
    RequiredMod(
        enabled = false,
        name = "FTB Teams",
        modId = "ftbteams",
        filePattern = "ftb-teams-*.jar",
        downloadUrl = "https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge",
    ),
)

object MenuTextResolver {
    private val builtIn = MenuConfig.default()

    fun resolve(menu: MenuConfig?, languageCode: String): ResolvedMenuText {
        val configured = menu ?: builtIn
        val translations = configured.translations.orEmpty()
        val normalizedLanguage = languageCode.lowercase()
        val defaultLanguage = configured.defaultLanguage?.lowercase().orEmpty()
        val selected = translations.entries.firstOrNull { it.key.lowercase() == normalizedLanguage }?.value
        val fallback = translations.entries.firstOrNull { it.key.lowercase() == defaultLanguage }?.value
            ?: translations.entries.firstOrNull { it.key.lowercase() == "en_us" }?.value
            ?: builtIn.translations.orEmpty()["en_us"]
            ?: MenuText()

        return ResolvedMenuText(
            title = value(selected?.title, fallback.title, "Required mods are missing"),
            description = value(selected?.description, fallback.description, "Install the following mods, then restart the game."),
            downloadButton = value(selected?.downloadButton, fallback.downloadButton, "Download {mod}"),
            continueButton = value(selected?.continueButton, fallback.continueButton, "Continue"),
            previousButton = value(selected?.previousButton, fallback.previousButton, "Previous"),
            nextButton = value(selected?.nextButton, fallback.nextButton, "Next"),
            pageIndicator = value(selected?.pageIndicator, fallback.pageIndicator, "Page {current} of {total}"),
            invalidLink = value(selected?.invalidLink, fallback.invalidLink, "The download link is invalid"),
        )
    }

    private fun value(selected: String?, fallback: String?, builtIn: String): String =
        selected?.takeIf { it.isNotBlank() } ?: fallback?.takeIf { it.isNotBlank() } ?: builtIn
}
