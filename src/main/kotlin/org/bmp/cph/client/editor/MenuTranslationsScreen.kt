package org.bmp.cph.client.editor

import com.google.gson.Gson
import com.google.gson.JsonObject
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.config.MenuText

class MenuTranslationsScreen(
    parent: Screen,
    private val session: EditorSession,
    private var page: Int = 0,
) : EditorScreenBase(tr("translations.title"), parent) {
    override fun init() {
        val translations = session.ensureMenu().translations.orEmpty()
        val entries = translations.entries.toList()
        val pageSize = ((height - 118) / 30).coerceAtLeast(1)
        val pages = ((entries.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        page = page.coerceIn(0, pages - 1)
        val w = (width - 24).coerceAtMost(650)
        val x = (width - w) / 2
        entries.drop(page * pageSize).take(pageSize).forEachIndexed { row, entry ->
            val y = 47 + row * 30
            addRenderableWidget(
                TechButton.builder(Component.literal(entry.key)) {
                    minecraft?.setScreen(MenuTranslationEntryScreen(this, session, entry.key))
                }.bounds(x, y, w - 30, 20).build()
            )
            addRenderableWidget(
                TechButton.builder(Component.literal("×")) {
                    val mutable = session.ensureMenu().translations.orEmpty().toMutableMap()
                    mutable.remove(entry.key)
                    session.ensureMenu().translations = mutable
                    session.markDirty()
                    rebuildWidgets()
                }.style(TechButtonStyle.DANGER).bounds(x + w - 24, y, 24, 20).build()
            )
        }
        val quarter = (w - 18) / 4
        val navY = height - 53
        val previous = TechButton.builder(tr("previous")) { page--; rebuildWidgets() }.bounds(x, navY, quarter, 20).build()
        previous.active = page > 0
        addRenderableWidget(previous)
        addRenderableWidget(
            TechButton.builder(tr("translations.add")) {
                minecraft?.setScreen(MenuTranslationEntryScreen(this, session, null))
            }.bounds(x + quarter + 6, navY, quarter * 2 + 6, 20).build()
        )
        val next = TechButton.builder(tr("next")) { page++; rebuildWidgets() }
            .bounds(x + (quarter + 6) * 3, navY, quarter, 20).build()
        next.active = page < pages - 1
        addRenderableWidget(next)
        addRenderableWidget(TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST).bounds(x, height - 27, w, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("translations.subtitle"))
    }

    override fun onClose() {
        super.onClose()
    }
}

class MenuTranslationEntryScreen(
    parent: Screen,
    private val session: EditorSession,
    private val originalLocale: String?,
    private var page: Int = 0,
    private val working: JsonObject = Gson().toJsonTree(
        originalLocale?.let { session.ensureMenu().translations.orEmpty()[it] } ?: MenuText()
    ).asJsonObject,
) : EditorScreenBase(tr("translation.title"), parent) {
    private val gson = Gson()
    private var localeValue = originalLocale ?: "en_us"
    private lateinit var localeField: EditBox
    private val fields = mutableListOf<Pair<String, EditBox>>()

    override fun init() {
        fields.clear()
        val w = (width - 26).coerceAtMost(680)
        val x = (width - w) / 2
        localeField = EditBox(font, x, 48, w, 20, tr("translation.locale")).also {
            it.value = localeValue; it.setMaxLength(32); addRenderableWidget(it)
        }
        val pageSize = if (height >= 300) 5 else 3
        val pageCount = ((TEXT_FIELDS.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        page = page.coerceIn(0, pageCount - 1)
        TEXT_FIELDS.drop(page * pageSize).take(pageSize).forEachIndexed { index, key ->
            val field = EditBox(font, x, 88 + index * 39, w, 20, Component.literal(key)).also {
                it.value = working.get(key)?.takeIf { value -> value.isJsonPrimitive }?.asString.orEmpty()
                it.setMaxLength(8192)
                addRenderableWidget(it)
            }
            fields += key to field
        }
        val third = (w - 12) / 3
        val previous = TechButton.builder(tr("previous")) { commitPage(); page--; rebuildWidgets() }
            .bounds(x, height - 27, third, 20).build()
        previous.active = page > 0
        addRenderableWidget(previous)
        addRenderableWidget(
            TechButton.builder(tr("save")) { saveAndClose() }.style(TechButtonStyle.PRIMARY)
                .bounds(x + third + 6, height - 27, third, 20).build()
        )
        val next = TechButton.builder(tr("next")) { commitPage(); page++; rebuildWidgets() }
            .bounds(x + (third + 6) * 2, height - 27, third, 20).build()
        next.active = page < pageCount - 1
        addRenderableWidget(next)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("translation.subtitle", page + 1))
        guiGraphics.drawString(font, tr("translation.locale"), localeField.x, localeField.y - 10, 0x90A7BC, false)
        fields.forEach { (key, field) ->
            guiGraphics.drawString(font, key, field.x, field.y - 10, 0x90A7BC, false)
        }
    }

    private fun commitPage() {
        localeValue = localeField.value
        fields.forEach { (key, field) ->
            if (field.value.isBlank()) working.remove(key) else working.addProperty(key, field.value)
        }
    }

    private fun saveAndClose() {
        commitPage()
        val locale = localeValue.trim().lowercase()
        if (locale.isNotBlank()) {
            val mutable = session.ensureMenu().translations.orEmpty().toMutableMap()
            originalLocale?.let(mutable::remove)
            mutable[locale] = gson.fromJson(working, MenuText::class.java)
            session.ensureMenu().translations = mutable
            session.markDirty()
        }
        onClose()
    }

    private companion object {
        val TEXT_FIELDS = listOf(
            "title", "description", "summary", "requiredLabel", "recommendedLabel", "missingStatus", "wrongVersionStatus",
            "downloadButton", "chooseSourceButton", "sourcesTitle", "continueButton", "recheckButton", "openModsFolderButton",
            "openConfigFolderButton", "previousButton", "nextButton", "pageIndicator", "configErrorTitle",
            "configErrorDescription", "allResolvedMessage", "allTab", "requiredTab", "recommendedTab", "detailsButton",
            "detailsTitle", "modIdLabel", "installedVersionLabel", "requiredVersionLabel", "backButton", "emptyTabMessage",
        )
    }
}
