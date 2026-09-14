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
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

object ProjectIconCache {
    data class IconTexture(val location: ResourceLocation, val width: Int, val height: Int)

    private const val MAX_ICON_BYTES = 4 * 1024 * 1024
    private const val MAX_ICON_DIMENSION = 2048
    private const val MAX_CACHED_ICONS = 256
    private const val MAX_FAILED_URLS = 512
    private const val FAILED_RETRY_MILLIS = 60_000L
    private val ready = ConcurrentHashMap<String, IconTexture>()
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap<String, Long>()
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun texture(url: String?): IconTexture? {
        val value = url?.takeIf(String::isNotBlank) ?: return null
        ready[value]?.let { return it }
        if (ready.size + loading.size >= MAX_CACHED_ICONS) return null
        failed[value]?.let { failedAt ->
            if (System.currentTimeMillis() - failedAt < FAILED_RETRY_MILLIS) return null
            failed.remove(value, failedAt)
        }
        if (loading.add(value)) {
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
                    require(redirects < 3) { "Too many icon redirects" }
                    redirects++
                    uri = uri.resolve(response.headers().firstValue("Location").orElseThrow())
                } else break
            }
            require(response.statusCode() in 200..299) { "Icon request failed with HTTP ${response.statusCode()}" }
            val contentType = response.headers().firstValue("Content-Type").orElse("").substringBefore(';').trim().lowercase()
            require(contentType.isBlank() || contentType.startsWith("image/") || contentType == "application/octet-stream") {
                "Icon response is not an image"
            }
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
            image = decodeImage(output.toByteArray())
            require(image.width in 1..MAX_ICON_DIMENSION && image.height in 1..MAX_ICON_DIMENSION) { "Invalid icon dimensions" }
            val loadedImage = image!!
            image = null
            Minecraft.getInstance().execute {
                try {
                    val texture = Minecraft.getInstance().textureManager.register("cph_project_icon", DynamicTexture(loadedImage))
                    ready[url] = IconTexture(texture, loadedImage.width, loadedImage.height)
                    failed.remove(url)
                } catch (exception: Exception) {
                    loadedImage.close()
                    rememberFailure(url)
                    Cph.LOGGER.debug("Could not register project icon {}", url, exception)
                } finally {
                    loading -= url
                }
            }
        } catch (exception: Exception) {
            image?.close()
            loading -= url
            rememberFailure(url)
            Cph.LOGGER.warn("Could not load project icon {}: {}", url, exception.message ?: exception.javaClass.simpleName)
        }
    }

    private fun decodeImage(bytes: ByteArray): NativeImage = try {
        NativeImage.read(bytes)
    } catch (nativeFailure: Exception) {
        val buffered = ImageIO.read(ByteArrayInputStream(bytes)) ?: throw nativeFailure
        require(buffered.width in 1..MAX_ICON_DIMENSION && buffered.height in 1..MAX_ICON_DIMENSION) {
            "Invalid icon dimensions"
        }
        val png = ByteArrayOutputStream()
        require(ImageIO.write(buffered, "png", png)) { "Could not convert the project icon" }
        NativeImage.read(png.toByteArray())
    }

    private fun rememberFailure(url: String) {
        if (failed.size >= MAX_FAILED_URLS) {
            failed.entries.minByOrNull(Map.Entry<String, Long>::value)?.let { failed.remove(it.key, it.value) }
        }
        failed[url] = System.currentTimeMillis()
    }

    private fun sourceType(uri: URI): DownloadSourceType = when (uri.host?.lowercase()?.trimEnd('.')) {
        "cdn.modrinth.com" -> DownloadSourceType.MODRINTH
        "media.forgecdn.net", "mediafilez.forgecdn.net" -> DownloadSourceType.CURSEFORGE
        else -> error("Only official Modrinth and CurseForge icon CDNs are allowed")
    }
}
