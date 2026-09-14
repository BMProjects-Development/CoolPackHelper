package org.bmp.cph.client.curseforge

import net.minecraft.network.chat.Component

object CurseForgeApiSupport {
    const val USER_AGENT = "BMP/CoolPackHelper/1.0.0 (https://github.com/BMProjects-Development/CoolPackHelper)"
    const val API_KEY_HELP_URL = "https://support.curseforge.com/support/solutions/articles/9000208346"

    fun normalizeKey(value: String?): String = value.orEmpty().trim().let { key ->
        when {
            key.length >= 2 && key.first() == '"' && key.last() == '"' -> key.substring(1, key.lastIndex).trim()
            key.length >= 2 && key.first() == '\'' && key.last() == '\'' -> key.substring(1, key.lastIndex).trim()
            else -> key
        }
    }

    fun error(status: Int, responseBody: String): String = when (status) {
        401 -> Component.translatable("cph.editor.curseforge.error.unauthorized").string
        403 -> Component.translatable("cph.editor.curseforge.error.forbidden").string
        429 -> Component.translatable("cph.editor.curseforge.error.rate_limit").string
        else -> {
            val details = responseBody.trim().replace(Regex("\\s+"), " ").take(180)
            if (details.isBlank()) Component.translatable("cph.editor.curseforge.error.http", status).string
            else Component.translatable("cph.editor.curseforge.error.http_details", status, details).string
        }
    }
}
