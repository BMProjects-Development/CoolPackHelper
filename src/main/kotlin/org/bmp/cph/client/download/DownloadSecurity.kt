package org.bmp.cph.client.download

import org.bmp.cph.config.DownloadSourceType
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

object DownloadSecurity {
    private val modrinthHosts = setOf("cdn.modrinth.com")
    private val curseForgeHosts = setOf("edge.forgecdn.net", "mediafilez.forgecdn.net", "media.forgecdn.net")
    private val githubHosts = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "github-releases.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    fun validateUri(uri: URI, sourceType: DownloadSourceType, resolveDns: Boolean = true): String? {
        if (!uri.scheme.equals("https", ignoreCase = true)) return "Only HTTPS downloads are allowed"
        if (uri.userInfo != null) return "URLs containing credentials are not allowed"
        if (uri.port != -1 && uri.port != 443) return "Only the standard HTTPS port is allowed"
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return "The URL has no valid host"
        if (host == "localhost" || host.endsWith(".localhost")) return "Local addresses are not allowed"
        if (!hostAllowedForSource(host, sourceType)) return "The final host does not match the configured source"
        if (resolveDns) {
            val addresses = try {
                InetAddress.getAllByName(host)
            } catch (exception: Exception) {
                return "Could not resolve the download host: ${exception.message ?: exception.javaClass.simpleName}"
            }
            if (addresses.isEmpty() || addresses.any(::isNonPublic)) return "Private, local, multicast, and reserved addresses are not allowed"
        }
        return null
    }

    fun safeFileName(value: String?): String? {
        val name = value?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
        if (name.isBlank() || name == "." || name == "..") return null
        if (!name.endsWith(".jar", ignoreCase = true)) return null
        if (name.any { it.code < 32 || it in "<>:\"|?*" }) return null
        return name.takeIf { it.length <= 180 }
    }

    private fun hostAllowedForSource(host: String, sourceType: DownloadSourceType): Boolean = when (sourceType) {
        DownloadSourceType.MODRINTH -> host in modrinthHosts
        DownloadSourceType.CURSEFORGE -> host in curseForgeHosts
        DownloadSourceType.GITHUB_RELEASE -> host in githubHosts
        DownloadSourceType.DIRECT -> true
        DownloadSourceType.PAGE -> false
    }

    private fun isNonPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return true
        val bytes = address.address
        return when (address) {
            is Inet4Address -> {
                val first = bytes[0].toInt() and 0xFF
                val second = bytes[1].toInt() and 0xFF
                first == 0 || first >= 224 || first == 127 ||
                    (first == 169 && second == 254) ||
                    (first == 100 && second in 64..127) ||
                    (first == 192 && second == 0) ||
                    (first == 192 && second == 88 && (bytes[2].toInt() and 0xFF) == 99) ||
                    (first == 198 && second in 18..19) ||
                    (first == 198 && second == 51 && (bytes[2].toInt() and 0xFF) == 100) ||
                    (first == 203 && second == 0 && (bytes[2].toInt() and 0xFF) == 113)
            }
            is Inet6Address -> {
                val first = bytes[0].toInt() and 0xFF
                first and 0xFE == 0xFC || first == 0xFF
            }
            else -> true
        }
    }
}
