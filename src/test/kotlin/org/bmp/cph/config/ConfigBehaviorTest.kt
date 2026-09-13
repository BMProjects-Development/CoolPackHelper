package org.bmp.cph.config

import com.google.gson.JsonParser
import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigBehaviorTest {
    @Test
    fun `GAME language follows Minecraft language`() {
        val menu = MenuConfig.default()
        menu.language = LanguageConfig(mode = "GAME", fixedLanguage = "en_us", fallbackLanguage = "en_us")

        val resolved = MenuTextResolver.resolve(menu, "ru_ru")

        assertEquals("ru_ru", resolved.languageCode)
        assertEquals("Все", resolved.allTab)
    }

    @Test
    fun `FIXED language overrides Minecraft language`() {
        val menu = MenuConfig.default()
        menu.language = LanguageConfig(mode = "FIXED", fixedLanguage = "en_us", fallbackLanguage = "ru_ru")

        val resolved = MenuTextResolver.resolve(menu, "ru_ru")

        assertEquals("en_us", resolved.languageCode)
        assertEquals("All", resolved.allTab)
    }

    @Test
    fun `translation fallback is resolved per field`() {
        val menu = MenuConfig(
            language = LanguageConfig(mode = "GAME", fallbackLanguage = "en_us"),
            translations = mapOf(
                "ru_ru" to MenuText(title = "Свой заголовок"),
                "en_us" to MenuText(detailsButton = "Custom details"),
            ),
        )

        val resolved = MenuTextResolver.resolve(menu, "ru_ru")

        assertEquals("Свой заголовок", resolved.title)
        assertEquals("Custom details", resolved.detailsButton)
        assertEquals("Обязательные", resolved.requiredTab)
    }

    @Test
    fun `unknown JSON fields are rejected`() {
        val json = JsonParser.parseString("""{"schemaVersion":3,"showPolciy":"NEVER"}""")

        val issues = ConfigValidator.validateStructure(json)

        assertTrue(issues.any { it.code == "unknown_field" && it.path == "showPolciy" })
    }

    @Test
    fun `URLs accept only HTTP and HTTPS with a host`() {
        assertTrue(validHttpUri("https://modrinth.com/mod/example") != null)
        assertTrue(validHttpUri("http://example.com/mod.jar") != null)
        assertFalse(validHttpUri("file:///mods/example.jar") != null)
        assertFalse(validHttpUri("not a url") != null)
    }

    @Test
    fun `mod validation issues expose a human readable owner`() {
        val config = PackHelperConfig(
            mods = listOf(
                RequiredMod(
                    name = "Example Technology",
                    modId = "example_technology",
                    links = listOf(DownloadLink("Website", "not a url")),
                )
            )
        )

        val issue = ConfigValidator.validate(config).first { it.code == "invalid_url" }

        assertEquals("mods[0].links[0].url", issue.path)
        assertEquals("Example Technology · Website", issue.displayPath)
    }

    @Test
    fun `project metadata survives config serialization`() {
        val mod = RequiredMod(
            name = "Example",
            modId = "example",
            iconUrl = "https://cdn.modrinth.com/data/example/icon.png",
            projectUrl = "https://modrinth.com/mod/example",
            authors = listOf("Alice", "Bob"),
            license = "MIT",
            links = listOf(DownloadLink(url = "https://modrinth.com/mod/example")),
        )

        val restored = Gson().fromJson(Gson().toJson(mod), RequiredMod::class.java)

        assertEquals(mod.iconUrl, restored.iconUrl)
        assertEquals(listOf("Alice", "Bob"), restored.authors)
        assertEquals("MIT", restored.license)
    }

    @Test
    fun `project icon requires HTTPS`() {
        val config = PackHelperConfig(
            mods = listOf(
                RequiredMod(
                    name = "Example",
                    modId = "example",
                    iconUrl = "http://cdn.modrinth.com/icon.png",
                    links = listOf(DownloadLink(url = "https://modrinth.com/mod/example")),
                )
            )
        )

        assertTrue(ConfigValidator.validate(config).any { it.code == "invalid_icon_url" })
    }
}
