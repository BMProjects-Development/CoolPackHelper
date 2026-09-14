package org.bmp.cph.client.editor

import com.google.gson.Gson
import com.google.gson.JsonObject
import net.neoforged.fml.loading.FMLPaths
import org.bmp.cph.Cph
import org.bmp.cph.client.curseforge.CurseForgeApiSupport
import org.bmp.cph.config.ModCategory
import org.bmp.cph.config.RequiredMod
import org.bmp.cph.config.validHttpUri
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
    private const val MAX_METADATA_BYTES = 1024 * 1024
    private const val MAX_API_RESPONSE_BYTES = 8 * 1024 * 1024
    private val gson = Gson()
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER)
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
        val jars = Files.list(directory).use { stream ->
            stream.filter(Files::isRegularFile)
                .filter { it.fileName.toString().endsWith(".jar", ignoreCase = true) }
                .sorted()
                .toList()
        }
        return jars.mapNotNull { path ->
            try {
                inspectJar(path).takeUnless { it.modId == Cph.ID }
            } catch (exception: Exception) {
                Cph.LOGGER.warn("Skipping unreadable mod file {} during platform scan", path.fileName, exception)
                null
            }
        }
    }

    private fun inspectJar(path: Path): LocalModArtifact {
        val metadata = readMetadata(path)
        val (sha1, fingerprint) = hashes(path)
        return LocalModArtifact(
            path = path,
            fileName = path.fileName.toString(),
            sha1 = sha1,
            curseForgeFingerprint = fingerprint,
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
            require(entry.size < 0 || entry.size <= MAX_METADATA_BYTES) { "Mod metadata is too large" }
            val bytes = zip.getInputStream(entry).use { it.readNBytes(MAX_METADATA_BYTES + 1) }
            require(bytes.size <= MAX_METADATA_BYTES) { "Mod metadata is too large" }
            val text = String(bytes, StandardCharsets.UTF_8)
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
                mapOf("User-Agent" to "BMP/CoolPackHelper/1.0.0 (https://github.com/BMPixel/CoolPackHelper)"),
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
        val normalizedKey = CurseForgeApiSupport.normalizeKey(apiKey)
        require(normalizedKey.isNotBlank()) { "CurseForge API key is required" }
        val unmatched = mutableSetOf<Long>()
        val matched = mutableMapOf<Long, String>()
        artifacts.chunked(100).forEach { chunk ->
            val body = JsonObject().apply {
                add("fingerprints", gson.toJsonTree(chunk.map(LocalModArtifact::curseForgeFingerprint)))
            }
            val response = sendJson(
                "https://api.curseforge.com/v1/fingerprints/432",
                body,
                mapOf("x-api-key" to normalizedKey),
            ).getAsJsonObject("data") ?: error("CurseForge returned no data")
            response.getAsJsonArray("unmatchedFingerprints")?.forEach { unmatched += it.asLong }
            response.getAsJsonArray("exactMatches")?.forEach { element ->
                val match = element.asJsonObject
                val file = match.getAsJsonObject("file")
                val fingerprint = file?.get("fileFingerprint")?.asLong ?: return@forEach
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
            .header("User-Agent", CurseForgeApiSupport.USER_AGENT)
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
        headers.forEach(builder::setHeader)
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
        val bytes = response.body().use { it.readNBytes(MAX_API_RESPONSE_BYTES + 1) }
        require(bytes.size <= MAX_API_RESPONSE_BYTES) { "The platform response is too large" }
        val responseBody = String(bytes, StandardCharsets.UTF_8)
        if (response.statusCode() !in 200..299) {
            val isCurseForge = headers.keys.any { it.equals("x-api-key", ignoreCase = true) }
            error(if (isCurseForge) CurseForgeApiSupport.error(response.statusCode(), responseBody)
            else "HTTP ${response.statusCode()}: ${responseBody.take(240)}")
        }
        return gson.fromJson(responseBody, JsonObject::class.java)
    }

    /** CurseForge's normalized MurmurHash2 fingerprint (whitespace bytes are ignored). */
    internal fun curseForgeFingerprint(source: ByteArray): Long {
        val length = source.count(::isFingerprintByte)
        val accumulator = Murmur2Accumulator(length)
        source.forEach { byte -> if (isFingerprintByte(byte)) accumulator.add(byte.toInt() and 0xFF) }
        return accumulator.finish()
    }

    private fun hashes(path: Path): Pair<String, Long> {
        var normalizedLength = 0
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                for (index in 0 until count) if (isFingerprintByte(buffer[index])) normalizedLength++
            }
        }
        val sha1 = MessageDigest.getInstance("SHA-1")
        val fingerprint = Murmur2Accumulator(normalizedLength)
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                sha1.update(buffer, 0, count)
                for (index in 0 until count) {
                    val byte = buffer[index]
                    if (isFingerprintByte(byte)) fingerprint.add(byte.toInt() and 0xFF)
                }
            }
        }
        return sha1.digest().joinToString("") { "%02x".format(it) } to fingerprint.finish()
    }

    private fun isFingerprintByte(byte: Byte): Boolean {
        val value = byte.toInt() and 0xFF
        return value != 9 && value != 10 && value != 13 && value != 32
    }

    private class Murmur2Accumulator(length: Int) {
        private var hash = 1 xor length
        private val tail = IntArray(4)
        private var tailSize = 0

        fun add(value: Int) {
            tail[tailSize++] = value
            if (tailSize == 4) {
                var block = tail[0] or (tail[1] shl 8) or (tail[2] shl 16) or (tail[3] shl 24)
                block *= 0x5bd1e995
                block = block xor (block ushr 24)
                block *= 0x5bd1e995
                hash *= 0x5bd1e995
                hash = hash xor block
                tailSize = 0
            }
        }

        fun finish(): Long {
            when (tailSize) {
                3 -> {
                    hash = hash xor (tail[2] shl 16)
                    hash = hash xor (tail[1] shl 8)
                    hash = hash xor tail[0]
                    hash *= 0x5bd1e995
                }
                2 -> {
                    hash = hash xor (tail[1] shl 8)
                    hash = hash xor tail[0]
                    hash *= 0x5bd1e995
                }
                1 -> {
                    hash = hash xor tail[0]
                    hash *= 0x5bd1e995
                }
            }
            hash = hash xor (hash ushr 13)
            hash *= 0x5bd1e995
            hash = hash xor (hash ushr 15)
            return hash.toLong() and 0xFFFF_FFFFL
        }
    }
}
