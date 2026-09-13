package org.bmp.cph.client.download

import net.neoforged.fml.loading.FMLPaths
import org.apache.maven.artifact.versioning.DefaultArtifactVersion
import org.apache.maven.artifact.versioning.VersionRange
import org.bmp.cph.Cph
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

object SecureDownloadManager {
    private const val MAX_REDIRECTS = 5
    private const val MAX_FILE_BYTES = 512L * 1024L * 1024L
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun installAsync(
        download: ResolvedDownload,
        batchId: String,
        progress: (DownloadProgress) -> Unit,
    ): CompletableFuture<InstallResult> = CompletableFuture.supplyAsync {
        install(download, batchId, progress)
    }

    internal fun install(
        download: ResolvedDownload,
        batchId: String,
        progress: (DownloadProgress) -> Unit = {},
    ): InstallResult {
        if (download.status != DownloadResolutionStatus.READY || download.downloadUri == null || download.fileName == null) {
            return InstallResult(download, false, message = download.message ?: "The source is not installable")
        }
        val modsDirectory = FMLPaths.MODSDIR.get().toAbsolutePath().normalize()
        val localRoot = FMLPaths.GAMEDIR.get().resolve("local").resolve("coolpackhelper").toAbsolutePath().normalize()
        val temporaryDirectory = localRoot.resolve("downloads")
        val safeName = DownloadSecurity.safeFileName(download.fileName)
            ?: return InstallResult(download, false, message = "Unsafe or unsupported filename")
        val target = modsDirectory.resolve(safeName).normalize()
        if (!target.startsWith(modsDirectory)) return InstallResult(download, false, message = "Unsafe target path")
        val temporary = temporaryDirectory.resolve("${UUID.randomUUID()}-$safeName.part")
        val movedBackups = linkedMapOf<Path, Path>()
        var installedTarget = false

        return try {
            Files.createDirectories(modsDirectory)
            Files.createDirectories(temporaryDirectory)
            val hashes = downloadToTemporary(download, temporary, progress)
            verifyExpected(download, hashes)
            val jar = JarInspector.inspect(temporary)
            verifyMod(download, jar)

            val replacementCandidates = findReplacementCandidates(modsDirectory, target, download.mod.modId)
            if (replacementCandidates.isNotEmpty()) {
                val backupDirectory = localRoot.resolve("backups").resolve(batchId)
                Files.createDirectories(backupDirectory)
                replacementCandidates.forEach { original ->
                    val backup = uniquePath(backupDirectory, original.fileName.toString())
                    Files.move(original, backup, StandardCopyOption.REPLACE_EXISTING)
                    movedBackups[original] = backup
                }
            }
            moveAtomically(temporary, target)
            installedTarget = true
            val recordId = UUID.randomUUID().toString()
            InstallationJournal.record(
                InstallationJournal.newRecord(
                    recordId,
                    batchId,
                    download,
                    target,
                    hashes.getValue("SHA-256"),
                    movedBackups,
                )
            )
            InstallResult(download, true, target, recordId, "Installed successfully. Restart Minecraft to load the mod.")
        } catch (exception: Exception) {
            Cph.LOGGER.error("Could not securely install {}", download.mod.displayName(), exception)
            try {
                Files.deleteIfExists(temporary)
                if (installedTarget) Files.deleteIfExists(target)
            } catch (cleanupException: Exception) {
                Cph.LOGGER.error("Could not remove files from the failed installation of {}", download.mod.displayName(), cleanupException)
            }
            movedBackups.entries.toList().asReversed().forEach { (original, backup) ->
                try {
                    if (Files.exists(backup)) Files.move(backup, original, StandardCopyOption.REPLACE_EXISTING)
                } catch (rollbackException: Exception) {
                    Cph.LOGGER.error("Could not restore {} after a failed install", original, rollbackException)
                }
            }
            InstallResult(download, false, message = exception.message ?: exception.javaClass.simpleName)
        }
    }

