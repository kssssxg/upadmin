package com.me.chat.ai.up.admin.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.me.chat.ai.up.admin.download.ModelDownloadService
import com.me.chat.ai.up.admin.mnn.MNNLLMEngine
import com.me.chat.ai.up.admin.mnn.ModelCatalogue
import com.me.chat.ai.up.admin.mnn.ModelInfo
import com.me.chat.ai.up.admin.mnn.ModelManager
import com.me.chat.ai.up.admin.mnn.ModelStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * JavaScript interface exposed to the WebView as `window.Android`.
 *
 * All methods annotated with [@JavascriptInterface] can be called directly
 * from JavaScript running inside the WebView.  Methods that perform I/O
 * dispatch work to background coroutines and return results via
 * `evaluateJavascript()` callbacks on the UI thread.
 *
 * ### JavaScript API surface
 *
 * | Method | Description |
 * |--------|-------------|
 * | `Android.getModelList()` | JSON array of all catalogue models + status |
 * | `Android.downloadModel(id)` | Start downloading a model |
 * | `Android.cancelDownload(id)` | (No-op placeholder; downloads cannot be paused mid-stream) |
 * | `Android.loadModel(id)` | Load a downloaded model into MNN |
 * | `Android.unloadModel()` | Unload the currently active model |
 * | `Android.getLoadedModel()` | ID of the currently loaded model, or "" |
 * | `Android.isNativeAvailable()` | Whether the MNN .so is present |
 * | `Android.chat(callbackId, messagesJson)` | Streaming inference; tokens delivered via `window.onMNNToken(callbackId, token, isDone)` |
 * | `Android.cancelChat()` | Abort an in-progress inference |
 * | `Android.proxyPost(callbackId, url, headersJson, bodyJson)` | Proxy an API POST through native HTTP (CORS bypass) |
 */
