package org.bmp.cph.client.editor

import com.google.gson.Gson
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.config.MenuText

class MenuTranslationsScreen(
    parent: Screen,
    private val session: EditorSession,
) : EditorScreenBase(tr("translations.title"), parent) {
    override fun init() {
        val translations = session.ensureMenu().translations.orEmpty()
        val entries = translations.entries.toList()
        val w = (width - 24).coerceAtMost(650)
        val x = (width - w) / 2
        val listWidth = (width - 16).coerceAtLeast(120)
        val list = StyledActionList(
            minecraft ?: net.minecraft.client.Minecraft.getInstance(),
            listWidth,
            (height - 83).coerceAtLeast(38),
            49,
            (listWidth - 18).coerceIn(100, 650),
            40,
            entries,
            titleOf = { it.key },
            subtitleOf = { translationSummary(it.value) },
            accentOf = { 0xFF9B7BFF.toInt() },
            actionsOf = { entry -> listOf(
                RowAction(label = { tr("mods.edit") }, width = 58) {
                    minecraft?.setScreen(MenuTranslationEntryScreen(this, session, entry.key))
                },
                RowAction(label = { Component.literal("×") }, width = 25, style = { TechButtonStyle.DANGER }) {
                    val mutable = session.ensureMenu().translations.orEmpty().toMutableMap()
                    mutable.remove(entry.key)
                    session.ensureMenu().translations = mutable
                    session.markDirty()
                    rebuildWidgets()
                },
            ) },
        )
        list.x = 8
        addRenderableWidget(list)
        val half = (w - 6) / 2
        addRenderableWidget(
            TechButton.builder(tr("translations.add")) {
                minecraft?.setScreen(MenuTranslationEntryScreen(this, session, null))
            }.style(TechButtonStyle.PRIMARY).bounds(x, height - 27, half, 20).build()
        )
        addRenderableWidget(TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST).bounds(x + half + 6, height - 27, half, 20).build())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("translations.subtitle"))
    }

    override fun onClose() {
        super.onClose()
    }

    private fun translationSummary(text: MenuText): String =
        text.title?.takeIf(String::isNotBlank) ?: text.description?.takeIf(String::isNotBlank) ?: tr("translations.empty_locale").string
}

class MenuTranslationEntryScreen(
    parent: Screen,
    private val session: EditorSession,
    private val originalLocale: String?,
    private val working: JsonObject = Gson().toJsonTree(
        originalLocale?.let { session.ensureMenu().translations.orEmpty()[it] } ?: MenuText()
    ).asJsonObject,
) : EditorScreenBase(tr("translation.title"), parent) {
    private val gson = Gson()
    private var localeValue = originalLocale ?: "en_us"
    private lateinit var localeField: EditBox

    override fun init() {
        val w = (width - 26).coerceAtMost(680)
        val x = (width - w) / 2
        localeField = StableEditBox(font, x, 48, w, 20, tr("translation.locale")).also {
            it.value = localeValue
            it.setMaxLength(32)
            it.setResponder { value -> localeValue = value }
            addRenderableWidget(it)
        }
        val listWidth = (width - 16).coerceAtLeast(120)
        val list = TranslationFieldsList(
            minecraft ?: Minecraft.getInstance(),
            listWidth,
            (height - 116).coerceAtLeast(38),
            82,
            (listWidth - 18).coerceIn(100, 680),
            TEXT_FIELDS,
            working,
        )
        list.x = 8
        addRenderableWidget(list)
        addRenderableWidget(
            TechButton.builder(tr("save_back")) { saveAndClose() }.style(TechButtonStyle.PRIMARY)
                .bounds(x, height - 27, w, 20).build()
        )
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("translation.subtitle"))
        guiGraphics.drawString(font, tr("translation.locale"), localeField.x, localeField.y - 10, 0x90A7BC, false)
    }

    private fun saveAndClose() {
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
            "detailsTitle", "modIdLabel", "installedVersionLabel", "requiredVersionLabel", "authorsLabel", "licenseLabel",
            "projectPageLabel", "backButton", "emptyTabMessage",
        )
    }
}

private class TranslationFieldsList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val rowWidth: Int,
    keys: List<String>,
    working: JsonObject,
) : ContainerObjectSelectionList<TranslationFieldsList.FieldEntry>(minecraft, width, height, top, 42) {
    init {
        keys.forEach { key -> addEntry(FieldEntry(key, minecraft.font, working, (rowWidth - 14).coerceAtLeast(20))) }
    }

    override fun getRowWidth(): Int = rowWidth

    override fun getScrollbarPosition(): Int = x + width - 7

    class FieldEntry(
        private val key: String,
        private val font: Font,
        working: JsonObject,
        fieldWidth: Int,
    ) : ContainerObjectSelectionList.Entry<FieldEntry>() {
        private val field = StableEditBox(font, 0, 0, fieldWidth, 20, Component.literal(key)).also { editBox ->
            editBox.value = working.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            editBox.setMaxLength(8192)
            editBox.setResponder { value ->
                if (value.isBlank()) working.remove(key) else working.addProperty(key, value)
            }
        }

        override fun children(): List<GuiEventListener> = listOf(field)

        override fun narratables(): List<NarratableEntry> = listOf(field)

        override fun render(
            guiGraphics: GuiGraphics,
            index: Int,
            top: Int,
            left: Int,
            width: Int,
            height: Int,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            partialTick: Float,
        ) {
            guiGraphics.fill(left, top, left + width, top + height - 2, if (hovered) 0xE0222D3E.toInt() else 0xC5161D29.toInt())
            guiGraphics.fill(left, top, left + 2, top + height - 2, 0xFF9B7BFF.toInt())
            guiGraphics.fill(left + 2, top, left + width, top + 1, 0x4A5A708A)
            guiGraphics.fill(left + width - 1, top + 1, left + width, top + height - 2, 0x334D6077)
            guiGraphics.fill(left + 2, top + height - 3, left + width, top + height - 2, 0x334D6077)
            guiGraphics.drawString(font, key, left + 7, top + 4, 0x90A7BC, false)
            field.x = left + 7
            field.y = top + 15
            field.render(guiGraphics, mouseX, mouseY, partialTick)
        }
    }
}
