package org.bmp.cph.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.apache.maven.artifact.versioning.VersionRange

enum class IssueSeverity {
    ERROR,
    WARNING,
}

data class ConfigIssue(
    val path: String,
    val code: String,
    val severity: IssueSeverity,
    val arguments: Map<String, String> = emptyMap(),
    val displayPath: String? = null,
) {
    fun localized(text: ResolvedMenuText): String {
        var result = text.validationMessages[code] ?: code
        arguments.forEach { (key, value) -> result = result.replace("{$key}", value) }
        return result
    }
}

object ConfigValidator {
    private val rootFields = setOf("\$schema", "schemaVersion", "pack", "showPolicy", "menu", "mods", "showOnlyOnce", "requiredMods")
    private val packFields = setOf("id", "name", "version")
    private val menuFields = setOf("language", "translations", "defaultLanguage")
    private val languageFields = setOf("mode", "fixedLanguage", "fallbackLanguage")
    private val menuTextFields = setOf(
        "title", "description", "summary", "requiredLabel", "recommendedLabel", "missingStatus", "wrongVersionStatus",
        "downloadButton", "chooseSourceButton", "sourcesTitle", "continueButton", "recheckButton", "openModsFolderButton",
        "openConfigFolderButton", "previousButton", "nextButton", "pageIndicator", "configErrorTitle",
        "configErrorDescription", "allResolvedMessage", "allTab", "requiredTab", "recommendedTab", "detailsButton",
        "detailsTitle", "modIdLabel", "installedVersionLabel", "requiredVersionLabel", "backButton", "emptyTabMessage",
        "validationMessages",
    )
    private val modFields = setOf(
        "enabled", "category", "name", "modId", "versionRange", "filePattern", "description", "descriptions", "links", "downloadUrl",
    )
    private val linkFields = setOf(
        "label", "url", "type", "projectId", "versionId", "fileId", "downloadUrl", "fileName", "sizeBytes",
        "sha256", "sha512", "sha1",
    )

    fun validateStructure(root: JsonElement): List<ConfigIssue> = buildList {
        if (!root.isJsonObject) return@buildList
        val rootObject = root.asJsonObject
        unknownFields(rootObject, rootFields, "")
        rootObject.objectAt("pack")?.let { unknownFields(it, packFields, "pack") }
        rootObject.objectAt("menu")?.let { menu ->
            unknownFields(menu, menuFields, "menu")
            menu.objectAt("language")?.let { unknownFields(it, languageFields, "menu.language") }
            menu.objectAt("translations")?.entrySet()?.forEach { (language, value) ->
                if (value.isJsonObject) {
                    unknownFields(value.asJsonObject, menuTextFields, "menu.translations.$language")
                }
            }
        }
        listOf("mods", "requiredMods").forEach { listName ->
            rootObject.get(listName)?.takeIf { it.isJsonArray }?.asJsonArray?.forEachIndexed { index, value ->
                if (value.isJsonObject) {
                    val mod = value.asJsonObject
                    unknownFields(mod, modFields, "$listName[$index]")
                    mod.get("links")?.takeIf { it.isJsonArray }?.asJsonArray?.forEachIndexed { linkIndex, link ->
                        if (link.isJsonObject) unknownFields(link.asJsonObject, linkFields, "$listName[$index].links[$linkIndex]")
                    }
                }
            }
        }
    }

