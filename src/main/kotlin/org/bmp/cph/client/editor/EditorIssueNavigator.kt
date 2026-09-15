package org.bmp.cph.client.editor

import net.minecraft.client.gui.screens.Screen
import org.bmp.cph.config.ConfigIssue

internal object EditorIssueNavigator {
    private val modPath = Regex("^(?:mods|requiredMods)\\[(\\d+)](?:\\.(.*))?$")
    private val linkIndex = Regex("^links\\[(\\d+)]")
    private val translationPath = Regex("^menu\\.translations\\.([^.]+)")
    private val externalOnlyCodes = setOf("parse_error", "save_error")

    fun canOpen(issue: ConfigIssue): Boolean = issue.code !in externalOnlyCodes

    fun destination(parent: Screen, session: EditorSession, issue: ConfigIssue): Screen? {
        if (!canOpen(issue)) return null
        if (parent is EditorWorkspaceScreen) {
            parent.openIssue(issue)
            return parent
        }

        modPath.matchEntire(issue.path)?.let { match ->
            val index = match.groupValues[1].toIntOrNull() ?: return ModsEditorScreen(parent, session)
            val mod = session.config.activeModEntries().getOrNull(index) ?: return ModsEditorScreen(parent, session)
            val working = mod.copyForEditor()
            val modEditor = ModEditorScreen(parent, session, index, working)
            val remainder = match.groupValues.getOrElse(2) { "" }

            if (remainder.startsWith("links") || remainder == "downloadUrl") {
                val commitLinks = {
                    val mods = session.config.activeModEntries().toMutableList()
                    if (index in mods.indices) {
                        mods[index] = working
                        session.replaceMods(mods)
                    }
                }
                val linksEditor = LinksEditorScreen(parent, working, commitLinks)
                val selectedLink = linkIndex.find(remainder)?.groupValues?.getOrNull(1)?.toIntOrNull()
                return if (selectedLink == null) linksEditor else LinkEntryEditorScreen(linksEditor, working, selectedLink, commitLinks)
            }
            if (remainder.startsWith("description")) return DescriptionsEditorScreen(modEditor, working)
            return modEditor
        }

        translationPath.find(issue.path)?.groupValues?.getOrNull(1)?.let { locale ->
            return MenuTranslationEntryScreen(parent, session, locale)
        }

        return when {
            issue.path == "mods" || issue.path == "requiredMods" -> ModsEditorScreen(parent, session)
            issue.path.startsWith("menu.translations") -> MenuTranslationsScreen(parent, session)
            issue.path.startsWith("pack") || issue.path.startsWith("showPolicy") ||
                issue.path.startsWith("showOnlyOnce") || issue.path.startsWith("menu.language") ||
                issue.path == "schemaVersion" -> GeneralEditorScreen(parent, session)
            else -> parent
        }
    }
}
