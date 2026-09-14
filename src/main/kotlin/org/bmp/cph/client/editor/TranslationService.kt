package org.bmp.cph.client.editor

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.bmp.cph.client.download.DownloadSecurity
import org.bmp.cph.config.DownloadSourceType
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture

enum class TranslationProvider(val id: String) {
    LIBRE_TRANSLATE("libretranslate"),
    DEEPL("deepl"),
    GOOGLE("google");

    companion object {
        fun fromId(value: String?): TranslationProvider = entries.firstOrNull { it.id.equals(value, true) }
            ?: LIBRE_TRANSLATE
    }
}

object TranslationService {
    const val DEFAULT_ENDPOINT = "https://libretranslate.com/translate"
    private const val DEEPL_FREE_ENDPOINT = "https://api-free.deepl.com/v2/translate"
    private const val DEEPL_PRO_ENDPOINT = "https://api.deepl.com/v2/translate"
    private const val GOOGLE_ENDPOINT = "https://translation.googleapis.com/language/translate/v2"
    private const val MAX_TEXT_LENGTH = 8192
    private const val MAX_RESPONSE_BYTES = 1024 * 1024
    private const val MAX_REDIRECTS = 3
    private val gson = Gson()
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun translateAsync(
        provider: TranslationProvider,
        endpoint: String?,
        apiKey: String?,
        text: String,
        sourceLocale: String,
        targetLocale: String,
    ): CompletableFuture<String> = CompletableFuture.supplyAsync {
        require(text.isNotBlank()) { tr("translation.error.empty_source").string }
        require(text.length <= MAX_TEXT_LENGTH) { tr("translation.error.too_long", MAX_TEXT_LENGTH).string }
        val source = languageCode(sourceLocale)
        val target = languageCode(targetLocale)
        require(source != target) { tr("translation.error.same_language").string }
        val prepared = prepareRequest(provider, endpoint, apiKey, text, source, target)
        var uri = prepared.uri
        var redirects = 0
        while (true) {
            validateEndpoint(uri)
            val requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "BMP/CoolPackHelper/1.0.0")
            prepared.headers.forEach(requestBuilder::header)
            val response = http.send(
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(prepared.body, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofInputStream(),
            )
            if (response.statusCode() in 300..399) {
                response.body().close()
                require(redirects++ < MAX_REDIRECTS) { tr("translation.error.redirects").string }
                val location = response.headers().firstValue("Location")
                    .orElseThrow { IllegalArgumentException(tr("translation.error.redirect_location").string) }
                val redirected = safeUri(uri.resolve(location).toString())
                    ?: error(tr("translation.error.endpoint").string)
                require(sameOrigin(uri, redirected)) { tr("translation.error.redirect_host").string }
                uri = redirected
                continue
            }
            val bytes = response.body().use { it.readNBytes(MAX_RESPONSE_BYTES + 1) }
            require(bytes.size <= MAX_RESPONSE_BYTES) { tr("translation.error.response_too_large").string }
            val responseBody = String(bytes, StandardCharsets.UTF_8)
            if (response.statusCode() !in 200..299) {
                val detail = errorDetail(responseBody)?.take(240)
                if (apiKey.isNullOrBlank() && detail?.contains("api key", ignoreCase = true) == true) {
                    error(tr("translation.error.api_key_required").string)
                }
                error(tr("translation.error.http", response.statusCode(), detail ?: tr("error.unknown")).string)
            }
            return@supplyAsync parseTranslation(provider, responseBody)
                ?.trim()?.takeIf(String::isNotBlank)
                ?: error(tr("translation.error.empty_response").string)
        }
        @Suppress("UNREACHABLE_CODE")
        error(tr("translation.error.empty_response").string)
    }

