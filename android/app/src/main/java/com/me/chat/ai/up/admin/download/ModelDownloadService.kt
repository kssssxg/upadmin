package com.me.chat.ai.up.admin.download

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.me.chat.ai.up.admin.App
import com.me.chat.ai.up.admin.R
import com.me.chat.ai.up.admin.mnn.ModelInfo
import com.me.chat.ai.up.admin.mnn.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Foreground service that downloads a model ZIP from [ModelInfo.downloadUrl],
 * extracts it into [ModelManager.modelDir], and reports progress back to the
 * JavaScript layer via a broadcast [Intent].
 *
 * The service stops itself automatically when the download completes or fails.
 */
class ModelDownloadService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): ModelDownloadService = this@ModelDownloadService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID) ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val modelInfo = com.me.chat.ai.up.admin.mnn.ModelCatalogue.findById(modelId) ?: run {
            Log.e(TAG, "Unknown model id: $modelId")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification(modelInfo.name, 0))
        scope.launch {
            download(modelInfo, startId)
        }
        return START_NOT_STICKY
    }

    private suspend fun download(info: ModelInfo, startId: Int) {
        val modelManager = ModelManager(this)
        val tmpZip = File(cacheDir, info.fileName)
        val destDir = modelManager.modelDir(info)

        try {
            // ── 1. Download ────────────────────────────────────────────
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url(info.downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }
                val body = response.body ?: throw Exception("Empty response body")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                FileOutputStream(tmpZip).use { out ->
                    val buf = ByteArray(8 * 1024)
                    body.byteStream().use { input ->
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            downloadedBytes += read
                            val progress = if (totalBytes > 0) {
                                (downloadedBytes * 100 / totalBytes).toInt()
                            } else {
                                -1
                            }
                            broadcastProgress(info.id, progress, downloadedBytes, totalBytes)
                            updateNotification(info.name, progress)
                        }
                    }
                }
            }

            // ── 2. Extract ZIP ─────────────────────────────────────────
            broadcastProgress(info.id, PROGRESS_EXTRACTING, 0, 0)
            destDir.mkdirs()
            ZipInputStream(tmpZip.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        FileOutputStream(entryFile).use { out ->
                            zis.copyTo(out)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            tmpZip.delete()

            Log.i(TAG, "Model ${info.id} downloaded and extracted to ${destDir.absolutePath}")
            broadcastComplete(info.id, success = true)

        } catch (e: Exception) {
            Log.e(TAG, "Download failed for ${info.id}", e)
            tmpZip.delete()
            destDir.deleteRecursively()
            broadcastComplete(info.id, success = false, error = e.message ?: "Unknown error")
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
    }

    // ── Broadcast helpers ──────────────────────────────────────────────

    private fun broadcastProgress(
        modelId: String, progress: Int, downloaded: Long, total: Long
    ) {
        sendBroadcast(Intent(ACTION_DOWNLOAD_PROGRESS).apply {
            putExtra(EXTRA_MODEL_ID, modelId)
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_DOWNLOADED_BYTES, downloaded)
            putExtra(EXTRA_TOTAL_BYTES, total)
        })
    }

    private fun broadcastComplete(modelId: String, success: Boolean, error: String = "") {
        // Sanitize the error message to avoid leaking internal paths or stack traces
        val safeError = error
            .replace(filesDir.absolutePath, "<files>")
            .replace(cacheDir.absolutePath, "<cache>")
            .lines()
            .firstOrNull()
            ?.take(200)
            ?: ""
        sendBroadcast(Intent(ACTION_DOWNLOAD_COMPLETE).apply {
            putExtra(EXTRA_MODEL_ID, modelId)
            putExtra(EXTRA_SUCCESS, success)
            putExtra(EXTRA_ERROR, safeError)
        })
    }

    // ── Notification helpers ───────────────────────────────────────────

    private fun buildNotification(modelName: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, App.CHANNEL_DOWNLOAD)
            .setContentTitle("下载模型: $modelName")
            .setContentText(if (progress < 0) "连接中…" else "$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress.coerceAtLeast(0), progress < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(modelName: String, progress: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(modelName, progress))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ModelDownloadService"
        private const val NOTIF_ID = 1001

        /** Sentinel value for the extraction phase (after download). */
        const val PROGRESS_EXTRACTING = -2

        const val ACTION_DOWNLOAD_PROGRESS = "com.me.chat.ai.up.admin.DOWNLOAD_PROGRESS"
        const val ACTION_DOWNLOAD_COMPLETE = "com.me.chat.ai.up.admin.DOWNLOAD_COMPLETE"

        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_DOWNLOADED_BYTES = "downloaded_bytes"
        const val EXTRA_TOTAL_BYTES = "total_bytes"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_ERROR = "error"
    }
}
