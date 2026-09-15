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
            (height - 72).coerceAtLeast(38),
            41,
            (listWidth - 18).coerceIn(100, 650),
            36,
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
        val add = TechButton.builder(tr("translations.add")) {
                minecraft?.setScreen(MenuTranslationEntryScreen(this, session, null))
            }.style(TechButtonStyle.PRIMARY).bounds(0, 0, compactButtonWidth(tr("translations.add")), 18).build()
        val back = TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("back")), 18).build()
        addFooterActions(back, add)
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
) : EditorScreenBase(tr("menu_translation.title"), parent) {
    private val gson = Gson()
    private var localeValue = originalLocale ?: "en_us"
    private lateinit var localeField: EditBox

    override fun init() {
        val w = (width - 26).coerceAtMost(680)
        val x = (width - w) / 2
        val localeWidth = (w * .62).toInt().coerceAtLeast(80)
        localeField = StableEditBox(font, x + w - localeWidth, 42, localeWidth, 18, tr("menu_translation.locale")).also {
            it.value = localeValue
            it.setMaxLength(32)
            it.setResponder { value -> localeValue = value }
            addRenderableWidget(it)
        }
        val listWidth = (width - 16).coerceAtLeast(120)
        val list = TranslationFieldsList(
            minecraft ?: Minecraft.getInstance(),
            listWidth,
            (height - 99).coerceAtLeast(38),
            67,
            (listWidth - 18).coerceIn(100, 680),
            TEXT_FIELDS,
            working,
        )
        list.x = 8
        addRenderableWidget(list)
        val save = TechButton.builder(tr("save_back")) { saveAndClose() }.style(TechButtonStyle.PRIMARY)
            .bounds(0, 0, compactButtonWidth(tr("save_back"), 90), 18).build()
        addFooterActions(save)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawHeader(guiGraphics, tr("menu_translation.subtitle"))
        guiGraphics.drawString(font, tr("menu_translation.locale"), xForLabel(localeField), localeField.y + 5, EditorTheme.TEXT_MUTED, false)
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

    private fun xForLabel(field: EditBox): Int = (width - (width - 26).coerceAtMost(680)) / 2

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

internal class TranslationFieldsList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val rowWidth: Int,
    keys: List<String>,
    working: JsonObject,
    private val onChanged: () -> Unit = {},
) : ContainerObjectSelectionList<TranslationFieldsList.FieldEntry>(minecraft, width, height, top, 30) {
    init {
        keys.forEach { key -> addEntry(FieldEntry(key, minecraft.font, working, (rowWidth * .62).toInt().coerceAtLeast(20), onChanged)) }
    }

    override fun getRowWidth(): Int = rowWidth

    override fun getScrollbarPosition(): Int = x + width - 7

    class FieldEntry(
        private val key: String,
        private val font: Font,
        working: JsonObject,
        fieldWidth: Int,
        onChanged: () -> Unit,
    ) : ContainerObjectSelectionList.Entry<FieldEntry>() {
        private val field = StableEditBox(font, 0, 0, fieldWidth, 20, Component.literal(key)).also { editBox ->
            editBox.value = working.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            editBox.setMaxLength(8192)
            editBox.setResponder { value ->
                if (value.isBlank()) working.remove(key) else working.addProperty(key, value)
                onChanged()
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
            drawEditorRow(guiGraphics, left, top, width, height, hovered)
            field.x = left + width - field.width - 7
            field.y = top + 4
            guiGraphics.drawString(font, font.plainSubstrByWidth(key, (field.x - left - 14).coerceAtLeast(25)), left + 7, top + 10, EditorTheme.TEXT_MUTED, false)
            field.render(guiGraphics, mouseX, mouseY, partialTick)
        }
    }
}
