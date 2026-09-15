package org.bmp.cph.client.editor

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.bmp.cph.client.MissingModDetector
import org.bmp.cph.client.MissingModsScreen
import org.bmp.cph.client.download.InstallationHistoryScreen
import org.bmp.cph.config.ConfigValidator
import org.bmp.cph.config.DownloadConfig
import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.DownloadSourceType
import org.bmp.cph.config.IssueSeverity
import org.bmp.cph.config.LanguageConfig
import org.bmp.cph.config.LanguageMode
import org.bmp.cph.config.MenuText
import org.bmp.cph.config.MenuTextResolver
import org.bmp.cph.config.ModCategory
import org.bmp.cph.config.PackInfo
import org.bmp.cph.config.RequiredMod
import org.bmp.cph.config.ShowPolicy
import org.lwjgl.glfw.GLFW
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * A desktop-like editor surface. Editor tools live in independent in-game windows instead of replacing
 * the current Screen. The window manager deliberately owns input dispatch: Minecraft's Screen child list
 * cannot express z-order, minimized children, clipping or independent focus correctly.
 */
open class EditorWorkspaceScreen(
    protected val parentScreen: Screen,
    internal val editorSession: EditorSession = EditorSession.open(),
) : Screen(tr("workspace.title")) {
    private val windows = mutableListOf<WorkspaceWindow>()
    private val taskWindows = mutableListOf<WorkspaceWindow>()
    private var activePointer: PointerOperation? = null
    private var activeTaskDrag: TaskbarDrag? = null
    private var initialized = false
    private var exitConfirmation = false
    private var lastWidth = 0
    private var lastHeight = 0
    private var resizingViewport = false
    private val sharedLayouts = mutableMapOf<WorkspaceWindow, WorkspaceWindowLayout>()

    override fun init() {
        if (!initialized) {
            initialized = true
            if (windows.isEmpty()) {
                openWindow(OverviewWindow(this), center = true)
            } else {
                windows.forEachIndexed { index, window ->
                    window.centerIn(workArea(), index)
                    window.constrainTo(workArea())
                    window.rebuild()
                    rememberLayout(window)
                }
            }
        } else if (!resizingViewport) {
            windows.forEach {
                it.constrainTo(workArea())
                rememberLayout(it)
            }
        }
        if (!resizingViewport) {
            lastWidth = width
            lastHeight = height
        }
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        val oldWidth = lastWidth
        val oldHeight = lastHeight
        val oldArea = if (oldWidth > 0 && oldHeight > 0) workArea(oldWidth, oldHeight) else null
        val layouts = windows.associateWith { window ->
            sharedLayouts[window] ?: window.captureLayout(oldArea ?: workArea())
        }
        resizingViewport = true
        try {
            super.resize(minecraft, width, height)
        } finally {
            resizingViewport = false
        }
        val newArea = workArea(width, height)
        windows.forEach { window ->
            window.restoreLayout(layouts.getValue(window), newArea)
        }
        lastWidth = width
        lastHeight = height
    }

    override fun tick() {
        super.tick()
        val client = minecraft ?: return
        if (client.window.guiScaledWidth != width || client.window.guiScaledHeight != height) {
            resize(client, client.window.guiScaledWidth, client.window.guiScaledHeight)
        }
        windows.forEach(WorkspaceWindow::tick)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderWorkspaceBackground(guiGraphics)
        renderRail(guiGraphics, mouseX, mouseY)
        val visibleWindows = windows.filterNot(WorkspaceWindow::minimized)
        val topVisible = visibleWindows.lastOrNull()
        visibleWindows.forEachIndexed { index, window ->
            guiGraphics.pose().pushPose()
            try {
                guiGraphics.pose().translate(0.0f, 0.0f, (index + 1) * WINDOW_LAYER_STEP)
                window.render(
                    guiGraphics, mouseX, mouseY, partialTick, window === topVisible,
                    visibleWindows.subList(index + 1, visibleWindows.size).map(WorkspaceWindow::bounds),
                )
                // Finish this depth layer before moving on so delayed glyphs retain both their scissor and
                // their z-order instead of appearing above a newer window.
                guiGraphics.flush()
            } finally {
                guiGraphics.pose().popPose()
            }
        }
        guiGraphics.pose().pushPose()
        try {
            guiGraphics.pose().translate(0.0f, 0.0f, (visibleWindows.size + 1) * WINDOW_LAYER_STEP)
            renderTaskbar(guiGraphics, mouseX, mouseY)
            renderStatus(guiGraphics)
            renderWorkspaceActions(guiGraphics, mouseX, mouseY)
            if (exitConfirmation) renderExitConfirmation(guiGraphics, mouseX, mouseY)
            guiGraphics.flush()
        } finally {
            guiGraphics.pose().popPose()
        }
        updateCursor(mouseX, mouseY)
    }

    private fun renderWorkspaceBackground(graphics: GuiGraphics) {
        graphics.fillGradient(0, 0, width, height, EditorTheme.BACKGROUND_TOP, EditorTheme.BACKGROUND_BOTTOM)
        graphics.fill(0, 0, width, TOP_HEIGHT, EditorTheme.TOP_BAR)
        graphics.fill(0, TOP_HEIGHT - 1, width, TOP_HEIGHT, EditorTheme.BORDER_SOFT)
        graphics.fill(0, TOP_HEIGHT, RAIL_WIDTH, height - TASKBAR_HEIGHT, 0xF0121418.toInt())
        graphics.fill(RAIL_WIDTH - 1, TOP_HEIGHT, RAIL_WIDTH, height - TASKBAR_HEIGHT, EditorTheme.BORDER_SOFT)
        graphics.fill(0, height - TASKBAR_HEIGHT, width, height, 0xFA101216.toInt())
        graphics.fill(0, height - TASKBAR_HEIGHT, width, height - TASKBAR_HEIGHT + 1, EditorTheme.BORDER_SOFT)

        val time = Util.getMillis() / 110L
        val area = workArea()
        repeat(8) { index ->
            val x = area.left + ((index * 139L + time * (index % 3 + 1)) % max(1, area.width)).toInt()
            val y = area.top + ((index * 79L + time / (index % 2 + 2)) % max(1, area.height)).toInt()
            graphics.fill(x, y, x + 1, y + 1, 0x123F5662)
        }
        graphics.drawString(font, title, 8, 8, EditorTheme.TEXT, false)
        val packName = editorSession.config.pack?.name?.takeIf(String::isNotBlank) ?: tr("hub.untitled").string
        val packX = 8 + font.width(title) + 18
        val available = statusLeft(statusText()) - 10 - packX
        if (available >= 24) {
            graphics.fill(packX - 9, 7, packX - 8, 17, EditorTheme.BORDER)
            graphics.drawString(font, font.plainSubstrByWidth(packName, available), packX, 8, EditorTheme.TEXT_MUTED, false)
        }
    }

    private data class RailItem(val glyph: String, val hint: Component, val action: () -> Unit)

    private fun railItems(): List<RailItem> = listOf(
        RailItem("⌂", tr("workspace.overview.hint")) { openOrFocus("overview") { OverviewWindow(this) } },
        RailItem("⚙", tr("general.hint")) { openOrFocus("general") { GeneralWindow(this) } },
        RailItem("▦", tr("mods.hint")) { openOrFocus("mods") { ModsWindow(this) } },
        RailItem("文", tr("translations.hint")) { openOrFocus("translations") { MenuTranslationsWindow(this) } },
        RailItem("M", tr("scan.modrinth.hint")) { openOrFocus("scan:modrinth") { ScanToolWindow(this, ScanPlatform.MODRINTH) } },
        RailItem("C", tr("scan.curseforge.hint")) { openOrFocus("scan:curseforge") { ScanToolWindow(this, ScanPlatform.CURSEFORGE) } },
        RailItem("↶", tr("history.hint")) { minecraft?.setScreen(InstallationHistoryScreen(this)) },
    )

    private fun renderRail(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        railItems().forEachIndexed { index, item ->
            val rect = railRect(index)
            val hovered = rect.contains(mouseX, mouseY)
            if (hovered) fillRoundedRect(graphics, rect.left, rect.top, rect.right, rect.bottom, 5, EditorTheme.SURFACE_HOVER)
            graphics.drawCenteredString(font, item.glyph, (rect.left + rect.right) / 2, rect.top + 7, if (hovered) EditorTheme.ACCENT else EditorTheme.TEXT_MUTED)
            if (hovered) setTooltipForNextRenderPass(item.hint)
        }
    }

    private fun renderTaskbar(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        taskbarLayout().forEach { (window, rect) ->
            val hovered = rect.contains(mouseX, mouseY)
            val focused = !window.minimized && windows.lastOrNull() === window
            drawRoundedOutline(
                graphics, rect.left, rect.top, rect.right, rect.bottom, 4,
                if (focused) EditorTheme.ACCENT else if (hovered) 0xFF515761.toInt() else EditorTheme.BORDER,
                if (hovered) EditorTheme.SURFACE_HOVER else EditorTheme.SURFACE,
            )
            val pinRect = taskbarPinRect(rect)
            val closeRect = taskbarCloseRect(rect)
            val pinHovered = pinRect.contains(mouseX, mouseY)
            val closeHovered = closeRect.contains(mouseX, mouseY)
            if (pinHovered) fillRoundedRect(graphics, pinRect.left, pinRect.top, pinRect.right, pinRect.bottom, 3, EditorTheme.SURFACE_HOVER)
            if (closeHovered) fillRoundedRect(graphics, closeRect.left, closeRect.top, closeRect.right, closeRect.bottom, 3, 0xFF63363D.toInt())
            graphics.drawCenteredString(font, if (window.pinned) "◆" else "◇", (pinRect.left + pinRect.right) / 2, pinRect.top + 5, if (window.pinned) EditorTheme.ACCENT else EditorTheme.TEXT_MUTED)
            graphics.drawCenteredString(font, "×", (closeRect.left + closeRect.right) / 2, closeRect.top + 5, if (closeHovered) EditorTheme.TEXT else EditorTheme.TEXT_MUTED)
            val labelLeft = pinRect.right + 2
            val labelWidth = (closeRect.left - labelLeft - 2).coerceAtLeast(0)
            if (labelWidth > 4) graphics.drawString(font, font.plainSubstrByWidth(window.title.string, labelWidth), labelLeft, rect.top + 5, EditorTheme.TEXT, false)
            when {
                pinHovered -> setTooltipForNextRenderPass(tr(if (window.pinned) "workspace.taskbar.unpin" else "workspace.taskbar.pin"))
                closeHovered -> setTooltipForNextRenderPass(tr("workspace.taskbar.close"))
                hovered -> setTooltipForNextRenderPass(Component.empty().append(window.title).append("\n").append(tr("workspace.taskbar.drag")))
            }
        }
    }

    private fun renderStatus(graphics: GuiGraphics) {
        val status = statusText()
        val color = if (hasUnsavedChanges()) 0xFFD8B36A.toInt() else 0xFF86A891.toInt()
        val left = statusLeft(status)
        if (left > RAIL_WIDTH) graphics.drawString(font, status, left, 8, color, false)
    }

    private fun statusText(): Component = when {
        windows.any(WorkspaceWindow::hasDraftChanges) -> tr("workspace.unsaved_windows")
        editorSession.dirty -> tr("workspace.config_unsaved")
        else -> tr("workspace.saved")
    }

    private fun hasUnsavedChanges(): Boolean = editorSession.dirty || windows.any(WorkspaceWindow::hasDraftChanges)

    private fun statusLeft(status: Component): Int = workspaceSaveRect().left - font.width(status) - 7

    private fun renderWorkspaceActions(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        listOf(
            Triple(workspaceSaveRect(), "✓", tr("workspace.save_config.hint")),
            // Keep the workspace exit visually distinct from a window's close button.
            Triple(workspaceExitRect(), "↪", tr("workspace.exit_action.hint")),
        ).forEach { (rect, glyph, tooltip) ->
            val hovered = !exitConfirmation && rect.contains(mouseX, mouseY)
            drawRoundedOutline(
                graphics, rect.left, rect.top, rect.right, rect.bottom, 4,
                if (hovered) EditorTheme.ACCENT else EditorTheme.BORDER,
                if (hovered) EditorTheme.SURFACE_HOVER else 0xFF17191D.toInt(),
            )
            graphics.drawCenteredString(font, glyph, (rect.left + rect.right) / 2, rect.top + 5, EditorTheme.TEXT)
            if (hovered) setTooltipForNextRenderPass(tooltip)
        }
    }

    private fun renderExitConfirmation(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        graphics.fill(0, 0, width, height, 0x99080A0D.toInt())
        val dialog = exitDialog()
        drawRoundedOutline(graphics, dialog.left, dialog.top, dialog.right, dialog.bottom, 7, EditorTheme.BORDER, EditorTheme.SURFACE)
        graphics.drawString(font, tr("workspace.exit.title"), dialog.left + 12, dialog.top + 12, EditorTheme.TEXT, false)
        font.split(tr("workspace.exit.hint"), dialog.width - 24).take(3).forEachIndexed { index, line ->
            graphics.drawString(font, line, dialog.left + 12, dialog.top + 30 + index * 11, EditorTheme.TEXT_MUTED, false)
        }
        exitButtons().forEach { (rect, label, style) ->
            val hovered = rect.contains(mouseX, mouseY)
            val outline = when (style) {
                TechButtonStyle.PRIMARY -> EditorTheme.ACCENT
                TechButtonStyle.DANGER -> 0xFFB96E78.toInt()
                else -> EditorTheme.BORDER
            }
            drawRoundedOutline(graphics, rect.left, rect.top, rect.right, rect.bottom, 4, if (hovered) outline else EditorTheme.BORDER, if (hovered) EditorTheme.SURFACE_HOVER else 0xFF17191D.toInt())
            graphics.drawCenteredString(font, label, (rect.left + rect.right) / 2, rect.top + 6, EditorTheme.TEXT)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (exitConfirmation) return handleExitClick(mouseX.toInt(), mouseY.toInt(), button)
        if (workspaceSaveRect().contains(mouseX, mouseY)) {
            if (button == 0) saveConfiguration()
            return true
        }
        if (workspaceExitRect().contains(mouseX, mouseY)) {
            if (button == 0) onClose()
            return true
        }
        railItems().forEachIndexed { index, item ->
            if (railRect(index).contains(mouseX.toInt(), mouseY.toInt())) {
                item.action(); return true
            }
        }
        taskbarHit(mouseX.toInt(), mouseY.toInt())?.let { hit ->
            if (button == 2 && hit.region == TaskbarRegion.BODY) {
                closeFromTaskbar(hit.window)
                return true
            }
            if (button != 0) return true
            when (hit.region) {
                TaskbarRegion.PIN -> hit.window.pinned = !hit.window.pinned
                TaskbarRegion.CLOSE -> closeFromTaskbar(hit.window)
                TaskbarRegion.BODY -> {
                    hit.window.minimized = false
                    focus(hit.window)
                    if (!hit.window.pinned) activeTaskDrag = TaskbarDrag(hit.window, mouseX, mouseY)
                }
            }
            return true
        }
        val window = windows.asReversed().firstOrNull { !it.minimized && it.contains(mouseX, mouseY) }
        if (window != null) {
            focus(window)
            val edge = window.resizeEdge(mouseX, mouseY)
            if (edge != ResizeEdge.NONE && !window.maximized) {
                activePointer = PointerOperation.Resize(window, edge, mouseX, mouseY, window.bounds.copy())
                return true
            }
            val chrome = window.chromeHit(mouseX, mouseY)
            when (chrome) {
                WindowChrome.CLOSE -> window.requestClose()
                WindowChrome.MINIMIZE -> {
                    window.minimized = true
                    windows.lastOrNull { !it.minimized }?.let(::focus)
                }
                WindowChrome.MAXIMIZE -> {
                    window.toggleMaximize(workArea())
                    rememberLayout(window)
                }
                WindowChrome.TITLE -> {
                    if (window.acceptTitleClick()) {
                        window.toggleMaximize(workArea())
                        rememberLayout(window)
                    }
                    else activePointer = PointerOperation.Move(window, mouseX, mouseY, window.bounds.left, window.bounds.top)
                }
                else -> if (window.mouseClicked(mouseX, mouseY, button)) return true
            }
            return true
        }
        return false
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        activeTaskDrag?.let { drag ->
            if (!drag.window.pinned && kotlin.math.abs(mouseX - drag.startMouseX) >= 3.0) reorderTaskWindow(drag.window, mouseX.toInt(), mouseY.toInt())
            return true
        }
        when (val operation = activePointer) {
            is PointerOperation.Move -> {
                operation.window.moveTo(
                    operation.startLeft + (mouseX - operation.startMouseX).toInt(),
                    operation.startTop + (mouseY - operation.startMouseY).toInt(),
                    workArea(),
                )
                return true
            }
            is PointerOperation.Resize -> {
                operation.window.resizeFrom(operation.edge, operation.original, mouseX - operation.startMouseX, mouseY - operation.startMouseY, workArea())
                return true
            }
            null -> Unit
        }
        return windows.lastOrNull { !it.minimized }?.mouseDragged(mouseX, mouseY, button, dragX, dragY) == true
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (activeTaskDrag != null) {
            activeTaskDrag = null
            return true
        }
        if (activePointer != null) {
            val changedWindow = when (val operation = activePointer) {
                is PointerOperation.Move -> operation.window
                is PointerOperation.Resize -> operation.window
                null -> null
            }
            activePointer = null
            changedWindow?.let(::rememberLayout)
            return true
        }
        return windows.lastOrNull { !it.minimized }?.mouseReleased(mouseX, mouseY, button) == true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean =
        windows.asReversed().firstOrNull { !it.minimized && it.contains(mouseX, mouseY) }
            ?.mouseScrolled(mouseX, mouseY, scrollX, scrollY) == true

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (exitConfirmation) {
            if (keyCode == 256) exitConfirmation = false
            return true
        }
        if (keyCode == 256) {
            windows.lastOrNull { !it.minimized }?.requestClose() ?: onClose()
            return true
        }
        if (hasControlDown() && keyCode == 83) {
            saveConfiguration()
            return true
        }
        if (hasControlDown() && keyCode == 87) {
            windows.lastOrNull { !it.minimized }?.requestClose()
            return true
        }
        return windows.lastOrNull { !it.minimized }?.keyPressed(keyCode, scanCode, modifiers)
            ?: super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean =
        windows.lastOrNull { !it.minimized }?.charTyped(codePoint, modifiers)
            ?: super.charTyped(codePoint, modifiers)

    override fun onClose() {
        if (hasUnsavedChanges()) exitConfirmation = true
        else minecraft?.setScreen(parentScreen)
    }

    override fun removed() {
        super.removed()
        WorkspaceCursors.apply(minecraft ?: return, WorkspaceCursor.ARROW)
    }

    internal fun openWindow(window: WorkspaceWindow, center: Boolean = false) {
        if (!initialized || width <= 0 || height <= 0) {
            windows += window
            taskWindows += window
            return
        }
        if (center) window.centerIn(workArea(), windows.size)
        window.constrainTo(workArea())
        windows += window
        taskWindows += window
        window.rebuild()
        rememberLayout(window)
    }

    internal fun openOrFocus(key: String, factory: () -> WorkspaceWindow) {
        windows.firstOrNull { it.key == key }?.let {
            it.minimized = false
            focus(it)
            return
        }
        openWindow(factory(), center = true)
    }

    internal fun openMod(index: Int? = null, section: String? = null) {
        val original = index?.let { editorSession.config.activeModEntries().getOrNull(it) }
        original?.let { mod ->
            windows.filterIsInstance<ModDocumentWindow>().firstOrNull { it.edits(mod) }?.let {
                it.minimized = false
                it.openSection(section)
                focus(it)
                return
            }
        }
        val key = original?.let { "mod:${System.identityHashCode(it)}" } ?: "mod:new:${UUID.randomUUID()}"
        openOrFocus(key) { ModDocumentWindow(this, key, original).also { it.openSection(section) } }
    }

    internal fun openIssue(issue: org.bmp.cph.config.ConfigIssue) {
        val modMatch = Regex("^(?:mods|requiredMods)\\[(\\d+)](?:\\.(.*))?$").matchEntire(issue.path)
        if (modMatch != null) {
            val index = modMatch.groupValues[1].toIntOrNull()
            if (index != null) openMod(index, modMatch.groupValues.getOrElse(2) { "" })
            else openOrFocus("mods") { ModsWindow(this) }
            return
        }
        Regex("^menu\\.translations\\.([^.]+)").find(issue.path)?.groupValues?.getOrNull(1)?.let { locale ->
            openOrFocus("translations") { MenuTranslationsWindow(this) }
            windows.filterIsInstance<MenuTranslationsWindow>().firstOrNull()?.focusLocale(locale)
            return
        }
        when {
            issue.path == "mods" || issue.path == "requiredMods" -> openOrFocus("mods") { ModsWindow(this) }
            issue.path.startsWith("menu.translations") -> openOrFocus("translations") { MenuTranslationsWindow(this) }
            issue.path.startsWith("pack") || issue.path.startsWith("showPolicy") || issue.path.startsWith("showOnlyOnce") ||
                issue.path.startsWith("menu.language") || issue.path.startsWith("downloads") || issue.path == "schemaVersion" ->
                openOrFocus("general") { GeneralWindow(this) }
        }
    }

    internal fun closeWindow(window: WorkspaceWindow) = window.requestClose()

    internal fun removeWindow(window: WorkspaceWindow) {
        windows.remove(window)
        taskWindows.remove(window)
        sharedLayouts.remove(window)
        windows.lastOrNull { !it.minimized }?.focused = true
    }

    internal fun hasWindow(window: WorkspaceWindow): Boolean = window in windows

    internal fun modsChanged() {
        windows.filterIsInstance<ModsWindow>().forEach(ModsWindow::refresh)
    }

    internal fun saveConfiguration(): Boolean {
        // Commit from the topmost child tool towards its owner. A translation window can make the mod
        // document below it dirty while being committed, so the dirty check must happen during iteration.
        windows.asReversed().toList().forEach { window ->
            if (window.hasDraftChanges()) window.commitShortcut()
        }
        val issues = editorSession.save()
        val errors = issues.filter { it.severity == IssueSeverity.ERROR }
        if (errors.isNotEmpty()) {
            openOrFocus("validation") { ValidationWindow(this, errors) }
            return false
        }
        Minecraft.getInstance().let {
            SystemToast.add(it.toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, tr("saved"), tr("saved.hint"))
        }
        return true
    }

    internal fun previewRequirements() {
        val issues = ConfigValidator.validate(editorSession.config).filter { it.severity == IssueSeverity.ERROR }
        if (issues.isNotEmpty()) {
            openOrFocus("validation") { ValidationWindow(this, issues) }
            return
        }
        val language = Minecraft.getInstance().languageManager.selected
        val text = MenuTextResolver.resolve(editorSession.config.menu, language)
        val results = MissingModDetector.findUnsatisfied(editorSession.config.activeModEntries())
        minecraft?.setScreen(MissingModsScreen(this, results, text))
    }

    private fun focus(window: WorkspaceWindow) {
        windows.forEach { it.focused = false }
        windows.remove(window)
        windows += window
        window.focused = true
    }

    private fun rememberLayout(window: WorkspaceWindow) {
        if (width > 0 && height > 0) sharedLayouts[window] = window.captureLayout(workArea())
    }

    private fun updateCursor(mouseX: Int, mouseY: Int) {
        val client = minecraft ?: return
        val cursor = when {
            exitConfirmation -> if (exitButtons().any { (rect, _, _) -> rect.contains(mouseX, mouseY) }) WorkspaceCursor.HAND else WorkspaceCursor.ARROW
            activeTaskDrag != null -> WorkspaceCursor.MOVE
            activePointer is PointerOperation.Move -> WorkspaceCursor.MOVE
            activePointer is PointerOperation.Resize -> cursorForEdge((activePointer as PointerOperation.Resize).edge)
            railItems().indices.any { railRect(it).contains(mouseX, mouseY) } -> WorkspaceCursor.HAND
            workspaceSaveRect().contains(mouseX, mouseY) || workspaceExitRect().contains(mouseX, mouseY) -> WorkspaceCursor.HAND
            taskbarHit(mouseX, mouseY) != null -> WorkspaceCursor.HAND
            else -> {
                val window = windows.asReversed().firstOrNull { !it.minimized && it.contains(mouseX.toDouble(), mouseY.toDouble()) }
                window?.cursorAt(mouseX.toDouble(), mouseY.toDouble()) ?: WorkspaceCursor.ARROW
            }
        }
        WorkspaceCursors.apply(client, cursor)
    }

    private fun cursorForEdge(edge: ResizeEdge): WorkspaceCursor = when (edge) {
        ResizeEdge.LEFT, ResizeEdge.RIGHT -> WorkspaceCursor.RESIZE_HORIZONTAL
        ResizeEdge.TOP, ResizeEdge.BOTTOM -> WorkspaceCursor.RESIZE_VERTICAL
        ResizeEdge.TOP_LEFT, ResizeEdge.BOTTOM_RIGHT -> WorkspaceCursor.RESIZE_NWSE
        ResizeEdge.TOP_RIGHT, ResizeEdge.BOTTOM_LEFT -> WorkspaceCursor.RESIZE_NESW
        ResizeEdge.NONE -> WorkspaceCursor.ARROW
    }

    private fun workArea(screenWidth: Int = width, screenHeight: Int = height) = EditorRect(
        RAIL_WIDTH + 4, TOP_HEIGHT + 4, max(RAIL_WIDTH + 64, screenWidth - 4), max(TOP_HEIGHT + 64, screenHeight - TASKBAR_HEIGHT - 4),
    )

    private fun workspaceSaveRect() = EditorRect(width - 49, 4, width - 28, 22)

    private fun workspaceExitRect() = EditorRect(width - 24, 4, width - 4, 22)

    private fun railRect(index: Int): EditorRect {
        val top = TOP_HEIGHT + 5 + index * 31
        return EditorRect(5, top, RAIL_WIDTH - 5, top + 27)
    }

    private fun taskbarLayout(): List<Pair<WorkspaceWindow, EditorRect>> {
        var left = RAIL_WIDTH + 5
        val sharedWidth = taskbarItemWidth()
        return taskWindows.mapNotNull { window ->
            val itemWidth = min((font.width(window.title) + 45).coerceAtLeast(58), sharedWidth)
            val rect = EditorRect(left, height - TASKBAR_HEIGHT + 4, min(left + itemWidth, width - 5), height - 4)
            left += itemWidth + 4
            rect.takeIf { it.width >= 34 }?.let { window to it }
        }
    }

    private fun taskbarHit(x: Int, y: Int): TaskbarHit? {
        if (y < height - TASKBAR_HEIGHT) return null
        val (window, rect) = taskbarLayout().firstOrNull { (_, rect) -> rect.contains(x, y) } ?: return null
        val region = when {
            taskbarPinRect(rect).contains(x, y) -> TaskbarRegion.PIN
            taskbarCloseRect(rect).contains(x, y) -> TaskbarRegion.CLOSE
            else -> TaskbarRegion.BODY
        }
        return TaskbarHit(window, region)
    }

    private fun taskbarPinRect(rect: EditorRect) = EditorRect(rect.left + 2, rect.top + 1, min(rect.left + 17, rect.right), rect.bottom - 1)

    private fun taskbarCloseRect(rect: EditorRect) = EditorRect(max(rect.left, rect.right - 17), rect.top + 1, rect.right - 2, rect.bottom - 1)

    private fun taskbarItemWidth(): Int {
        if (taskWindows.isEmpty()) return 150
        val available = (width - RAIL_WIDTH - 10 - (taskWindows.size - 1) * 4).coerceAtLeast(taskWindows.size * 34)
        return (available / taskWindows.size).coerceIn(34, 150)
    }

    private fun closeFromTaskbar(window: WorkspaceWindow) {
        if (window.hasDraftChanges() || window.pinned) {
            window.minimized = false
            focus(window)
        }
        window.requestClose()
    }

    private fun reorderTaskWindow(window: WorkspaceWindow, mouseX: Int, mouseY: Int) {
        if (window.pinned || mouseY < height - TASKBAR_HEIGHT) return
        val target = taskbarLayout().firstOrNull { (_, rect) -> rect.contains(mouseX, mouseY) }?.first ?: return
        if (target === window || target.pinned) return
        val from = taskWindows.indexOf(window)
        val targetIndex = taskWindows.indexOf(target)
        if (from < 0 || targetIndex < 0) return
        val pinnedBefore = (from - 1 downTo 0).firstOrNull { taskWindows[it].pinned } ?: -1
        val pinnedAfter = (from + 1 until taskWindows.size).firstOrNull { taskWindows[it].pinned } ?: taskWindows.size
        if (targetIndex !in (pinnedBefore + 1) until pinnedAfter) return
        taskWindows.removeAt(from)
        val insertion = if (targetIndex > from) targetIndex - 1 else targetIndex
        taskWindows.add(insertion.coerceIn(0, taskWindows.size), window)
    }

    private fun exitDialog(): EditorRect {
        val w = (width - 24).coerceIn(250, 430)
        val h = 126
        return EditorRect((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
    }

    private fun exitButtons(): List<Triple<EditorRect, Component, TechButtonStyle>> {
        val dialog = exitDialog()
        val y = dialog.bottom - 32
        val gap = 5
        val buttonWidth = (dialog.width - 34) / 3
        return listOf(
            Triple(EditorRect(dialog.left + 12, y, dialog.left + 12 + buttonWidth, y + 20), tr("cancel"), TechButtonStyle.GHOST),
            Triple(EditorRect(dialog.left + 17 + buttonWidth, y, dialog.left + 17 + buttonWidth * 2, y + 20), tr("workspace.discard"), TechButtonStyle.DANGER),
            Triple(EditorRect(dialog.left + 22 + buttonWidth * 2, y, dialog.right - 12, y + 20), tr("save"), TechButtonStyle.PRIMARY),
        )
    }

    private fun handleExitClick(x: Int, y: Int, button: Int): Boolean {
        if (button != 0) return true
        exitButtons().forEachIndexed { index, (rect, _, _) ->
            if (!rect.contains(x, y)) return@forEachIndexed
            when (index) {
                0 -> exitConfirmation = false
                1 -> minecraft?.setScreen(parentScreen)
                2 -> {
                    if (saveConfiguration()) minecraft?.setScreen(parentScreen) else exitConfirmation = false
                }
            }
            return true
        }
        return true
    }

    private sealed interface PointerOperation {
        data class Move(val window: WorkspaceWindow, val startMouseX: Double, val startMouseY: Double, val startLeft: Int, val startTop: Int) : PointerOperation
        data class Resize(val window: WorkspaceWindow, val edge: ResizeEdge, val startMouseX: Double, val startMouseY: Double, val original: EditorRect) : PointerOperation
    }

    private data class TaskbarDrag(val window: WorkspaceWindow, val startMouseX: Double, val startMouseY: Double)
    private data class TaskbarHit(val window: WorkspaceWindow, val region: TaskbarRegion)
    private enum class TaskbarRegion { PIN, BODY, CLOSE }

    companion object {
        internal const val TOP_HEIGHT = 26
        internal const val RAIL_WIDTH = 43
        internal const val TASKBAR_HEIGHT = 25
        private const val WINDOW_LAYER_STEP = 20.0f
    }
}

private fun EditorRect.contains(x: Int, y: Int): Boolean = x >= left && x < right && y >= top && y < bottom
private fun EditorRect.contains(x: Double, y: Double): Boolean = x >= left && x < right && y >= top && y < bottom
private fun EditorRect.intersects(other: EditorRect): Boolean =
    left < other.right && right > other.left && top < other.bottom && bottom > other.top
private fun EditorRect.intersection(other: EditorRect): EditorRect? {
    val clipped = EditorRect(max(left, other.left), max(top, other.top), min(right, other.right), min(bottom, other.bottom))
    return clipped.takeIf { it.width > 0 && it.height > 0 }
}

private fun visibleRegions(base: EditorRect, occluders: List<EditorRect>): List<EditorRect> =
    occluders.fold(listOf(base)) { regions, cover ->
        regions.flatMap { region ->
            val overlap = region.intersection(cover) ?: return@flatMap listOf(region)
            buildList {
                if (region.top < overlap.top) add(EditorRect(region.left, region.top, region.right, overlap.top))
                if (overlap.bottom < region.bottom) add(EditorRect(region.left, overlap.bottom, region.right, region.bottom))
                if (region.left < overlap.left) add(EditorRect(region.left, overlap.top, overlap.left, overlap.bottom))
                if (overlap.right < region.right) add(EditorRect(overlap.right, overlap.top, region.right, overlap.bottom))
            }
        }
    }
internal data class WorkspaceWindowLayout(
    val bounds: EditorRect,
    val referenceArea: EditorRect,
    val maximized: Boolean,
    val restoreBounds: EditorRect?,
)
internal enum class WindowChrome { NONE, TITLE, MINIMIZE, MAXIMIZE, CLOSE }
internal enum class ResizeEdge { NONE, LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
internal enum class WorkspaceCursor { ARROW, HAND, TEXT, MOVE, RESIZE_HORIZONTAL, RESIZE_VERTICAL, RESIZE_NWSE, RESIZE_NESW }
private enum class WindowCloseReason { DIRTY, PINNED, PINNED_DIRTY }

private object WorkspaceCursors {
    private val handles by lazy {
        mapOf(
            WorkspaceCursor.ARROW to GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR),
            WorkspaceCursor.HAND to GLFW.glfwCreateStandardCursor(GLFW.GLFW_POINTING_HAND_CURSOR),
            WorkspaceCursor.TEXT to GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR),
            WorkspaceCursor.MOVE to GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_ALL_CURSOR),
            WorkspaceCursor.RESIZE_HORIZONTAL to GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_EW_CURSOR),
            WorkspaceCursor.RESIZE_VERTICAL to GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NS_CURSOR),
            WorkspaceCursor.RESIZE_NWSE to GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR),
            WorkspaceCursor.RESIZE_NESW to GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NESW_CURSOR),
        )
    }
    private var current: WorkspaceCursor? = null

    fun apply(minecraft: Minecraft, cursor: WorkspaceCursor) {
        if (cursor == current) return
        current = cursor
        GLFW.glfwSetCursor(minecraft.window.window, handles[cursor] ?: 0L)
    }
}

