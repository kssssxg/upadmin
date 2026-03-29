package com.me.chat.ai.up.admin.mnn

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Method

/**
 * Kotlin wrapper around the MNN-LLM runtime, provided by the
 * `com.alibaba.android:mnn` AAR dependency.
 *
 * The MNN Android SDK bundles native LLM inference (libmnn_llm) together
 * with Java/JNI bridge classes.  This class discovers those classes via
 * reflection at runtime so that the rest of the app can compile and run
 * even on devices / build configurations where the MNN library is absent.
 *
 * ### How native inference is enabled
 *
 * The MNN AAR ships a Java class (typically `com.alibaba.android.mnn.MNNLlm`)
 * that exposes static JNI methods:
 *   - `initLlm(configPath: String, enableLog: Boolean): Long`  → session handle
 *   - `runLlm(session: Long, input: String, isThinking: Boolean): String` → response
 *   - `resetLlm(session: Long)`
 *   - `releaseLlm(session: Long)`
 *
 * If those are absent (older AAR version / stripped build), `nativeAvailable`
 * stays false and every call throws, falling back to cloud API mode.
 */
class MNNLLMEngine {

    /** Opaque native session handle; 0 when no model is loaded. */
    private var sessionHandle: Long = 0L

    /** True once a model has been loaded and the session is ready. */
    val isLoaded: Boolean get() = sessionHandle != 0L

    /**
     * Load a model from [configPath] (absolute path to `config.json`).
     *
     * @throws RuntimeException if the MNN native library is unavailable or
     *                          if the model fails to load.
     */
    @Throws(Exception::class)
    suspend fun loadModel(configPath: String): Unit = withContext(Dispatchers.IO) {
        if (isLoaded) throw IllegalStateException("A model is already loaded. Call release() first.")
        if (!nativeAvailable || mnnInitFn == null) {
            throw RuntimeException("MNN native library is not available on this build.")
        }
        Log.i(TAG, "Loading model from: $configPath")
        sessionHandle = mnnInitFn!!.invoke(null, configPath, false) as? Long
            ?: throw RuntimeException("MNN initLlm returned null for $configPath")
        if (sessionHandle == 0L) {
            throw RuntimeException("MNN failed to load model at $configPath")
        }
        Log.i(TAG, "Model loaded, session=$sessionHandle")
    }

    /**
     * Run streaming inference.
     *
     * Calls [onToken] on [Dispatchers.IO] for each chunk as it arrives.
     * MNN 2.8 returns the whole response at once; this method reports it
     * as a single final token so callers need not change.
     *
     * @param messages  OpenAI-style JSON messages array.
     * @param onToken   Called with `(token, isDone)`.
     */
    @Throws(Exception::class)
    suspend fun chatStreaming(
        messages: String,
        onToken: (token: String, isDone: Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        requireLoaded()
        val prompt = extractLastUserContent(messages)
        val response = mnnRunFn!!.invoke(null, sessionHandle, prompt, false) as? String ?: ""
        onToken(response, true)
    }

    /** Reset conversation KV-cache (start a new turn). */
    fun reset() {
        if (isLoaded) {
            try { mnnResetFn?.invoke(null, sessionHandle) } catch (e: Exception) {
                Log.w(TAG, "reset failed: ${e.message}")
            }
        }
    }

    /** Unload the model and free native memory. */
    fun release() {
        if (isLoaded) {
            try { mnnReleaseFn?.invoke(null, sessionHandle) } catch (e: Exception) {
                Log.w(TAG, "release failed: ${e.message}")
            }
            sessionHandle = 0L
            Log.i(TAG, "Model released")
        }
    }

    // ── Private helpers ────────────────────────────────────────────────

    private fun requireLoaded() {
        check(isLoaded) { "No model loaded. Call loadModel() first." }
        check(nativeAvailable && mnnRunFn != null) { "MNN native library is not available." }
    }

    /**
     * Extract the content of the last user message from an OpenAI-style
     * JSON messages array. Falls back to the whole string if parsing fails.
     */
    private fun extractLastUserContent(messagesJson: String): String {
        try {
            val arr = org.json.JSONArray(messagesJson)
            for (i in arr.length() - 1 downTo 0) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("role") == "user") {
                    return obj.optString("content", messagesJson)
                }
            }
        } catch (_: Exception) {}
        return messagesJson
    }

    // ── Static / companion ─────────────────────────────────────────────

    companion object {
        private const val TAG = "MNNLLMEngine"

        /** True when the MNN LLM native library is available at runtime. */
        @JvmField
        var nativeAvailable: Boolean = false

        private var mnnInitFn: Method? = null
        private var mnnRunFn: Method? = null
        private var mnnResetFn: Method? = null
        private var mnnReleaseFn: Method? = null

        init {
            // Try to locate the MNN LLM Java bridge class provided by the AAR.
            // MNN SDK versions differ on the exact class name; try common candidates.
            val candidates = listOf(
                "com.alibaba.android.mnn.MNNLlm",
                "com.alibaba.android.mnn.MNNLLMEngine",
                "com.alibaba.android.mnn.LlmSession"
            )
            for (className in candidates) {
                try {
                    val cls = Class.forName(className)
                    mnnInitFn    = cls.getMethod("initLlm",    String::class.java, Boolean::class.java)
                    mnnRunFn     = cls.getMethod("runLlm",     Long::class.javaPrimitiveType!!, String::class.java, Boolean::class.java)
                    mnnResetFn   = cls.getMethod("resetLlm",   Long::class.javaPrimitiveType!!)
                    mnnReleaseFn = cls.getMethod("releaseLlm", Long::class.javaPrimitiveType!!)
                    nativeAvailable = true
                    Log.i(TAG, "MNN LLM native bridge found: $className")
                    break
                } catch (e: Exception) {
                    Log.d(TAG, "MNN class '$className' not found: ${e.message}")
                }
            }
            if (!nativeAvailable) {
                Log.w(TAG, "MNN LLM native bridge not found – local inference disabled")
            }
        }
    }
}