    fun validate(config: PackHelperConfig): List<ConfigIssue> = buildList {
        val schema = config.schemaVersion ?: 1
        if (schema > CONFIG_SCHEMA_VERSION) {
            error("schemaVersion", "schema_newer", "value" to schema.toString(), "supported" to CONFIG_SCHEMA_VERSION.toString())
        }
        if (config.mods != null && config.requiredMods != null) error("mods", "both_mod_lists")

        if (config.showPolicy != null && ShowPolicy.entries.none { it.name.equals(config.showPolicy, ignoreCase = true) }) {
            error("showPolicy", "unknown_policy", "value" to config.showPolicy.orEmpty())
        }

        val language = config.menu?.language
        if (language?.mode != null && LanguageMode.entries.none { it.name.equals(language.mode, ignoreCase = true) }) {
            error("menu.language.mode", "unknown_language_mode", "value" to language.mode.orEmpty())
        }
        if (language?.mode.equals(LanguageMode.FIXED.name, ignoreCase = true) && language?.fixedLanguage.isNullOrBlank()) {
            error("menu.language.fixedLanguage", "fixed_language_required")
        }

        if (config.resolvedShowPolicy() == ShowPolicy.ONCE_PER_PACK_VERSION) {
            if (config.pack?.id.isNullOrBlank()) error("pack.id", "pack_id_required")
            if (config.pack?.version.isNullOrBlank()) error("pack.version", "pack_version_required")
        }

        val fallbackLanguage = language?.fallbackLanguage ?: config.menu?.defaultLanguage
        if (!fallbackLanguage.isNullOrBlank() && config.menu?.translations.orEmpty().keys.none {
                it.equals(fallbackLanguage, ignoreCase = true) || it.equals(fallbackLanguage.substringBefore('_'), ignoreCase = true)
            }) {
            warning("menu.language.fallbackLanguage", "missing_translation", "value" to fallbackLanguage)
        }

        val enabledMods = config.activeModEntries().withIndex().filter { it.value.enabled != false }
        val duplicateIds = enabledMods.mapNotNull { it.value.modId?.trim()?.lowercase()?.takeIf(String::isNotBlank) }
            .groupingBy { it }.eachCount().filterValues { it > 1 }.keys

        enabledMods.forEach { (index, mod) ->
            val path = if (config.mods != null) "mods[$index]" else "requiredMods[$index]"
            val modDisplayName = mod.displayName().takeUnless { it == "Unknown mod" } ?: "Mod #${index + 1}"
            if (mod.name.isNullOrBlank() && mod.modId.isNullOrBlank() && mod.filePattern.isNullOrBlank()) {
                error(path, "empty_entry", displayPath = modDisplayName)
            }
            if (mod.modId.isNullOrBlank() && mod.filePattern.isNullOrBlank()) {
                error(path, "missing_detector", displayPath = modDisplayName)
            }
            if (mod.category != null && ModCategory.entries.none { it.name.equals(mod.category, ignoreCase = true) }) {
                error("$path.category", "unknown_category", "value" to mod.category.orEmpty(), displayPath = modDisplayName)
            }
            if (!mod.modId.isNullOrBlank() && mod.modId!!.trim().lowercase() in duplicateIds) {
                warning("$path.modId", "duplicate_mod_id", "value" to mod.modId.orEmpty(), displayPath = modDisplayName)
            }

            mod.versionRange?.takeIf { it.isNotBlank() }?.let { range ->
                if (mod.modId.isNullOrBlank()) {
                    warning("$path.versionRange", "version_requires_mod_id", displayPath = modDisplayName)
                } else {
                    try {
                        VersionRange.createFromVersionSpec(range)
                    } catch (exception: Exception) {
                        error(
                            "$path.versionRange",
                            "invalid_version_range",
                            "value" to range,
                            "details" to exception.message.orEmpty(),
                            displayPath = modDisplayName,
                        )
                    }
                }
            }

            val links = mod.availableLinks()
            if (links.isEmpty()) error("$path.links", "missing_links", displayPath = modDisplayName)
            links.forEachIndexed { linkIndex, link ->
                val linkName = link.label?.takeIf { it.isNotBlank() } ?: "#${linkIndex + 1}"
                val linkDisplayPath = "$modDisplayName · $linkName"
                if (validHttpUri(link.url) == null && validHttpUri(link.downloadUrl) == null && link.projectId.isNullOrBlank()) {
                    error("$path.links[$linkIndex].url", "invalid_url", displayPath = linkDisplayPath)
                }
                if (link.type != null && DownloadSourceType.entries.none { it.name.equals(link.type, ignoreCase = true) }) {
                    error(
                        "$path.links[$linkIndex].type",
                        "unknown_source_type",
                        "value" to link.type.orEmpty(),
                        displayPath = linkDisplayPath,
                    )
                }
                link.sizeBytes?.let { size ->
                    if (size <= 0) error("$path.links[$linkIndex].sizeBytes", "invalid_file_size", displayPath = linkDisplayPath)
                }
                listOf(
                    Triple("sha256", link.sha256, 64),
                    Triple("sha512", link.sha512, 128),
                    Triple("sha1", link.sha1, 40),
                ).forEach { (algorithm, hash, length) ->
                    if (!hash.isNullOrBlank() && !hash.matches(Regex("^[0-9a-fA-F]{$length}$"))) {
                        error(
                            "$path.links[$linkIndex].$algorithm",
                            "invalid_hash",
                            "algorithm" to algorithm.uppercase(),
                            displayPath = linkDisplayPath,
                        )
                    }
                }
                if (link.resolvedType() == DownloadSourceType.DIRECT && link.sha256.isNullOrBlank() && link.sha512.isNullOrBlank()) {
                    warning("$path.links[$linkIndex].sha256", "missing_integrity_hash", displayPath = linkDisplayPath)
                }
                if (link.label.isNullOrBlank()) {
                    warning("$path.links[$linkIndex].label", "missing_link_label", displayPath = linkDisplayPath)
                }
            }
        }
    }

    private fun MutableList<ConfigIssue>.unknownFields(value: JsonObject, allowed: Set<String>, path: String) {
        value.keySet().filterNot(allowed::contains).forEach { field ->
            error(if (path.isEmpty()) field else "$path.$field", "unknown_field", "field" to field)
        }
    }

    private fun JsonObject.objectAt(name: String): JsonObject? = get(name)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

    private fun MutableList<ConfigIssue>.error(
        path: String,
        code: String,
        vararg arguments: Pair<String, String>,
        displayPath: String? = null,
    ) {
        add(ConfigIssue(path, code, IssueSeverity.ERROR, arguments.toMap(), displayPath))
    }

    private fun MutableList<ConfigIssue>.warning(
        path: String,
        code: String,
        vararg arguments: Pair<String, String>,
        displayPath: String? = null,
    ) {
        add(ConfigIssue(path, code, IssueSeverity.WARNING, arguments.toMap(), displayPath))
    }
}
