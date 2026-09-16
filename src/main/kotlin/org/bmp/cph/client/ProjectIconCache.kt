package org.bmp.cph.client

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import org.bmp.cph.Cph
import org.bmp.cph.client.download.DownloadSecurity
import org.bmp.cph.config.DownloadSourceType
import org.bmp.cph.config.validHttpUri
import org.bmp.cph.util.CphExecutors
import org.bmp.cph.util.CphHttpClients
import java.awt.AlphaComposite
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

object ProjectIconCache {
    data class IconTexture(val location: ResourceLocation, val width: Int, val height: Int)

    private data class CachedIcon(val texture: IconTexture, val bytes: Long)

    private const val MAX_ICON_BYTES = 4 * 1024 * 1024
    private const val MAX_SOURCE_DIMENSION = 2048
    private const val MAX_SOURCE_PIXELS = MAX_SOURCE_DIMENSION.toLong() * MAX_SOURCE_DIMENSION
    private const val MAX_TEXTURE_DIMENSION = 128
    private const val MAX_CACHED_ICONS = 128
    private const val MAX_PENDING_ICONS = 32
    private const val MAX_TEXTURE_BYTES = 16L * 1024L * 1024L
    private const val MAX_FAILED_URLS = 512
    private const val FAILED_RETRY_MILLIS = 60_000L
    private val lock = Any()
    private val ready = LinkedHashMap<String, CachedIcon>(16, .75f, true)
    private val loading = linkedMapOf<String, Long>()
    private val failed = linkedMapOf<String, Long>()
    private var cachedTextureBytes = 0L
    private var generation = 0L
    private val http = CphHttpClients.image

    fun texture(url: String?): IconTexture? {
        val value = url?.takeIf(String::isNotBlank) ?: return null
        val requestGeneration = synchronized(lock) {
            ready[value]?.let { return it.texture }
            failed[value]?.let { failedAt ->
                if (System.currentTimeMillis() - failedAt < FAILED_RETRY_MILLIS) return null
                failed.remove(value)
            }
            if (value in loading || loading.size >= MAX_PENDING_ICONS) return null
            generation.also { loading[value] = it }
        }
        try {
            CphExecutors.image.execute { load(value, requestGeneration) }
        } catch (exception: RuntimeException) {
            fail(value, requestGeneration)
            Cph.LOGGER.warn("Could not queue project icon {}: {}", value, exception.message ?: exception.javaClass.simpleName)
        }
        return null
    }

    /** Release all GPU resources, for example after changing packs or leaving the client. */
    fun clear() {
        val textures = synchronized(lock) {
            val values = ready.values.map { it.texture.location }
            ready.clear()
            loading.clear()
            failed.clear()
            cachedTextureBytes = 0
            generation++
            values
        }
        val minecraft = Minecraft.getInstance()
        minecraft.execute { textures.forEach(minecraft.textureManager::release) }
    }

    private fun load(url: String, requestGeneration: Long) {
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
                    .header("Accept", "image/png, image/jpeg, image/webp, image/gif, image/*")
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
            image = decodeAndResize(output.toByteArray())
            val loadedImage = image
            image = null
            Minecraft.getInstance().execute { register(url, requestGeneration, loadedImage) }
        } catch (exception: Exception) {
            image?.close()
            fail(url, requestGeneration)
            Cph.LOGGER.warn("Could not load project icon {}: {}", url, exception.message ?: exception.javaClass.simpleName)
        }
    }

    private fun register(url: String, requestGeneration: Long, image: NativeImage) {
        val minecraft = Minecraft.getInstance()
        var registeredLocation: ResourceLocation? = null
        try {
            val location = minecraft.textureManager.register("cph_project_icon", DynamicTexture(image))
            registeredLocation = location
            val texture = IconTexture(location, image.width, image.height)
            val bytes = image.width.toLong() * image.height * 4L
            val evicted = mutableListOf<ResourceLocation>()
            val accepted = synchronized(lock) {
                if (generation != requestGeneration || loading[url] != requestGeneration) return@synchronized false
                loading.remove(url)
                failed.remove(url)
                while (ready.size >= MAX_CACHED_ICONS || cachedTextureBytes + bytes > MAX_TEXTURE_BYTES) {
                    val eldest = ready.entries.firstOrNull() ?: break
                    ready.remove(eldest.key)
                    cachedTextureBytes -= eldest.value.bytes
                    evicted += eldest.value.texture.location
                }
                ready.put(url, CachedIcon(texture, bytes))?.let { replaced ->
                    cachedTextureBytes -= replaced.bytes
                    evicted += replaced.texture.location
                }
                cachedTextureBytes += bytes
                true
            }
            if (!accepted) evicted += location
            evicted.forEach(minecraft.textureManager::release)
        } catch (exception: Exception) {
            registeredLocation?.let(minecraft.textureManager::release) ?: image.close()
            fail(url, requestGeneration)
            Cph.LOGGER.debug("Could not register project icon {}", url, exception)
        }
    }

    /** Read dimensions before decoding and upload only a small thumbnail to the GPU. */
    private fun decodeAndResize(bytes: ByteArray): NativeImage {
        val buffered = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input != null) { "Unsupported project icon format" }
            val readers = ImageIO.getImageReaders(input)
            require(readers.hasNext()) { "Unsupported project icon format" }
            val reader = readers.next()
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                require(width in 1..MAX_SOURCE_DIMENSION && height in 1..MAX_SOURCE_DIMENSION) { "Invalid icon dimensions" }
                require(width.toLong() * height <= MAX_SOURCE_PIXELS) { "Project icon has too many pixels" }
                reader.read(0) ?: error("Unsupported project icon format")
            } finally {
                reader.dispose()
            }
        }
        val longest = max(buffered.width, buffered.height)
        val scale = if (longest <= MAX_TEXTURE_DIMENSION) 1.0 else MAX_TEXTURE_DIMENSION.toDouble() / longest
        val width = max(1, (buffered.width * scale).roundToInt())
        val height = max(1, (buffered.height * scale).roundToInt())
        val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        resized.createGraphics().useGraphics { graphics ->
            graphics.composite = AlphaComposite.Src
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(buffered, 0, 0, width, height, null)
        }
        buffered.flush()
        val png = ByteArrayOutputStream()
        try {
            require(ImageIO.write(resized, "png", png)) { "Could not convert the project icon" }
            return NativeImage.read(png.toByteArray())
        } finally {
            resized.flush()
        }
    }

    private fun fail(url: String, requestGeneration: Long) {
        synchronized(lock) {
            if (generation != requestGeneration || loading[url] != requestGeneration) return
            loading.remove(url)
            if (failed.size >= MAX_FAILED_URLS) failed.entries.firstOrNull()?.let { failed.remove(it.key) }
            failed[url] = System.currentTimeMillis()
        }
    }

    private fun sourceType(uri: URI): DownloadSourceType = when (uri.host?.lowercase()?.trimEnd('.')) {
        "cdn.modrinth.com" -> DownloadSourceType.MODRINTH
        "media.forgecdn.net", "mediafilez.forgecdn.net" -> DownloadSourceType.CURSEFORGE
        else -> error("Only official Modrinth and CurseForge icon CDNs are allowed")
    }

    private inline fun <T : java.awt.Graphics2D, R> T.useGraphics(block: (T) -> R): R = try {
        block(this)
    } finally {
        dispose()
    }
}
