package org.bmp.cph.client.editor

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.bmp.cph.config.ConfigManager
import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.DownloadSourceType
import org.bmp.cph.config.RequiredMod
import org.bmp.cph.config.validHttpUri
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture

data class ProjectMetadata(
    val projectId: String,
    val name: String,
    val summary: String?,
    val iconUrl: String?,
    val projectUrl: String,
    val authors: List<String>,
    val license: String?,
)

object ProjectMetadataService {
    private const val USER_AGENT = "BMP/CoolPackHelper/1.0.0 (https://github.com/BMPixel/CoolPackHelper)"
    private val gson = Gson()
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun fetchAsync(source: DownloadLink): CompletableFuture<ProjectMetadata> = CompletableFuture.supplyAsync {
        when (source.resolvedType()) {
            DownloadSourceType.MODRINTH -> fetchModrinth(source)
            DownloadSourceType.CURSEFORGE -> fetchCurseForge(source)
            else -> error("Metadata import is available only for Modrinth and CurseForge projects")
        }
    }

    fun apply(metadata: ProjectMetadata, mod: RequiredMod, source: DownloadLink, locale: String = "en_us") {
        mod.name = metadata.name
        mod.iconUrl = metadata.iconUrl
        mod.projectUrl = metadata.projectUrl
        mod.authors = metadata.authors
        mod.license = metadata.license
        metadata.summary?.takeIf(String::isNotBlank)?.let { summary ->
            mod.descriptions = mod.descriptions.orEmpty().toMutableMap().also { values ->
                values[locale] = summary
            }
        }
        source.projectId = metadata.projectId
        source.url = metadata.projectUrl
    }

    private fun fetchModrinth(source: DownloadLink): ProjectMetadata {
        val id = source.projectId?.takeIf(String::isNotBlank) ?: modrinthId(source.url)
            ?: error("Set a Modrinth project ID or project URL first")
        val project = getJson("https://api.modrinth.com/v2/project/${encode(id)}").asJsonObject
        require(project.get("project_type")?.asString == "mod") { "The selected Modrinth project is not a mod" }
        val canonicalId = project.get("id").asString
        val slug = project.get("slug")?.takeUnless(JsonElement::isJsonNull)?.asString ?: canonicalId
        val members = getJson("https://api.modrinth.com/v2/project/${encode(canonicalId)}/members").asJsonArray
            .mapNotNull { entry ->
                val value = entry.asJsonObject
                if (value.get("accepted")?.asBoolean == false) null
                else value.getAsJsonObject("user")?.let { user ->
                    user.get("name")?.takeUnless(JsonElement::isJsonNull)?.asString
                        ?.takeIf(String::isNotBlank) ?: user.get("username")?.asString
                }
            }
        val license = project.getAsJsonObject("license")?.let { value ->
            value.get("name")?.takeUnless(JsonElement::isJsonNull)?.asString
                ?: value.get("id")?.takeUnless(JsonElement::isJsonNull)?.asString
        }
        return ProjectMetadata(
            projectId = canonicalId,
            name = project.get("title").asString.trim().take(256),
            summary = project.get("description")?.asString?.trim()?.take(4096),
            iconUrl = officialIcon(project.get("icon_url")?.takeUnless(JsonElement::isJsonNull)?.asString, DownloadSourceType.MODRINTH),
            projectUrl = "https://modrinth.com/mod/$slug",
            authors = members.distinct().take(32),
            license = license?.trim()?.take(256),
        )
    }

    private fun fetchCurseForge(source: DownloadLink): ProjectMetadata {
        val id = source.projectId?.trim()?.takeIf { it.matches(Regex("^[0-9]+$")) }
            ?: error("Set the numeric CurseForge project ID first")
        val key = ConfigManager.loadAuthorSettings().curseForgeApiKey?.takeIf(String::isNotBlank)
            ?: error("Configure a local CurseForge API key in the scan screen first")
        val project = getJson("https://api.curseforge.com/v1/mods/${encode(id)}", mapOf("x-api-key" to key))
            .asJsonObject.getAsJsonObject("data") ?: error("CurseForge returned no project metadata")
        val authors = project.getAsJsonArray("authors")?.mapNotNull { entry ->
            entry.asJsonObject.get("name")?.asString?.trim()?.takeIf(String::isNotBlank)
        }.orEmpty()
        val page = project.getAsJsonObject("links")?.get("websiteUrl")?.takeUnless(JsonElement::isJsonNull)?.asString
            ?: "https://www.curseforge.com/minecraft/mc-mods/${project.get("slug")?.asString ?: id}"
        return ProjectMetadata(
            projectId = project.get("id").asString,
            name = project.get("name").asString.trim().take(256),
            summary = project.get("summary")?.takeUnless(JsonElement::isJsonNull)?.asString?.trim()?.take(4096),
            iconUrl = officialIcon(
                project.getAsJsonObject("logo")?.get("thumbnailUrl")?.takeUnless(JsonElement::isJsonNull)?.asString,
                DownloadSourceType.CURSEFORGE,
            ),
            projectUrl = page,
            authors = authors.distinct().take(32),
            license = null,
        )
    }

    private fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonElement {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .apply { headers.forEach(::header) }
            .GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        require(response.statusCode() in 200..299) { "Metadata request failed with HTTP ${response.statusCode()}" }
        return gson.fromJson(response.body(), JsonElement::class.java)
    }

    private fun modrinthId(value: String?): String? {
        val uri = validHttpUri(value) ?: return null
        if (uri.host?.lowercase()?.removePrefix("www.") != "modrinth.com") return null
        val parts = uri.path.split('/').filter(String::isNotBlank)
        val marker = parts.indexOfFirst { it == "mod" || it == "project" }
        return parts.getOrNull(marker + 1)
    }

    private fun officialIcon(value: String?, type: DownloadSourceType): String? {
        val uri = validHttpUri(value) ?: return null
        if (!uri.scheme.equals("https", true)) return null
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
        val allowed = when (type) {
            DownloadSourceType.MODRINTH -> host == "cdn.modrinth.com"
            DownloadSourceType.CURSEFORGE -> host == "media.forgecdn.net" || host == "mediafilez.forgecdn.net"
            else -> false
        }
        return uri.toString().takeIf { allowed }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
