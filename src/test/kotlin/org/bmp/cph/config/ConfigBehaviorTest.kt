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
    fun `download URL host is used when a source has no custom label`() {
        val link = DownloadLink(downloadUrl = "https://cdn.example.com/releases/example.jar")

        assertEquals("cdn.example.com", link.displayLabel())
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
            projectLinks = ProjectLinks(
                homepage = "https://modrinth.com/mod/example",
                source = "https://github.com/example/example",
                issues = "https://github.com/example/example/issues",
                wiki = "https://example.invalid/wiki",
                discord = "https://discord.gg/example",
                donations = listOf(ProjectDonationLink("Ko-fi", "https://ko-fi.com/example")),
            ),
            authors = listOf("Alice", "Bob"),
            license = "MIT",
            links = listOf(DownloadLink(url = "https://modrinth.com/mod/example")),
        )

        val restored = Gson().fromJson(Gson().toJson(mod), RequiredMod::class.java)

        assertEquals(mod.iconUrl, restored.iconUrl)
        assertEquals(mod.projectLinks, restored.projectLinks)
        assertEquals(listOf("Alice", "Bob"), restored.authors)
        assertEquals("MIT", restored.license)
    }

    @Test
    fun `schema migration converts legacy fields without losing mod data`() {
        val legacy = JsonParser.parseString(
            """{
                "schemaVersion": 2,
                "showOnlyOnce": true,
                "menu": {"defaultLanguage": "ru_ru"},
                "requiredMods": [{
                    "name": "Example",
                    "modId": "example",
                    "projectUrl": "https://modrinth.com/mod/example",
                    "links": [{"url": "https://modrinth.com/mod/example"}]
                }]
            }""".trimIndent(),
        ).asJsonObject

        val migrated = ConfigMigrator.migrate(legacy)

        assertTrue(migrated.changed)
        assertEquals(2, migrated.fromVersion)
        assertEquals(CONFIG_SCHEMA_VERSION, migrated.root.get("schemaVersion").asInt)
        assertEquals("ONCE_EVER", migrated.root.get("showPolicy").asString)
        assertEquals("ru_ru", migrated.root.getAsJsonObject("menu").getAsJsonObject("language").get("fallbackLanguage").asString)
        assertEquals(
            "https://modrinth.com/mod/example",
            migrated.root.getAsJsonArray("mods")[0].asJsonObject.getAsJsonObject("projectLinks").get("homepage").asString,
        )
        assertEquals("Example", migrated.root.getAsJsonArray("mods")[0].asJsonObject.get("name").asString)
        assertFalse(migrated.root.has("requiredMods"))
        assertFalse(migrated.root.has("showOnlyOnce"))
    }

    @Test
    fun `invalid project links point to the owning mod`() {
        val config = PackHelperConfig(mods = listOf(
            RequiredMod(
                name = "Example",
                modId = "example",
                projectLinks = ProjectLinks(source = "file:///source"),
                links = listOf(DownloadLink(url = "https://modrinth.com/mod/example")),
            ),
        ))

        val issue = ConfigValidator.validate(config).first { it.path.endsWith("projectLinks.source") }

        assertEquals("invalid_url", issue.code)
        assertEquals("Example", issue.displayPath)
    }

    @Test
    fun `backup batch limit is validated`() {
        val config = PackHelperConfig(downloads = DownloadConfig(maxBackupBatches = 0), mods = emptyList())

        assertTrue(ConfigValidator.validate(config).any { it.code == "invalid_backup_limit" })
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
