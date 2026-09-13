package org.bmp.cph.client

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import org.bmp.cph.Cph
import org.bmp.cph.client.download.DownloadSecurity
import org.bmp.cph.config.DownloadSourceType
import org.bmp.cph.config.validHttpUri
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object ProjectIconCache {
    data class IconTexture(val location: ResourceLocation, val width: Int, val height: Int)

    private const val MAX_ICON_BYTES = 1024 * 1024
    private const val MAX_ICON_DIMENSION = 1024
    private val ready = ConcurrentHashMap<String, IconTexture>()
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun texture(url: String?): IconTexture? {
        val value = url?.takeIf(String::isNotBlank) ?: return null
        ready[value]?.let { return it }
        if (value !in failed && loading.add(value)) {
            CompletableFuture.runAsync { load(value) }
        }
        return null
    }

    private fun load(url: String) {
        var image: NativeImage? = null
        try {
            var uri = validHttpUri(url) ?: error("Invalid icon URL")
            val type = sourceType(uri)
            var redirects = 0
            var response: HttpResponse<java.io.InputStream>
            while (true) {
                DownloadSecurity.validateUri(uri, type)?.let(::error)
                val request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "image/png, image/jpeg, image/webp, image/*")
                    .header("User-Agent", "BMP/CoolPackHelper/1.0.0")
                    .GET().build()
                response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
                if (response.statusCode() in 300..399) {
                    response.body().close()
                    require(redirects++ < 3) { "Too many icon redirects" }
                    uri = uri.resolve(response.headers().firstValue("Location").orElseThrow())
                } else break
            }
            require(response.statusCode() in 200..299) { "Icon request failed with HTTP ${response.statusCode()}" }
            val contentType = response.headers().firstValue("Content-Type").orElse("").lowercase()
            require(contentType.startsWith("image/")) { "Icon response is not an image" }
            val declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
            require(declared <= MAX_ICON_BYTES) { "Icon is too large" }
            val output = ByteArrayOutputStream()
            response.body().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_ICON_BYTES) { "Icon is too large" }
                    output.write(buffer, 0, count)
                }
            }
            image = NativeImage.read(output.toByteArray())
            require(image.width in 1..MAX_ICON_DIMENSION && image.height in 1..MAX_ICON_DIMENSION) { "Invalid icon dimensions" }
            val loadedImage = image!!
            image = null
            Minecraft.getInstance().execute {
                try {
                    val texture = Minecraft.getInstance().textureManager.register("cph_project_icon", DynamicTexture(loadedImage))
                    ready[url] = IconTexture(texture, loadedImage.width, loadedImage.height)
                } catch (exception: Exception) {
                    loadedImage.close()
                    failed += url
                    Cph.LOGGER.debug("Could not register project icon {}", url, exception)
                } finally {
                    loading -= url
                }
            }
        } catch (exception: Exception) {
            image?.close()
            loading -= url
            failed += url
            Cph.LOGGER.debug("Could not load project icon {}", url, exception)
        }
    }

    private fun sourceType(uri: URI): DownloadSourceType = when (uri.host?.lowercase()?.trimEnd('.')) {
        "cdn.modrinth.com" -> DownloadSourceType.MODRINTH
        "media.forgecdn.net", "mediafilez.forgecdn.net" -> DownloadSourceType.CURSEFORGE
        else -> error("Only official Modrinth and CurseForge icon CDNs are allowed")
    }
}
