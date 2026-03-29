package com.me.chat.ai.up.admin.mnn

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manages the lifecycle of downloaded model files and exposes their
 * on-disk status to the rest of the application.
 */
class ModelManager(private val context: Context) {

    /** Root directory where all model packages are stored. */
    val modelsDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    /** Returns the extracted model directory for [modelInfo], or null if not present. */
    fun modelDir(modelInfo: ModelInfo): File =
        File(modelsDir, modelInfo.id)

    /** Returns true when the model package has been fully extracted on disk. */
    fun isDownloaded(modelInfo: ModelInfo): Boolean {
        val dir = modelDir(modelInfo)
        // A valid MNN model directory contains at least a config.json and *.mnn file
        return dir.exists() && dir.isDirectory &&
                dir.listFiles()?.any { it.extension == "mnn" || it.name == "config.json" } == true
    }

    /** Deletes the model directory from disk. */
    fun deleteModel(modelInfo: ModelInfo): Boolean {
        val dir = modelDir(modelInfo)
        return if (dir.exists()) {
            dir.deleteRecursively().also {
                Log.i(TAG, "Deleted model ${modelInfo.id}: $it")
            }
        } else {
            true
        }
    }

    /**
     * Returns the path of the config file expected by the MNN LLM runtime.
     * The MNN-LLM engine uses a `config.json` in the model directory.
     */
    fun configPath(modelInfo: ModelInfo): String =
        File(modelDir(modelInfo), "config.json").absolutePath

    /** Lists all model IDs that are currently extracted on disk. */
    fun listDownloadedModelIds(): List<String> {
        return modelsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?: emptyList()
    }

    companion object {
        private const val TAG = "ModelManager"
    }
}
