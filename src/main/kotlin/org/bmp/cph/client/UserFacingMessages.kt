package org.bmp.cph.client

/** Keeps low-level validation testable without placing Minecraft classes on the JUnit runtime classpath. */
internal fun cphMessage(key: String, vararg arguments: Any): String = runCatching {
    val componentClass = Class.forName("net.minecraft.network.chat.Component")
    val component = componentClass.getMethod("translatable", String::class.java, Array<Any>::class.java)
        .invoke(null, key, arguments)
    componentClass.getMethod("getString").invoke(component) as String
}.getOrElse { key }

internal fun cphError(key: String, vararg arguments: Any): Nothing =
    throw IllegalArgumentException(cphMessage(key, *arguments))

internal fun cphRequire(condition: Boolean, key: String, vararg arguments: Any) {
    if (!condition) cphError(key, *arguments)
}
