package org.bmp.cph.client.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.bmp.cph.config.ConfigValidator
import org.bmp.cph.config.IssueSeverity
import org.bmp.cph.config.PackHelperConfig
import java.nio.file.Path

class PlatformScannerTest {
    @Test
    fun `CurseForge fingerprint ignores normalized whitespace`() {
        val compact = "abc123".toByteArray()
        val spaced = " a\tb\nc\r1 2 3 ".toByteArray()

        assertEquals(
            PlatformScanner.curseForgeFingerprint(compact),
            PlatformScanner.curseForgeFingerprint(spaced),
        )
    }

    @Test
    fun `generated scan entries are safe disabled drafts`() {
        val draft = LocalModArtifact(
            path = Path.of("example.jar"),
            fileName = "example-1.2.0.jar",
            sha1 = "abc",
            curseForgeFingerprint = 1,
            name = "Example",
            modId = "example",
            version = "1.2.0",
            description = "Example description",
            homepage = null,
        ).toDraft()

        assertFalse(draft.enabled!!)
        assertEquals("[1.2.0]", draft.versionRange)
        assertEquals("example-1.2.0.jar", draft.filePattern)
        assertEquals("Example description", draft.descriptions?.get("en_us"))
        assertTrue(draft.links.orEmpty().isEmpty())
        assertFalse(
            ConfigValidator.validate(PackHelperConfig.default().copy(mods = listOf(draft)))
                .any { it.severity == IssueSeverity.ERROR }
        )
    }
}
