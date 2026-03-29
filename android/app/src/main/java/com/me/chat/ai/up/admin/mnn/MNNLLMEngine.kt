package com.me.chat.ai.up.admin.mnn

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kotlin wrapper around the MNN-LLM native library.
 *
 * The MNN-LLM engine exposes a thin JNI surface. This class manages the
 * native session handle and routes all inference calls through coroutines
 * so the UI thread is never blocked.
 *
 * ## Integrating the native library
 *
 * 1. Download the MNN Android release from
 *    https://github.com/alibaba/MNN/releases (look for `MNN-Android-*.zip`).
 * 2. Copy the `.so` files for `arm64-v8a` / `armeabi-v7a` into
 *    `app/src/main/jniLibs/<abi>/`:
 *    - `libMNN.so`
 *    - `libMNN_Express.so`
 *    - `libmnn_llm.so`
 * 3. Ensure `build.gradle.kts` lists those ABIs in `ndk { abiFilters }`.
 *
 * The `System.loadLibrary` calls below are guarded so that the rest of
 * the app can still compile and run on a simulator / build server even
 * when the `.so` files are absent.
 */
class MNNLLMEngine {

    /** Opaque handle returned by the native `create()` function. */
    private var nativeHandle: Long = 0L

    /** True once a model has been successfully loaded. */
    val isLoaded: Boolean get() = nativeHandle != 0L

    /**
     * Load a model from [configPath] (absolute path to `config.json`).
     *
     * @throws IllegalStateException if a model is already loaded.
     * @throws RuntimeException if the native library fails to initialise.
     */
    @Throws(Exception::class)
    suspend fun loadModel(configPath: String): Unit = withContext(Dispatchers.IO) {
        if (isLoaded) throw IllegalStateException("A model is already loaded. Call release() first.")
        if (!nativeAvailable) throw RuntimeException("MNN native library is not available on this device/build.")

        Log.i(TAG, "Loading model from: $configPath")
        nativeHandle = nativeCreate(configPath)
        if (nativeHandle == 0L) throw RuntimeException("MNN failed to load model at $configPath")
        Log.i(TAG, "Model loaded, handle=$nativeHandle")
    }

    /**
     * Run a single-turn inference and return the complete reply.
     *
     * For streaming output use [chatStreaming].
     */
    @Throws(Exception::class)
    suspend fun chat(messages: String): String = withContext(Dispatchers.IO) {
        requireLoaded()
        nativeChat(nativeHandle, messages)
    }

    /**
     * Run streaming inference.  [onToken] is called on [Dispatchers.IO]
     * for each token as it is produced.
     *
     * @param messages  JSON-serialised OpenAI-style messages array.
     * @param onToken   Callback invoked with (token, isDone).
     */
    @Throws(Exception::class)
    suspend fun chatStreaming(
        messages: String,
        onToken: (token: String, isDone: Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        requireLoaded()
        nativeChatWithCallback(nativeHandle, messages) { token, isDone ->
            onToken(token, isDone)
        }
    }

    /** Reset the conversation context (clear KV cache). */
    fun reset() {
        if (isLoaded) nativeReset(nativeHandle)
    }

    /** Unload the model and free native memory. */
    fun release() {
        if (isLoaded) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
            Log.i(TAG, "Model released")
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────

    private fun requireLoaded() {
        check(isLoaded) { "No model loaded. Call loadModel() first." }
    }

    // ──────────────────────────────────────────────────────────────────
    // Native declarations
    // ──────────────────────────────────────────────────────────────────

    /** Functional interface matching the JNI callback signature. */
    fun interface TokenCallback {
        fun onToken(token: String, isDone: Boolean)
    }

    private external fun nativeCreate(configPath: String): Long
    private external fun nativeChat(handle: Long, messages: String): String
    private external fun nativeChatWithCallback(
        handle: Long,
        messages: String,
        callback: TokenCallback
    )
    private external fun nativeReset(handle: Long)
    private external fun nativeRelease(handle: Long)

    companion object {
        private const val TAG = "MNNLLMEngine"

        /** Whether the native library loaded successfully at class-init time. */
        var nativeAvailable: Boolean = false
            private set

        init {
            try {
                System.loadLibrary("MNN")
                // MNN_Express is embedded into libMNN in MNN 3.x; attempt to load
                // but ignore the failure so older split-library builds also work.
                try {
                    System.loadLibrary("MNN_Express")
                } catch (_: UnsatisfiedLinkError) {
                    // Integrated into libMNN in newer builds — not a fatal error.
                }
                System.loadLibrary("mnn_llm")
                // JNI wrapper that exposes the native* methods above
                System.loadLibrary("mnn_jni")
                nativeAvailable = true
                Log.i(TAG, "MNN native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "MNN native library not found – local inference disabled: ${e.message}")
            }
        }
    }
}
