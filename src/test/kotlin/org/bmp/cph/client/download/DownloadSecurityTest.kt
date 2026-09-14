package org.bmp.cph.client.download

import org.bmp.cph.config.ConfigValidator
import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.DownloadSourceType
import org.bmp.cph.config.PackHelperConfig
import org.bmp.cph.config.RequiredMod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DownloadSecurityTest {
    @TempDir
    lateinit var temporary: Path

    @Test
    fun `source type is inferred from known platforms`() {
        assertEquals(DownloadSourceType.MODRINTH, DownloadLink(url = "https://modrinth.com/mod/example").resolvedType())
        assertEquals(DownloadSourceType.CURSEFORGE, DownloadLink(url = "https://www.curseforge.com/minecraft/mc-mods/example").resolvedType())
        assertEquals(DownloadSourceType.GITHUB_RELEASE, DownloadLink(url = "https://github.com/acme/mod/releases/download/v1/mod.jar").resolvedType())
        assertEquals(DownloadSourceType.DIRECT, DownloadLink(downloadUrl = "https://example.com/mod.jar").resolvedType())
    }

    @Test
    fun `download URLs require HTTPS and a source-specific CDN`() {
        assertNull(DownloadSecurity.validateUri(URI("https://cdn.modrinth.com/data/id/versions/v/mod.jar"), DownloadSourceType.MODRINTH, false))
        assertNotNull(DownloadSecurity.validateUri(URI("http://cdn.modrinth.com/mod.jar"), DownloadSourceType.MODRINTH, false))
        assertNotNull(DownloadSecurity.validateUri(URI("https://example.com/mod.jar"), DownloadSourceType.MODRINTH, false))
        assertNotNull(DownloadSecurity.validateUri(URI("https://localhost/mod.jar"), DownloadSourceType.DIRECT, false))
    }

    @Test
    fun `filenames are restricted to safe jars`() {
        assertEquals("example.jar", DownloadSecurity.safeFileName("example.jar"))
        assertNull(DownloadSecurity.safeFileName("example.zip"))
        assertNull(DownloadSecurity.safeFileName("bad?.jar"))
    }

    @Test
    fun `direct source requires a strong correctly sized hash`() {
        val source = DownloadLink(type = "DIRECT", downloadUrl = "https://example.com/example.jar", sha256 = "abc")
        val config = PackHelperConfig(mods = listOf(RequiredMod(name = "Example", modId = "example", links = listOf(source))))

        val issues = ConfigValidator.validate(config)

        assertTrue(issues.any { it.code == "invalid_hash" })
    }

    @Test
    fun `jar inspection reads NeoForge identity`() {
        val jar = temporary.resolve("example.jar")
        ZipOutputStream(Files.newOutputStream(jar)).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/neoforge.mods.toml"))
            zip.write("""[[mods]]
                modId="example"
                version="1.2.3"
            """.trimIndent().toByteArray())
            zip.closeEntry()
        }

        val inspected = JarInspector.inspect(jar)

        assertEquals(setOf("example"), inspected.modIds)
        assertEquals("1.2.3", inspected.versions["example"])
    }

    @Test
    fun `backup retention keeps complete newest batches`() {
        val records = listOf(
            InstallationRecord(id = "a1", batchId = "batch-a"),
            InstallationRecord(id = "a2", batchId = "batch-a"),
            InstallationRecord(id = "b1", batchId = "batch-b"),
            InstallationRecord(id = "c1", batchId = "batch-c"),
        )

        val retained = InstallationJournal.retainedBatchIds(records, 2)

        assertEquals(setOf("batch-b", "batch-c"), retained)
    }
}
