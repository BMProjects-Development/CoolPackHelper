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

    override fun init() {
        val config = session.config
        val pack = config.pack ?: PackInfo().also { config.pack = it }
        val language = session.ensureMenu().language ?: LanguageConfig().also { session.ensureMenu().language = it }
        val contentWidth = (width - 28).coerceAtMost(620)
        val left = (width - contentWidth) / 2
        val twoColumns = width >= 480 || height < 310
        val fieldWidth = if (twoColumns) (contentWidth - 10) / 2 else contentWidth
        val rightColumn = left + contentWidth - fieldWidth

        packId = field(left, 62, fieldWidth, pack.id.orEmpty())
        packName = field(if (twoColumns) rightColumn else left, if (twoColumns) 62 else 101, fieldWidth, pack.name.orEmpty())
        packVersion = field(left, if (twoColumns) 101 else 140, fieldWidth, pack.version.orEmpty())
        fixedLanguage = field(if (twoColumns) rightColumn else left, if (twoColumns) 101 else 179, fieldWidth, language.fixedLanguage.orEmpty())
        fallbackLanguage = field(left, if (twoColumns) 140 else 218, fieldWidth, language.fallbackLanguage.orEmpty())
        labels = listOf(
            packId to "general.pack_id",
            packName to "general.pack_name",
            packVersion to "general.pack_version",
            fixedLanguage to "general.fixed_language",
            fallbackLanguage to "general.fallback_language",
        )
        fixedLanguage.active = language.mode.equals(LanguageMode.FIXED.name, true)

        val buttonsY = if (twoColumns) 180 else 257
        val half = (contentWidth - 8) / 2
        addRenderableWidget(
            TechButton.builder(tr("general.policy", tr("policy.${config.resolvedShowPolicy().name.lowercase()}"))) {
                commit()
                val current = config.resolvedShowPolicy()
                config.showPolicy = ShowPolicy.entries[(current.ordinal + 1) % ShowPolicy.entries.size].name
                rebuildWidgets()
            }.bounds(left, buttonsY, half, 20).build()
        )
        addRenderableWidget(
            TechButton.builder(tr("general.language_mode", tr("language.${languageMode().name.lowercase()}"))) {
                commit()
                language.mode = if (languageMode() == LanguageMode.GAME) LanguageMode.FIXED.name else LanguageMode.GAME.name
                rebuildWidgets()
            }.bounds(left + half + 8, buttonsY, half, 20).build()
        )
        addRenderableWidget(
            TechButton.builder(tr("save_back")) { onClose() }.style(TechButtonStyle.PRIMARY)
                .bounds(left, height - 27, contentWidth, 20).build()
        )
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("general.subtitle"))
        labels.forEach { (field, key) ->
            guiGraphics.drawString(font, tr(key), field.x, field.y - 11, 0x90A7BC, false)
        }
    }

    override fun onClose() {
        commit()
        super.onClose()
    }

    private fun field(x: Int, y: Int, width: Int, value: String): EditBox =
        EditBox(font, x, y, width, 20, tr("field")).also {
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
