package org.bmp.cph.client.editor

import org.bmp.cph.config.ConfigIssue
import org.bmp.cph.config.ConfigManager
import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.MenuConfig
import org.bmp.cph.config.PackHelperConfig
import org.bmp.cph.config.RequiredMod

class EditorSession private constructor(val config: PackHelperConfig) {
    var dirty: Boolean = false
        private set

    fun markDirty() {
        dirty = true
    }

    fun mods(): MutableList<RequiredMod> {
        val mutable = config.mods?.toMutableList() ?: config.activeModEntries().toMutableList()
        config.mods = mutable
        config.requiredMods = null
        return mutable
    }

    fun replaceMods(mods: List<RequiredMod>) {
        config.mods = mods.toMutableList()
        config.requiredMods = null
        dirty = true
    }

    fun addDrafts(drafts: List<RequiredMod>): Int {
        val existing = config.activeModEntries()
        val keys = existing.flatMap { entry ->
            listOfNotNull(
                entry.modId?.lowercase()?.takeIf(String::isNotBlank)?.let { "id:$it" },
                entry.filePattern?.lowercase()?.takeIf(String::isNotBlank)?.let { "file:$it" },
            )
        }.toMutableSet()
        val accepted = drafts.filter { draft ->
            val candidates = listOfNotNull(
                draft.modId?.lowercase()?.takeIf(String::isNotBlank)?.let { "id:$it" },
                draft.filePattern?.lowercase()?.takeIf(String::isNotBlank)?.let { "file:$it" },
            )
            candidates.none(keys::contains).also { add -> if (add) keys.addAll(candidates) }
        }
        if (accepted.isNotEmpty()) replaceMods(existing + accepted)
        return accepted.size
    }

    fun save(): List<ConfigIssue> {
        val issues = ConfigManager.save(config)
        if (issues.none { it.severity == org.bmp.cph.config.IssueSeverity.ERROR }) dirty = false
        return issues
    }

    fun ensureMenu(): MenuConfig = config.menu ?: MenuConfig.default().also { config.menu = it }

    companion object {
        fun open(): EditorSession = EditorSession(ConfigManager.editableCopy())
    }
}

internal fun RequiredMod.copyForEditor(): RequiredMod = copy(
    descriptions = descriptions?.toMutableMap(),
    authors = authors?.toMutableList(),
    links = links?.map(DownloadLink::copy)?.toMutableList(),
)
