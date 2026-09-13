package org.bmp.cph.client.editor

import com.google.gson.Gson
import com.google.gson.JsonObject
import net.neoforged.fml.loading.FMLPaths
import org.bmp.cph.Cph
import org.bmp.cph.config.ModCategory
import org.bmp.cph.config.RequiredMod
import org.bmp.cph.config.validHttpUri
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.zip.ZipFile

enum class ScanPlatform(val displayName: String) {
    MODRINTH("Modrinth"),
    CURSEFORGE("CurseForge"),
}

enum class PlatformMatchStatus {
    FOUND,
    NOT_FOUND,
    UNKNOWN,
}

data class LocalModArtifact(
    val path: Path,
    val fileName: String,
    val sha1: String,
    val curseForgeFingerprint: Long,
    val name: String,
    val modId: String?,
    val version: String?,
    val description: String?,
    val homepage: String?,
) {
    fun toDraft(): RequiredMod = RequiredMod(
        enabled = false,
        category = ModCategory.REQUIRED.name,
        name = name,
        modId = modId,
        versionRange = version?.takeIf { it.isNotBlank() && !it.contains('$') }?.let { "[$it]" },
        filePattern = fileName,
        descriptions = description?.takeIf(String::isNotBlank)?.let { linkedMapOf("en_us" to it) } ?: linkedMapOf(),
        links = validHttpUri(homepage)?.let {
            listOf(org.bmp.cph.config.DownloadLink(label = it.host, url = it.toString()))
        } ?: emptyList(),
    )
}

data class PlatformScanItem(
    val artifact: LocalModArtifact,
    val status: PlatformMatchStatus,
    val projectId: String? = null,
)

data class PlatformScanReport(
    val platform: ScanPlatform,
    val items: List<PlatformScanItem>,
    val error: String? = null,
)

object PlatformScanner {
    private val gson = Gson()
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun scanAsync(platform: ScanPlatform, curseForgeApiKey: String? = null): CompletableFuture<PlatformScanReport> =
        CompletableFuture.supplyAsync {
            val artifacts = inspectModsFolder()
            if (artifacts.isEmpty()) return@supplyAsync PlatformScanReport(platform, emptyList())
            try {
                when (platform) {
                    ScanPlatform.MODRINTH -> scanModrinth(artifacts)
                    ScanPlatform.CURSEFORGE -> scanCurseForge(artifacts, curseForgeApiKey.orEmpty())
                }
            } catch (exception: Exception) {
                Cph.LOGGER.error("Could not scan installed mods against {}", platform.displayName, exception)
                PlatformScanReport(
                    platform,
                    artifacts.map { PlatformScanItem(it, PlatformMatchStatus.UNKNOWN) },
                    exception.message ?: exception.javaClass.simpleName,
                )
            }
        }

    internal fun inspectModsFolder(): List<LocalModArtifact> {
        val directory = FMLPaths.MODSDIR.get()
        if (Files.notExists(directory)) return emptyList()
        return Files.list(directory).use { stream ->
            stream.filter(Files::isRegularFile)
                .filter { it.fileName.toString().endsWith(".jar", ignoreCase = true) }
                .sorted()
                .map(::inspectJar)
                .filter { it.modId != Cph.ID }
                .toList()
        }
    }

    private fun inspectJar(path: Path): LocalModArtifact {
        val metadata = readMetadata(path)
        val bytes = Files.readAllBytes(path)
        val sha1 = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
        return LocalModArtifact(
            path = path,
            fileName = path.fileName.toString(),
            sha1 = sha1,
            curseForgeFingerprint = curseForgeFingerprint(bytes),
            name = metadata["displayName"] ?: path.fileName.toString().removeSuffix(".jar"),
            modId = metadata["modId"],
            version = metadata["version"],
            description = metadata["description"],
            homepage = metadata["displayURL"],
        )
    }

    private fun readMetadata(path: Path): Map<String, String> = try {
        ZipFile(path.toFile()).use { zip ->
            val entry = zip.getEntry("META-INF/neoforge.mods.toml") ?: zip.getEntry("META-INF/mods.toml") ?: return emptyMap()
            val text = zip.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val primaryBlock = text.substringAfter("[[mods]]", text)
            listOf("modId", "displayName", "version", "description", "displayURL")
                .mapNotNull { key -> tomlValue(primaryBlock, key)?.let { key to it } }
                .toMap()
        }
    } catch (exception: Exception) {
        Cph.LOGGER.debug("Could not read metadata from {}", path.fileName, exception)
        emptyMap()
    }

    private fun tomlValue(text: String, key: String): String? {
        val triple = Regex("(?ms)^\\s*${Regex.escape(key)}\\s*=\\s*['\"]{3}(.*?)['\"]{3}")
            .find(text)?.groupValues?.get(1)?.trim()
        if (!triple.isNullOrBlank()) return triple
        return Regex("(?m)^\\s*${Regex.escape(key)}\\s*=\\s*(['\"])(.*?)\\1\\s*(?:#.*)?$")
            .find(text)?.groupValues?.get(2)?.trim()?.takeIf(String::isNotBlank)
    }

