package org.bmp.cph.client.editor

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import org.bmp.cph.config.ConfigManager
import org.bmp.cph.config.RequiredMod

class DescriptionsEditorScreen(
    parent: Screen,
    private val mod: RequiredMod,
    private val onChanged: () -> Unit = {},
) : EditorScreenBase(tr("descriptions.title", mod.displayName()), parent) {
    private var dialog = EditorRect(0, 0, 0, 0)

    override fun usesModalBackground(): Boolean = true

    override fun init() {
        val entries = mod.descriptions.orEmpty().entries.toList()
        dialog = centeredModal(680, 350, 190)
        val listWidth = (dialog.width - 12).coerceAtLeast(120)
        val list = StyledActionList(
            minecraft ?: net.minecraft.client.Minecraft.getInstance(),
            listWidth,
            (dialog.height - 70).coerceAtLeast(38),
            dialog.top + 38,
            (listWidth - 10).coerceAtLeast(100),
            36,
            entries,
            titleOf = { it.key },
            subtitleOf = { it.value },
            accentOf = { 0xFF62D9FF.toInt() },
            actionsOf = { entry -> listOf(
                RowAction(label = { tr("mods.edit") }, width = 58) {
                    minecraft?.setScreen(DescriptionEntryEditorScreen(this, mod, entry.key, onChanged))
                },
                RowAction(label = { Component.literal("×") }, width = 25, style = { TechButtonStyle.DANGER }) {
                    mod.descriptions = mod.descriptions.orEmpty().toMutableMap().also { it.remove(entry.key) }
                    onChanged()
                    rebuildWidgets()
                },
            ) },
        )
        list.x = dialog.left + 6
        addRenderableWidget(list)
        val add = TechButton.builder(tr("descriptions.add")) {
                minecraft?.setScreen(DescriptionEntryEditorScreen(this, mod, null, onChanged))
            }.style(TechButtonStyle.PRIMARY).bounds(0, 0, compactButtonWidth(tr("descriptions.add")), 18).build()
        val back = TechButton.builder(tr("back")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("back")), 18).build()
        val translate = TechButton.builder(tr("descriptions.translate")) {
            minecraft?.setScreen(AutoTranslationScreen(this, mod, onChanged))
        }.tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("descriptions.translate.hint")))
            .style(TechButtonStyle.SECONDARY)
            .bounds(0, 0, compactButtonWidth(tr("descriptions.translate"), 86), 18).build()
        translate.active = entries.any { it.value.isNotBlank() }
        addCompactActions(dialog.left + 8, dialog.right - 8, dialog.bottom - 23, back, translate, add)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawModalFrame(guiGraphics, dialog, tr("descriptions.subtitle"))
        if (mod.descriptions.orEmpty().isEmpty()) guiGraphics.drawCenteredString(font, tr("descriptions.empty"), width / 2, height / 2, 0x91A4B8)
    }

    override fun onClose() {
        super.onClose()
    }
}

