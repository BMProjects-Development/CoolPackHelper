package org.bmp.cph.client.download

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.bmp.cph.Cph
import org.bmp.cph.config.ConfigManager
import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.DownloadSourceType
import org.bmp.cph.config.DownloadTrustLevel
import org.bmp.cph.config.RequiredMod
import org.bmp.cph.config.validHttpUri
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture

object DownloadResolver {
    private const val USER_AGENT = "BMP/CoolPackHelper/1.0.0"
    private const val MAX_API_RESPONSE_BYTES = 4 * 1024 * 1024
    private val gson = Gson()
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun resolveAsync(request: DownloadRequest): CompletableFuture<ResolvedDownload> = CompletableFuture.supplyAsync {
        request.source?.let { resolve(request.mod, it) } ?: resolveBest(request.mod)
    }

    internal fun resolveBest(mod: RequiredMod): ResolvedDownload {
        val sources = mod.availableLinks().sortedBy { sourcePriority(it.resolvedType()) }
        if (sources.isEmpty()) return failed(mod, DownloadLink(), "No download sources are configured")
        var firstPageOnly: ResolvedDownload? = null
        var firstFailure: ResolvedDownload? = null
        for (source in sources) {
            val attempt = try {
                resolve(mod, source)
            } catch (exception: Exception) {
                Cph.LOGGER.warn("Could not resolve {} for {}", source.displayLabel(), mod.displayName(), exception)
                failed(mod, source, exception.message ?: exception.javaClass.simpleName)
            }
            when (attempt.status) {
                DownloadResolutionStatus.READY -> return attempt
                DownloadResolutionStatus.PAGE_ONLY -> if (firstPageOnly == null) firstPageOnly = attempt
                DownloadResolutionStatus.FAILED -> if (firstFailure == null) firstFailure = attempt
            }
        }
        return firstPageOnly ?: firstFailure ?: failed(mod, sources.first(), "No usable source was found")
    }

    internal fun resolve(mod: RequiredMod, source: DownloadLink): ResolvedDownload = try {
        when (source.resolvedType()) {
            DownloadSourceType.MODRINTH -> resolveModrinth(mod, source)
            DownloadSourceType.CURSEFORGE -> resolveCurseForge(mod, source)
            DownloadSourceType.GITHUB_RELEASE -> resolveGithub(mod, source)
            DownloadSourceType.DIRECT -> resolveDirect(mod, source)
            DownloadSourceType.PAGE -> pageOnly(mod, source, "This source can only be opened in a browser")
        }
    } catch (exception: Exception) {
        Cph.LOGGER.warn("Could not resolve download source {} for {}", source.displayLabel(), mod.displayName(), exception)
        failed(mod, source, exception.message ?: exception.javaClass.simpleName)
    }

    private fun resolveModrinth(mod: RequiredMod, source: DownloadLink): ResolvedDownload {
        val project = source.projectId?.takeIf(String::isNotBlank) ?: modrinthProjectFrom(source.url)
            ?: return pageOnly(mod, source, "Set a Modrinth project ID or project URL")
        val version = if (!source.versionId.isNullOrBlank()) {
            getJsonObject("https://api.modrinth.com/v2/version/${encodePath(source.versionId!!)}")
        } else {
            val versions = getJson(
                "https://api.modrinth.com/v2/project/${encodePath(project)}/version" +
                    "?loaders=%5B%22neoforge%22%5D&game_versions=%5B%221.21.1%22%5D",
            )
            versions.takeIf(JsonElement::isJsonArray)?.asJsonArray?.firstOrNull()?.asJsonObject
                ?: return pageOnly(mod, source, "No compatible NeoForge 1.21.1 version was found on Modrinth")
        }
        val file = version.getAsJsonArray("files")?.map(JsonElement::getAsJsonObject)
            ?.sortedByDescending { it.get("primary")?.asBoolean == true }
            ?.firstOrNull { DownloadSecurity.safeFileName(it.get("filename")?.asString) != null }
            ?: return failed(mod, source, "Modrinth returned no installable JAR")
        val uri = validHttpUri(file.get("url")?.asString) ?: return failed(mod, source, "Modrinth returned an invalid download URL")
        val hashes = linkedMapOf<String, String>()
        file.getAsJsonObject("hashes")?.let {
            it.get("sha512")?.asString?.let { value -> hashes["SHA-512"] = value.lowercase() }
            it.get("sha1")?.asString?.let { value -> hashes["SHA-1"] = value.lowercase() }
        }
        configuredHashes(source).forEach { (algorithm, configured) ->
            val official = hashes[algorithm]
            require(official == null || official.equals(configured, ignoreCase = true)) { "$algorithm does not match Modrinth metadata" }
            hashes.putIfAbsent(algorithm, configured)
        }
        return ready(
            mod, source, DownloadSourceType.MODRINTH, DownloadTrustLevel.PLATFORM,
            validHttpUri(source.url) ?: URI.create("https://modrinth.com/project/$project"), uri,
            file.get("filename")?.asString, file.get("size")?.asLong, hashes,
        )
    }