internal abstract class WorkspaceWindow(
    protected val workspace: EditorWorkspaceScreen,
    val key: String,
    title: Component,
    initialWidth: Int,
    initialHeight: Int,
    private val minimumWidth: Int = 280,
    private val minimumHeight: Int = 190,
) {
    var title: Component = title
        protected set
    var bounds = EditorRect(0, 0, initialWidth, initialHeight)
        private set
    var minimized = false
    var maximized = false
    var pinned = false
    var focused = true
    protected val widgets = mutableListOf<AbstractWidget>()
    protected val modalWidgets = mutableListOf<AbstractWidget>()
    private var restoreBounds: EditorRect? = null
    private var lastTitleClick = 0L
    private var capturedWidget: AbstractWidget? = null
    private var closeConfirmation: WindowCloseReason? = null
    private val openedAt = Util.getMillis()
    private var focusGlow = 0f

    protected val minecraft: Minecraft get() = Minecraft.getInstance()
    protected val font get() = minecraft.font
    protected val bodyLeft get() = bounds.left + 7
    protected val bodyTop get() = bounds.top + TITLE_HEIGHT + 5
    protected val bodyRight get() = bounds.right - 7
    protected val bodyBottom get() = bounds.bottom - 7
    protected val bodyWidth get() = (bodyRight - bodyLeft).coerceAtLeast(20)
    protected val bodyHeight get() = (bodyBottom - bodyTop).coerceAtLeast(20)

    protected abstract fun buildWidgets()
    protected abstract fun renderBody(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float)
    protected open fun renderModalOverlay(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val reason = closeConfirmation ?: return
        graphics.fill(bounds.left + 1, bounds.top + TITLE_HEIGHT + 1, bounds.right - 1, bounds.bottom - 1, 0xC8080A0D.toInt())
        val dialog = closeDialog()
        drawRoundedOutline(graphics, dialog.left, dialog.top, dialog.right, dialog.bottom, 6, EditorTheme.BORDER, EditorTheme.SURFACE)
        val titleKey = when (reason) {
            WindowCloseReason.DIRTY -> "workspace.close_changes"
            WindowCloseReason.PINNED -> "workspace.close_pinned"
            WindowCloseReason.PINNED_DIRTY -> "workspace.close_pinned_changes"
        }
        graphics.drawString(font, tr(titleKey), dialog.left + 11, dialog.top + 12, EditorTheme.TEXT, false)
        font.split(tr("$titleKey.hint"), dialog.width - 22).take(3).forEachIndexed { index, line ->
            graphics.drawString(font, line, dialog.left + 11, dialog.top + 29 + index * 10, EditorTheme.TEXT_MUTED, false)
        }
    }
    protected open fun isDocumentDirty(): Boolean = false
    fun hasDraftChanges(): Boolean = isDocumentDirty()
    open fun commitShortcut(): Boolean = false
    protected fun markDraftCommitted() {
        closeConfirmation = null
        modalWidgets.clear()
        capturedWidget = null
    }
    protected open fun closeRequested() {
        closeConfirmation = when {
            pinned && isDocumentDirty() -> WindowCloseReason.PINNED_DIRTY
            pinned -> WindowCloseReason.PINNED
            isDocumentDirty() -> WindowCloseReason.DIRTY
            else -> null
        }
        if (closeConfirmation != null) {
            rebuild()
        } else workspace.removeWindow(this)
    }
    protected open fun tickWindow() = Unit

    fun rebuild() {
        widgets.clear()
        modalWidgets.clear()
        capturedWidget = null
        buildWidgets()
        if (closeConfirmation != null) buildCloseConfirmation()
    }

    fun tick() {
        tickWindow()
    }

    fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
        topmost: Boolean,
        occluders: List<EditorRect>,
    ) {
        val shadow = if (topmost) 0x58000000 else 0x38000000
        fillRoundedRect(graphics, bounds.left + 4, bounds.top + 5, bounds.right + 4, bounds.bottom + 5, 8, shadow)
        drawRoundedOutline(
            graphics, bounds.left, bounds.top, bounds.right, bounds.bottom, 7,
            if (topmost) 0xFF4D545E.toInt() else EditorTheme.BORDER,
            0xFF15171B.toInt(),
        )
        graphics.fill(bounds.left + 1, bounds.top + TITLE_HEIGHT, bounds.right - 1, bounds.top + TITLE_HEIGHT + 1, EditorTheme.BORDER_SOFT)
        val opening = ((Util.getMillis() - openedAt) / 190f).coerceIn(0f, 1f)
        val focusTarget = if (topmost) 1f else 0f
        focusGlow += (focusTarget - focusGlow) * .22f
        val accentWidth = ((bounds.width - 2) * opening * focusGlow).toInt()
        if (accentWidth > 0) graphics.fill(bounds.left + 1, bounds.top + TITLE_HEIGHT, bounds.left + 1 + accentWidth, bounds.top + TITLE_HEIGHT + 1, EditorTheme.ACCENT)
        val titleWidth = (bounds.width - 86).coerceAtLeast(0)
        if (titleWidth >= 8) graphics.drawString(font, font.plainSubstrByWidth(title.string, titleWidth), bounds.left + 9, bounds.top + 8, if (topmost) EditorTheme.TEXT else EditorTheme.TEXT_MUTED, false)
        if (isDocumentDirty()) graphics.fill(bounds.left + 5, bounds.top + 10, bounds.left + 7, bounds.top + 12, 0xFFD8B36A.toInt())
        renderChrome(graphics, mouseX, mouseY, occluders)
        val contentBounds = EditorRect(bounds.left + 1, bounds.top + TITLE_HEIGHT + 1, bounds.right - 1, bounds.bottom - 1)
        val pointerCovered = occluders.any { it.contains(mouseX, mouseY) }
        val contentMouseX = if (pointerCovered) HIDDEN_MOUSE else mouseX
        val contentMouseY = if (pointerCovered) HIDDEN_MOUSE else mouseY
        visibleRegions(contentBounds, occluders).forEach { region ->
            graphics.enableScissor(region.left, region.top, region.right, region.bottom)
            try {
                renderBody(graphics, contentMouseX, contentMouseY, partialTick)
                widgets.forEach { it.render(graphics, contentMouseX, contentMouseY, partialTick) }
                if (modalWidgets.isNotEmpty()) {
                    renderModalOverlay(graphics, contentMouseX, contentMouseY, partialTick)
                    modalWidgets.forEach { it.render(graphics, contentMouseX, contentMouseY, partialTick) }
                }
                // Keep every deferred glyph inside the active compositor region. Flushing after the
                // scissor is removed allows button labels from a lower window to appear over a higher one.
                graphics.flush()
            } finally {
                graphics.disableScissor()
            }
        }
    }

    private fun renderChrome(graphics: GuiGraphics, mouseX: Int, mouseY: Int, occluders: List<EditorRect>) {
        val labels = listOf(WindowChrome.MINIMIZE to "–", WindowChrome.MAXIMIZE to if (maximized) "❐" else "□", WindowChrome.CLOSE to "×")
        labels.forEach { (kind, label) ->
            val rect = chromeRect(kind)
            // Font glyphs and solid fills are submitted through different render types. Do not submit a
            // covered control at all: otherwise a delayed glyph batch can appear through a higher window.
            if (occluders.any(rect::intersects)) return@forEach
            val hovered = rect.contains(mouseX, mouseY)
            if (hovered) fillRoundedRect(graphics, rect.left, rect.top, rect.right, rect.bottom, 3, if (kind == WindowChrome.CLOSE) 0xFF63363D.toInt() else EditorTheme.SURFACE_HOVER)
            graphics.drawCenteredString(font, label, (rect.left + rect.right) / 2, rect.top + 5, if (hovered) EditorTheme.TEXT else EditorTheme.TEXT_MUTED)
            if (hovered) workspace.setTooltipForNextRenderPass(
                tr(when (kind) {
                    WindowChrome.MINIMIZE -> "workspace.window.minimize"
                    WindowChrome.MAXIMIZE -> if (maximized) "workspace.window.restore" else "workspace.window.maximize"
                    WindowChrome.CLOSE -> "workspace.window.close"
                    else -> "workspace.window"
                })
            )
        }
    }

    fun chromeHit(mouseX: Double, mouseY: Double): WindowChrome {
        WindowChrome.entries.filter { it !in setOf(WindowChrome.NONE, WindowChrome.TITLE) }.forEach {
            if (chromeRect(it).contains(mouseX, mouseY)) return it
        }
        return if (mouseY >= bounds.top && mouseY < bounds.top + TITLE_HEIGHT && mouseX >= bounds.left && mouseX < bounds.right) WindowChrome.TITLE else WindowChrome.NONE
    }

    private fun chromeRect(kind: WindowChrome): EditorRect {
        val position = when (kind) {
            WindowChrome.CLOSE -> 0
            WindowChrome.MAXIMIZE -> 1
            WindowChrome.MINIMIZE -> 2
            else -> 3
        }
        val right = bounds.right - 4 - position * 20
        return EditorRect(right - 18, bounds.top + 4, right, bounds.top + 21)
    }

    fun acceptTitleClick(): Boolean {
        val now = Util.getMillis()
        val double = now - lastTitleClick < 330
        lastTitleClick = now
        return double
    }

    fun requestClose() = closeRequested()

    fun contains(x: Double, y: Double) = bounds.contains(x, y)

    fun centerIn(area: EditorRect, cascade: Int) {
        val w = min(bounds.width, area.width)
        val h = min(bounds.height, area.height)
        val offset = (cascade % 7) * 12
        val left = (area.left + (area.width - w) / 2 + offset).coerceAtMost(area.right - w)
        val top = (area.top + (area.height - h) / 2 + offset).coerceAtMost(area.bottom - h)
        bounds = EditorRect(left, top, left + w, top + h)
    }

    fun constrainTo(area: EditorRect) {
        if (maximized) {
            applyMaximizedBounds(area)
            return
        }
        val w = bounds.width.coerceIn(min(minimumWidth, area.width), area.width)
        val h = bounds.height.coerceIn(min(minimumHeight, area.height), area.height)
        val left = bounds.left.coerceIn(area.left, max(area.left, area.right - w))
        val top = bounds.top.coerceIn(area.top, max(area.top, area.bottom - h))
        setBounds(EditorRect(left, top, left + w, top + h))
    }

    fun captureLayout(area: EditorRect): WorkspaceWindowLayout =
        WorkspaceWindowLayout(bounds.copy(), area.copy(), maximized, restoreBounds?.copy())

    fun restoreLayout(layout: WorkspaceWindowLayout, area: EditorRect) {
        maximized = layout.maximized
        restoreBounds = layout.restoreBounds?.let { scaleRect(it, layout.referenceArea, area) }
        if (maximized) applyMaximizedBounds(area)
        else {
            setBounds(scaleRect(layout.bounds, layout.referenceArea, area))
            constrainTo(area)
        }
    }

    private fun scaleRect(rect: EditorRect, source: EditorRect, target: EditorRect): EditorRect {
        if (source.width <= 0 || source.height <= 0) return rect.copy()
        fun scaleX(value: Int): Int = target.left + ((value - source.left).toDouble() * target.width / source.width).toInt()
        fun scaleY(value: Int): Int = target.top + ((value - source.top).toDouble() * target.height / source.height).toInt()
        val left = scaleX(rect.left)
        val top = scaleY(rect.top)
        return EditorRect(left, top, max(left + 1, scaleX(rect.right)), max(top + 1, scaleY(rect.bottom)))
    }

    fun moveTo(left: Int, top: Int, area: EditorRect) {
        val x = left.coerceIn(area.left, max(area.left, area.right - bounds.width))
        val y = top.coerceIn(area.top, max(area.top, area.bottom - bounds.height))
        setBounds(EditorRect(x, y, x + bounds.width, y + bounds.height), rebuild = false)
    }

    fun toggleMaximize(area: EditorRect) {
        if (maximized) {
            maximized = false
            restoreBounds?.let { setBounds(it) }
            restoreBounds = null
            constrainTo(area)
        } else {
            restoreBounds = bounds.copy()
            maximized = true
            applyMaximizedBounds(area)
        }
    }

    fun applyMaximizedBounds(area: EditorRect) = setBounds(area)

    fun resizeEdge(x: Double, y: Double): ResizeEdge {
        if (!contains(x, y)) return ResizeEdge.NONE
        val left = x - bounds.left < RESIZE_GRAB
        val right = bounds.right - x <= RESIZE_GRAB
        val top = y - bounds.top < RESIZE_GRAB
        val bottom = bounds.bottom - y <= RESIZE_GRAB
        return when {
            left && top -> ResizeEdge.TOP_LEFT
            right && top -> ResizeEdge.TOP_RIGHT
            left && bottom -> ResizeEdge.BOTTOM_LEFT
            right && bottom -> ResizeEdge.BOTTOM_RIGHT
            left -> ResizeEdge.LEFT
            right -> ResizeEdge.RIGHT
            top -> ResizeEdge.TOP
            bottom -> ResizeEdge.BOTTOM
            else -> ResizeEdge.NONE
        }
    }

    fun cursorAt(x: Double, y: Double): WorkspaceCursor {
        if (!maximized) {
            when (resizeEdge(x, y)) {
                ResizeEdge.LEFT, ResizeEdge.RIGHT -> return WorkspaceCursor.RESIZE_HORIZONTAL
                ResizeEdge.TOP, ResizeEdge.BOTTOM -> return WorkspaceCursor.RESIZE_VERTICAL
                ResizeEdge.TOP_LEFT, ResizeEdge.BOTTOM_RIGHT -> return WorkspaceCursor.RESIZE_NWSE
                ResizeEdge.TOP_RIGHT, ResizeEdge.BOTTOM_LEFT -> return WorkspaceCursor.RESIZE_NESW
                ResizeEdge.NONE -> Unit
            }
        }
        return when (chromeHit(x, y)) {
            WindowChrome.TITLE -> WorkspaceCursor.MOVE
            WindowChrome.MINIMIZE, WindowChrome.MAXIMIZE, WindowChrome.CLOSE -> WorkspaceCursor.HAND
            WindowChrome.NONE -> {
                val inputWidgets = if (modalWidgets.isEmpty()) widgets else modalWidgets
                inputWidgets.asReversed().firstNotNullOfOrNull { cursorForListener(it, x, y) } ?: WorkspaceCursor.ARROW
            }
        }
    }

    private fun cursorForListener(listener: GuiEventListener, x: Double, y: Double): WorkspaceCursor? {
        if (!listener.isMouseOver(x, y)) return null
        if (listener is ContainerEventHandler) {
            listener.children().asReversed().firstNotNullOfOrNull { cursorForListener(it, x, y) }?.let { return it }
        }
        return when (listener) {
            is EditBox, is MultiLineEditBox -> WorkspaceCursor.TEXT
            is AbstractWidget -> if (listener.active) WorkspaceCursor.HAND else WorkspaceCursor.ARROW
            else -> WorkspaceCursor.ARROW
        }
    }

    fun resizeFrom(edge: ResizeEdge, original: EditorRect, deltaX: Double, deltaY: Double, area: EditorRect) {
        var left = original.left
        var right = original.right
        var top = original.top
        var bottom = original.bottom
        if (edge in setOf(ResizeEdge.LEFT, ResizeEdge.TOP_LEFT, ResizeEdge.BOTTOM_LEFT)) left += deltaX.toInt()
        if (edge in setOf(ResizeEdge.RIGHT, ResizeEdge.TOP_RIGHT, ResizeEdge.BOTTOM_RIGHT)) right += deltaX.toInt()
        if (edge in setOf(ResizeEdge.TOP, ResizeEdge.TOP_LEFT, ResizeEdge.TOP_RIGHT)) top += deltaY.toInt()
        if (edge in setOf(ResizeEdge.BOTTOM, ResizeEdge.BOTTOM_LEFT, ResizeEdge.BOTTOM_RIGHT)) bottom += deltaY.toInt()
        val minW = min(minimumWidth, area.width)
        val minH = min(minimumHeight, area.height)
        if (right - left < minW) {
            if (edge in setOf(ResizeEdge.LEFT, ResizeEdge.TOP_LEFT, ResizeEdge.BOTTOM_LEFT)) left = right - minW else right = left + minW
        }
        if (bottom - top < minH) {
            if (edge in setOf(ResizeEdge.TOP, ResizeEdge.TOP_LEFT, ResizeEdge.TOP_RIGHT)) top = bottom - minH else bottom = top + minH
        }
        setBounds(EditorRect(left.coerceAtLeast(area.left), top.coerceAtLeast(area.top), right.coerceAtMost(area.right), bottom.coerceAtMost(area.bottom)))
    }

    private fun setBounds(value: EditorRect, rebuild: Boolean = true) {
        val dx = value.left - bounds.left
        val dy = value.top - bounds.top
        val sizeChanged = value.width != bounds.width || value.height != bounds.height
        bounds = value
        if (sizeChanged && rebuild) rebuild()
        else if (dx != 0 || dy != 0) (widgets + modalWidgets).forEach { widget -> widget.x += dx; widget.y += dy }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val inputWidgets = if (modalWidgets.isEmpty()) widgets else modalWidgets
        // A button action may rebuild or close its window synchronously. Iterate over a snapshot so clearing
        // and repopulating the live widget list from that action cannot invalidate this mouse dispatch.
        val clicked = inputWidgets.asReversed().toList().firstOrNull { widget ->
            widget.visible && widget.mouseClicked(mouseX, mouseY, button)
        }
        if (clicked != null) {
            val currentWidgets = if (modalWidgets.isEmpty()) widgets else modalWidgets
            currentWidgets.forEach { it.isFocused = it === clicked }
            capturedWidget = clicked.takeIf { it in currentWidgets }
            return true
        }
        (if (modalWidgets.isEmpty()) widgets else modalWidgets).forEach { it.isFocused = false }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean =
        capturedWidget?.mouseDragged(mouseX, mouseY, button, dragX, dragY) == true

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val handled = capturedWidget?.mouseReleased(mouseX, mouseY, button) == true
        capturedWidget = null
        return handled
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean =
        (if (modalWidgets.isEmpty()) widgets else modalWidgets).asReversed().firstOrNull { it.visible && it.isMouseOver(mouseX, mouseY) }
            ?.mouseScrolled(mouseX, mouseY, scrollX, scrollY) == true

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean =
        if (keyCode == 258) {
            focusNext(Screen.hasShiftDown())
            true
        } else (if (modalWidgets.isEmpty()) widgets else modalWidgets).firstOrNull { it.isFocused }?.keyPressed(keyCode, scanCode, modifiers) == true

    fun charTyped(codePoint: Char, modifiers: Int): Boolean =
        (if (modalWidgets.isEmpty()) widgets else modalWidgets).firstOrNull { it.isFocused }?.charTyped(codePoint, modifiers) == true

    protected fun add(widget: AbstractWidget): AbstractWidget = widget.also(widgets::add)

    protected fun addModal(widget: AbstractWidget): AbstractWidget = widget.also(modalWidgets::add)

    protected fun drawFittedLine(
        graphics: GuiGraphics,
        text: Component,
        x: Int,
        y: Int,
        width: Int,
        color: Int = EditorTheme.TEXT_MUTED,
    ) {
        if (width >= 4) graphics.drawString(font, font.plainSubstrByWidth(text.string, width), x, y, color, false)
    }

    protected fun drawFittedLine(
        graphics: GuiGraphics,
        text: String,
        x: Int,
        y: Int,
        width: Int,
        color: Int = EditorTheme.TEXT_MUTED,
    ) = drawFittedLine(graphics, Component.literal(text), x, y, width, color)

    protected fun drawWrappedText(
        graphics: GuiGraphics,
        text: Component,
        x: Int,
        top: Int,
        width: Int,
        bottom: Int,
        color: Int = EditorTheme.TEXT_MUTED,
        lineHeight: Int = 11,
        maxLines: Int = Int.MAX_VALUE,
    ) {
        if (width < 4 || bottom - top < font.lineHeight) return
        val visibleLines = min(maxLines, 1 + (bottom - top - font.lineHeight) / lineHeight)
        font.split(text, width).take(visibleLines).forEachIndexed { index, line ->
            graphics.drawString(font, line, x, top + index * lineHeight, color, false)
        }
    }

    protected fun button(
        label: Component,
        x: Int,
        y: Int,
        width: Int,
        action: () -> Unit,
        style: TechButtonStyle = TechButtonStyle.SECONDARY,
        tooltip: Component = label,
    ): TechButton = TechButton.builder(label) { action() }.style(style).tooltip(Tooltip.create(tooltip)).bounds(x, y, width, 18).build().also(::add)

    protected fun modalButton(
        label: Component,
        x: Int,
        y: Int,
        width: Int,
        action: () -> Unit,
        style: TechButtonStyle = TechButtonStyle.SECONDARY,
    ): TechButton = TechButton.builder(label) { action() }.style(style).bounds(x, y, width, 18).build().also(::addModal)

    private fun focusNext(backwards: Boolean) {
        val candidates = (if (modalWidgets.isEmpty()) widgets else modalWidgets).filter { it.visible && it.active }
        if (candidates.isEmpty()) return
        val current = candidates.indexOfFirst { it.isFocused }
        val next = if (backwards) {
            if (current <= 0) candidates.lastIndex else current - 1
        } else {
            if (current < 0 || current == candidates.lastIndex) 0 else current + 1
        }
        candidates.forEachIndexed { index, widget -> widget.isFocused = index == next }
    }

    private fun closeDialog(): EditorRect {
        val w = min(350, bodyWidth - 20)
        val h = 94
        val left = bounds.left + (bounds.width - w) / 2
        val top = bounds.top + (bounds.height - h) / 2
        return EditorRect(left, top, left + w, top + h)
    }

    private fun buildCloseConfirmation() {
        val reason = closeConfirmation ?: return
        val dialog = closeDialog()
        if (reason == WindowCloseReason.PINNED) {
            val buttonWidth = (dialog.width - 30) / 2
            modalButton(tr("workspace.keep_editing"), dialog.left + 10, dialog.bottom - 28, buttonWidth, ::keepEditing, TechButtonStyle.GHOST)
            modalButton(tr("workspace.close_anyway"), dialog.left + 15 + buttonWidth, dialog.bottom - 28, buttonWidth, {
                workspace.removeWindow(this)
            }, TechButtonStyle.DANGER)
            return
        }

        val buttonWidth = (dialog.width - 40) / 3
        modalButton(tr("workspace.keep_editing"), dialog.left + 10, dialog.bottom - 28, buttonWidth, ::keepEditing, TechButtonStyle.GHOST)
        modalButton(tr("workspace.discard"), dialog.left + 15 + buttonWidth, dialog.bottom - 28, buttonWidth, {
            workspace.removeWindow(this)
        }, TechButtonStyle.DANGER)
        modalButton(tr("save"), dialog.left + 20 + buttonWidth * 2, dialog.bottom - 28, buttonWidth, ::saveAndClose, TechButtonStyle.PRIMARY)
    }

    private fun keepEditing() {
        closeConfirmation = null
        rebuild()
    }

    private fun saveAndClose() {
        closeConfirmation = null
        if (!commitShortcut()) {
            rebuild()
            return
        }
        workspace.removeWindow(this)
    }

    protected fun field(x: Int, y: Int, width: Int, value: String, maxLength: Int = 4096, changed: (String) -> Unit): StableEditBox =
        StableEditBox(font, x, y, width, 20, tr("field")).also {
            it.value = value
            it.setMaxLength(maxLength)
            it.setResponder(changed)
            add(it)
        }

    companion object {
        const val TITLE_HEIGHT = 25
        const val RESIZE_GRAB = 5
        private const val HIDDEN_MOUSE = -10_000
    }
}

