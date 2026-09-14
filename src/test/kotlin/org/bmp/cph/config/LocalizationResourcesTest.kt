package org.bmp.cph.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class LocalizationResourcesTest {
    @Test
    fun `English and Russian resources have matching keys and placeholders`() {
        val english = language("en_us")
        val russian = language("ru_ru")

        assertEquals(english.keySet(), russian.keySet(), "Language files must contain the same translation keys")
        english.keySet().forEach { key ->
            assertEquals(
                placeholders(english.get(key).asString),
                placeholders(russian.get(key).asString),
                "Translation placeholders differ for $key",
            )
            assertTrue(english.get(key).asString.isNotBlank(), "English translation is blank for $key")
            assertTrue(russian.get(key).asString.isNotBlank(), "Russian translation is blank for $key")
        }
    }

    @Test
    fun `literal translation keys used by Kotlin sources exist`() {
        val translations = language("en_us").keySet()
        val sourceRoot = Path.of("src", "main", "kotlin")
        val referenced = Files.walk(sourceRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .flatMap { path ->
                    Regex("\"(cph\\.[a-z0-9_.-]+)\"").findAll(Files.readString(path))
                        .map { it.groupValues[1] }
                        .filterNot { it.endsWith('.') }
                        .toList()
                        .stream()
                }
                .toList()
                .toSet()
        }

        assertTrue(
            translations.containsAll(referenced),
            "Missing translation keys: ${referenced - translations}",
        )
    }

    private fun language(code: String): JsonObject {
        val stream = checkNotNull(javaClass.getResourceAsStream("/assets/cph/lang/$code.json"))
        return stream.reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
    }

    private fun placeholders(value: String): List<String> =
        Regex("%(?:\\d+\\$)?[a-zA-Z]|\\{[a-zA-Z0-9_.-]+}").findAll(value).map { it.value }.sorted().toList()
}
