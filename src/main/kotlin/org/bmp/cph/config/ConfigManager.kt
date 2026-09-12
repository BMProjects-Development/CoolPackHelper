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
import java.security.MessageDigest
import java.time.Instant

object ConfigManager {
    private const val CONFIG_FILE = "coolpackhelper.json"
    private const val STATE_FILE = "coolpackhelper-state.json"
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @Volatile
    var config: PackHelperConfig = PackHelperConfig()
        private set

    private val configPath: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE)
    private val statePath: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(STATE_FILE)

    fun load() {
        val path = configPath
        try {
            Files.createDirectories(path.parent)
            if (Files.notExists(path)) {
                config = PackHelperConfig()
                writeJson(path, config)
                Cph.LOGGER.info("Created default CoolPackHelper config at {}", path)
                return
            }

            val loaded = Files.newBufferedReader(path, StandardCharsets.UTF_8).use {
                gson.fromJson(it, PackHelperConfig::class.java)
            }
            if (loaded == null) {
                Cph.LOGGER.error("CoolPackHelper config is empty: {}", path)
                config = PackHelperConfig(requiredMods = emptyList())
                return
            }

            config = loaded
            if ((loaded.schemaVersion ?: 0) > CONFIG_SCHEMA_VERSION) {
                Cph.LOGGER.warn(
                    "CoolPackHelper config schema {} is newer than supported schema {}",
                    loaded.schemaVersion,
                    CONFIG_SCHEMA_VERSION,
                )
            }
        } catch (exception: Exception) {
            config = PackHelperConfig(requiredMods = emptyList())
            Cph.LOGGER.error("Could not read CoolPackHelper config at {}. The menu will not be shown.", path, exception)
        }
    }

    fun shouldShowOnce(): Boolean {
        if (config.showOnlyOnce != true) return true

        val fingerprint = fingerprint(config)
        val previous = readState()
        if (previous?.lastShownFingerprint == fingerprint) return false

        try {
            writeJson(
                statePath,
                ShownState(lastShownFingerprint = fingerprint, shownAt = Instant.now().toString()),
            )
        } catch (exception: Exception) {
            Cph.LOGGER.error("Could not save CoolPackHelper one-time display state", exception)
        }
        return true
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

    private fun fingerprint(value: PackHelperConfig): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(gson.toJson(value).toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
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
        var lastShownFingerprint: String? = null,
        var shownAt: String? = null,
    )
}