class AppJsBridge(
    private val context: Context,
    private val webView: WebView
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val engine = MNNLLMEngine()
    private val modelManager = ModelManager(context)

    /** Currently loaded model id, or null. */
    private var loadedModelId: String? = null

    /** Set to true to abort the running chat coroutine. */
    private val cancelRequested = AtomicBoolean(false)

    private var chatJob: Job? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── Download broadcast receiver ────────────────────────────────────

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ModelDownloadService.ACTION_DOWNLOAD_PROGRESS -> {
                    val modelId = intent.getStringExtra(ModelDownloadService.EXTRA_MODEL_ID) ?: return
                    val progress = intent.getIntExtra(ModelDownloadService.EXTRA_PROGRESS, 0)
                    val downloaded = intent.getLongExtra(ModelDownloadService.EXTRA_DOWNLOADED_BYTES, 0)
                    val total = intent.getLongExtra(ModelDownloadService.EXTRA_TOTAL_BYTES, 0)
                    runJs(
                        "window.onModelDownloadProgress && window.onModelDownloadProgress(" +
                                "${jsonStr(modelId)}, $progress, $downloaded, $total)"
                    )
                }
                ModelDownloadService.ACTION_DOWNLOAD_COMPLETE -> {
                    val modelId = intent.getStringExtra(ModelDownloadService.EXTRA_MODEL_ID) ?: return
                    val success = intent.getBooleanExtra(ModelDownloadService.EXTRA_SUCCESS, false)
                    val error = intent.getStringExtra(ModelDownloadService.EXTRA_ERROR) ?: ""
                    runJs(
                        "window.onModelDownloadComplete && window.onModelDownloadComplete(" +
                                "${jsonStr(modelId)}, $success, ${jsonStr(error)})"
                    )
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ModelDownloadService.ACTION_DOWNLOAD_PROGRESS)
            addAction(ModelDownloadService.ACTION_DOWNLOAD_COMPLETE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(downloadReceiver, filter)
        }
    }

    // ── Model catalogue & status ───────────────────────────────────────

    /**
     * Returns a JSON array describing every model in the catalogue,
     * including its on-device download/load status.
     */
    @JavascriptInterface
    fun getModelList(): String {
        val arr = JSONArray()
        ModelCatalogue.models.forEach { info ->
            val status = statusOf(info)
            arr.put(JSONObject().apply {
                put("id", info.id)
                put("name", info.name)
                put("sizeLabel", info.sizeLabel)
                put("fileSizeMb", info.fileSizeMb)
                put("description", info.description)
                put("status", status.name)
                put("isLoaded", info.id == loadedModelId)
            })
        }
        return arr.toString()
    }

    // ── Download ───────────────────────────────────────────────────────

    /** Start downloading the model with [modelId]. */
    @JavascriptInterface
    fun downloadModel(modelId: String) {
        val info = ModelCatalogue.findById(modelId) ?: return
        if (modelManager.isDownloaded(info)) {
            runJs("window.onModelDownloadComplete && window.onModelDownloadComplete(${jsonStr(modelId)}, true, '')")
            return
        }
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, modelId)
        }
        context.startForegroundService(intent)
    }

    /** Delete a downloaded model from disk. */
    @JavascriptInterface
    fun deleteModel(modelId: String) {
        if (modelId == loadedModelId) {
            engine.release()
            loadedModelId = null
        }
        val info = ModelCatalogue.findById(modelId) ?: return
        modelManager.deleteModel(info)
        runJs("window.onModelDeleted && window.onModelDeleted(${jsonStr(modelId)})")
    }

    // ── Load / Unload ──────────────────────────────────────────────────

    /**
     * Load the model identified by [modelId] into the MNN engine.
     * Result delivered via `window.onModelLoaded(id, success, errorMsg)`.
     */
    @JavascriptInterface
    fun loadModel(modelId: String) {
        scope.launch {
            val info = ModelCatalogue.findById(modelId)
            if (info == null) {
                runJs("window.onModelLoaded && window.onModelLoaded(${jsonStr(modelId)}, false, 'Unknown model id')")
                return@launch
            }
            if (!modelManager.isDownloaded(info)) {
                runJs("window.onModelLoaded && window.onModelLoaded(${jsonStr(modelId)}, false, '模型尚未下载')")
                return@launch
            }
            try {
                // Release any previously loaded model
                if (engine.isLoaded) {
                    withContext(Dispatchers.IO) { engine.release() }
                    loadedModelId = null
                }
                withContext(Dispatchers.IO) {
                    engine.loadModel(modelManager.configPath(info))
                }
                loadedModelId = modelId
                runJs("window.onModelLoaded && window.onModelLoaded(${jsonStr(modelId)}, true, '')")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model $modelId", e)
                runJs("window.onModelLoaded && window.onModelLoaded(${jsonStr(modelId)}, false, ${jsonStr(e.message ?: "Unknown error")})")
            }
        }
    }

    /** Unload the currently active model and free native memory. */
    @JavascriptInterface
    fun unloadModel() {
        if (engine.isLoaded) {
            scope.launch(Dispatchers.IO) {
                engine.release()
                loadedModelId = null
            }
        }
    }

    /** Returns the id of the currently loaded model, or an empty string. */
    @JavascriptInterface
    fun getLoadedModel(): String = loadedModelId ?: ""

    /** Returns "true" if the MNN native library is present on this device/build. */
    @JavascriptInterface
    fun isNativeAvailable(): Boolean = MNNLLMEngine.nativeAvailable

    // ── Inference ─────────────────────────────────────────────────────

    /**
     * Run streaming chat inference.
     *
     * @param callbackId  Opaque string the JS layer uses to match callbacks.
     * @param messagesJson  JSON array of `{role, content}` objects (OpenAI format).
     *
     * Tokens are delivered one-by-one via:
     * ```js
     * window.onMNNToken(callbackId, token, isDone)
     * ```
     */
    @JavascriptInterface
    fun chat(callbackId: String, messagesJson: String) {
        cancelRequested.set(false)
        chatJob?.cancel()
        chatJob = scope.launch {
            if (!engine.isLoaded) {
                runJs(
                    "window.onMNNToken && window.onMNNToken(${jsonStr(callbackId)}, " +
                            "'[错误] 没有已加载的模型，请先下载并加载一个本地模型', true)"
                )
                return@launch
            }
            try {
                engine.chatStreaming(messagesJson) { token, isDone ->
                    if (!cancelRequested.get()) {
                        runJs(
                            "window.onMNNToken && window.onMNNToken(" +
                                    "${jsonStr(callbackId)}, ${jsonStr(token)}, $isDone)"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Inference error", e)
                runJs(
                    "window.onMNNToken && window.onMNNToken(${jsonStr(callbackId)}, " +
                            "${jsonStr("[推理错误] ${e.message}")}, true)"
                )
            }
        }
    }

    /** Abort the currently running inference. */
    @JavascriptInterface
    fun cancelChat() {
        cancelRequested.set(true)
        chatJob?.cancel()
    }

    // ── CORS proxy ────────────────────────────────────────────────────

    /**
     * Proxy an API POST request through the native HTTP client, bypassing
     * browser CORS restrictions.
     *
     * @param callbackId    Opaque string used to route the response back in JS.
     * @param url           Full URL to call.
     * @param headersJson   JSON object of request headers.
     * @param bodyJson      Request body as a JSON string.
     *
     * Result delivered via:
     * ```js
     * window.onProxyResponse(callbackId, statusCode, responseBodyJson)
     * window.onProxyError(callbackId, errorMessage)
     * ```
     */
    @JavascriptInterface
    fun proxyPost(callbackId: String, url: String, headersJson: String, bodyJson: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val headers = JSONObject(headersJson)
                val reqBuilder = Request.Builder()
                    .url(url)
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))

                headers.keys().forEach { key ->
                    reqBuilder.addHeader(key, headers.getString(key))
                }

                val response = httpClient.newCall(reqBuilder.build()).execute()
                val body = response.body?.string() ?: ""
                val status = response.code

                withContext(Dispatchers.Main) {
                    runJs(
                        "window.onProxyResponse && window.onProxyResponse(" +
                                "${jsonStr(callbackId)}, $status, ${jsonStr(body)})"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "proxyPost failed", e)
                withContext(Dispatchers.Main) {
                    runJs(
                        "window.onProxyError && window.onProxyError(" +
                                "${jsonStr(callbackId)}, ${jsonStr(e.message ?: "Network error")})"
                    )
                }
            }
        }
    }

    // ── Cleanup ────────────────────────────────────────────────────────

    fun destroy() {
        try {
            context.unregisterReceiver(downloadReceiver)
        } catch (_: Exception) {}
        chatJob?.cancel()
        engine.release()
    }

    // ── Internal helpers ───────────────────────────────────────────────

    private fun statusOf(info: ModelInfo): ModelStatus = when {
        info.id == loadedModelId -> ModelStatus.LOADED
        modelManager.isDownloaded(info) -> ModelStatus.DOWNLOADED
        else -> ModelStatus.NOT_DOWNLOADED
    }

    /** Runs a JS expression on the UI thread via [webView.evaluateJavascript]. */
    private fun runJs(js: String) {
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    /** Returns a JSON-safe double-quoted string. */
    private fun jsonStr(value: String): String = JSONObject.quote(value)

    companion object {
        private const val TAG = "AppJsBridge"
    }
}