internal class OverviewWindow(workspace: EditorWorkspaceScreen) : WorkspaceWindow(
    workspace, "overview", tr("workspace.overview"), 470, 285, 330, 220,
) {
    override fun buildWidgets() {
        val buttonWidth = ((bodyWidth - 15) / 2).coerceAtLeast(80)
        val y = bodyBottom - 48
        button(tr("mods"), bodyLeft + 5, y, buttonWidth, action = { workspace.openOrFocus("mods") { ModsWindow(workspace) } })
        button(tr("general"), bodyLeft + 10 + buttonWidth, y, buttonWidth, action = { workspace.openOrFocus("general") { GeneralWindow(workspace) } })
        button(tr("requirements"), bodyLeft + 5, y + 23, buttonWidth, workspace::previewRequirements, TechButtonStyle.GHOST)
        button(tr("workspace.save_config"), bodyLeft + 10 + buttonWidth, y + 23, buttonWidth, { workspace.saveConfiguration() }, TechButtonStyle.PRIMARY, tr("workspace.save_config.hint"))
    }

    override fun renderBody(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val config = workspace.editorSession.config
        val pack = config.pack
        val left = bodyLeft + 8
        val lineWidth = bodyWidth - 16
        graphics.drawString(font, font.plainSubstrByWidth(pack?.name?.takeIf(String::isNotBlank) ?: tr("hub.untitled").string, lineWidth), left, bodyTop + 7, EditorTheme.TEXT, false)
        val identity = listOfNotNull(pack?.id?.takeIf(String::isNotBlank), pack?.version?.takeIf(String::isNotBlank)).joinToString(" · ")
        drawFittedLine(graphics, identity.ifBlank { "—" }, left, bodyTop + 22, lineWidth)
        graphics.fill(left, bodyTop + 40, bodyRight - 8, bodyTop + 41, EditorTheme.BORDER_SOFT)
        val mods = config.activeModEntries().size
        val errors = ConfigValidator.validate(config).count { it.severity == IssueSeverity.ERROR }
        graphics.drawString(font, tr("hub.mods", mods), left, bodyTop + 53, EditorTheme.TEXT_MUTED, false)
        graphics.drawString(font, tr(if (errors == 0) "hub.ready" else "hub.errors", errors), left, bodyTop + 69, if (errors == 0) 0xFF8DAA96.toInt() else 0xFFC58B91.toInt(), false)
        drawWrappedText(graphics, tr("workspace.overview.body"), left, bodyTop + 95, lineWidth, bodyBottom - 53, maxLines = 4)
    }
}