    private fun downloadToTemporary(
        download: ResolvedDownload,
        temporary: Path,
        progress: (DownloadProgress) -> Unit,
    ): Map<String, String> {
        var uri = download.downloadUri!!
        var redirects = 0
        val received: HttpResponse<InputStream>
        while (true) {
            DownloadSecurity.validateUri(uri, download.sourceType)?.let(::error)
            val request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(3))
                .header("Accept", "application/java-archive, application/octet-stream, */*")
                .header("User-Agent", "BMP/CoolPackHelper/1.0.0")
                .GET()
                .build()
            val current = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (current.statusCode() in 300..399) {
                current.body().close()
                require(redirects < MAX_REDIRECTS) { "Too many redirects" }
                redirects++
                val location = current.headers().firstValue("Location").orElseThrow { IllegalStateException("Redirect without Location") }
                uri = uri.resolve(location)
            } else {
                received = current
                break
            }
        }
        require(received.statusCode() in 200..299) { "Download failed with HTTP ${received.statusCode()}" }
        val declaredLength = received.headers().firstValueAsLong("Content-Length").orElse(-1L)
        val expectedSize = download.sizeBytes
        if (declaredLength > MAX_FILE_BYTES) error("The file exceeds the 512 MiB safety limit")
        if (expectedSize != null && expectedSize > MAX_FILE_BYTES) error("The expected file exceeds the 512 MiB safety limit")
        if (expectedSize != null && declaredLength >= 0 && expectedSize != declaredLength) error("The server reported an unexpected file size")

        val algorithms = (download.hashes.keys + "SHA-256").associateWith(MessageDigest::getInstance)
        var written = 0L
        received.body().use { input ->
            Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    written += count
                    require(written <= MAX_FILE_BYTES) { "The file exceeds the 512 MiB safety limit" }
                    algorithms.values.forEach { it.update(buffer, 0, count) }
                    output.write(buffer, 0, count)
                    progress(DownloadProgress(download.mod.displayName(), download.fileName!!, written, expectedSize ?: declaredLength.takeIf { it >= 0 }))
                }
            }
        }
        if (expectedSize != null && written != expectedSize) error("Downloaded size does not match the expected size")
        require(written > 0) { "The downloaded file is empty" }
        return algorithms.mapValues { (_, digest) -> digest.digest().toHex() }
    }

    private fun verifyExpected(download: ResolvedDownload, actual: Map<String, String>) {
        download.hashes.forEach { (algorithm, expected) ->
            val value = actual[algorithm] ?: error("Unsupported hash algorithm $algorithm")
            require(MessageDigest.isEqual(value.hexBytes(), expected.hexBytes())) { "$algorithm integrity check failed" }
        }
    }

    private fun verifyMod(download: ResolvedDownload, jar: InspectedJar) {
        val expectedId = download.mod.modId?.trim()?.lowercase()
        if (!expectedId.isNullOrBlank()) {
            require(expectedId in jar.modIds) { "The JAR does not contain the expected mod '$expectedId'" }
            val range = download.mod.versionRange?.trim().orEmpty()
            val version = jar.versions[expectedId].orEmpty()
            if (range.isNotEmpty() && version.isNotEmpty() && !version.contains('$')) {
                require(VersionRange.createFromVersionSpec(range).containsVersion(DefaultArtifactVersion(version))) {
                    "Downloaded version $version does not satisfy $range"
                }
            }
        }
    }

    private fun findReplacementCandidates(directory: Path, target: Path, modId: String?): List<Path> {
        val candidates = linkedSetOf<Path>()
        if (Files.exists(target)) candidates.add(target)
        val expected = modId?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: return candidates.toList()
        Files.list(directory).use { stream ->
            stream.filter(Files::isRegularFile)
                .filter { it.fileName.toString().endsWith(".jar", ignoreCase = true) }
                .filter { it != target }
                .forEach { path ->
                    try {
                        if (expected in JarInspector.inspect(path).modIds) candidates.add(path)
                    } catch (_: Exception) {
                        // A malformed unrelated JAR must not prevent installation.
                    }
                }
        }
        return candidates.toList()
    }

    private fun uniquePath(directory: Path, name: String): Path {
        val timestamp = Instant.now().toEpochMilli()
        var candidate = directory.resolve("$timestamp-$name")
        var suffix = 1
        while (Files.exists(candidate)) candidate = directory.resolve("$timestamp-${suffix++}-$name")
        return candidate
    }

    private fun moveAtomically(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexBytes(): ByteArray {
        require(length % 2 == 0 && matches(Regex("^[0-9a-fA-F]+$"))) { "Invalid expected hash" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