private class AutoTranslationScreen(
    parent: Screen,
    private val mod: RequiredMod,
    private val onChanged: () -> Unit,
) : EditorScreenBase(tr("translation.title"), parent) {
    private lateinit var providerButton: TechButton
    private lateinit var sourceLocale: EditBox
    private lateinit var targetLocale: EditBox
    private lateinit var endpoint: EditBox
    private lateinit var apiKey: EditBox
    private var selectedProvider: TranslationProvider? = null
    private var lastRenderedProvider: TranslationProvider? = null
    private var rememberApiKey: Boolean? = null
    private val sessionKeys = mutableMapOf<String, String>()
    private var resultField: MultiLineEditBox? = null
    private var translatedText: String? = null
    private var loading = false
    private var testingConnection = false
    private var errorMessage: String? = null
    private var previewTop = 0
    private var dialog = EditorRect(0, 0, 0, 0)

    override fun usesModalBackground(): Boolean = true

    override fun init() {
        dialog = centeredModal(700, 420, 280)
        val settings = ConfigManager.loadAuthorSettings()
        val provider = selectedProvider ?: TranslationProvider.fromId(settings.translationProvider)
        selectedProvider = provider
        val languages = mod.descriptions.orEmpty().keys
        val rememberedSource = if (::sourceLocale.isInitialized) sourceLocale.value else null
        val rememberedTarget = if (::targetLocale.isInitialized) targetLocale.value else null
        val rememberedEndpoint = if (::endpoint.isInitialized) endpoint.value else null
        val rememberedKey = if (::apiKey.isInitialized && lastRenderedProvider == provider) apiKey.value else null
        val legacyKey = settings.translationApiKey?.takeIf(String::isNotBlank)
        val persistedKey = settings.translationApiKeys.orEmpty()[provider.id]
            ?: legacyKey?.takeIf { provider == TranslationProvider.LIBRE_TRANSLATE }
        val keyValue = rememberedKey ?: sessionKeys[provider.id] ?: persistedKey.orEmpty()
        if (rememberApiKey == null) {
            rememberApiKey = settings.translationRememberApiKey ?: !persistedKey.isNullOrBlank()
        }
        val defaultSource = rememberedSource
            ?: languages.firstOrNull { it.equals("en_us", true) } ?: languages.firstOrNull().orEmpty()
        val gameLanguage = Minecraft.getInstance().languageManager.selected
        val defaultTarget = rememberedTarget
            ?: gameLanguage.takeUnless { it.equals(defaultSource, true) } ?: "ru_ru"
        val left = dialog.left + 10
        val labelWidth = (dialog.width * .34).toInt().coerceIn(100, 180)
        val fieldX = left + labelWidth
        val fieldWidth = (dialog.right - fieldX - 10).coerceAtLeast(70)
        providerButton = TechButton.builder(providerName(provider)) { switchProvider() }
            .style(TechButtonStyle.SECONDARY)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("translation.provider.hint")))
            .bounds(fieldX, dialog.top + 45, fieldWidth, 20).build().also { addRenderableWidget(it) }
        sourceLocale = stableField(fieldX, dialog.top + 71, fieldWidth, defaultSource, 32)
        targetLocale = stableField(fieldX, dialog.top + 97, fieldWidth, defaultTarget, 32)
        val initialEndpoint = rememberedEndpoint
            ?: settings.translationEndpoint
            ?: TranslationService.DEFAULT_ENDPOINT
        val displayedEndpoint = if (rememberedEndpoint == null) {
            TranslationService.normalizeEndpoint(initialEndpoint) ?: initialEndpoint
        } else {
            initialEndpoint
        }
        endpoint = StableEditBox(font, fieldX, dialog.top + 123, fieldWidth, 20, tr("field")).also {
            it.value = displayedEndpoint
            it.setMaxLength(2048)
            if (provider == TranslationProvider.LIBRE_TRANSLATE) addRenderableWidget(it)
        }
        val keyY = if (provider == TranslationProvider.LIBRE_TRANSLATE) dialog.top + 149 else dialog.top + 123
        apiKey = stableField(
            fieldX, keyY, fieldWidth,
            keyValue,
            512,
        ).also { field ->
            field.setFormatter { value, _ -> FormattedCharSequence.forward("•".repeat(value.length), Style.EMPTY) }
            field.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("translation.api_key.hint")))
        }
        val rememberY = keyY + 26
        val rememberText = tr(if (rememberApiKey == true) "translation.remember_key.on" else "translation.remember_key.off")
        TechButton.builder(rememberText) { button ->
            rememberApiKey = rememberApiKey != true
            button.message = tr(if (rememberApiKey == true) "translation.remember_key.on" else "translation.remember_key.off")
        }.style(TechButtonStyle.GHOST)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("translation.remember_key.hint")))
            .bounds(fieldX, rememberY, fieldWidth, 18).build().also {
                it.active = !loading
                addRenderableWidget(it)
            }
        listOf(providerButton, sourceLocale, targetLocale, endpoint, apiKey).forEach { it.active = !loading }
        previewTop = rememberY + 30

        translatedText?.let { value ->
            resultField = StableMultiLineEditBox(
                font, left, previewTop + 12, dialog.width - 20,
                (dialog.bottom - previewTop - 43).coerceAtLeast(30),
                tr("translation.preview"), tr("translation.preview"),
            ).also {
                it.value = value
                it.setCharacterLimit(8192)
                it.setValueListener { text -> translatedText = text }
                addRenderableWidget(it)
            }
        }
        lastRenderedProvider = provider

        val back = TechButton.builder(tr("cancel")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("cancel")), 18).build()
        val testText = tr(if (testingConnection) "translation.testing" else "translation.test")
        val test = TechButton.builder(testText) { testConnection() }.style(TechButtonStyle.GHOST)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("translation.test.hint")))
            .bounds(0, 0, compactButtonWidth(testText, 78), 18).build()
        test.active = !loading
        val actionText = tr(if (loading && !testingConnection) "translation.loading" else "translation.action")
        val action = TechButton.builder(actionText) { translate() }.style(TechButtonStyle.SECONDARY)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(tr("translation.privacy")))
            .bounds(0, 0, compactButtonWidth(actionText, 82), 18).build()
        action.active = !loading
        val actions = mutableListOf(back, test, action)
        if (translatedText != null) {
            actions += TechButton.builder(tr("translation.save")) { saveTranslation() }.style(TechButtonStyle.PRIMARY)
                .bounds(0, 0, compactButtonWidth(tr("translation.save"), 90), 18).build()
        }
        addCompactActions(dialog.left + 8, dialog.right - 8, dialog.bottom - 23, *actions.toTypedArray())
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawModalFrame(guiGraphics, dialog, tr("translation.subtitle"))
        val provider = selectedProvider ?: TranslationProvider.LIBRE_TRANSLATE
        val fields = buildList<Pair<String, net.minecraft.client.gui.components.AbstractWidget>> {
            add("translation.provider" to providerButton)
            add("translation.source" to sourceLocale)
            add("translation.target" to targetLocale)
            if (provider == TranslationProvider.LIBRE_TRANSLATE) add("translation.endpoint" to endpoint)
            add("translation.api_key" to apiKey)
        }
        fields.forEach { (label, field) ->
            guiGraphics.drawString(
                font, font.plainSubstrByWidth(tr(label).string, (field.x - dialog.left - 24).coerceAtLeast(40)),
                dialog.left + 12, field.y + 6, EditorTheme.TEXT_MUTED, false,
            )
        }
        if (translatedText != null) {
            guiGraphics.drawString(font, tr("translation.preview"), dialog.left + 11, previewTop, EditorTheme.TEXT_MUTED, false)
        }
        errorMessage?.let { error ->
            font.split(Component.literal(error), dialog.width - 28).take(2).forEachIndexed { index, line ->
                guiGraphics.drawString(font, line, dialog.left + 14, dialog.bottom - 52 + index * 10, 0xFFFF7777.toInt(), false)
            }
        }
    }

    private fun translate() {
        if (loading) return
        val provider = selectedProvider ?: TranslationProvider.LIBRE_TRANSLATE
        val sourceCode = sourceLocale.value.trim().lowercase()
        val sourceText = mod.descriptions.orEmpty().entries
            .firstOrNull { it.key.equals(sourceCode, true) }?.value.orEmpty()
        val targetCode = targetLocale.value.trim().lowercase()
        val endpointValue = if (provider == TranslationProvider.LIBRE_TRANSLATE) {
            TranslationService.normalizeEndpoint(endpoint.value) ?: endpoint.value.trim()
        } else {
            endpoint.value.trim()
        }
        val keyValue = apiKey.value.trim()
        endpoint.value = endpointValue
        persistSettings(provider, endpointValue, keyValue)
        loading = true
        testingConnection = false
        translatedText = null
        resultField = null
        errorMessage = null
        rebuildWidgets()
        TranslationService.translateAsync(provider, endpointValue, keyValue, sourceText, sourceCode, targetCode)
            .whenComplete { result, exception ->
                Minecraft.getInstance().execute {
                    loading = false
                    if (Minecraft.getInstance().screen !== this) return@execute
                    if (result != null) {
                        translatedText = result
                        errorMessage = null
                    } else {
                        errorMessage = exception?.cause?.message ?: exception?.message ?: tr("error.unknown").string
                    }
                    rebuildWidgets()
                }
            }
    }

    private fun testConnection() {
        if (loading) return
        val provider = selectedProvider ?: TranslationProvider.LIBRE_TRANSLATE
        val endpointValue = if (provider == TranslationProvider.LIBRE_TRANSLATE) {
            TranslationService.normalizeEndpoint(endpoint.value) ?: endpoint.value.trim()
        } else {
            endpoint.value.trim()
        }
        val keyValue = apiKey.value.trim()
        endpoint.value = endpointValue
        persistSettings(provider, endpointValue, keyValue)
        loading = true
        testingConnection = true
        errorMessage = null
        rebuildWidgets()
        TranslationService.testConnectionAsync(provider, endpointValue, keyValue)
            .whenComplete { _, exception ->
                Minecraft.getInstance().execute {
                    loading = false
                    testingConnection = false
                    if (Minecraft.getInstance().screen !== this) return@execute
                    if (exception == null) {
                        errorMessage = null
                        SystemToast.add(
                            Minecraft.getInstance().toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                            tr("translation.connection_ok"), providerName(provider),
                        )
                    } else {
                        errorMessage = exception.cause?.message ?: exception.message ?: tr("error.unknown").string
                    }
                    rebuildWidgets()
                }
            }
    }

    private fun persistSettings(provider: TranslationProvider, endpointValue: String, keyValue: String) {
        sessionKeys[provider.id] = keyValue
        val currentSettings = ConfigManager.loadAuthorSettings()
        val storedKeys = currentSettings.translationApiKeys.orEmpty().toMutableMap().also { keys ->
            currentSettings.translationApiKey?.takeIf(String::isNotBlank)?.let {
                keys.putIfAbsent(TranslationProvider.LIBRE_TRANSLATE.id, it)
            }
            if (rememberApiKey == true) {
                if (keyValue.isBlank()) keys.remove(provider.id) else keys[provider.id] = keyValue
            } else {
                keys.clear()
            }
        }
        ConfigManager.saveAuthorSettings(
            currentSettings.copy(
                translationProvider = provider.id,
                translationEndpoint = endpointValue,
                translationApiKeys = storedKeys.takeIf { it.isNotEmpty() },
                translationRememberApiKey = rememberApiKey == true,
                translationApiKey = null,
            )
        )
    }

    private fun switchProvider() {
        if (loading) return
        lastRenderedProvider?.let { sessionKeys[it.id] = apiKey.value }
        val current = selectedProvider ?: TranslationProvider.LIBRE_TRANSLATE
        selectedProvider = TranslationProvider.entries[(current.ordinal + 1) % TranslationProvider.entries.size]
        translatedText = null
        resultField = null
        errorMessage = null
        rebuildWidgets()
    }

    private fun providerName(provider: TranslationProvider): Component =
        tr("translation.provider.${provider.id}")

    private fun saveTranslation() {
        val locale = targetLocale.value.trim().lowercase()
        val value = translatedText?.trim().orEmpty()
        if (locale.isBlank() || value.isBlank()) return
        mod.descriptions = mod.descriptions.orEmpty().toMutableMap().also { it[locale] = value }
        onChanged()
        SystemToast.add(
            Minecraft.getInstance().toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
            tr("translation.done"), Component.literal(locale),
        )
        minecraft?.setScreen(previousScreen)
    }

    private fun stableField(x: Int, y: Int, width: Int, value: String, maximum: Int): EditBox =
        StableEditBox(font, x, y, width, 20, tr("field")).also {
            it.value = value
            it.setMaxLength(maximum)
            addRenderableWidget(it)
        }

    override fun onClose() {
        if (!loading) super.onClose()
    }
}

