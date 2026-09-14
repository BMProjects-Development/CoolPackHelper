package org.bmp.cph.client.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MinecraftLanguagePickerTest {
    private val languages = listOf(
        MinecraftLanguageChoice("en_us", "English", "US", false),
        MinecraftLanguageChoice("ru_ru", "Русский", "Россия", false),
        MinecraftLanguageChoice("ar_sa", "العربية", "السعودية", true),
    )

    @Test
    fun `filters languages by code name and region`() {
        assertEquals(listOf("ru_ru"), filterMinecraftLanguages(languages, "ru_").map { it.code })
        assertEquals(listOf("ru_ru"), filterMinecraftLanguages(languages, "рус").map { it.code })
        assertEquals(listOf("en_us"), filterMinecraftLanguages(languages, "US").map { it.code })
    }

    @Test
    fun `blank search keeps every Minecraft language`() {
        assertEquals(languages, filterMinecraftLanguages(languages, "  "))
    }
}
