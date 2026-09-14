package org.bmp.cph.client.curseforge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CurseForgeApiSupportTest {
    @Test
    fun `normalizes whitespace and copied quotes`() {
        assertEquals("abc123", CurseForgeApiSupport.normalizeKey("  \"abc123\"  "))
        assertEquals("abc123", CurseForgeApiSupport.normalizeKey("'abc123'"))
    }
}