    private fun resolveCurseForge(mod: RequiredMod, source: DownloadLink): ResolvedDownload {
        val direct = validHttpUri(source.downloadUrl)
        if (direct != null && direct.host.lowercase().endsWith(".forgecdn.net")) {
            val hashes = configuredHashes(source)
            if (hashes.isEmpty()) return pageOnly(mod, source, "A hash is required for a preconfigured CurseForge CDN URL")
            return ready(
                mod, source, DownloadSourceType.CURSEFORGE, DownloadTrustLevel.PLATFORM,
                validHttpUri(source.url), direct, source.fileName ?: direct.path.substringAfterLast('/'), source.sizeBytes, hashes,
            )
        }
        val projectId = source.projectId?.takeIf(String::isNotBlank)
        val fileId = source.fileId?.takeIf(String::isNotBlank) ?: source.versionId?.takeIf(String::isNotBlank)
        val apiKey = ConfigManager.loadAuthorSettings().curseForgeApiKey?.takeIf(String::isNotBlank)
        if (projectId == null || fileId == null || apiKey == null) {
            return pageOnly(mod, source, "CurseForge automatic download requires project ID, file ID, and a locally configured API key")
        }
        val file = getJsonObject("https://api.curseforge.com/v1/mods/${encodePath(projectId)}/files/${encodePath(fileId)}", mapOf("x-api-key" to apiKey))
            .getAsJsonObject("data") ?: return failed(mod, source, "CurseForge returned no file metadata")
        val uri = validHttpUri(file.get("downloadUrl")?.takeUnless(JsonElement::isJsonNull)?.asString)
            ?: return pageOnly(mod, source, "The author does not allow this file to be downloaded by third-party clients")
        val hashes = linkedMapOf<String, String>()
        file.getAsJsonArray("hashes")?.forEach { element ->
            val value = element.asJsonObject
            if (value.get("algo")?.asInt == 1) hashes["SHA-1"] = value.get("value").asString.lowercase()
        }
        configuredHashes(source).forEach { (algorithm, configured) ->
            val official = hashes[algorithm]
            require(official == null || official.equals(configured, ignoreCase = true)) { "$algorithm does not match CurseForge metadata" }
            hashes.putIfAbsent(algorithm, configured)
        }
        if (hashes.isEmpty()) return failed(mod, source, "CurseForge returned no supported integrity hash")
        return ready(
            mod, source, DownloadSourceType.CURSEFORGE, DownloadTrustLevel.PLATFORM,
            validHttpUri(source.url), uri, file.get("fileName")?.asString, file.get("fileLength")?.asLong, hashes,
        )
    }

    private fun resolveGithub(mod: RequiredMod, source: DownloadLink): ResolvedDownload {
        val direct = validHttpUri(source.downloadUrl ?: source.url)
            ?: return pageOnly(mod, source, "Set a GitHub Release Asset URL")
        val match = GITHUB_RELEASE.matchEntire(direct.toString())
            ?: return pageOnly(mod, source, "Only GitHub Release Assets can be installed automatically")
        val owner = match.groupValues[1]
        val repository = match.groupValues[2]
        val tag = URLDecoder.decode(match.groupValues[3], StandardCharsets.UTF_8)
        val assetName = URLDecoder.decode(match.groupValues[4], StandardCharsets.UTF_8)
        val hashes = configuredHashes(source).toMutableMap()
        var size = source.sizeBytes
        var immutable = false
        if (hashes["SHA-256"] == null) {
            val release = getJsonObject("https://api.github.com/repos/${encodePath(owner)}/${encodePath(repository)}/releases/tags/${encodePath(tag)}")
            immutable = release.get("immutable")?.asBoolean == true
            val asset = release.getAsJsonArray("assets")?.map(JsonElement::getAsJsonObject)
                ?.firstOrNull { it.get("name")?.asString == assetName }
            asset?.get("digest")?.takeUnless(JsonElement::isJsonNull)?.asString
                ?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.let { hashes["SHA-256"] = it.lowercase() }
            size = asset?.get("size")?.asLong ?: size
        }
        if (hashes["SHA-256"] == null) {
            return pageOnly(mod, source, "GitHub automatic download requires a SHA-256 digest")
        }
        return ready(
            mod, source, DownloadSourceType.GITHUB_RELEASE, DownloadTrustLevel.REPOSITORY,
            URI.create("https://github.com/$owner/$repository/releases/tag/${encodePath(tag)}"), direct,
            source.fileName ?: assetName, size, hashes,
            if (immutable) "Immutable GitHub release" else "Verify the repository owner before installing",
        )
    }