    fun testConnectionAsync(
        provider: TranslationProvider,
        endpoint: String?,
        apiKey: String?,
    ): CompletableFuture<Unit> = CompletableFuture.supplyAsync {
        val key = apiKey?.trim().orEmpty()
        val probe = when (provider) {
            TranslationProvider.LIBRE_TRANSLATE -> {
                val translateUri = translationUri(endpoint.orEmpty(), normalizePath = true)
                    ?: error(tr("translation.error.endpoint").string)
                ProbeRequest(siblingEndpoint(translateUri, "translate", "languages"))
            }
            TranslationProvider.DEEPL -> {
                require(key.isNotBlank()) { providerKeyRequired(provider) }
                ProbeRequest(
                    siblingEndpoint(URI.create(deepLEndpoint(key)), "translate", "usage"),
                    mapOf("Authorization" to "DeepL-Auth-Key $key"),
                )
            }
            TranslationProvider.GOOGLE -> {
                require(key.isNotBlank()) { providerKeyRequired(provider) }
                ProbeRequest(
                    URI.create("https://translation.googleapis.com/language/translate/v2/languages"),
                    mapOf("X-goog-api-key" to key),
                )
            }
        }
        var uri = probe.uri
        var redirects = 0
        while (true) {
            validateEndpoint(uri)
            val builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "BMP/CoolPackHelper/1.0.0")
            probe.headers.forEach(builder::header)
            val response = http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() in 300..399) {
                response.body().close()
                require(redirects++ < MAX_REDIRECTS) { tr("translation.error.redirects").string }
                val location = response.headers().firstValue("Location")
                    .orElseThrow { IllegalArgumentException(tr("translation.error.redirect_location").string) }
                val redirected = safeUri(uri.resolve(location).toString())
                    ?: error(tr("translation.error.endpoint").string)
                require(sameOrigin(uri, redirected)) { tr("translation.error.redirect_host").string }
                uri = redirected
                continue
            }
            val bytes = response.body().use { it.readNBytes(MAX_RESPONSE_BYTES + 1) }
            require(bytes.size <= MAX_RESPONSE_BYTES) { tr("translation.error.response_too_large").string }
            if (response.statusCode() !in 200..299) {
                val detail = errorDetail(String(bytes, StandardCharsets.UTF_8))?.take(240)
                error(tr("translation.error.http", response.statusCode(), detail ?: tr("error.unknown")).string)
            }
            return@supplyAsync Unit
        }
        @Suppress("UNREACHABLE_CODE")
        Unit
    }

    private fun prepareRequest(
        provider: TranslationProvider,
        endpoint: String?,
        apiKey: String?,
        text: String,
        source: String,
        target: String,
    ): PreparedRequest {
        val key = apiKey?.trim().orEmpty()
        return when (provider) {
            TranslationProvider.LIBRE_TRANSLATE -> {
                val uri = translationUri(endpoint.orEmpty(), normalizePath = true)
                    ?: error(tr("translation.error.endpoint").string)
                val body = JsonObject().apply {
                    addProperty("q", text)
                    addProperty("source", source)
                    addProperty("target", target)
                    addProperty("format", "text")
                    key.takeIf(String::isNotBlank)?.let { addProperty("api_key", it) }
                }
                PreparedRequest(uri, gson.toJson(body))
            }
            TranslationProvider.DEEPL -> {
                require(key.isNotBlank()) { providerKeyRequired(provider) }
                val body = JsonObject().apply {
                    add("text", JsonArray().apply { add(text) })
                    addProperty("source_lang", source.uppercase())
                    addProperty("target_lang", target.uppercase())
                }
                PreparedRequest(
                    URI.create(deepLEndpoint(key)),
                    gson.toJson(body),
                    mapOf("Authorization" to "DeepL-Auth-Key $key"),
                )
            }
            TranslationProvider.GOOGLE -> {
                require(key.isNotBlank()) { providerKeyRequired(provider) }
                val body = JsonObject().apply {
                    addProperty("q", text)
                    addProperty("source", source)
                    addProperty("target", target)
                    addProperty("format", "text")
                }
                PreparedRequest(
                    URI.create(GOOGLE_ENDPOINT),
                    gson.toJson(body),
                    mapOf("X-goog-api-key" to key),
                )
            }
        }
    }

    internal fun parseTranslation(provider: TranslationProvider, responseBody: String): String? {
        val root = runCatching { gson.fromJson(responseBody, JsonObject::class.java) }.getOrNull() ?: return null
        return when (provider) {
            TranslationProvider.LIBRE_TRANSLATE -> root.string("translatedText")
            TranslationProvider.DEEPL -> root.array("translations")?.firstOrNull()?.asObject()?.string("text")
            TranslationProvider.GOOGLE -> root.obj("data")?.array("translations")
                ?.firstOrNull()?.asObject()?.string("translatedText")
        }
    }

    private fun errorDetail(responseBody: String): String? {
        val root = runCatching { gson.fromJson(responseBody, JsonObject::class.java) }.getOrNull() ?: return null
        val error = root.get("error")
        return when {
            error?.isJsonPrimitive == true -> error.asString
            error?.isJsonObject == true -> error.asJsonObject.string("message")
            else -> root.string("message")
        }?.trim()?.takeIf(String::isNotBlank)
    }

    private fun providerKeyRequired(provider: TranslationProvider): String =
        tr("translation.error.provider_key_required", tr("translation.provider.${provider.id}")).string

    private fun languageCode(locale: String): String = locale.trim().lowercase().substringBefore('_').substringBefore('-')

    internal fun normalizeEndpoint(value: String): String? =
        translationUri(value, normalizePath = true)?.toString()

    internal fun deepLEndpoint(apiKey: String): String =
        if (apiKey.trim().endsWith(":fx", ignoreCase = true)) DEEPL_FREE_ENDPOINT else DEEPL_PRO_ENDPOINT

    private fun translationUri(value: String, normalizePath: Boolean): URI? {
        var uri = safeUri(value) ?: return null
        if (normalizePath) {
            val path = uri.path.orEmpty().trimEnd('/')
            val normalizedPath = when {
                path.isBlank() -> "/translate"
                path.endsWith("/translate", ignoreCase = true) -> path
                path.endsWith("/trans", ignoreCase = true) -> path.dropLast("/trans".length) + "/translate"
                else -> "$path/translate"
            }
            uri = runCatching {
                URI(uri.scheme, uri.userInfo, uri.host, uri.port, normalizedPath, uri.query, null)
            }.getOrNull() ?: return null
        }
        return uri
    }

    private fun safeUri(value: String): URI? {
        val uri = runCatching { URI.create(value.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
        val local = host == "localhost" || runCatching { InetAddress.getByName(host).isLoopbackAddress }.getOrDefault(false)
        return uri.takeIf { it.scheme.equals("https", true) || local && it.scheme.equals("http", true) }
    }

    private fun validateEndpoint(uri: URI) {
        if (uri.scheme.equals("https", true)) {
            DownloadSecurity.validateUri(uri, DownloadSourceType.DIRECT)?.let(::error)
        } else {
            require(uri.host.equals("localhost", true) || InetAddress.getByName(uri.host).isLoopbackAddress) {
                tr("translation.error.endpoint").string
            }
        }
    }

    private fun sameOrigin(first: URI, second: URI): Boolean =
        first.scheme.equals(second.scheme, true) && first.host.equals(second.host, true) && first.port == second.port

    private fun siblingEndpoint(uri: URI, from: String, to: String): URI {
        val path = uri.path.orEmpty()
        val suffix = "/$from"
        val replacement = if (path.endsWith(suffix, true)) path.dropLast(suffix.length) + "/$to" else "$path/$to"
        return URI(uri.scheme, uri.userInfo, uri.host, uri.port, replacement, null, null)
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf(JsonElement::isJsonPrimitive)?.asString

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

    private fun JsonObject.array(key: String): JsonArray? =
        get(key)?.takeIf(JsonElement::isJsonArray)?.asJsonArray

    private fun JsonElement.asObject(): JsonObject? = takeIf(JsonElement::isJsonObject)?.asJsonObject

    private data class PreparedRequest(
        val uri: URI,
        val body: String,
        val headers: Map<String, String> = emptyMap(),
    )

    private data class ProbeRequest(
        val uri: URI,
        val headers: Map<String, String> = emptyMap(),
    )
}
