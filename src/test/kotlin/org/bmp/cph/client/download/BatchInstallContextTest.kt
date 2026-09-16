package org.bmp.cph.client.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class BatchInstallContextTest {
    @Test
    fun `installed artifact replaces old paths in the batch index`() {
        val oldPath = Path.of("mods", "example-old.jar").toAbsolutePath().normalize()
        val unrelated = Path.of("mods", "unrelated.jar").toAbsolutePath().normalize()
        val target = Path.of("mods", "example-new.jar").toAbsolutePath().normalize()
        val context = SecureDownloadManager.BatchInstallContext(
            linkedMapOf(
                "example" to linkedSetOf(oldPath),
                "unrelated" to linkedSetOf(unrelated),
            )
        )

        context.installed(listOf(oldPath), target, listOf("example", "library"))

        assertEquals(setOf(target), context.pathsFor("example"))
        assertEquals(setOf(target), context.pathsFor("library"))
        assertEquals(setOf(unrelated), context.pathsFor("unrelated"))
        assertTrue(oldPath !in context.pathsFor("example"))
    }
}