internal class ValidationWindow(workspace: EditorWorkspaceScreen, private val errors: List<org.bmp.cph.config.ConfigIssue>) : WorkspaceWindow(
    workspace, "validation", tr("validation.title"), 520, 310, 320, 210,
) {
    override fun buildWidgets() {
        val text = MenuTextResolver.resolve(workspace.editorSession.config.menu, minecraft.languageManager.selected)
        StyledActionList(
            minecraft, bodyWidth, (bodyHeight - 27).coerceAtLeast(38), bodyTop,
            (bodyWidth - 8).coerceAtLeast(100), 38, errors,
            titleOf = { it.displayPath ?: it.path },
            subtitleOf = { it.localized(text) },
            accentOf = { 0xFFC66A73.toInt() },
            onRowClick = { workspace.openIssue(it) },
            rowTooltipOf = { tr("validation.click_hint") },
        ).also { it.x = bodyLeft; add(it) }
        button(tr("close"), bodyRight - 78, bodyBottom - 21, 74, { workspace.closeWindow(this) }, TechButtonStyle.GHOST)
    }

    override fun renderBody(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (errors.isEmpty()) graphics.drawCenteredString(font, tr("validation.empty"), (bodyLeft + bodyRight) / 2, (bodyTop + bodyBottom) / 2, EditorTheme.TEXT_MUTED)
    }
}

