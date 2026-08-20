package com.tdpl.chat.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Serializable
data class ModelVariant(val url: String, val sha256: String, val size_bytes: Long)

@Serializable
data class ModelManifest(
    val repo: String,
    val default_variant: String,
    val variants: Map<String, ModelVariant>
)

sealed class ModelState {
    data object NotReady : ModelState()
    data class CheckingForUpdate(val message: String) : ModelState()
    data class RestoringFromBackup(val message: String) : ModelState()
    data class Downloading(val bytesDone: Long, val bytesTotal: Long) : ModelState()
    data object Verifying : ModelState()
    data object Ready : ModelState()
    data class Error(val message: String) : ModelState()
}

/**
 * Three-tier model storage:
 *
 *  1. App-internal (<filesDir>/models/model.gguf) — used for inference, fast,
 *     no permissions needed. Wiped on uninstall.
 *  2. Local backup, in public shared storage (Downloads/NyraModels/model.gguf,
 *     via MediaStore on Android 10+, direct File below that) — survives
 *     uninstall since it lives outside the app's private sandbox.
 *  3. Network (Hugging Face) — last resort.
 *
 * Lookup order on every launch: internal -> local backup -> network.
 * After a successful network download, the file is automatically mirrored
 * into the local backup folder (created on first run if it doesn't exist),
 * so a future reinstall can restore instantly without re-downloading.
 *
 * All file copies stream through a fixed-size buffer — the model is a
 * multi-gigabyte file, so nothing here ever loads it fully into memory.
 */
class ModelDownloadManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
    private val modelFile = File(modelsDir, "model.gguf")
    private val versionFile = File(modelsDir, "model.sha256")

    private val backup = LocalModelBackup(context)

    private val _state = MutableStateFlow<ModelState>(ModelState.NotReady)
    val state: StateFlow<ModelState> = _state

    fun localModelPath(): String? = if (modelFile.exists()) modelFile.absolutePath else null

    /** True if a usable model is already on disk (internal) without touching the network. */
    fun hasLocalModel(): Boolean = modelFile.exists() && versionFile.exists()

    suspend fun ensureModelReady(manifestUrl: String, variant: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            try {
                _state.value = ModelState.CheckingForUpdate("Checking for model updates…")
                val manifest = fetchManifest(manifestUrl)
                val chosen = manifest.variants[variant ?: manifest.default_variant]
                    ?: manifest.variants.values.first()

                // 1) Already on internal storage with the right version?
                val localSha = if (versionFile.exists()) versionFile.readText().trim() else null
                if (modelFile.exists() && localSha == chosen.sha256) {
                    _state.value = ModelState.Ready
                    return@withContext true
                }

                // 2) Not on internal storage (fresh install / reinstall) — check the
                //    local backup folder before touching the network at all.
                _state.value = ModelState.RestoringFromBackup("Checking local backup…")
                if (backup.restoreInto(modelFile, chosen.sha256)) {
                    versionFile.writeText(chosen.sha256)
                    _state.value = ModelState.Ready
                    return@withContext true
                }

                // 3) Nothing usable locally — download from network.
                download(chosen)
                versionFile.writeText(chosen.sha256)

                // Mirror to the local backup folder (creating it on first run) so a
                // future reinstall can skip the network entirely. Non-fatal on
                // failure — the app is still fully usable from internal storage.
                runCatching { backup.save(modelFile, chosen.sha256) }

                _state.value = ModelState.Ready
                true
            } catch (e: Exception) {
                _state.value = ModelState.Error(e.message ?: "Unknown error")
                false
            }
        }

    private fun fetchManifest(url: String): ModelManifest {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            require(resp.isSuccessful) { "Manifest fetch failed: ${resp.code}" }
            val body = resp.body?.string() ?: error("Empty manifest body")
            return json.decodeFromString(ModelManifest.serializer(), body)
        }
    }

    private fun download(variant: ModelVariant) {
        val tmpFile = File(modelsDir, "model.gguf.part")
        val req = Request.Builder().url(variant.url).build()

        client.newCall(req).execute().use { resp ->
            require(resp.isSuccessful) { "Download failed: ${resp.code}" }
            val body = resp.body ?: error("Empty download body")
            val total = variant.size_bytes.takeIf { it > 0 } ?: body.contentLength()

            body.byteStream().use { input ->
                tmpFile.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        done += read
                        _state.value = ModelState.Downloading(done, total)
                    }
                }
            }
        }

        _state.value = ModelState.Verifying
        val actualSha = streamingSha256(tmpFile)
        require(actualSha == variant.sha256) { "Checksum mismatch after download" }

        if (modelFile.exists()) modelFile.delete()
        tmpFile.renameTo(modelFile)
    }
}

/** Streams [file] through SHA-256 without loading it fully into memory. */
internal fun streamingSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input -> pipe(input, digest) }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun pipe(input: InputStream, digest: MessageDigest) {
    val buffer = ByteArray(1 shl 16)
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        digest.update(buffer, 0, read)
    }
}

