package org.bmp.cph.client.editor

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

internal data class MinecraftLanguageChoice(
    val code: String,
    val name: String,
    val region: String,
    val bidirectional: Boolean,
)

internal fun filterMinecraftLanguages(
    languages: List<MinecraftLanguageChoice>,
    query: String,
): List<MinecraftLanguageChoice> {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return languages
    return languages.filter { language ->
        language.code.lowercase().contains(normalized) ||
            language.name.lowercase().contains(normalized) ||
            language.region.lowercase().contains(normalized)
    }
}

internal class MinecraftLanguagePickerScreen(
    parent: Screen,
    private val currentCode: String,
    private val onSelected: (String) -> Unit,
) : EditorScreenBase(tr("language_picker.title"), parent) {
    private lateinit var searchField: EditBox
    private lateinit var languageList: StyledActionList<MinecraftLanguageChoice>
    private var searchQuery = ""
    private var allLanguages = emptyList<MinecraftLanguageChoice>()
    private var dialog = EditorRect(0, 0, 0, 0)

    override fun usesModalBackground(): Boolean = true

    override fun init() {
        dialog = centeredModal(680, 430, 230)
        allLanguages = Minecraft.getInstance().languageManager.languages.map { (code, info) ->
            MinecraftLanguageChoice(code, info.name, info.region, info.bidirectional)
        }
        val innerLeft = dialog.left + 8
        val innerWidth = (dialog.width - 16).coerceAtLeast(120)
        searchField = StableEditBox(
            font, innerLeft, dialog.top + 40, innerWidth, 20, tr("language_picker.search"),
        ).also { field ->
            field.value = searchQuery
            field.setMaxLength(128)
            field.setHint(tr("language_picker.search"))
            field.setResponder { value ->
                searchQuery = value
                if (::languageList.isInitialized) {
                    languageList.replaceItems(filterMinecraftLanguages(allLanguages, searchQuery))
                }
            }
            addRenderableWidget(field)
        }
        val listTop = dialog.top + 66
        languageList = StyledActionList(
            Minecraft.getInstance(),
            innerWidth,
            (dialog.bottom - listTop - 31).coerceAtLeast(38),
            listTop,
            (innerWidth - 10).coerceAtLeast(100),
            36,
            filterMinecraftLanguages(allLanguages, searchQuery),
            titleOf = { it.name },
            subtitleOf = { language ->
                buildString {
                    append(language.code)
                    language.region.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
                    if (language.bidirectional) append(" · RTL")
                }
            },
            accentOf = { if (it.code.equals(currentCode, true)) EditorTheme.ACCENT else 0xFF737982.toInt() },
            badgeOf = { if (it.code.equals(currentCode, true)) RowBadge(Component.literal("✓"), EditorTheme.ACCENT) else null },
            onRowClick = { select(it) },
            rowTooltipOf = { tr("language_picker.select", it.name, it.code) },
        ).also {
            it.x = innerLeft
            addRenderableWidget(it)
        }
        val back = TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("back")), 18).build()
        addCompactActions(dialog.left + 8, dialog.right - 8, dialog.bottom - 23, back)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawModalFrame(guiGraphics, dialog, tr("language_picker.subtitle"))
        if (filterMinecraftLanguages(allLanguages, searchQuery).isEmpty()) {
            guiGraphics.drawCenteredString(font, tr("language_picker.empty"), width / 2, dialog.top + 92, EditorTheme.TEXT_MUTED)
        }
    }

    private fun select(language: MinecraftLanguageChoice) {
        onSelected(language.code)
        minecraft?.setScreen(previousScreen)
    }
}