class DescriptionEntryEditorScreen(
    parent: Screen,
    private val mod: RequiredMod,
    private val originalLocale: String?,
    private val onChanged: () -> Unit = {},
) : EditorScreenBase(tr("description.title"), parent) {
    private lateinit var localeField: EditBox
    private lateinit var textField: MultiLineEditBox
    private var dialog = EditorRect(0, 0, 0, 0)

    override fun usesModalBackground(): Boolean = true

    override fun init() {
        dialog = centeredModal(650, 360, 220)
        val innerLeft = dialog.left + 9
        val innerWidth = dialog.width - 18
        localeField = StableEditBox(font, innerLeft, dialog.top + 55, innerWidth, 18, tr("description.locale")).also {
            it.value = originalLocale ?: "en_us"; it.setMaxLength(32); addRenderableWidget(it)
        }
        textField = StableMultiLineEditBox(font, innerLeft, dialog.top + 91, innerWidth, (dialog.bottom - dialog.top - 124).coerceAtLeast(42), tr("description.text"), tr("description.text")).also {
            it.value = originalLocale?.let { code -> mod.descriptions.orEmpty()[code] }.orEmpty()
            it.setCharacterLimit(8192)
            addRenderableWidget(it)
        }
        val save = TechButton.builder(tr("save_back")) { save() }.style(TechButtonStyle.PRIMARY)
            .bounds(0, 0, compactButtonWidth(tr("save_back"), 90), 18).build()
        val cancel = TechButton.builder(tr("cancel")) { onClose() }.style(TechButtonStyle.GHOST)
            .bounds(0, 0, compactButtonWidth(tr("cancel")), 18).build()
        addCompactActions(dialog.left + 8, dialog.right - 8, dialog.bottom - 23, cancel, save)
    }

    override fun renderEditorContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawModalFrame(guiGraphics, dialog, tr("description.subtitle"))
        guiGraphics.drawString(font, tr("description.locale"), localeField.x, localeField.y - 12, 0x90A7BC, false)
        guiGraphics.drawString(font, tr("description.text"), textField.x, textField.y - 12, 0x90A7BC, false)
    }

    private fun save() {
        val locale = localeField.value.trim().lowercase()
        if (locale.isNotBlank()) {
            val mutable = mod.descriptions.orEmpty().toMutableMap()
            originalLocale?.let(mutable::remove)
            mutable[locale] = textField.value
            mod.descriptions = mutable
            onChanged()
        }
        onClose()
    }
}
