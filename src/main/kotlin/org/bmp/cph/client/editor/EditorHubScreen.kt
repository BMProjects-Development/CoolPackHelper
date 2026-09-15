package org.bmp.cph.client.editor

import net.minecraft.client.gui.screens.Screen

/** Public entry point retained for NeoForge's config screen factory. */
class EditorHubScreen(
    parent: Screen,
    session: EditorSession = EditorSession.open(),
) : EditorWorkspaceScreen(parent, session)