    private fun resolveDirect(mod: RequiredMod, source: DownloadLink): ResolvedDownload {
        val uri = validHttpUri(source.downloadUrl ?: source.url)
            ?: return pageOnly(mod, source, "Set a valid HTTPS download URL")
        val hashes = configuredHashes(source)
        if (hashes["SHA-512"] == null && hashes["SHA-256"] == null) {
            return pageOnly(mod, source, "Direct downloads require SHA-256 or SHA-512")
        }
        return ready(
            mod, source, DownloadSourceType.DIRECT, DownloadTrustLevel.UNVERIFIED,
            validHttpUri(source.url), uri, source.fileName ?: uri.path.substringAfterLast('/'), source.sizeBytes, hashes,
            "The publisher and contents of this external file are not verified",
        )
    }

    private fun ready(
        mod: RequiredMod,
        source: DownloadLink,
        type: DownloadSourceType,
        trust: DownloadTrustLevel,
        page: URI?,
        download: URI,
        fileName: String?,
        size: Long?,
        hashes: Map<String, String>,
        message: String? = null,
    ): ResolvedDownload {
        val safeName = DownloadSecurity.safeFileName(fileName)
            ?: return failed(mod, source, "The source did not provide a safe JAR filename")
        DownloadSecurity.validateUri(download, type, resolveDns = false)?.let { return failed(mod, source, it) }
        if (hashes.isEmpty()) return failed(mod, source, "No supported integrity hash is available")
        return ResolvedDownload(mod, source, type, trust, DownloadResolutionStatus.READY, page, download, safeName, size, hashes, message)
    }

    private fun pageOnly(mod: RequiredMod, source: DownloadLink, message: String) = ResolvedDownload(
        mod, source, source.resolvedType(), trustFor(source.resolvedType()), DownloadResolutionStatus.PAGE_ONLY,
        pageUri = validHttpUri(source.url), message = message,
    )

    private fun failed(mod: RequiredMod, source: DownloadLink, message: String) = ResolvedDownload(
        mod, source, source.resolvedType(), trustFor(source.resolvedType()), DownloadResolutionStatus.FAILED,
        pageUri = validHttpUri(source.url), message = message,
    )

    private fun configuredHashes(source: DownloadLink): Map<String, String> = buildMap {
        source.sha512?.trim()?.lowercase()?.takeIf(String::isNotBlank)?.let { put("SHA-512", it) }
        source.sha256?.trim()?.lowercase()?.takeIf(String::isNotBlank)?.let { put("SHA-256", it) }
        source.sha1?.trim()?.lowercase()?.takeIf(String::isNotBlank)?.let { put("SHA-1", it) }
    }

    private fun trustFor(type: DownloadSourceType) = when (type) {
        DownloadSourceType.MODRINTH, DownloadSourceType.CURSEFORGE -> DownloadTrustLevel.PLATFORM
        DownloadSourceType.GITHUB_RELEASE -> DownloadTrustLevel.REPOSITORY
        DownloadSourceType.DIRECT, DownloadSourceType.PAGE -> DownloadTrustLevel.UNVERIFIED
    }

    private fun sourcePriority(type: DownloadSourceType): Int = when (type) {
        DownloadSourceType.MODRINTH -> 0
        DownloadSourceType.CURSEFORGE -> 1
        DownloadSourceType.GITHUB_RELEASE -> 2
        DownloadSourceType.DIRECT -> 3
        DownloadSourceType.PAGE -> 4
    }

    private fun modrinthProjectFrom(value: String?): String? {
        val uri = validHttpUri(value) ?: return null
        if (uri.host?.lowercase()?.removePrefix("www.") != "modrinth.com") return null
        val segments = uri.path.split('/').filter(String::isNotBlank)
        val marker = segments.indexOfFirst { it == "mod" || it == "project" }
        return segments.getOrNull(marker + 1)?.takeIf(String::isNotBlank)
    }

    private fun getJsonObject(url: String, headers: Map<String, String> = emptyMap()): JsonObject =
        getJson(url, headers).asJsonObject

    private fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonElement {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .apply { headers.forEach(::header) }
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        val bytes = response.body().use { it.readNBytes(MAX_API_RESPONSE_BYTES + 1) }
        require(bytes.size <= MAX_API_RESPONSE_BYTES) { "The API response is too large" }
        val body = String(bytes, StandardCharsets.UTF_8)
        require(response.statusCode() in 200..299) { "HTTP ${response.statusCode()}: ${body.take(180)}" }
        return gson.fromJson(body, JsonElement::class.java)
    }

    private fun encodePath(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private val GITHUB_RELEASE = Regex("https://github\\.com/([^/]+)/([^/]+)/releases/download/([^/]+)/([^?#]+)", RegexOption.IGNORE_CASE)
}
