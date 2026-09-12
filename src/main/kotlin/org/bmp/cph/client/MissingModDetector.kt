package org.bmp.cph.client

import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
import org.apache.maven.artifact.versioning.VersionRange
import org.bmp.cph.Cph
import org.bmp.cph.config.RequiredMod
import java.nio.file.Files

enum class RequirementStatus {
    MISSING,
    WRONG_VERSION,
}

data class ModCheckResult(
    val mod: RequiredMod,
    val status: RequirementStatus,
    val installedVersion: String? = null,
)

object MissingModDetector {
    fun findUnsatisfied(entries: List<RequiredMod>): List<ModCheckResult> {
        val enabled = entries.filter { it.enabled != false }
        if (enabled.isEmpty()) return emptyList()

        val fileNames = readModFileNames()
        return enabled.mapNotNull { inspect(it, fileNames) }
            .sortedBy { it.mod.resolvedCategory().ordinal }
    }

    private fun inspect(entry: RequiredMod, fileNames: List<String>): ModCheckResult? {
        val modId = entry.modId?.trim().orEmpty()
        if (modId.isNotEmpty()) {
            val container = ModList.get().getModContainerById(modId).orElse(null)
                ?: return ModCheckResult(entry, RequirementStatus.MISSING)
            val installedVersion = container.modInfo.version.toString()
            val requestedRange = entry.versionRange?.trim().orEmpty()
            if (requestedRange.isNotEmpty()) {
                return try {
                    if (VersionRange.createFromVersionSpec(requestedRange).containsVersion(container.modInfo.version)) null
                    else ModCheckResult(entry, RequirementStatus.WRONG_VERSION, installedVersion)
                } catch (exception: Exception) {
                    Cph.LOGGER.error("Invalid version range '{}' for mod '{}'", requestedRange, modId, exception)
                    ModCheckResult(entry, RequirementStatus.WRONG_VERSION, installedVersion)
                }
            }
            return null
        }

        val pattern = entry.filePattern?.trim().orEmpty()
        if (pattern.isEmpty()) return ModCheckResult(entry, RequirementStatus.MISSING)
        return if (fileNames.any(wildcardRegex(pattern)::matches)) null
        else ModCheckResult(entry, RequirementStatus.MISSING)
    }

    private fun readModFileNames(): List<String> {
        return try {
            val modsDirectory = FMLPaths.MODSDIR.get()
            if (Files.notExists(modsDirectory)) {
                emptyList()
            } else {
                Files.list(modsDirectory).use { paths ->
                    paths.filter(Files::isRegularFile).map { it.fileName.toString() }.toList()
                }
            }
        } catch (exception: Exception) {
            Cph.LOGGER.warn("Could not inspect the mods directory", exception)
            emptyList()
        }
    }

    private fun wildcardRegex(pattern: String): Regex {
        val expression = buildString {
            append('^')
            pattern.forEach { character ->
                when (character) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(character.toString()))
                }
            }
            append('$')
        }
        return Regex(expression, RegexOption.IGNORE_CASE)
    }
}
