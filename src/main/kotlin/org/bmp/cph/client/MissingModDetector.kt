package org.bmp.cph.client

import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
import org.bmp.cph.Cph
import org.bmp.cph.config.RequiredMod
import java.nio.file.Files

object MissingModDetector {
    fun findMissing(entries: List<RequiredMod>): List<RequiredMod> {
        val fileNames = readModFileNames()
        return entries.filter { it.enabled != false }.filterNot { isInstalled(it, fileNames) }
    }

    private fun isInstalled(entry: RequiredMod, fileNames: List<String>): Boolean {
        val modIdMatches = entry.modId?.trim()?.takeIf { it.isNotEmpty() }?.let { ModList.get().isLoaded(it) } == true
        val fileMatches = entry.filePattern?.trim()?.takeIf { it.isNotEmpty() }?.let { pattern ->
            val regex = wildcardRegex(pattern)
            fileNames.any(regex::matches)
        } == true

        if (entry.modId.isNullOrBlank() && entry.filePattern.isNullOrBlank()) {
            Cph.LOGGER.warn("Required mod entry '{}' has neither modId nor filePattern and will be treated as missing", entry.name)
        }
        return modIdMatches || fileMatches
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
