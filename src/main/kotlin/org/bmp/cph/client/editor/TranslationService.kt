package org.bmp.cph.client.editor

import com.google.gson.Gson
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

object TranslationService {
    const val DEFAULT_ENDPOINT = "https://libretranslate.com/translate"
    private const val MAX_TEXT_LENGTH = 8192
    private const val MAX_RESPONSE_BYTES = 1024 * 1024
    private const val MAX_REDIRECTS = 3
    private val gson = Gson()
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun translateAsync(
        endpoint: String,
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
        var uri = translationUri(endpoint) ?: error(tr("translation.error.endpoint").string)
        val body = JsonObject().apply {
            addProperty("q", text)
            addProperty("source", source)
            addProperty("target", target)
            addProperty("format", "text")
            apiKey?.trim()?.takeIf(String::isNotBlank)?.let { addProperty("api_key", it) }
        }
        var redirects = 0
        while (true) {
            validateEndpoint(uri)
            val request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "BMP/CoolPackHelper/1.0.0")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() in 300..399) {
                response.body().close()
                require(redirects++ < MAX_REDIRECTS) { tr("translation.error.redirects").string }
                val location = response.headers().firstValue("Location")
                    .orElseThrow { IllegalArgumentException(tr("translation.error.redirect_location").string) }
                uri = translationUri(uri.resolve(location).toString())
                    ?: error(tr("translation.error.endpoint").string)
                continue
            }
            val bytes = response.body().use { it.readNBytes(MAX_RESPONSE_BYTES + 1) }
            require(bytes.size <= MAX_RESPONSE_BYTES) { tr("translation.error.response_too_large").string }
            val responseBody = String(bytes, StandardCharsets.UTF_8)
            if (response.statusCode() !in 200..299) {
                val detail = runCatching {
                    gson.fromJson(responseBody, JsonObject::class.java).get("error")?.asString
                }.getOrNull()?.trim()?.take(240)
                error(tr("translation.error.http", response.statusCode(), detail ?: tr("error.unknown")).string)
            }
            val translated = gson.fromJson(responseBody, JsonObject::class.java)
                .get("translatedText")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            return@supplyAsync translated?.takeIf(String::isNotBlank)
                ?: error(tr("translation.error.empty_response").string)
        }
        @Suppress("UNREACHABLE_CODE")
        error(tr("translation.error.empty_response").string)
    }

    private fun languageCode(locale: String): String = locale.trim().lowercase().substringBefore('_').substringBefore('-')

    private fun translationUri(value: String): URI? {
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
}
