package org.bmp.cph.config

import com.google.gson.JsonObject

data class ConfigMigrationResult(
    val root: JsonObject,
    val fromVersion: Int,
    val changed: Boolean,
)

object ConfigMigrator {
    fun migrate(source: JsonObject): ConfigMigrationResult {
        val root = source.deepCopy()
        val fromVersion = root.get("schemaVersion")?.takeIf { it.isJsonPrimitive }?.asInt ?: 1
        if (fromVersion >= CONFIG_SCHEMA_VERSION) return ConfigMigrationResult(root, fromVersion, false)

        if (!root.has("mods") && root.get("requiredMods")?.isJsonArray == true) {
            root.add("mods", root.getAsJsonArray("requiredMods").deepCopy())
        }
        root.remove("requiredMods")

        if (!root.has("showPolicy")) {
            root.addProperty("showPolicy", if (root.get("showOnlyOnce")?.asBoolean == true) {
                ShowPolicy.ONCE_EVER.name
            } else ShowPolicy.UNTIL_RESOLVED.name)
        }
        root.remove("showOnlyOnce")

        root.get("menu")?.takeIf { it.isJsonObject }?.asJsonObject?.let { menu ->
            val legacyDefault = menu.get("defaultLanguage")?.takeIf { it.isJsonPrimitive }?.asString
            if (!legacyDefault.isNullOrBlank()) {
                val language = menu.get("language")?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: JsonObject().also { menu.add("language", it) }
                if (!language.has("fallbackLanguage")) language.addProperty("fallbackLanguage", legacyDefault)
            }
            menu.remove("defaultLanguage")
        }

        root.get("mods")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
            if (!element.isJsonObject) return@forEach
            val mod = element.asJsonObject
            val legacyProjectUrl = mod.get("projectUrl")?.takeIf { it.isJsonPrimitive }?.asString
            if (!legacyProjectUrl.isNullOrBlank()) {
                val projectLinks = mod.get("projectLinks")?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: JsonObject().also { mod.add("projectLinks", it) }
                if (!projectLinks.has("homepage")) projectLinks.addProperty("homepage", legacyProjectUrl)
            }
            mod.remove("projectUrl")
        }

        root.addProperty("schemaVersion", CONFIG_SCHEMA_VERSION)
        return ConfigMigrationResult(root, fromVersion, true)
    }
}