/** Streaming copy from [input] to [output]; closes neither stream. */
internal fun streamCopy(input: InputStream, output: OutputStream) {
    val buffer = ByteArray(1 shl 16)
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        output.write(buffer, 0, read)
    }
}

/**
 * Handles the "survives uninstall" copy in public shared storage.
 * Uses MediaStore (Downloads collection) on Android 10+, since apps can no
 * longer freely read/write arbitrary paths under scoped storage; falls back
 * to a direct file path on older versions. The checksum file is tiny (a
 * 64-char hex string) and is the only thing ever read fully into memory —
 * the multi-gigabyte model itself is always streamed.
 */
private class LocalModelBackup(private val context: Context) {

    private val backupSubDir = "NyraModels"
    private val backupFileName = "model.gguf"
    private val checksumFileName = "model.sha256"
    private val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$backupSubDir/"

    /** Streams the current internal model + its checksum out to the backup folder. */
    fun save(sourceFile: File, sha256: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(sourceFile, sha256)
        } else {
            saveViaLegacyFile(sourceFile, sha256)
        }
    }

    /**
     * If a backup exists and its checksum matches [expectedSha256], streams it into
     * [destFile] (restoring it as the active internal model) and returns true.
     */
    fun restoreInto(destFile: File, expectedSha256: String): Boolean {
        val storedSha = readChecksum() ?: return false
        if (storedSha != expectedSha256) return false

        destFile.parentFile?.mkdirs()
        val tmp = File(destFile.parentFile, "${destFile.name}.restoring")
        val copied = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            copyModelFromMediaStore(tmp)
        } else {
            copyModelFromLegacyFile(tmp)
        }
        if (!copied) {
            tmp.delete()
            return false
        }

        // Verify the streamed copy actually matches before trusting it.
        if (streamingSha256(tmp) != expectedSha256) {
            tmp.delete()
            return false
        }

        if (destFile.exists()) destFile.delete()
        tmp.renameTo(destFile)
        return true
    }

    // ---- Android 10+ (scoped storage, via MediaStore) ----

    private fun findEntryUri(displayName: String): android.net.Uri? {
        val resolver = context.contentResolver
        val cursor = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME}=?",
            arrayOf(relativePath, displayName),
            null
        ) ?: return null

        cursor.use { c ->
            if (!c.moveToFirst()) return null
            val id = c.getLong(0)
            return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
        }
    }

    private fun deleteEntry(displayName: String) {
        findEntryUri(displayName)?.let { uri ->
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun saveViaMediaStore(sourceFile: File, sha256: String) {
        val resolver = context.contentResolver

        // Model file — streamed, never fully loaded into memory.
        deleteEntry(backupFileName)
        val modelValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, backupFileName)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$backupSubDir")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val modelUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, modelValues)
        if (modelUri != null) {
            resolver.openOutputStream(modelUri)?.use { out ->
                sourceFile.inputStream().use { input -> streamCopy(input, out) }
            }
            modelValues.clear()
            modelValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(modelUri, modelValues, null, null)
        }

        // Checksum file — tiny, fine as a single small write.
        deleteEntry(checksumFileName)
        val shaValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, checksumFileName)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$backupSubDir")
        }
        val shaUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, shaValues)
        if (shaUri != null) {
            resolver.openOutputStream(shaUri)?.use { it.write(sha256.toByteArray()) }
        }
    }

    private fun copyModelFromMediaStore(destTmp: File): Boolean {
        val uri = findEntryUri(backupFileName) ?: return false
        context.contentResolver.openInputStream(uri)?.use { input ->
            destTmp.outputStream().use { out -> streamCopy(input, out) }
        } ?: return false
        return true
    }

    private fun readChecksumFromMediaStore(): String? {
        val uri = findEntryUri(checksumFileName) ?: return null
        return context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8).trim()
        }
    }

    // ---- Pre-Android 10 (legacy direct file access) ----
    // Requires WRITE_EXTERNAL_STORAGE (maxSdkVersion=28) in the manifest.

    private fun legacyBackupDir(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            backupSubDir
        ).apply { mkdirs() }

    private fun saveViaLegacyFile(sourceFile: File, sha256: String) {
        val dir = legacyBackupDir()
        sourceFile.inputStream().use { input ->
            File(dir, backupFileName).outputStream().use { out -> streamCopy(input, out) }
        }
        File(dir, checksumFileName).writeText(sha256)
    }

    private fun copyModelFromLegacyFile(destTmp: File): Boolean {
        val srcModel = File(legacyBackupDir(), backupFileName)
        if (!srcModel.exists()) return false
        srcModel.inputStream().use { input ->
            destTmp.outputStream().use { out -> streamCopy(input, out) }
        }
        return true
    }

    private fun readChecksumFromLegacyFile(): String? {
        val shaFile = File(legacyBackupDir(), checksumFileName)
        return if (shaFile.exists()) shaFile.readText().trim() else null
    }

    // ---- shared ----

    private fun readChecksum(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) readChecksumFromMediaStore()
        else readChecksumFromLegacyFile()
}
