package org.bmp.cph.client.download

import org.bmp.cph.config.DownloadSourceType
import org.bmp.cph.client.cphMessage
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
        if (!uri.scheme.equals("https", ignoreCase = true)) return cphMessage("cph.download.error.https_required")
        if (uri.userInfo != null) return cphMessage("cph.download.error.credentials")
        if (uri.port != -1 && uri.port != 443) return cphMessage("cph.download.error.port")
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return cphMessage("cph.download.error.invalid_host")
        if (host == "localhost" || host.endsWith(".localhost")) return cphMessage("cph.download.error.local_address")
        if (!hostAllowedForSource(host, sourceType)) return cphMessage("cph.download.error.source_host")
        if (resolveDns) {
            val addresses = try {
                InetAddress.getAllByName(host)
            } catch (exception: Exception) {
                return cphMessage("cph.download.error.resolve_host", exception.message ?: exception.javaClass.simpleName)
            }
            if (addresses.isEmpty() || addresses.any(::isNonPublic)) return cphMessage("cph.download.error.non_public_address")
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
