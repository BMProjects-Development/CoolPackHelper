package org.bmp.cph.client.download

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.zip.ZipFile

data class InspectedJar(
    val modIds: Set<String>,
    val versions: Map<String, String>,
)

object JarInspector {
    private const val MAX_METADATA_BYTES = 1024 * 1024L
    private const val MAX_ENTRIES = 100_000

    fun inspect(path: Path): InspectedJar {
        ZipFile(path.toFile()).use { zip ->
            require(zip.size() in 1..MAX_ENTRIES) { "The JAR has an invalid number of entries" }
            zip.entries().asSequence().forEach { entry ->
                require(!entry.name.startsWith('/') && entry.name.split('/').none { it == ".." }) { "The JAR contains an unsafe entry path" }
            }
            val metadata = zip.getEntry("META-INF/neoforge.mods.toml") ?: zip.getEntry("META-INF/mods.toml")
                ?: error("The file has no NeoForge mod metadata")
            require(metadata.size in 0..MAX_METADATA_BYTES || metadata.size == -1L) { "The NeoForge metadata is too large" }
            val text = zip.getInputStream(metadata).bufferedReader(StandardCharsets.UTF_8).use { reader ->
                val value = reader.readText()
                require(value.toByteArray(StandardCharsets.UTF_8).size <= MAX_METADATA_BYTES) { "The NeoForge metadata is too large" }
                value
            }
            val blocks = text.split(Regex("(?m)^\\s*\\[\\[mods]]\\s*$")).drop(1)
            val versions = linkedMapOf<String, String>()
            blocks.forEach { block ->
                val id = tomlValue(block, "modId")?.lowercase() ?: return@forEach
                versions[id] = tomlValue(block, "version").orEmpty()
            }
            require(versions.isNotEmpty()) { "The JAR declares no NeoForge mods" }
            return InspectedJar(versions.keys, versions)
        }
    }

    private fun tomlValue(text: String, key: String): String? =
        Regex("(?m)^\\s*${Regex.escape(key)}\\s*=\\s*(['\"])(.*?)\\1\\s*(?:#.*)?$")
            .find(text)?.groupValues?.get(2)?.trim()?.takeIf(String::isNotBlank)
}
