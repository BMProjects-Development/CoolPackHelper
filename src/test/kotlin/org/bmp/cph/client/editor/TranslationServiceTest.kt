package org.bmp.cph.client.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationServiceTest {
    @Test
    fun `normalizes a LibreTranslate service address`() {
        assertEquals("https://libretranslate.com/translate", TranslationService.normalizeEndpoint("https://libretranslate.com"))
        assertEquals("https://example.org/api/translate", TranslationService.normalizeEndpoint("https://example.org/api/"))
    }

    @Test
    fun `repairs the shortened translate path`() {
        assertEquals(
            "https://libretranslate.com/translate",
            TranslationService.normalizeEndpoint("https://libretranslate.com/trans"),
        )
    }

    @Test
    fun `rejects an insecure remote endpoint`() {
        assertNull(TranslationService.normalizeEndpoint("http://example.org"))
    }

    @Test
    fun `selects the correct DeepL endpoint from its key type`() {
        assertEquals(
            "https://api-free.deepl.com/v2/translate",
            TranslationService.deepLEndpoint("example:fx"),
        )
        assertEquals(
            "https://api.deepl.com/v2/translate",
            TranslationService.deepLEndpoint("example-pro-key"),
        )
    }

    @Test
    fun `parses supported provider response formats`() {
        assertEquals(
            "Привет",
            TranslationService.parseTranslation(
                TranslationProvider.LIBRE_TRANSLATE,
                """{"translatedText":"Привет"}""",
            ),
        )
        assertEquals(
            "Привет",
            TranslationService.parseTranslation(
                TranslationProvider.DEEPL,
                """{"translations":[{"detected_source_language":"EN","text":"Привет"}]}""",
            ),
        )
        assertEquals(
            "Привет",
            TranslationService.parseTranslation(
                TranslationProvider.GOOGLE,
                """{"data":{"translations":[{"translatedText":"Привет"}]}}""",
            ),
        )
        assertEquals(
            "Привет",
            TranslationService.parseTranslation(
                TranslationProvider.MYMEMORY,
                """{"responseData":{"translatedText":"Привет"},"responseStatus":200}""",
            ),
        )
    }

    @Test
    fun `falls back to LibreTranslate for unknown persisted provider`() {
        assertEquals(TranslationProvider.LIBRE_TRANSLATE, TranslationProvider.fromId("removed-provider"))
        assertEquals(TranslationProvider.GOOGLE, TranslationProvider.fromId("GOOGLE"))
    }

    @Test
    fun `MyMemory chunks stay within its UTF-8 byte limit`() {
        val text = List(180) { "модификация" }.joinToString(" ")
        val chunks = TranslationService.myMemoryChunks(text)

        assertEquals(text, chunks.joinToString(" "))
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.toByteArray(Charsets.UTF_8).size <= 500 })
    }

    @Test
    fun `MyMemory safely splits one oversized unicode token`() {
        val chunks = TranslationService.myMemoryChunks("я".repeat(600))

        assertEquals("я".repeat(600), chunks.joinToString(""))
        assertTrue(chunks.all { it.toByteArray(Charsets.UTF_8).size <= 500 })
    }
}
