package org.bmp.cph.client.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    }

    @Test
    fun `falls back to LibreTranslate for unknown persisted provider`() {
        assertEquals(TranslationProvider.LIBRE_TRANSLATE, TranslationProvider.fromId("removed-provider"))
        assertEquals(TranslationProvider.GOOGLE, TranslationProvider.fromId("GOOGLE"))
    }
}