internal class GeneralWindow(workspace: EditorWorkspaceScreen) : WorkspaceWindow(
    workspace, "general", tr("general.title"), 565, 345, 360, 275,
) {
    private val initialPack = workspace.editorSession.config.pack ?: PackInfo()
    private val initialLanguage = workspace.editorSession.ensureMenu().language ?: LanguageConfig()
    private var packId = initialPack.id.orEmpty()
    private var packName = initialPack.name.orEmpty()
    private var packVersion = initialPack.version.orEmpty()
    private var fixedLanguage = initialLanguage.fixedLanguage.orEmpty()
    private var fallbackLanguage = initialLanguage.fallbackLanguage.orEmpty()
    private var showPolicy = workspace.editorSession.config.resolvedShowPolicy()
    private var languageMode = LanguageMode.entries.firstOrNull { it.name.equals(initialLanguage.mode, true) } ?: LanguageMode.GAME
    private var backups = workspace.editorSession.config.downloads?.resolvedMaxBackupBatches() ?: 10
    private var savedDraft = currentDraft()

    override fun isDocumentDirty() = currentDraft() != savedDraft

    override fun commitShortcut(): Boolean {
        apply()
        return true
    }

    override fun buildWidgets() {
        val labelWidth = (bodyWidth * .36).toInt().coerceIn(116, 180)
        val x = bodyLeft + labelWidth
        val fieldWidth = (bodyRight - x - 6).coerceAtLeast(60)
        val top = bodyTop + 8
        field(x, top, fieldWidth, packId, 256) { packId = it }
        field(x, top + 28, fieldWidth, packName, 256) { packName = it }
        field(x, top + 56, fieldWidth, packVersion, 256) { packVersion = it }
        field(x, top + 84, fieldWidth, fixedLanguage, 32) { fixedLanguage = it }
        field(x, top + 112, fieldWidth, fallbackLanguage, 32) { fallbackLanguage = it }
        val buttonY = top + 144
        val third = ((bodyWidth - 14) / 3).coerceAtLeast(60)
        button(tr("general.policy", tr("policy.${showPolicy.name.lowercase()}")), bodyLeft + 2, buttonY, third, {
            showPolicy = ShowPolicy.entries[(showPolicy.ordinal + 1) % ShowPolicy.entries.size]; rebuild()
        })
        button(tr("general.language_mode", tr("language.${languageMode.name.lowercase()}")), bodyLeft + 7 + third, buttonY, third, {
            languageMode = if (languageMode == LanguageMode.GAME) LanguageMode.FIXED else LanguageMode.GAME; rebuild()
        })
        button(tr("general.backups", backups), bodyLeft + 12 + third * 2, buttonY, bodyRight - (bodyLeft + 12 + third * 2), {
            val choices = listOf(5, 10, 20, 50, 100)
            backups = choices[(choices.indexOf(backups).takeIf { it >= 0 } ?: 0).plus(1) % choices.size]; rebuild()
        }, tooltip = tr("general.backups.hint"))
        val footerY = bodyBottom - 20
        button(tr("close"), bodyRight - 168, footerY, 78, { workspace.closeWindow(this) }, TechButtonStyle.GHOST, tr("workspace.close_window.hint"))
        button(tr("save"), bodyRight - 85, footerY, 81, ::apply, TechButtonStyle.PRIMARY, tr("workspace.save_window.hint"))
    }

    override fun renderBody(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val labels = listOf("general.pack_id", "general.pack_name", "general.pack_version", "general.fixed_language", "general.fallback_language")
        labels.forEachIndexed { index, key ->
            graphics.drawString(font, font.plainSubstrByWidth(tr(key).string, (bodyWidth * .34).toInt()), bodyLeft + 6, bodyTop + 14 + index * 28, EditorTheme.TEXT_MUTED, false)
        }
        val subtitleY = bodyBottom - 43
        if (subtitleY >= bodyTop + 178) {
            drawFittedLine(graphics, tr("general.subtitle"), bodyLeft + 5, subtitleY, bodyWidth - 10)
        }
    }

    private fun apply() {
        val config = workspace.editorSession.config
        config.pack = (config.pack ?: PackInfo()).also {
            it.id = packId.trim(); it.name = packName.trim(); it.version = packVersion.trim()
        }
        config.showPolicy = showPolicy.name
        config.downloads = (config.downloads ?: DownloadConfig()).also { it.maxBackupBatches = backups }
        val menu = workspace.editorSession.ensureMenu()
        menu.language = (menu.language ?: LanguageConfig()).also {
            it.mode = languageMode.name
            it.fixedLanguage = fixedLanguage.trim().lowercase().takeIf(String::isNotBlank) ?: "en_us"
            it.fallbackLanguage = fallbackLanguage.trim().lowercase().takeIf(String::isNotBlank) ?: "en_us"
        }
        workspace.editorSession.markDirty()
        savedDraft = currentDraft()
        markDraftCommitted()
    }

    /**
     * A value snapshot makes the window state authoritative: after Apply the exact
     * values on screen are clean, and editing (or reverting) fields is detected
     * without relying on responder ordering or a stale boolean flag.
     */
    private fun currentDraft() = GeneralDraft(
        packId = packId,
        packName = packName,
        packVersion = packVersion,
        fixedLanguage = fixedLanguage,
        fallbackLanguage = fallbackLanguage,
        showPolicy = showPolicy,
        languageMode = languageMode,
        backups = backups,
    )

    private data class GeneralDraft(
        val packId: String,
        val packName: String,
        val packVersion: String,
        val fixedLanguage: String,
        val fallbackLanguage: String,
        val showPolicy: ShowPolicy,
        val languageMode: LanguageMode,
        val backups: Int,
    )
}

