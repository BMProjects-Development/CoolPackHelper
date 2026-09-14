package org.bmp.cph.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.neoforged.fml.loading.FMLPaths
import org.bmp.cph.Cph
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

object ConfigManager {
    private const val MAX_CONFIG_BYTES = 8L * 1024L * 1024L
    private const val CONFIG_FILE = "coolpackhelper.json"
    private const val SCHEMA_FILE = "coolpackhelper.schema.json"
    private const val STATE_FILE = "coolpackhelper-state.json"
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @Volatile
    var config: PackHelperConfig = PackHelperConfig.default()
        private set

    @Volatile
    var validationIssues: List<ConfigIssue> = emptyList()
        private set

    val configPath: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE)
    val configDirectory: Path
        get() = FMLPaths.CONFIGDIR.get()
    private val statePath: Path
        get() = FMLPaths.GAMEDIR.get().resolve("local").resolve("coolpackhelper").resolve("state.json")
    private val legacyStatePath: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(STATE_FILE)
    private val authorSettingsPath: Path
        get() = FMLPaths.GAMEDIR.get().resolve("local").resolve("coolpackhelper").resolve("author-settings.json")

    fun editableCopy(): PackHelperConfig = synchronized(this) {
        gson.fromJson(gson.toJson(config), PackHelperConfig::class.java)
    }

    /**
     * Validates and atomically replaces the distributable configuration. The previous
     * version remains beside it as coolpackhelper.json.bak so the in-game editor is
     * recoverable even if Minecraft is interrupted while the author is working.
     */
    @Synchronized
    fun save(edited: PackHelperConfig): List<ConfigIssue> {
        edited.schemaVersion = CONFIG_SCHEMA_VERSION
        edited.schemaUrl = edited.schemaUrl ?: SCHEMA_FILE
        edited.mods = edited.activeModEntries()
        edited.requiredMods = null
        edited.showOnlyOnce = null
        edited.menu?.defaultLanguage = null
        edited.mods.orEmpty().forEach { mod ->
            if (mod.projectLinks == null && !mod.projectUrl.isNullOrBlank()) {
                mod.projectLinks = ProjectLinks(homepage = mod.projectUrl)
            }
            mod.projectUrl = null
        }

        val root = gson.toJsonTree(edited)
        val issues = ConfigValidator.validateStructure(root) + ConfigValidator.validate(edited)
        if (issues.any { it.severity == IssueSeverity.ERROR }) return issues

        try {
            val backup = configPath.resolveSibling("${configPath.fileName}.bak")
            if (Files.exists(configPath)) {
                Files.copy(configPath, backup, StandardCopyOption.REPLACE_EXISTING)
            }
            writeJson(configPath, edited)
        } catch (exception: Exception) {
            Cph.LOGGER.error("Could not save CoolPackHelper config from the in-game editor", exception)
            return issues + ConfigIssue(
                CONFIG_FILE,
                "save_error",
                IssueSeverity.ERROR,
                mapOf("details" to (exception.message ?: exception.javaClass.simpleName)),
            )
        }
        config = gson.fromJson(gson.toJson(edited), PackHelperConfig::class.java)
        validationIssues = issues
        Cph.LOGGER.info("Saved CoolPackHelper config from the in-game editor")
        return issues
    }

    fun loadAuthorSettings(): AuthorSettings = try {
        if (Files.notExists(authorSettingsPath)) AuthorSettings()
        else Files.newBufferedReader(authorSettingsPath, StandardCharsets.UTF_8).use {
            gson.fromJson(it, AuthorSettings::class.java)
        } ?: AuthorSettings()
    } catch (exception: Exception) {
        Cph.LOGGER.warn("Could not read local CoolPackHelper author settings", exception)
        AuthorSettings()
    }

    @Synchronized
    fun saveAuthorSettings(settings: AuthorSettings) {
        try {
            writeJson(authorSettingsPath, settings)
        } catch (exception: Exception) {
            Cph.LOGGER.error("Could not save local CoolPackHelper author settings", exception)
        }
    }

    @Synchronized
    fun load() {
        val path = configPath
        try {
            Files.createDirectories(path.parent)
            writeSchemaFile()
            if (Files.notExists(path)) {
                config = PackHelperConfig.default()
                writeJson(path, config)
                validationIssues = emptyList()
                Cph.LOGGER.info("Created default CoolPackHelper config at {}", path)
                return
            }

            if (Files.size(path) > MAX_CONFIG_BYTES) {
                config = PackHelperConfig.default().copy(mods = emptyList())
                validationIssues = listOf(ConfigIssue(CONFIG_FILE, "config_too_large", IssueSeverity.ERROR))
                return
            }

            val parsedRoot = Files.newBufferedReader(path, StandardCharsets.UTF_8).use {
                JsonParser.parseReader(it)
            }
            val migration = parsedRoot.takeIf { it.isJsonObject }?.asJsonObject?.let(ConfigMigrator::migrate)
            val root = migration?.root ?: parsedRoot
            if (migration?.changed == true) {
                val backup = path.resolveSibling("${path.fileName}.v${migration.fromVersion}.bak")
                Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING)
                writeJson(path, migration.root)
                Cph.LOGGER.info(
                    "Migrated CoolPackHelper config schema from {} to {}; backup: {}",
                    migration.fromVersion,
                    CONFIG_SCHEMA_VERSION,
                    backup,
                )
            }
            val loaded = gson.fromJson(root, PackHelperConfig::class.java)
            if (loaded == null) {
                config = PackHelperConfig.default().copy(mods = emptyList())
                validationIssues = listOf(ConfigIssue(CONFIG_FILE, "empty_config", IssueSeverity.ERROR))
                return
            }

            config = loaded
            validationIssues = ConfigValidator.validateStructure(root) + ConfigValidator.validate(loaded)
            validationIssues.forEach { issue ->
                val message = "CoolPackHelper config ${issue.severity.name.lowercase()} at ${issue.path}: ${issue.code} ${issue.arguments}"
                if (issue.severity == IssueSeverity.ERROR) Cph.LOGGER.error(message) else Cph.LOGGER.warn(message)
            }
        } catch (exception: Exception) {
            config = PackHelperConfig.default().copy(mods = emptyList())
            validationIssues = listOf(
                ConfigIssue(
                    CONFIG_FILE,
                    "parse_error",
                    IssueSeverity.ERROR,
                    mapOf("details" to (exception.message ?: exception.javaClass.simpleName)),
                )
            )
            Cph.LOGGER.error("Could not read CoolPackHelper config at {}", path, exception)
        }
    }

    fun hasErrors(): Boolean = validationIssues.any { it.severity == IssueSeverity.ERROR }

    fun shouldShowMenu(): Boolean = when (config.resolvedShowPolicy()) {
        ShowPolicy.UNTIL_RESOLVED -> true
        ShowPolicy.NEVER -> false
        ShowPolicy.ONCE_EVER -> {
            val state = readState()
            val packId = config.pack?.id?.takeIf { it.isNotBlank() } ?: "default"
            !state?.shownPackIds.orEmpty().contains(packId)
        }
        ShowPolicy.ONCE_PER_PACK_VERSION -> {
            val key = "${config.pack?.id.orEmpty()}:${config.pack?.version.orEmpty()}"
            val state = readState()
            !state?.shownPackVersions.orEmpty().contains(key)
        }
    }

    @Synchronized
    fun markMenuShown() {
        val state = readState() ?: ShownState()
        val updated = when (config.resolvedShowPolicy()) {
            ShowPolicy.ONCE_EVER -> {
                val packId = config.pack?.id?.takeIf { it.isNotBlank() } ?: "default"
                state.copy(shownPackIds = state.shownPackIds.orEmpty() + packId, shownAt = Instant.now().toString())
            }
            ShowPolicy.ONCE_PER_PACK_VERSION -> {
                val key = "${config.pack?.id.orEmpty()}:${config.pack?.version.orEmpty()}"
                state.copy(shownPackVersions = state.shownPackVersions.orEmpty() + key, shownAt = Instant.now().toString())
            }
            else -> return
        }
        saveState(updated)
    }

    private fun readState(): ShownState? {
        return try {
            val source = when {
                Files.exists(statePath) -> statePath
                Files.exists(legacyStatePath) -> legacyStatePath
                else -> null
            } ?: return null
            val loaded = Files.newBufferedReader(source, StandardCharsets.UTF_8).use {
                gson.fromJson(it, ShownState::class.java)
            } ?: return null
            loaded.copy(
                shownPackIds = loaded.shownPackIds.orEmpty() + if (loaded.everShown == true) {
                    setOf(config.pack?.id?.takeIf { it.isNotBlank() } ?: "default")
                } else emptySet(),
                shownPackVersions = loaded.shownPackVersions.orEmpty() + listOfNotNull(loaded.lastPackVersionKey),
            )
        } catch (exception: Exception) {
            Cph.LOGGER.warn("Could not read CoolPackHelper state; the menu will be shown again", exception)
            null
        }
    }

    private fun saveState(state: ShownState) {
        try {
            writeJson(statePath, state)
        } catch (exception: Exception) {
            Cph.LOGGER.error("Could not save CoolPackHelper display state", exception)
        }
    }

    private fun writeJson(path: Path, value: Any) {
        Files.createDirectories(path.parent)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.newBufferedWriter(temporary, StandardCharsets.UTF_8).use { gson.toJson(value, it) }
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun writeSchemaFile() {
        val target = configDirectory.resolve(SCHEMA_FILE)
        ConfigManager::class.java.getResourceAsStream("/$SCHEMA_FILE")?.use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        } ?: Cph.LOGGER.warn("Bundled CoolPackHelper JSON schema is missing")
    }

    private data class ShownState(
        var schemaVersion: Int? = CONFIG_SCHEMA_VERSION,
        var shownPackIds: Set<String>? = emptySet(),
        var shownPackVersions: Set<String>? = emptySet(),
        // Schema v2 compatibility.
        var everShown: Boolean? = false,
        var lastPackVersionKey: String? = null,
        var shownAt: String? = null,
    )
}

/** Local-only data. This file is deliberately stored outside config/ and is never exported. */
data class AuthorSettings(
    var curseForgeApiKey: String? = null,
    var translationProvider: String? = "libretranslate",
    var translationEndpoint: String? = "https://libretranslate.com/translate",
    var translationApiKeys: MutableMap<String, String>? = null,
    var translationRememberApiKey: Boolean? = null,
    // Kept only to migrate settings created before provider support.
    var translationApiKey: String? = null,
)
