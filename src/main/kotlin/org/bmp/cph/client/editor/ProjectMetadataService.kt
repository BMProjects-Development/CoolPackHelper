package org.bmp.cph.client.editor

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.bmp.cph.client.curseforge.CurseForgeApiSupport
import org.bmp.cph.client.cphMessage
import org.bmp.cph.config.ConfigManager
import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.DownloadSourceType
import org.bmp.cph.config.ProjectDonationLink
import org.bmp.cph.config.ProjectLinks
import org.bmp.cph.config.RequiredMod
import org.bmp.cph.util.CphExecutors
import org.bmp.cph.util.CphHttpClients
import org.bmp.cph.config.validHttpUri
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

data class ProjectMetadata(
    val projectId: String,
    val name: String,
    val summary: String?,
    val iconUrl: String?,
    val projectLinks: ProjectLinks,
    val authors: List<String>,
    val license: String?,
)

enum class MetadataApplyMode {
    FILL_EMPTY,
    REPLACE,
}

object ProjectMetadataService {
    private const val MAX_API_RESPONSE_BYTES = 4 * 1024 * 1024
    private const val CACHE_TTL_MILLIS = 10 * 60 * 1000L
    private const val MAX_CACHE_ENTRIES = 256
    private val gson = Gson()
    private data class CacheEntry(val loadedAt: Long, val metadata: ProjectMetadata)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val http = CphHttpClients.api

    fun fetchAsync(source: DownloadLink): CompletableFuture<ProjectMetadata> {
        val cacheKey = "${source.resolvedType()}:${source.projectId.orEmpty()}:${source.url.orEmpty()}"
        cache[cacheKey]?.takeIf { System.currentTimeMillis() - it.loadedAt < CACHE_TTL_MILLIS }?.let {
            return CompletableFuture.completedFuture(it.metadata)
        }
        return CphExecutors.supply(CphExecutors.network) {
            val metadata = when (source.resolvedType()) {
            DownloadSourceType.MODRINTH -> fetchModrinth(source)
            DownloadSourceType.CURSEFORGE -> fetchCurseForge(source)
            else -> error(cphMessage("cph.metadata.error.unsupported_source"))
            }
            cacheMetadata(cacheKey, metadata)
            metadata
        }
    }

    private fun cacheMetadata(key: String, metadata: ProjectMetadata) {
        val now = System.currentTimeMillis()
        if (cache.size >= MAX_CACHE_ENTRIES && !cache.containsKey(key)) {
            cache.entries.removeIf { now - it.value.loadedAt >= CACHE_TTL_MILLIS }
            if (cache.size >= MAX_CACHE_ENTRIES) {
                cache.entries.minByOrNull { it.value.loadedAt }?.let { cache.remove(it.key, it.value) }
            }
        }
        cache[key] = CacheEntry(now, metadata)
    }

    fun apply(
        metadata: ProjectMetadata,
        mod: RequiredMod,
        source: DownloadLink,
        locale: String = "en_us",
        mode: MetadataApplyMode = MetadataApplyMode.FILL_EMPTY,
    ) {
        fun shouldReplace(value: String?): Boolean = mode == MetadataApplyMode.REPLACE || value.isNullOrBlank()
        if (shouldReplace(mod.name)) mod.name = metadata.name
        metadata.iconUrl?.takeIf { shouldReplace(mod.iconUrl) }?.let { mod.iconUrl = it }
        if (metadata.authors.isNotEmpty() && (mode == MetadataApplyMode.REPLACE || mod.authors.isNullOrEmpty())) {
            mod.authors = metadata.authors
        }
        metadata.license?.takeIf { shouldReplace(mod.license) }?.let { mod.license = it }
        metadata.summary?.takeIf(String::isNotBlank)?.let { summary ->
            val descriptions = mod.descriptions.orEmpty().toMutableMap()
            if (mode == MetadataApplyMode.REPLACE || descriptions[locale].isNullOrBlank()) {
                descriptions[locale] = summary
                mod.descriptions = descriptions
            }
        }
        mod.projectLinks = mergeLinks(mod.resolvedProjectLinks(), metadata.projectLinks, mode)
        mod.projectUrl = null
        source.projectId = metadata.projectId
        source.url = metadata.projectLinks.homepage
    }