internal class ModsWindow(workspace: EditorWorkspaceScreen) : WorkspaceWindow(
    workspace, "mods", tr("mods.title"), 555, 410, 330, 245,
) {
    private enum class SortMode { CONFIGURED, NAME, CATEGORY }
    private var query = ""
    private var sort = SortMode.CONFIGURED
    private lateinit var list: StyledActionList<Pair<Int, RequiredMod>>
    private lateinit var search: StableEditBox

    override fun buildWidgets() {
        val top = bodyTop + 2
        val sortWidth = min(135, max(90, bodyWidth / 4))
        search = field(bodyLeft + 2, top, bodyWidth - sortWidth - 31, query, 256) { value ->
            query = value
            if (::list.isInitialized) list.replaceItems(filtered(), resetScroll = false)
        }
        button(Component.literal("×"), bodyRight - sortWidth - 24, top + 1, 21, {
            search.value = ""; search.isFocused = true
        }, TechButtonStyle.GHOST, tr("mods.search.clear.hint"))
        button(tr("mods.sort.${sort.name.lowercase()}"), bodyRight - sortWidth, top + 1, sortWidth, {
            sort = SortMode.entries[(sort.ordinal + 1) % SortMode.entries.size]; rebuild()
        }, TechButtonStyle.GHOST, tr("mods.sort.hint"))
        list = StyledActionList(
            minecraft, bodyWidth, (bodyHeight - 57).coerceAtLeast(38), top + 27,
            (bodyWidth - 10).coerceAtLeast(100), 39, filtered(),
            titleOf = { it.second.displayName() },
            subtitleOf = { it.second.modId?.takeIf(String::isNotBlank) ?: it.second.filePattern.orEmpty() },
            accentOf = { (_, mod) -> if (mod.enabled == false) 0xFF647386.toInt() else if (mod.resolvedCategory() == ModCategory.REQUIRED) 0xFFE46A6A.toInt() else 0xFFE0B85B.toInt() },
            iconOf = { it.second.iconUrl },
            actionsOf = { (index, _) -> listOf(
                RowAction(label = { Component.literal("⧉") }, width = 24, tooltip = tr("mods.duplicate.hint")) { duplicate(index) },
                RowAction(label = { Component.literal("×") }, width = 24, style = { TechButtonStyle.DANGER }, tooltip = tr("mods.delete.hint")) { delete(index) },
            ) },
            onRowClick = { (index, _) -> workspace.openMod(index) },
            rowTooltipOf = { tr("workspace.mod.open.hint") },
        ).also { it.x = bodyLeft; add(it) }
        val footer = bodyBottom - 20
        button(tr("mods.import_local"), bodyLeft + 2, footer, min(120, bodyWidth / 3), {
            workspace.openOrFocus("import:local") { LocalImportWindow(workspace) }
        }, TechButtonStyle.GHOST)
        button(tr("mods.add"), bodyRight - min(105, bodyWidth / 3), footer, min(105, bodyWidth / 3), { workspace.openMod() }, TechButtonStyle.PRIMARY)
    }

    override fun renderBody(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (workspace.editorSession.config.activeModEntries().isEmpty()) {
            graphics.drawCenteredString(font, tr("mods.empty"), (bodyLeft + bodyRight) / 2, (bodyTop + bodyBottom) / 2, EditorTheme.TEXT_MUTED)
        }
    }

    fun refresh() {
        if (::list.isInitialized) list.replaceItems(filtered(), resetScroll = false) else rebuild()
    }

    private fun filtered(): List<Pair<Int, RequiredMod>> {
        val needle = query.trim().lowercase()
        val result = workspace.editorSession.config.activeModEntries().withIndex().map { it.index to it.value }.filter { (_, mod) ->
            needle.isBlank() || listOf(mod.name, mod.modId, mod.filePattern, mod.authors.orEmpty().joinToString(" ")).any { it?.lowercase()?.contains(needle) == true }
        }
        return when (sort) {
            SortMode.CONFIGURED -> result
            SortMode.NAME -> result.sortedBy { it.second.displayName().lowercase() }
            SortMode.CATEGORY -> result.sortedWith(compareBy({ it.second.resolvedCategory() }, { it.second.displayName().lowercase() }))
        }
    }

    private fun duplicate(index: Int) {
        val mods = workspace.editorSession.config.activeModEntries().toMutableList()
        val original = mods.getOrNull(index) ?: return
        val copy = original.copyForEditor().also { it.name = tr("mods.copy_name", original.displayName()).string }
        mods.add(index + 1, copy)
        workspace.editorSession.replaceMods(mods)
        refresh()
    }

    private fun delete(index: Int) {
        val mods = workspace.editorSession.config.activeModEntries().toMutableList()
        if (index !in mods.indices) return
        mods.removeAt(index)
        workspace.editorSession.replaceMods(mods)
        refresh()
    }
}

