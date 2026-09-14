package org.bmp.cph.client.editor

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import org.bmp.cph.config.LanguageConfig
import org.bmp.cph.config.LanguageMode
import org.bmp.cph.config.PackInfo
import org.bmp.cph.config.ShowPolicy

class GeneralEditorScreen(
    parent: Screen,
    private val session: EditorSession,
) : EditorScreenBase(tr("general.title"), parent) {
    private lateinit var packId: EditBox
    private lateinit var packName: EditBox
    private lateinit var packVersion: EditBox
    private lateinit var fixedLanguage: EditBox
    private lateinit var fallbackLanguage: EditBox
    private var labels = emptyList<Pair<EditBox, String>>()
    private var propertyLeft = 0
    private var propertyWidth = 0

    override fun init() {
        val config = session.config
        val pack = config.pack ?: PackInfo().also { config.pack = it }
        val language = session.ensureMenu().language ?: LanguageConfig().also { session.ensureMenu().language = it }
        val contentWidth = (width - 20).coerceAtMost(590)
        val left = (width - contentWidth) / 2
        val labelWidth = (contentWidth * .38).toInt().coerceIn(112, 185)
        val fieldX = left + labelWidth
        val fieldWidth = (contentWidth - labelWidth - 6).coerceAtLeast(50)
        val firstY = 45
        propertyLeft = left
        propertyWidth = contentWidth

        packId = field(fieldX, firstY + 3, fieldWidth, pack.id.orEmpty())
        packName = field(fieldX, firstY + 31, fieldWidth, pack.name.orEmpty())
        packVersion = field(fieldX, firstY + 59, fieldWidth, pack.version.orEmpty())
        fixedLanguage = field(fieldX, firstY + 87, fieldWidth, language.fixedLanguage.orEmpty())
        fallbackLanguage = field(fieldX, firstY + 115, fieldWidth, language.fallbackLanguage.orEmpty())
        labels = listOf(
            packId to "general.pack_id",
            packName to "general.pack_name",
            packVersion to "general.pack_version",
            fixedLanguage to "general.fixed_language",
            fallbackLanguage to "general.fallback_language",
        )
        fixedLanguage.active = language.mode.equals(LanguageMode.FIXED.name, true)

        val buttonsY = firstY + 151
        val policyText = tr("general.policy", tr("policy.${config.resolvedShowPolicy().name.lowercase()}"))
        val languageText = tr("general.language_mode", tr("language.${languageMode().name.lowercase()}"))
        var policyWidth = compactButtonWidth(policyText, 120, 245)
        var languageWidth = compactButtonWidth(languageText, 120, 220)
        if (policyWidth + languageWidth + 6 > contentWidth) {
            policyWidth = (contentWidth - 6) / 2
            languageWidth = contentWidth - policyWidth - 6
        }
        addRenderableWidget(
            TechButton.builder(policyText) {
                commit()
                val current = config.resolvedShowPolicy()
                config.showPolicy = ShowPolicy.entries[(current.ordinal + 1) % ShowPolicy.entries.size].name
                rebuildWidgets()
            }.style(TechButtonStyle.SECONDARY).bounds(left, buttonsY, policyWidth, 18).build()
        )
        addRenderableWidget(
            TechButton.builder(languageText) {
                commit()
                language.mode = if (languageMode() == LanguageMode.GAME) LanguageMode.FIXED.name else LanguageMode.GAME.name
                rebuildWidgets()
            }.style(TechButtonStyle.SECONDARY)
                .bounds(left + policyWidth + 6, buttonsY, languageWidth, 18).build()
        )
        val back = TechButton.builder(tr("save_back")) { onClose() }.style(TechButtonStyle.PRIMARY)
            .bounds(0, 0, compactButtonWidth(tr("save_back"), 90), 18).build()
        addFooterActions(back)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("general.subtitle"))
        labels.forEachIndexed { index, _ ->
            drawEditorRow(guiGraphics, propertyLeft, 45 + index * 28, propertyWidth, 27, false)
        }
        labels.forEach { (field, key) ->
            guiGraphics.drawString(font, font.plainSubstrByWidth(tr(key).string, (field.x - propertyLeft - 14).coerceAtLeast(30)), propertyLeft + 8, field.y + 6, EditorTheme.TEXT_MUTED, false)
        }
    }

    override fun onClose() {
        commit()
        super.onClose()
    }

    private fun field(x: Int, y: Int, width: Int, value: String): EditBox =
        StableEditBox(font, x, y, width, 20, tr("field")).also {
            it.setMaxLength(512)
            it.value = value
            addRenderableWidget(it)
        }

    private fun languageMode(): LanguageMode {
        val value = session.ensureMenu().language?.mode
        return LanguageMode.entries.firstOrNull { it.name.equals(value, true) } ?: LanguageMode.GAME
    }

    private fun commit() {
        val config = session.config
        val pack = config.pack ?: PackInfo().also { config.pack = it }
        val language = session.ensureMenu().language ?: LanguageConfig().also { session.ensureMenu().language = it }
        pack.id = packId.value.trim()
        pack.name = packName.value.trim()
        pack.version = packVersion.value.trim()
        fixedLanguage.value.trim().takeIf(String::isNotBlank)?.let { language.fixedLanguage = it.lowercase() }
        fallbackLanguage.value.trim().takeIf(String::isNotBlank)?.let { language.fallbackLanguage = it.lowercase() }
        session.markDirty()
    }
}
