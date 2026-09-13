package org.bmp.cph.client.download

import org.bmp.cph.config.DownloadLink
import org.bmp.cph.config.DownloadSourceType
import org.bmp.cph.config.DownloadTrustLevel
import org.bmp.cph.config.RequiredMod
import java.net.URI
import java.nio.file.Path

enum class DownloadResolutionStatus {
    READY,
    PAGE_ONLY,
    FAILED,
}

data class DownloadRequest(
    val mod: RequiredMod,
    val source: DownloadLink? = null,
)

data class ResolvedDownload(
    val mod: RequiredMod,
    val source: DownloadLink,
    val sourceType: DownloadSourceType,
    val trust: DownloadTrustLevel,
    val status: DownloadResolutionStatus,
    val pageUri: URI? = null,
    val downloadUri: URI? = null,
    val fileName: String? = null,
    val sizeBytes: Long? = null,
    val hashes: Map<String, String> = emptyMap(),
    val message: String? = null,
) {
    fun strongestHash(): Pair<String, String>? = listOf("SHA-512", "SHA-256", "SHA-1")
        .firstNotNullOfOrNull { algorithm -> hashes[algorithm]?.let { algorithm to it } }
}

data class DownloadProgress(
    val modName: String,
    val fileName: String,
    val downloadedBytes: Long,
    val totalBytes: Long?,
)

data class InstallResult(
    val download: ResolvedDownload,
    val success: Boolean,
    val target: Path? = null,
    val journalId: String? = null,
    val message: String,
)