internal class ModDocumentWindow(
    workspace: EditorWorkspaceScreen,
    key: String,
    private var original: RequiredMod?,
) : WorkspaceWindow(
    workspace, key, Component.literal(original?.displayName() ?: tr("mods.add").string), 650, 440, 390, 285,
) {
    private enum class Tab { GENERAL, DESCRIPTIONS, LINKS, METADATA }
    private var working = original?.copyForEditor() ?: RequiredMod(enabled = false, descriptions = linkedMapOf(), links = emptyList())
    private var tab = Tab.GENERAL
    private var savedDraft: RequiredMod? = original?.copyForEditor()
    private var selectedDescription: String? = working.descriptions.orEmpty().keys.firstOrNull()
    private var selectedLink = working.links.orEmpty().indices.firstOrNull()
    private var advancedLinkFields = false
    private var pendingNewLink: DownloadLink? = null

    override fun isDocumentDirty() = savedDraft == null || working != savedDraft

    override fun commitShortcut(): Boolean {
        apply()
        return true
    }

    override fun buildWidgets() {
        val tabs = listOf(
            Tab.GENERAL to tr("workspace.tab.general"),
            Tab.DESCRIPTIONS to tr("workspace.tab.descriptions"),
            Tab.LINKS to tr("workspace.tab.links"),
            Tab.METADATA to tr("workspace.tab.metadata"),
        )
        val tabWidth = ((bodyWidth - 6) / tabs.size).coerceAtLeast(52)
        tabs.forEachIndexed { index, (value, label) ->
            button(label, bodyLeft + index * (tabWidth + 2), bodyTop, tabWidth, { tab = value; rebuild() }, if (tab == value) TechButtonStyle.PRIMARY else TechButtonStyle.GHOST)
        }
        val contentTop = bodyTop + 25
        val contentBottom = bodyBottom - 24
        when (tab) {
            Tab.GENERAL -> buildGeneral(contentTop, contentBottom)
            Tab.DESCRIPTIONS -> buildDescriptions(contentTop, contentBottom)
            Tab.LINKS -> buildLinks(contentTop, contentBottom)
            Tab.METADATA -> buildMetadata(contentTop, contentBottom)
        }
        button(tr("close"), bodyRight - 177, bodyBottom - 20, 78, ::requestClose, TechButtonStyle.GHOST, tr("workspace.close_window.hint"))
        button(tr("save"), bodyRight - 94, bodyBottom - 20, 90, ::apply, TechButtonStyle.PRIMARY, tr("workspace.save_window.hint"))
    }

    private fun buildGeneral(top: Int, bottom: Int) {
        val fieldWidth = (bodyWidth * .61).toInt().coerceAtLeast(90)
        val x = bodyRight - fieldWidth
        val values = listOf(
            Triple("mod.name", working.name.orEmpty(), { value: String -> working.name = value.trim(); title = Component.literal(working.displayName()) }),
            Triple("mod.id", working.modId.orEmpty(), { value: String -> working.modId = value.trim() }),
            Triple("mod.version", working.versionRange.orEmpty(), { value: String -> working.versionRange = value.trim() }),
            Triple("mod.pattern", working.filePattern.orEmpty(), { value: String -> working.filePattern = value.trim() }),
        )
        values.forEachIndexed { index, (_, value, setter) -> field(x, top + 7 + index * 29, fieldWidth, value) { setter(it); changed() } }
        val toggleY = min(bottom - 23, top + 129)
        button(tr(if (working.enabled == false) "mod.disabled" else "mod.enabled"), bodyLeft + 4, toggleY, (bodyWidth - 13) / 2, {
            working.enabled = working.enabled == false; changed(); rebuild()
        }, if (working.enabled == false) TechButtonStyle.GHOST else TechButtonStyle.PRIMARY)
        button(tr("mod.category", tr("category.${working.resolvedCategory().name.lowercase()}")), bodyLeft + 9 + (bodyWidth - 13) / 2, toggleY, (bodyWidth - 13) / 2, {
            working.category = if (working.resolvedCategory() == ModCategory.REQUIRED) ModCategory.RECOMMENDED.name else ModCategory.REQUIRED.name
            changed(); rebuild()
        })
    }

    private fun buildDescriptions(top: Int, bottom: Int) {
        val descriptions = working.descriptions.orEmpty()
        val leftWidth = (bodyWidth * .28).toInt().coerceIn(105, 190)
        val items = descriptions.entries.toList()
        val listHeight = (bottom - top - 26).coerceAtLeast(38)
        val list = StyledActionList(
            minecraft, leftWidth, listHeight, top, (leftWidth - 8).coerceAtLeast(80), 34, items,
            titleOf = { it.key }, subtitleOf = { it.value }, accentOf = { if (it.key == selectedDescription) EditorTheme.ACCENT else EditorTheme.ACCENT_PURPLE },
            onRowClick = { selectedDescription = it.key; rebuild() },
        ).also { it.x = bodyLeft; add(it) }
        val descriptionActionWidth = (leftWidth - 4) / 2
        button(tr("descriptions.add"), bodyLeft, bottom - 20, descriptionActionWidth, {
            var code = "en_us"
            var suffix = 2
            while (descriptions.containsKey(code)) code = "en_us_${suffix++}"
            working.descriptions = descriptions.toMutableMap().also { it[code] = "" }
            selectedDescription = code; changed(); rebuild()
        }, TechButtonStyle.GHOST)
        button(tr("descriptions.translate"), bodyLeft + descriptionActionWidth + 4, bottom - 20, leftWidth - descriptionActionWidth - 4, {
            workspace.openWindow(TranslationToolWindow(workspace, working, key) { changed(); rebuild() }, center = true)
        }, TechButtonStyle.GHOST, tr("descriptions.translate.hint"))
        val selected = selectedDescription?.takeIf(descriptions::containsKey)
        if (selected != null) {
            val rightX = bodyLeft + leftWidth + 7
            val rightWidth = bodyRight - rightX
            field(rightX, top, rightWidth - 27, selected, 32) { value ->
                val normalized = value.trim().lowercase()
                if (normalized.isNotBlank() && normalized != selected && !working.descriptions.orEmpty().containsKey(normalized)) {
                    val mutable = working.descriptions.orEmpty().toMutableMap()
                    val text = mutable.remove(selected).orEmpty()
                    mutable[normalized] = text
                    working.descriptions = mutable
                    selectedDescription = normalized
                    changed()
                }
            }
            button(Component.literal("×"), bodyRight - 23, top + 1, 22, {
                working.descriptions = working.descriptions.orEmpty().toMutableMap().also { it.remove(selected) }
                selectedDescription = working.descriptions.orEmpty().keys.firstOrNull(); changed(); rebuild()
            }, TechButtonStyle.DANGER, tr("workspace.description.delete.hint"))
            StableMultiLineEditBox(font, rightX, top + 26, rightWidth, (bottom - top - 26).coerceAtLeast(35), tr("description.text"), tr("description.text")).also {
                it.value = descriptions[selected].orEmpty()
                it.setCharacterLimit(8192)
                it.setValueListener { text -> working.descriptions = working.descriptions.orEmpty().toMutableMap().also { map -> map[selected] = text }; changed() }
                add(it)
            }
        }
    }

    private fun buildLinks(top: Int, bottom: Int) {
        val links = working.links.orEmpty()
        val leftWidth = (bodyWidth * .3).toInt().coerceIn(115, 210)
        val listHeight = (bottom - top - 26).coerceAtLeast(38)
        val indexed = links.withIndex().map { it.index to it.value }
        StyledActionList(
            minecraft, leftWidth, listHeight, top, (leftWidth - 8).coerceAtLeast(90), 36, indexed,
            titleOf = { it.second.displayLabel() }, subtitleOf = { it.second.downloadUrl ?: it.second.url.orEmpty() },
            accentOf = { if (it.first == selectedLink) EditorTheme.ACCENT else EditorTheme.ACCENT_PURPLE },
            onRowClick = { selectedLink = it.first; rebuild() },
        ).also { it.x = bodyLeft; add(it) }
        button(tr("links.add"), bodyLeft, bottom - 20, leftWidth, {
            val draft = DownloadLink(type = DownloadSourceType.MODRINTH.name, label = "Modrinth")
            working.links = links.toMutableList().also { it += draft }
            pendingNewLink = draft
            selectedLink = working.links.orEmpty().lastIndex; changed(); rebuild()
        }, TechButtonStyle.GHOST, tr("links.add.hint"))
        val index = selectedLink?.takeIf { it in links.indices } ?: return
        val link = links[index]
        val x = bodyLeft + leftWidth + 7
        val w = bodyRight - x
        var y = top
        button(tr("link.type", link.resolvedType().name), x, y, w, {
            val types = DownloadSourceType.entries
            link.type = types[(types.indexOf(link.resolvedType()) + 1) % types.size].name; changed(); rebuild()
        }, TechButtonStyle.GHOST, tr("link.type.hint")); y += 24
        DownloadSourceFieldsList(
            minecraft, w, (bottom - y - 25).coerceAtLeast(38), y,
            (w - 8).coerceAtLeast(90), link, advancedLinkFields, ::changed,
        ).also { it.x = x; add(it) }
        button(tr(if (advancedLinkFields) "link.advanced.hide" else "link.advanced.show"), x, bottom - 20, min(125, w / 2), {
            advancedLinkFields = !advancedLinkFields; rebuild()
        }, TechButtonStyle.GHOST, tr("link.advanced.hint"))
        val pending = link === pendingNewLink
        val removeWidth = if (pending) min(132, w / 2) else 24
        button(if (pending) tr("workspace.link.cancel_new") else Component.literal("×"), bodyRight - removeWidth, bottom - 20, removeWidth, {
            working.links = links.toMutableList().also { it.removeAt(index) }
            if (pending) pendingNewLink = null
            selectedLink = working.links.orEmpty().indices.firstOrNull(); changed(); rebuild()
        }, if (pending) TechButtonStyle.GHOST else TechButtonStyle.DANGER, tr(if (pending) "workspace.link.cancel_new.hint" else "workspace.link.delete.hint"))
    }

    private fun buildMetadata(top: Int, bottom: Int) {
        MetadataFieldsList(
            minecraft, bodyWidth, (bottom - top - 25).coerceAtLeast(38), top,
            (bodyWidth - 8).coerceAtLeast(100), working, ::changed,
        ).also { it.x = bodyLeft; add(it) }
        val importWidth = min(155, (bodyWidth - 5) / 2)
        button(tr("metadata.import.modrinth"), bodyLeft, bottom - 20, importWidth, {
            workspace.openWindow(MetadataImportWindow(workspace, working, DownloadSourceType.MODRINTH, onApplied = { changed(); rebuild() }, ownerKey = key), center = true)
        }, TechButtonStyle.GHOST)
        button(tr("metadata.import.curseforge"), bodyLeft + importWidth + 5, bottom - 20, importWidth, {
            workspace.openWindow(MetadataImportWindow(workspace, working, DownloadSourceType.CURSEFORGE, onApplied = { changed(); rebuild() }, ownerKey = key), center = true)
        }, TechButtonStyle.GHOST)
    }

    override fun renderBody(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val contentTop = bodyTop + 25
        when (tab) {
            Tab.GENERAL -> listOf("mod.name", "mod.id", "mod.version", "mod.pattern").forEachIndexed { index, key ->
                graphics.drawString(font, tr(key), bodyLeft + 5, contentTop + 14 + index * 29, EditorTheme.TEXT_MUTED, false)
            }
            Tab.LINKS -> Unit
            Tab.METADATA -> Unit
            Tab.DESCRIPTIONS -> Unit
        }
    }

    // Child controls mutate [working] directly. Dirtiness is derived from its
    // structural value, so callbacks cannot leave a stale flag after saving.
    private fun changed() = Unit

    fun edits(mod: RequiredMod): Boolean = original === mod

    fun openSection(section: String?) {
        tab = when {
            section?.startsWith("links") == true || section == "downloadUrl" -> Tab.LINKS
            section?.startsWith("description") == true -> Tab.DESCRIPTIONS
            section?.startsWith("icon") == true || section?.startsWith("project") == true ||
                section?.startsWith("authors") == true || section?.startsWith("license") == true -> Tab.METADATA
            else -> Tab.GENERAL
        }
        if (bounds.width > 0) rebuild()
    }

    private fun apply() {
        val mods = workspace.editorSession.config.activeModEntries().toMutableList()
        val identityIndex = original?.let { item -> mods.indexOfFirst { it === item } } ?: -1
        val index = identityIndex.takeIf { it >= 0 }
        val committed = working.copyForEditor()
        if (index != null) mods[index] = committed else mods += committed
        original = committed
        pendingNewLink = null
        workspace.editorSession.replaceMods(mods)
        savedDraft = working.copyForEditor()
        markDraftCommitted()
        workspace.modsChanged()
    }
}

