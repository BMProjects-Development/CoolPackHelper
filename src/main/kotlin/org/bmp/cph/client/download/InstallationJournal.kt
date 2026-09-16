package org.bmp.cph.client.download

import com.google.gson.GsonBuilder
import net.neoforged.fml.loading.FMLPaths
import org.bmp.cph.Cph
import org.bmp.cph.config.ConfigManager
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant

data class InstallationRecord(
    var id: String = "",
    var batchId: String = "",
    var modName: String = "",
    var source: String = "",
    var target: String = "",
    var sha256: String = "",
    var installedAt: String = "",
    var backups: Map<String, String> = emptyMap(),
    var rolledBack: Boolean = false,
)

object InstallationJournal {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val root: Path get() = FMLPaths.GAMEDIR.get().resolve("local").resolve("coolpackhelper")
    private val journalPath: Path get() = root.resolve("installations.json")
    private var cachedRecords: List<InstallationRecord>? = null

    @Synchronized
    fun record(value: InstallationRecord) {
        val records = load().toMutableList()
        records += value
        save(pruneBackups(records))
    }

    @Synchronized
    fun rollbackBatch(batchId: String): Pair<Int, List<String>> {
        val records = load().toMutableList()
        var restored = 0
        val errors = mutableListOf<String>()
        records.filter { it.batchId == batchId && !it.rolledBack }.asReversed().forEach { record ->
            try {
                val target = Path.of(record.target).toAbsolutePath().normalize()
                val mods = FMLPaths.MODSDIR.get().toAbsolutePath().normalize()
                require(target.startsWith(mods)) { "Refusing to remove a file outside the mods folder" }
                if (Files.exists(target)) {
                    require(record.sha256.isNotBlank() && sha256(target).equals(record.sha256, ignoreCase = true)) {
                        "The installed file has changed since installation; it was not removed"
                    }
                    Files.delete(target)
                }
                record.backups.forEach { (originalValue, backupValue) ->
                    val original = Path.of(originalValue).toAbsolutePath().normalize()
                    val backup = Path.of(backupValue).toAbsolutePath().normalize()
                    require(original.startsWith(mods) && backup.startsWith(root.toAbsolutePath().normalize())) { "Unsafe backup path" }
                    if (Files.exists(backup)) {
                        Files.createDirectories(original.parent)
                        Files.move(backup, original, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
                record.rolledBack = true
                restored++
            } catch (exception: Exception) {
                Cph.LOGGER.error("Could not roll back installation {}", record.id, exception)
                errors += "${record.modName}: ${exception.message ?: exception.javaClass.simpleName}"
            }
        }
        save(records)
        return restored to errors
    }

    @Synchronized
    fun deleteRolledBackBatch(batchId: String): Boolean {
        val records = load()
        val remaining = withoutRolledBackBatch(records, batchId) ?: return false
        try {
            save(remaining)
        } catch (exception: Exception) {
            Cph.LOGGER.error("Could not delete rolled-back installation batch {}", batchId, exception)
            return false
        }
        try {
            deleteBackupDirectory(batchId)
        } catch (exception: Exception) {
            Cph.LOGGER.warn("Deleted installation history but could not clean backup batch {}", batchId, exception)
        }
        return true
    }

    @Synchronized
    fun load(): List<InstallationRecord> {
        cachedRecords?.let { return it.detachedCopy() }
        val loaded = try {
            if (Files.notExists(journalPath)) emptyList()
            else Files.newBufferedReader(journalPath, StandardCharsets.UTF_8).use { reader ->
                gson.fromJson(reader, Array<InstallationRecord>::class.java)?.toList().orEmpty()
            }
        } catch (exception: Exception) {
            Cph.LOGGER.warn("Could not read the CoolPackHelper installation journal", exception)
            emptyList()
        }
        cachedRecords = loaded.detachedCopy()
        return loaded.detachedCopy()
    }

    private fun save(records: List<InstallationRecord>) {
        Files.createDirectories(root)
        val temporary = journalPath.resolveSibling("${journalPath.fileName}.tmp")
        Files.newBufferedWriter(temporary, StandardCharsets.UTF_8).use { gson.toJson(records, it) }
        try {
            Files.move(temporary, journalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(temporary, journalPath, StandardCopyOption.REPLACE_EXISTING)
        }
        cachedRecords = records.detachedCopy()
    }

    private fun pruneBackups(records: List<InstallationRecord>): List<InstallationRecord> {
        val maximum = ConfigManager.config.downloads?.resolvedMaxBackupBatches() ?: 10
        val retained = retainedBatchIds(records, maximum)
        val removable = records.map(InstallationRecord::batchId).distinct().filterNot(retained::contains)
        if (removable.isEmpty()) return records
        val backupRoot = root.resolve("backups").toAbsolutePath().normalize()
        val removed = mutableSetOf<String>()
        removable.forEach { batchId ->
            try {
                deleteBackupDirectory(batchId, backupRoot)
                removed += batchId
            } catch (exception: Exception) {
                Cph.LOGGER.warn("Could not prune installation backup batch {}", batchId, exception)
            }
        }
        return records.filterNot { it.batchId in removed }
    }

    internal fun retainedBatchIds(records: List<InstallationRecord>, maximum: Int): Set<String> =
        records.map(InstallationRecord::batchId).filter(String::isNotBlank).distinct().takeLast(maximum.coerceAtLeast(1)).toSet()

    internal fun withoutRolledBackBatch(records: List<InstallationRecord>, batchId: String): List<InstallationRecord>? {
        val batch = records.filter { it.batchId == batchId }
        if (batch.isEmpty() || batch.any { !it.rolledBack }) return null
        return records.filterNot { it.batchId == batchId }
    }

    private fun deleteBackupDirectory(
        batchId: String,
        backupRoot: Path = root.resolve("backups").toAbsolutePath().normalize(),
    ) {
        val directory = backupRoot.resolve(batchId).normalize()
        require(batchId.isNotBlank() && directory.startsWith(backupRoot) && directory != backupRoot) { "Unsafe backup batch path" }
        if (Files.exists(directory)) {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun newRecord(
        id: String,
        batchId: String,
        download: ResolvedDownload,
        target: Path,
        sha256: String,
        backups: Map<Path, Path>,
    ) = InstallationRecord(
        id = id,
        batchId = batchId,
        modName = download.mod.displayName(),
        source = download.downloadUri.toString(),
        target = target.toAbsolutePath().normalize().toString(),
        sha256 = sha256,
        installedAt = Instant.now().toString(),
        backups = backups.mapKeys { it.key.toAbsolutePath().normalize().toString() }
            .mapValues { it.value.toAbsolutePath().normalize().toString() },
    )
}

private fun List<InstallationRecord>.detachedCopy(): List<InstallationRecord> = map { record ->
    record.copy(backups = record.backups.toMap())
}
