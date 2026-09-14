package org.bmp.cph.client.editor

import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.ProjectDonationLink
import org.bmp.cph.config.ProjectLinks
import org.bmp.cph.config.RequiredMod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProjectMetadataServiceTest {
    private val imported = ProjectMetadata(
        projectId = "project-id",
        name = "Imported name",
        summary = "Imported description",
        iconUrl = "https://cdn.modrinth.com/data/project-id/icon.png",
        projectLinks = ProjectLinks(
            homepage = "https://modrinth.com/mod/project-id",
            source = "https://github.com/example/project",
            issues = "https://github.com/example/project/issues",
            donations = listOf(ProjectDonationLink("Ko-fi", "https://ko-fi.com/example")),
        ),
        authors = listOf("Imported author"),
        license = "MIT",
    )

    @Test
    fun `fill empty keeps author-provided metadata`() {
        val mod = RequiredMod(
            name = "Custom name",
            descriptions = mapOf("ru_ru" to "Авторское описание"),
            projectLinks = ProjectLinks(homepage = "https://example.org/custom"),
            authors = listOf("Pack author"),
        )
        val source = DownloadLink()

        ProjectMetadataService.apply(imported, mod, source, "ru_ru", MetadataApplyMode.FILL_EMPTY)

        assertEquals("Custom name", mod.name)
        assertEquals("Авторское описание", mod.descriptions?.get("ru_ru"))
        assertEquals("https://example.org/custom", mod.projectLinks?.homepage)
        assertEquals("https://github.com/example/project", mod.projectLinks?.source)
        assertEquals(listOf("Pack author"), mod.authors)
        assertEquals("MIT", mod.license)
    }

    @Test
    fun `replace applies imported metadata and source identity`() {
        val mod = RequiredMod(name = "Custom name", projectUrl = "https://example.org/legacy")
        val source = DownloadLink()

        ProjectMetadataService.apply(imported, mod, source, "en_us", MetadataApplyMode.REPLACE)

        assertEquals("Imported name", mod.name)
        assertEquals("Imported description", mod.descriptions?.get("en_us"))
        assertEquals(imported.projectLinks, mod.projectLinks)
        assertEquals(null, mod.projectUrl)
        assertEquals("project-id", source.projectId)
        assertEquals("https://modrinth.com/mod/project-id", source.url)
    }
}
