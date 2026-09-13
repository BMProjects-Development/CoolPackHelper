package org.bmp.cph.client.editor

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.config.RequiredMod

class ModsEditorScreen(
    parent: Screen,
    private val session: EditorSession,
) : EditorScreenBase(tr("mods.title"), parent) {
    private enum class SortMode { CONFIGURED, NAME, CATEGORY }

    private var searchText = ""
    private var appliedSearch = ""
    private var sortMode = SortMode.CONFIGURED
    private lateinit var searchField: EditBox

    override fun init() {
        val mods = session.config.activeModEntries()
        val contentWidth = (width - 24).coerceAtMost(720)
        val left = (width - contentWidth) / 2
        val sortWidth = (contentWidth / 3).coerceIn(100, 180)
        searchField = EditBox(font, left, 49, contentWidth - sortWidth - 6, 20, tr("mods.search")).also {
            it.value = searchText
            it.setMaxLength(256)
            it.setResponder { value -> searchText = value }
            if (searchText.isNotEmpty()) it.setCursorPosition(searchText.length)
            addRenderableWidget(it)
        }
        addRenderableWidget(
            TechButton.builder(tr("mods.sort.${sortMode.name.lowercase()}")) { cycleSort() }
                .style(TechButtonStyle.GHOST).bounds(left + contentWidth - sortWidth, 49, sortWidth, 20).build()
        )
        val listWidth = (width - 16).coerceAtLeast(120)
        val listTop = 76
        val listBottom = height - 86
        val query = appliedSearch.trim().lowercase()
        val indexedMods = mods.withIndex().map { it.index to it.value }
            .filter { (_, mod) -> query.isEmpty() || listOf(mod.name, mod.modId, mod.filePattern).any { it?.lowercase()?.contains(query) == true } }
            .let { values ->
                when (sortMode) {
                    SortMode.CONFIGURED -> values
                    SortMode.NAME -> values.sortedBy { it.second.displayName().lowercase() }
                    SortMode.CATEGORY -> values.sortedWith(compareBy({ it.second.resolvedCategory() }, { it.second.displayName().lowercase() }))
                }
            }
        val list = StyledActionList(
            minecraft ?: net.minecraft.client.Minecraft.getInstance(),
            listWidth,
            (listBottom - listTop).coerceAtLeast(38),
            listTop,
            (listWidth - 18).coerceIn(100, 720),
            42,
            indexedMods,
            titleOf = { it.second.displayName() },
            subtitleOf = { it.second.modId?.takeIf(String::isNotBlank) ?: it.second.filePattern.orEmpty() },
            accentOf = { (_, mod) ->
                when {
                    mod.enabled == false -> 0xFF647386.toInt()
                    mod.resolvedCategory() == org.bmp.cph.config.ModCategory.REQUIRED -> 0xFFE46A6A.toInt()
                    else -> 0xFFE0B85B.toInt()
                }
            },
            actionsOf = { (index, _) ->
                listOf(
                    RowAction(label = { tr("mods.edit") }, width = 58) {
                        minecraft?.setScreen(ModEditorScreen(this, session, index))
                    },
                    RowAction(label = { tr("mods.duplicate") }, width = 48, style = { TechButtonStyle.GHOST }) {
                        duplicate(index)
                    },
                    RowAction(label = { Component.literal("×") }, width = 25, style = { TechButtonStyle.DANGER }) {
                        val mutable = session.config.activeModEntries().toMutableList()
                        if (index in mutable.indices) mutable.removeAt(index)
                        session.replaceMods(mutable)
                        rebuildWidgets()
                    },
                )
            },
        )
        list.x = 8
        addRenderableWidget(list)

        val footerWidth = (width - 24).coerceAtMost(720)
        val footerX = (width - footerWidth) / 2
        val navigationY = height - 79
        val half = (footerWidth - 6) / 2
        addRenderableWidget(
            TechButton.builder(tr("mods.add")) {
                val mutable = session.config.activeModEntries().toMutableList()
                mutable += RequiredMod(enabled = false, descriptions = linkedMapOf(), links = emptyList())
                session.replaceMods(mutable)
                minecraft?.setScreen(ModEditorScreen(this, session, mutable.lastIndex))
            }.style(TechButtonStyle.PRIMARY).bounds(footerX, navigationY, half, 20).build()
        )
        addRenderableWidget(
            TechButton.builder(tr("mods.import_local")) {
                minecraft?.setScreen(LocalImportScreen(this, session))
            }.bounds(footerX + half + 6, navigationY, half, 20).build()
        )
        val allEnabled = mods.isNotEmpty() && mods.all { it.enabled != false }
        addRenderableWidget(
            TechButton.builder(tr(if (allEnabled) "mods.disable_all" else "mods.enable_all")) { setAllEnabled(!allEnabled) }
                .style(TechButtonStyle.SECONDARY).bounds(footerX, height - 53, half, 20).build()
        )
        addRenderableWidget(TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST).bounds(footerX + half + 6, height - 53, half, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val total = session.config.activeModEntries().size
        val shown = visibleCount()
        drawHeader(guiGraphics, if (searchText.isBlank()) tr("mods.count", total) else tr("mods.filtered_count", shown, total))
        if (session.config.activeModEntries().isEmpty()) guiGraphics.drawCenteredString(font, tr("mods.empty"), width / 2, height / 2, 0x91A4B8)
    }

    override fun tick() {
        super.tick()
        if (searchText != appliedSearch) {
            appliedSearch = searchText
            rebuildWidgets()
            searchField.isFocused = true
        }
    }

    override fun onClose() {
        super.onClose()
    }

    private fun cycleSort() {
        sortMode = SortMode.entries[(sortMode.ordinal + 1) % SortMode.entries.size]
        rebuildWidgets()
    }

    private fun duplicate(index: Int) {
        val mutable = session.config.activeModEntries().toMutableList()
        val original = mutable.getOrNull(index) ?: return
        val copy = original.copy(
            name = tr("mods.copy_name", original.displayName()).string,
            descriptions = original.descriptions?.toMap(),
            links = original.links?.map { it.copy() },
        )
        mutable.add(index + 1, copy)
        session.replaceMods(mutable)
        rebuildWidgets()
    }

    private fun setAllEnabled(enabled: Boolean) {
        session.config.activeModEntries().forEach { it.enabled = enabled }
        session.markDirty()
        rebuildWidgets()
    }

    private fun visibleCount(): Int {
        val query = searchText.trim().lowercase()
        return session.config.activeModEntries().count { mod ->
            query.isEmpty() || listOf(mod.name, mod.modId, mod.filePattern).any { it?.lowercase()?.contains(query) == true }
        }
    }
}
