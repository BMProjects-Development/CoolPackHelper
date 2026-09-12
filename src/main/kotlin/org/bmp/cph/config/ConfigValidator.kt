package org.bmp.cph.config

import org.apache.maven.artifact.versioning.VersionRange

enum class IssueSeverity {
    ERROR,
    WARNING,
}

data class ConfigIssue(
    val path: String,
    val message: String,
    val severity: IssueSeverity,
)

object ConfigValidator {
    fun validate(config: PackHelperConfig): List<ConfigIssue> = buildList {
        val schema = config.schemaVersion ?: 1
        if (schema > CONFIG_SCHEMA_VERSION) {
            error("schemaVersion", "Schema $schema is newer than supported schema $CONFIG_SCHEMA_VERSION.")
        }

        if (config.showPolicy != null && ShowPolicy.entries.none { it.name.equals(config.showPolicy, ignoreCase = true) }) {
            error("showPolicy", "Unknown policy '${config.showPolicy}'.")
        }

        if (config.resolvedShowPolicy() == ShowPolicy.ONCE_PER_PACK_VERSION) {
            if (config.pack?.id.isNullOrBlank()) error("pack.id", "A non-empty pack id is required by ONCE_PER_PACK_VERSION.")
            if (config.pack?.version.isNullOrBlank()) error("pack.version", "A non-empty pack version is required by ONCE_PER_PACK_VERSION.")
        }

        val defaultLanguage = config.menu?.defaultLanguage
        if (!defaultLanguage.isNullOrBlank() && config.menu?.translations.orEmpty().keys.none {
                it.equals(defaultLanguage, ignoreCase = true)
            }) {
            warning("menu.defaultLanguage", "No translation exists for '$defaultLanguage'; English defaults will be used.")
        }

        val enabledMods = config.activeModEntries().withIndex().filter { it.value.enabled != false }
        val duplicateIds = enabledMods.mapNotNull { it.value.modId?.lowercase()?.takeIf(String::isNotBlank) }
            .groupingBy { it }.eachCount().filterValues { it > 1 }.keys

        enabledMods.forEach { (index, mod) ->
            val path = if (config.mods != null) "mods[$index]" else "requiredMods[$index]"
            if (mod.name.isNullOrBlank() && mod.modId.isNullOrBlank() && mod.filePattern.isNullOrBlank()) {
                error(path, "The entry has no name or detector.")
            }
            if (mod.modId.isNullOrBlank() && mod.filePattern.isNullOrBlank()) {
                error(path, "Set modId or filePattern.")
            }
            if (mod.category != null && ModCategory.entries.none { it.name.equals(mod.category, ignoreCase = true) }) {
                error("$path.category", "Unknown category '${mod.category}'.")
            }
            if (!mod.modId.isNullOrBlank() && mod.modId!!.lowercase() in duplicateIds) {
                warning("$path.modId", "Duplicate mod id '${mod.modId}'.")
            }

            mod.versionRange?.takeIf { it.isNotBlank() }?.let { range ->
                if (mod.modId.isNullOrBlank()) {
                    warning("$path.versionRange", "Version checks require modId; the range will be ignored.")
                } else {
                    try {
                        VersionRange.createFromVersionSpec(range)
                    } catch (exception: Exception) {
                        error("$path.versionRange", "Invalid Maven version range '$range': ${exception.message}")
                    }
                }
            }

            val links = mod.availableLinks()
            if (links.isEmpty()) {
                error("$path.links", "Add at least one download link.")
            }
            links.forEachIndexed { linkIndex, link ->
                if (validHttpUri(link.url) == null) {
                    error("$path.links[$linkIndex].url", "Only a valid HTTP or HTTPS URL is allowed.")
                }
                if (link.label.isNullOrBlank()) {
                    warning("$path.links[$linkIndex].label", "The website domain will be used as the button label.")
                }
            }
        }
    }

    private fun MutableList<ConfigIssue>.error(path: String, message: String) {
        add(ConfigIssue(path, message, IssueSeverity.ERROR))
    }

    private fun MutableList<ConfigIssue>.warning(path: String, message: String) {
        add(ConfigIssue(path, message, IssueSeverity.WARNING))
    }
}
