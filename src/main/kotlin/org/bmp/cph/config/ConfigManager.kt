package org.bmp.cph.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.neoforged.fml.loading.FMLPaths
import org.bmp.cph.Cph
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

object ConfigManager {
    private const val CONFIG_FILE = "coolpackhelper.json"
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
        get() = FMLPaths.CONFIGDIR.get().resolve(STATE_FILE)

    @Synchronized
    fun load() {
        val path = configPath
        try {
            Files.createDirectories(path.parent)
            if (Files.notExists(path)) {
                config = PackHelperConfig.default()
                writeJson(path, config)
                validationIssues = emptyList()
                Cph.LOGGER.info("Created default CoolPackHelper config at {}", path)
                return
            }

            val loaded = Files.newBufferedReader(path, StandardCharsets.UTF_8).use {
                gson.fromJson(it, PackHelperConfig::class.java)
            }
            if (loaded == null) {
                config = PackHelperConfig.default().copy(mods = emptyList())
                validationIssues = listOf(ConfigIssue("$CONFIG_FILE", "The config file is empty.", IssueSeverity.ERROR))
                return
            }

            config = loaded
            validationIssues = ConfigValidator.validate(loaded)
            validationIssues.forEach { issue ->
                val message = "CoolPackHelper config ${issue.severity.name.lowercase()} at ${issue.path}: ${issue.message}"
                if (issue.severity == IssueSeverity.ERROR) Cph.LOGGER.error(message) else Cph.LOGGER.warn(message)
            }
        } catch (exception: Exception) {
            config = PackHelperConfig.default().copy(mods = emptyList())
            validationIssues = listOf(
                ConfigIssue(
                    CONFIG_FILE,
                    "Could not parse the config: ${exception.message ?: exception.javaClass.simpleName}",
                    IssueSeverity.ERROR,
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
            if (state?.everShown == true) false
            else {
                saveState((state ?: ShownState()).copy(everShown = true, shownAt = Instant.now().toString()))
                true
            }
        }
        ShowPolicy.ONCE_PER_PACK_VERSION -> {
            val key = "${config.pack?.id.orEmpty()}:${config.pack?.version.orEmpty()}"
            val state = readState()
            if (state?.lastPackVersionKey == key) false
            else {
                saveState((state ?: ShownState()).copy(lastPackVersionKey = key, shownAt = Instant.now().toString()))
                true
            }
        }
    }

    private fun readState(): ShownState? = try {
        if (Files.notExists(statePath)) null
        else Files.newBufferedReader(statePath, StandardCharsets.UTF_8).use {
            gson.fromJson(it, ShownState::class.java)
        }
    } catch (exception: Exception) {
        Cph.LOGGER.warn("Could not read CoolPackHelper state; the menu will be shown again", exception)
        null
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

    private data class ShownState(
        var schemaVersion: Int? = CONFIG_SCHEMA_VERSION,
        var everShown: Boolean? = false,
        var lastPackVersionKey: String? = null,
        var shownAt: String? = null,
    )
}
