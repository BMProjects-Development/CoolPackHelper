package org.bmp.cph.client.download

import org.bmp.cph.client.cphMessage
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.zip.ZipFile

data class InspectedJar(
    val modIds: Set<String>,
    val versions: Map<String, String>,
)

object JarInspector {
    private const val MAX_METADATA_BYTES = 1024 * 1024
    private const val MAX_ENTRIES = 100_000

    fun inspect(path: Path): InspectedJar {
        ZipFile(path.toFile()).use { zip ->
            require(zip.size() in 1..MAX_ENTRIES) { cphMessage("cph.download.error.jar_entries") }
            zip.entries().asSequence().forEach { entry ->
                require(!entry.name.startsWith('/') && entry.name.split('/').none { it == ".." }) { cphMessage("cph.download.error.jar_path") }
            }
            val metadata = zip.getEntry("META-INF/neoforge.mods.toml") ?: zip.getEntry("META-INF/mods.toml")
                ?: error(cphMessage("cph.download.error.jar_metadata_missing"))
            require(metadata.size in 0..MAX_METADATA_BYTES.toLong() || metadata.size == -1L) { cphMessage("cph.download.error.jar_metadata_large") }
            val metadataBytes = zip.getInputStream(metadata).use { it.readNBytes(MAX_METADATA_BYTES + 1) }
            require(metadataBytes.size <= MAX_METADATA_BYTES) { cphMessage("cph.download.error.jar_metadata_large") }
            val text = String(metadataBytes, StandardCharsets.UTF_8)
            val blocks = text.split(Regex("(?m)^\\s*\\[\\[mods]]\\s*$")).drop(1)
            val versions = linkedMapOf<String, String>()
            blocks.forEach { block ->
                val id = tomlValue(block, "modId")?.lowercase() ?: return@forEach
                versions[id] = tomlValue(block, "version").orEmpty()
            }
            require(versions.isNotEmpty()) { cphMessage("cph.download.error.jar_no_mods") }
            return InspectedJar(versions.keys, versions)
        }
    }

    private fun tomlValue(text: String, key: String): String? =
        Regex("(?m)^\\s*${Regex.escape(key)}\\s*=\\s*(['\"])(.*?)\\1\\s*(?:#.*)?$")
            .find(text)?.groupValues?.get(2)?.trim()?.takeIf(String::isNotBlank)
}