    private fun scanModrinth(artifacts: List<LocalModArtifact>): PlatformScanReport {
        val matches = mutableMapOf<String, String>()
        artifacts.chunked(100).forEach { chunk ->
            val body = JsonObject().apply {
                add("hashes", gson.toJsonTree(chunk.map(LocalModArtifact::sha1)))
                addProperty("algorithm", "sha1")
            }
            val response = sendJson(
                "https://api.modrinth.com/v2/version_files",
                body,
                mapOf("User-Agent" to "BMP/CoolPackHelper/1.3.0 (https://github.com/BMPixel/CoolPackHelper)"),
            )
            response.entrySet().forEach { (hash, value) ->
                value.takeIf { it.isJsonObject }?.asJsonObject?.get("project_id")?.asString?.let { matches[hash] = it }
            }
        }
        return PlatformScanReport(
            ScanPlatform.MODRINTH,
            artifacts.map { artifact ->
                PlatformScanItem(
                    artifact,
                    if (matches.containsKey(artifact.sha1)) PlatformMatchStatus.FOUND else PlatformMatchStatus.NOT_FOUND,
                    matches[artifact.sha1],
                )
            },
        )
    }

    private fun scanCurseForge(artifacts: List<LocalModArtifact>, apiKey: String): PlatformScanReport {
        require(apiKey.isNotBlank()) { "CurseForge API key is required" }
        val unmatched = mutableSetOf<Long>()
        val matched = mutableMapOf<Long, String>()
        artifacts.chunked(100).forEach { chunk ->
            val body = JsonObject().apply {
                add("fingerprints", gson.toJsonTree(chunk.map(LocalModArtifact::curseForgeFingerprint)))
            }
            val response = sendJson(
                "https://api.curseforge.com/v1/fingerprints/432",
                body,
                mapOf("x-api-key" to apiKey),
            ).getAsJsonObject("data") ?: error("CurseForge returned no data")
            response.getAsJsonArray("unmatchedFingerprints")?.forEach { unmatched += it.asLong }
            response.getAsJsonArray("exactMatches")?.forEach { element ->
                val match = element.asJsonObject
                val file = match.getAsJsonObject("file")
                val fingerprint = file?.get("fileFingerprint")?.asLong ?: match.get("id")?.asLong ?: return@forEach
                matched[fingerprint] = file?.get("modId")?.asString.orEmpty()
            }
        }
        return PlatformScanReport(
            ScanPlatform.CURSEFORGE,
            artifacts.map { artifact ->
                val fingerprint = artifact.curseForgeFingerprint
                PlatformScanItem(
                    artifact,
                    when {
                        matched.containsKey(fingerprint) -> PlatformMatchStatus.FOUND
                        fingerprint in unmatched -> PlatformMatchStatus.NOT_FOUND
                        else -> PlatformMatchStatus.UNKNOWN
                    },
                    matched[fingerprint],
                )
            },
        )
    }

    private fun sendJson(url: String, body: JsonObject, headers: Map<String, String>): JsonObject {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(40))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
        headers.forEach(builder::header)
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            error("HTTP ${response.statusCode()}: ${response.body().take(240)}")
        }
        return gson.fromJson(response.body(), JsonObject::class.java)
    }

    /** CurseForge's normalized MurmurHash2 fingerprint (whitespace bytes are ignored). */
    internal fun curseForgeFingerprint(source: ByteArray): Long {
        val filtered = ByteArrayOutputStream(source.size)
        source.forEach { byte ->
            val value = byte.toInt() and 0xFF
            if (value != 9 && value != 10 && value != 13 && value != 32) filtered.write(value)
        }
        val data = filtered.toByteArray()
        var hash = 1 xor data.size
        var index = 0
        var remaining = data.size
        while (remaining >= 4) {
            var k = (data[index].toInt() and 0xFF) or
                ((data[index + 1].toInt() and 0xFF) shl 8) or
                ((data[index + 2].toInt() and 0xFF) shl 16) or
                ((data[index + 3].toInt() and 0xFF) shl 24)
            k *= 0x5bd1e995
            k = k xor (k ushr 24)
            k *= 0x5bd1e995
            hash *= 0x5bd1e995
            hash = hash xor k
            index += 4
            remaining -= 4
        }
        when (remaining) {
            3 -> {
                hash = hash xor ((data[index + 2].toInt() and 0xFF) shl 16)
                hash = hash xor ((data[index + 1].toInt() and 0xFF) shl 8)
                hash = hash xor (data[index].toInt() and 0xFF)
                hash *= 0x5bd1e995
            }
            2 -> {
                hash = hash xor ((data[index + 1].toInt() and 0xFF) shl 8)
                hash = hash xor (data[index].toInt() and 0xFF)
                hash *= 0x5bd1e995
            }
            1 -> {
                hash = hash xor (data[index].toInt() and 0xFF)
                hash *= 0x5bd1e995
            }
        }
        hash = hash xor (hash ushr 13)
        hash *= 0x5bd1e995
        hash = hash xor (hash ushr 15)
        return hash.toLong() and 0xFFFF_FFFFL
    }
}