internal class MetadataImportWindow(
    workspace: EditorWorkspaceScreen,
    private val mod: RequiredMod,
    private val type: DownloadSourceType,
    private val onApplied: () -> Unit,
    ownerKey: String,
) : WorkspaceWindow(workspace, "metadata-import:$ownerKey:${type.name}", tr("metadata.import.title", type.name), 500, 245, 330, 210) {
    private var project = mod.links.orEmpty().firstOrNull { it.resolvedType() == type }?.let { it.projectId ?: it.url }.orEmpty()
    private var loading = false
    private var preview: ProjectMetadata? = null
    private var previewSource: DownloadLink? = null
    private var error: String? = null
    private var apiKey = if (type == DownloadSourceType.CURSEFORGE) org.bmp.cph.config.ConfigManager.loadAuthorSettings().curseForgeApiKey.orEmpty() else ""

    override fun buildWidgets() {
        if (preview == null) {
            field(bodyLeft + 4, bodyTop + 28, bodyWidth - 8, project, 2048) { project = it }
            if (type == DownloadSourceType.CURSEFORGE) {
                field(bodyLeft + 4, bodyTop + 55, bodyWidth - 8, apiKey, 512) { apiKey = it }
            }
            val action = button(tr(if (loading) "metadata.import.loading" else "metadata.import.action"), bodyRight - 128, bodyBottom - 20, 124, ::load, TechButtonStyle.PRIMARY)
            action.active = !loading
        } else {
            button(tr("metadata.import.fill_empty"), bodyRight - 260, bodyBottom - 20, 126, { apply(MetadataApplyMode.FILL_EMPTY) }, TechButtonStyle.GHOST)
            button(tr("metadata.import.replace"), bodyRight - 129, bodyBottom - 20, 125, { apply(MetadataApplyMode.REPLACE) }, TechButtonStyle.PRIMARY)
        }
        button(tr("cancel"), bodyLeft + 4, bodyBottom - 20, 78, { workspace.closeWindow(this) }, TechButtonStyle.GHOST)
    }

    override fun renderBody(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawFittedLine(graphics, tr("metadata.import.subtitle.${type.name.lowercase()}"), bodyLeft + 4, bodyTop + 5, bodyWidth - 8)
        preview?.let { metadata ->
            val lines = listOf(
                tr("metadata.import.preview.name", metadata.name),
                tr("metadata.import.preview.authors", metadata.authors.joinToString(", ").ifBlank { "—" }),
                tr("metadata.import.preview.license", metadata.license ?: "—"),
                tr("metadata.import.preview.description", Component.translatable(if (metadata.summary.isNullOrBlank()) "options.off" else "options.on")),
            )
            lines.forEachIndexed { index, line ->
                val y = bodyTop + 12 + index * 23
                if (y + font.lineHeight <= bodyBottom - 25) drawFittedLine(graphics, line, bodyLeft + 7, y, bodyWidth - 16, EditorTheme.TEXT)
            }
        }
        error?.let { message ->
            drawWrappedText(graphics, Component.literal(message), bodyLeft + 7, bodyTop + 82, bodyWidth - 16, bodyBottom - 25, 0xFFFF7777.toInt(), 10, 3)
        }
    }

    private fun load() {
        if (loading) return
        if (type == DownloadSourceType.CURSEFORGE) {
            val normalized = org.bmp.cph.client.curseforge.CurseForgeApiSupport.normalizeKey(apiKey)
            if (normalized.isNotBlank()) {
                apiKey = normalized
                val settings = org.bmp.cph.config.ConfigManager.loadAuthorSettings()
                org.bmp.cph.config.ConfigManager.saveAuthorSettings(settings.copy(curseForgeApiKey = normalized))
            }
        }
        val existing = mod.links.orEmpty().firstOrNull { it.resolvedType() == type }?.copy()
        val source = existing ?: DownloadLink(label = if (type == DownloadSourceType.MODRINTH) "Modrinth" else "CurseForge", type = type.name)
        source.type = type.name
        if (type == DownloadSourceType.MODRINTH && project.trim().startsWith("http", ignoreCase = true)) {
            source.url = project.trim()
            source.projectId = null
        } else source.projectId = project.trim()
        loading = true; error = null; rebuild()
        ProjectMetadataService.fetchAsync(source).whenComplete { value, exception ->
            Minecraft.getInstance().execute {
                loading = false
                if (value != null) {
                    preview = value
                    previewSource = source
                } else error = exception?.cause?.message ?: exception?.message ?: tr("error.unknown").string
                rebuild()
            }
        }
    }

    private fun apply(mode: MetadataApplyMode) {
        val metadata = preview ?: return
        val mutable = mod.links.orEmpty().toMutableList()
        val index = mutable.indexOfFirst { it.resolvedType() == type }
        val source = previewSource ?: return
        ProjectMetadataService.apply(metadata, mod, source, "en_us", mode)
        if (index >= 0) mutable[index] = source else mutable += source
        mod.links = mutable
        onApplied()
        workspace.closeWindow(this)
    }
}