    private fun fetchModrinth(source: DownloadLink): ProjectMetadata {
        val id = source.projectId?.takeIf(String::isNotBlank) ?: modrinthId(source.url)
            ?: error(cphMessage("cph.metadata.error.modrinth_project"))
        val project = getJson("https://api.modrinth.com/v2/project/${encode(id)}").asJsonObject
        require(project.get("project_type")?.asString == "mod") { cphMessage("cph.metadata.error.not_mod") }
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
            projectLinks = ProjectLinks(
                homepage = "https://modrinth.com/mod/$slug",
                source = externalUrl(project, "source_url"),
                issues = externalUrl(project, "issues_url"),
                wiki = externalUrl(project, "wiki_url"),
                discord = externalUrl(project, "discord_url"),
                donations = project.getAsJsonArray("donation_urls")?.mapNotNull { entry ->
                    val donation = entry.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
                    val url = externalUrl(donation, "url") ?: return@mapNotNull null
                    val label = stringValue(donation, "platform") ?: stringValue(donation, "id")
                    ProjectDonationLink(label?.trim()?.take(128), url)
                }?.distinctBy { it.url }.orEmpty(),
            ),
            authors = members.distinct().take(32),
            license = license?.trim()?.take(256),
        )
    }

    private fun fetchCurseForge(source: DownloadLink): ProjectMetadata {
        val id = source.projectId?.trim()?.takeIf { it.matches(Regex("^[0-9]+$")) }
            ?: error(cphMessage("cph.metadata.error.curseforge_project"))
        val key = CurseForgeApiSupport.normalizeKey(ConfigManager.loadAuthorSettings().curseForgeApiKey)
            .takeIf(String::isNotBlank)
            ?: error(cphMessage("cph.metadata.error.curseforge_key"))
        val project = getJson("https://api.curseforge.com/v1/mods/${encode(id)}", mapOf("x-api-key" to key))
            .asJsonObject.getAsJsonObject("data") ?: error(cphMessage("cph.metadata.error.empty_response", "CurseForge"))
        val authors = project.getAsJsonArray("authors")?.mapNotNull { entry ->
            entry.asJsonObject.get("name")?.asString?.trim()?.takeIf(String::isNotBlank)
        }.orEmpty()
        val links = project.getAsJsonObject("links")
        val page = externalUrl(links, "websiteUrl")
            ?: "https://www.curseforge.com/minecraft/mc-mods/${project.get("slug")?.asString ?: id}"
        return ProjectMetadata(
            projectId = project.get("id").asString,
            name = project.get("name").asString.trim().take(256),
            summary = project.get("summary")?.takeUnless(JsonElement::isJsonNull)?.asString?.trim()?.take(4096),
            iconUrl = officialIcon(
                project.getAsJsonObject("logo")?.get("thumbnailUrl")?.takeUnless(JsonElement::isJsonNull)?.asString,
                DownloadSourceType.CURSEFORGE,
            ),
            projectLinks = ProjectLinks(
                homepage = page,
                source = externalUrl(links, "sourceUrl"),
                issues = externalUrl(links, "issuesUrl"),
                wiki = externalUrl(links, "wikiUrl"),
            ),
            authors = authors.distinct().take(32),
            license = null,
        )
    }

    private fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonElement {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("User-Agent", CurseForgeApiSupport.USER_AGENT)
            .apply { headers.forEach(::header) }
            .GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        val bytes = response.body().use { it.readNBytes(MAX_API_RESPONSE_BYTES + 1) }
        require(bytes.size <= MAX_API_RESPONSE_BYTES) { cphMessage("cph.metadata.error.response_too_large") }
        val body = String(bytes, StandardCharsets.UTF_8)
        if (response.statusCode() !in 200..299) {
            val isCurseForge = headers.keys.any { it.equals("x-api-key", ignoreCase = true) }
            error(if (isCurseForge) CurseForgeApiSupport.error(response.statusCode(), body)
            else cphMessage("cph.metadata.error.http", response.statusCode()))
        }
        return gson.fromJson(body, JsonElement::class.java)
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

    private fun mergeLinks(current: ProjectLinks, imported: ProjectLinks, mode: MetadataApplyMode): ProjectLinks {
        fun choose(existing: String?, replacement: String?): String? =
            if (mode == MetadataApplyMode.REPLACE || existing.isNullOrBlank()) replacement ?: existing else existing
        val donations = when {
            mode == MetadataApplyMode.REPLACE -> imported.donations
            current.donations.isNullOrEmpty() -> imported.donations
            else -> (current.donations.orEmpty() + imported.donations.orEmpty()).distinctBy { it.url }
        }
        return ProjectLinks(
            homepage = choose(current.homepage, imported.homepage),
            source = choose(current.source, imported.source),
            issues = choose(current.issues, imported.issues),
            wiki = choose(current.wiki, imported.wiki),
            discord = choose(current.discord, imported.discord),
            donations = donations,
        )
    }

    private fun externalUrl(objectValue: JsonObject?, field: String): String? =
        stringValue(objectValue, field)?.let(::validHttpUri)?.toString()

    private fun stringValue(objectValue: JsonObject?, field: String): String? =
        objectValue?.get(field)?.takeUnless(JsonElement::isJsonNull)?.takeIf(JsonElement::isJsonPrimitive)
            ?.asString?.trim()?.takeIf(String::isNotBlank)

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
