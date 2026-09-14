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
    private var sortMode = SortMode.CONFIGURED
    private lateinit var searchField: EditBox
    private lateinit var clearSearchButton: TechButton
    private lateinit var sortButton: TechButton
    private lateinit var modsList: StyledActionList<Pair<Int, RequiredMod>>
    private var selectedIndex: Int? = null
    private var inspectorLeft = 0
    private var twoPane = false
    private var modsScrollAmount = 0.0

    override fun init() {
        val mods = session.config.activeModEntries()
        selectedIndex = selectedIndex?.takeIf { it in mods.indices } ?: mods.indices.firstOrNull()
        twoPane = width >= 620
        val outerGap = 8
        val paneGap = 7
        val availableWidth = (width - outerGap * 2).coerceAtLeast(120)
        val listPaneWidth = if (twoPane) (width * .36).toInt().coerceIn(230, 320) else availableWidth
        val left = outerGap
        val sortText = tr("mods.sort.${sortMode.name.lowercase()}")
        val sortWidth = compactButtonWidth(sortText, 92, if (twoPane) 126 else 180)
        val clearWidth = 22
        searchField = StableEditBox(font, left, 42, (listPaneWidth - sortWidth - clearWidth - 8).coerceAtLeast(48), 18, tr("mods.search")).also {
            it.value = searchText
            it.setMaxLength(256)
            it.setResponder { value ->
                searchText = value
                if (::modsList.isInitialized) refreshList()
                if (::clearSearchButton.isInitialized) clearSearchButton.active = value.isNotEmpty()
            }
            if (searchText.isNotEmpty()) it.setCursorPosition(searchText.length)
            addRenderableWidget(it)
        }
        clearSearchButton = TechButton.builder(Component.literal("×")) {
            searchField.value = ""
            setFocused(searchField)
        }.tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("mods.search.clear.hint")))
            .style(TechButtonStyle.GHOST)
            .bounds(left + listPaneWidth - sortWidth - clearWidth - 4, 42, clearWidth, 18).build()
        clearSearchButton.active = searchText.isNotEmpty()
        addRenderableWidget(clearSearchButton)
        sortButton = TechButton.builder(sortText) { cycleSort() }
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("mods.sort.hint")))
            .style(TechButtonStyle.GHOST).bounds(left + listPaneWidth - sortWidth, 42, sortWidth, 18).build()
        addRenderableWidget(sortButton)
        val listWidth = listPaneWidth
        val listTop = 65
        val listBottom = height - 32
        modsList = StyledActionList(
            minecraft ?: net.minecraft.client.Minecraft.getInstance(),
            listWidth,
            (listBottom - listTop).coerceAtLeast(38),
            listTop,
            (listWidth - 10).coerceAtLeast(100),
            36,
            filteredMods(),
            titleOf = { it.second.displayName() },
            subtitleOf = { it.second.modId?.takeIf(String::isNotBlank) ?: it.second.filePattern.orEmpty() },
            accentOf = { (index, mod) ->
                when {
                    twoPane && index == selectedIndex -> EditorTheme.ACCENT
                    mod.enabled == false -> 0xFF647386.toInt()
                    mod.resolvedCategory() == org.bmp.cph.config.ModCategory.REQUIRED -> 0xFFE46A6A.toInt()
                    else -> 0xFFE0B85B.toInt()
                }
            },
            iconOf = { it.second.iconUrl },
            actionsOf = { (index, _) ->
                listOf(
                    RowAction(
                        label = { tr("mods.duplicate") }, width = 48,
                        style = { TechButtonStyle.GHOST }, tooltip = tr("mods.duplicate.hint"),
                    ) {
                        duplicate(index)
                    },
                    RowAction(
                        label = { Component.literal("×") }, width = 25,
                        style = { TechButtonStyle.DANGER }, tooltip = tr("mods.delete.hint"),
                    ) {
                        rememberListScroll()
                        val mutable = session.config.activeModEntries().toMutableList()
                        if (index in mutable.indices) mutable.removeAt(index)
                        session.replaceMods(mutable)
                        selectedIndex = when {
                            mutable.isEmpty() -> null
                            index >= mutable.size -> mutable.lastIndex
                            else -> index
                        }
                        rebuildWidgets()
                    },
                )
            },
            onRowClick = { (index, _) ->
                if (twoPane) {
                    rememberListScroll()
                    selectedIndex = index
                    rebuildWidgets()
                } else {
                    minecraft?.setScreen(ModEditorScreen(this, session, index))
                }
            },
        )
        modsList.x = left
        modsList.setScrollAmount(modsScrollAmount)
        addRenderableWidget(modsList)

        if (twoPane) {
            inspectorLeft = left + listPaneWidth + paneGap
            val inspectorWidth = (width - inspectorLeft - outerGap).coerceAtLeast(180)
            mods.getOrNull(selectedIndex ?: -1)?.let { selected ->
                val inspector = ModEditorContentList(
                    minecraft ?: net.minecraft.client.Minecraft.getInstance(),
                    inspectorWidth,
                    (height - 91).coerceAtLeast(38),
                    59,
                    (inspectorWidth - 10).coerceAtLeast(100),
                    selected,
                    openDescriptions = { minecraft?.setScreen(DescriptionsEditorScreen(this, selected, session::markDirty)) },
                    openLinks = { minecraft?.setScreen(LinksEditorScreen(this, selected, session::markDirty)) },
                    openMetadata = { minecraft?.setScreen(ModMetadataEditorScreen(this, selected, session::markDirty)) },
                    onChanged = session::markDirty,
                )
                inspector.x = inspectorLeft
                addRenderableWidget(inspector)
            }
        }

        val add = TechButton.builder(tr("mods.add")) {
                val mutable = session.config.activeModEntries().toMutableList()
                mutable += RequiredMod(enabled = false, descriptions = linkedMapOf(), links = emptyList())
                session.replaceMods(mutable)
                selectedIndex = mutable.lastIndex
                if (twoPane) rebuildWidgets() else minecraft?.setScreen(ModEditorScreen(this, session, mutable.lastIndex))
            }.style(TechButtonStyle.PRIMARY).bounds(0, 0, compactButtonWidth(tr("mods.add")), 18).build()
        val import = TechButton.builder(tr("mods.import_local")) {
            minecraft?.setScreen(LocalImportScreen(this, session))
        }.bounds(0, 0, compactButtonWidth(tr("mods.import_local"), 78), 18).build()
        val allEnabled = mods.isNotEmpty() && mods.all { it.enabled != false }
        val toggleText = tr(if (allEnabled) "mods.disable_all" else "mods.enable_all")
        val toggle = TechButton.builder(toggleText) { setAllEnabled(!allEnabled) }
            .style(TechButtonStyle.SECONDARY).bounds(0, 0, compactButtonWidth(toggleText, 76), 18).build()
        val back = TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("back")), 18).build()
        addFooterActions(back, toggle, import, add)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val total = session.config.activeModEntries().size
        val shown = visibleCount()
        drawHeader(guiGraphics, if (searchText.isBlank()) tr("mods.count", total) else tr("mods.filtered_count", shown, total))
        if (twoPane) {
            guiGraphics.fill(inspectorLeft - 4, 40, inspectorLeft - 3, height - 32, EditorTheme.BORDER_SOFT)
            val selected = session.config.activeModEntries().getOrNull(selectedIndex ?: -1)
            val inspectorTitle = selected?.displayName() ?: tr("mods.select").string
            guiGraphics.drawString(
                font,
                font.plainSubstrByWidth(inspectorTitle, (width - inspectorLeft - 18).coerceAtLeast(40)),
                inspectorLeft + 5,
                45,
                EditorTheme.TEXT,
                false,
            )
        }
        if (session.config.activeModEntries().isEmpty()) guiGraphics.drawCenteredString(font, tr("mods.empty"), width / 2, height / 2, 0x91A4B8)
    }

    override fun onClose() {
        super.onClose()
    }

    private fun cycleSort() {
        sortMode = SortMode.entries[(sortMode.ordinal + 1) % SortMode.entries.size]
        sortButton.message = tr("mods.sort.${sortMode.name.lowercase()}")
        refreshList()
    }

    private fun duplicate(index: Int) {
        rememberListScroll()
        val mutable = session.config.activeModEntries().toMutableList()
        val original = mutable.getOrNull(index) ?: return
        val copy = original.copy(
            name = tr("mods.copy_name", original.displayName()).string,
            descriptions = original.descriptions?.toMap(),
            links = original.links?.map { it.copy() },
        )
        mutable.add(index + 1, copy)
        session.replaceMods(mutable)
        selectedIndex = index + 1
        rebuildWidgets()
    }

    private fun setAllEnabled(enabled: Boolean) {
        rememberListScroll()
        session.config.activeModEntries().forEach { it.enabled = enabled }
        session.markDirty()
        rebuildWidgets()
    }

    private fun visibleCount(): Int = filteredMods().size

    private fun filteredMods(): List<Pair<Int, RequiredMod>> {
        val query = searchText.trim().lowercase()
        val values = session.config.activeModEntries().withIndex().map { it.index to it.value }
            .filter { (_, mod) ->
                query.isEmpty() || listOf(
                    mod.name,
                    mod.modId,
                    mod.filePattern,
                    mod.authors.orEmpty().joinToString(" "),
                    mod.links.orEmpty().joinToString(" ") { it.resolvedType().name },
                ).any { it?.lowercase()?.contains(query) == true }
            }
        return when (sortMode) {
            SortMode.CONFIGURED -> values
            SortMode.NAME -> values.sortedBy { it.second.displayName().lowercase() }
            SortMode.CATEGORY -> values.sortedWith(compareBy({ it.second.resolvedCategory() }, { it.second.displayName().lowercase() }))
        }
    }

    private fun refreshList() {
        modsScrollAmount = 0.0
        modsList.replaceItems(filteredMods())
    }

    private fun rememberListScroll() {
        if (::modsList.isInitialized) modsScrollAmount = modsList.getScrollAmount()
    }
}
