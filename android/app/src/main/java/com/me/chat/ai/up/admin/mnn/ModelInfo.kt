package com.me.chat.ai.up.admin.mnn

/**
 * Metadata for a locally runnable LLM model.
 *
 * @param id          Unique identifier used for storage and selection.
 * @param name        Human-readable display name shown in the UI.
 * @param sizeLabel   Approximate parameter size label, e.g. "0.5B", "1.5B", "7B".
 * @param fileSizeMb  Approximate model file size in megabytes (for UI display).
 * @param downloadUrl HTTPS URL to download the MNN model package (.zip or .mnn).
 * @param fileName    Local filename saved under the app's model directory.
 * @param description Brief description shown in the model-picker panel.
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val sizeLabel: String,
    val fileSizeMb: Int,
    val downloadUrl: String,
    val fileName: String,
    val description: String
)

/**
 * Runtime status of a model on this device.
 */
enum class ModelStatus {
    /** Not downloaded yet. */
    NOT_DOWNLOADED,
    /** Currently being downloaded. */
    DOWNLOADING,
    /** Downloaded but not yet loaded into memory. */
    DOWNLOADED,
    /** Loaded into memory and ready to use. */
    LOADED
}

/**
 * Snapshot of a model's status, used to build the JSON payload
 * returned to the JavaScript layer.
 */
data class ModelState(
    val info: ModelInfo,
    val status: ModelStatus,
    /** Download progress 0–100, only meaningful when status == DOWNLOADING. */
    val downloadProgress: Int = 0
)

/**
 * Catalogue of built-in downloadable MNN-format models.
 *
 * Models are sourced from the taobao-mnn organisation on Hugging Face and
 * the official MNN-LLM ModelScope repository.  Each entry provides a
 * complete, versioned download URL so users can run inference fully
 * offline after the initial download.
 */
object ModelCatalogue {

    val models: List<ModelInfo> = listOf(

        // ── 0.5 B ────────────────────────────────────────────────────────
        ModelInfo(
            id = "qwen2.5-0.5b-int4",
            name = "Qwen2.5 0.5B (INT4)",
            sizeLabel = "0.5B",
            fileSizeMb = 450,
            downloadUrl = "https://huggingface.co/taobao-mnn/Qwen2.5-0.5B-Instruct-MNN/resolve/main/Qwen2.5-0.5B-Instruct-MNN.zip",
            fileName = "Qwen2.5-0.5B-Instruct-MNN.zip",
            description = "超轻量模型，速度最快，适合低端设备"
        ),

        // ── 1.5 B ────────────────────────────────────────────────────────
        ModelInfo(
            id = "qwen2.5-1.5b-int4",
            name = "Qwen2.5 1.5B (INT4)",
            sizeLabel = "1.5B",
            fileSizeMb = 1100,
            downloadUrl = "https://huggingface.co/taobao-mnn/Qwen2.5-1.5B-Instruct-MNN/resolve/main/Qwen2.5-1.5B-Instruct-MNN.zip",
            fileName = "Qwen2.5-1.5B-Instruct-MNN.zip",
            description = "轻量级模型，平衡速度与效果"
        ),

        // ── 3 B ──────────────────────────────────────────────────────────
        ModelInfo(
            id = "qwen2.5-3b-int4",
            name = "Qwen2.5 3B (INT4)",
            sizeLabel = "3B",
            fileSizeMb = 2100,
            downloadUrl = "https://huggingface.co/taobao-mnn/Qwen2.5-3B-Instruct-MNN/resolve/main/Qwen2.5-3B-Instruct-MNN.zip",
            fileName = "Qwen2.5-3B-Instruct-MNN.zip",
            description = "中型模型，效果较好，需要约 4 GB RAM"
        ),

        // ── 7 B ──────────────────────────────────────────────────────────
        ModelInfo(
            id = "qwen2.5-7b-int4",
            name = "Qwen2.5 7B (INT4)",
            sizeLabel = "7B",
            fileSizeMb = 4800,
            downloadUrl = "https://huggingface.co/taobao-mnn/Qwen2.5-7B-Instruct-MNN/resolve/main/Qwen2.5-7B-Instruct-MNN.zip",
            fileName = "Qwen2.5-7B-Instruct-MNN.zip",
            description = "旗舰本地模型，效果最佳，需要约 6 GB RAM"
        ),

        // ── Llama 3.2 1B (backup English-capable model) ──────────────────
        ModelInfo(
            id = "llama3.2-1b-int4",
            name = "Llama 3.2 1B (INT4)",
            sizeLabel = "1B",
            fileSizeMb = 800,
            downloadUrl = "https://huggingface.co/taobao-mnn/Llama-3.2-1B-Instruct-MNN/resolve/main/Llama-3.2-1B-Instruct-MNN.zip",
            fileName = "Llama-3.2-1B-Instruct-MNN.zip",
            description = "Meta Llama 3.2 1B，英文支持强"
        ),

        // ── Llama 3.2 3B ─────────────────────────────────────────────────
        ModelInfo(
            id = "llama3.2-3b-int4",
            name = "Llama 3.2 3B (INT4)",
            sizeLabel = "3B",
            fileSizeMb = 2000,
            downloadUrl = "https://huggingface.co/taobao-mnn/Llama-3.2-3B-Instruct-MNN/resolve/main/Llama-3.2-3B-Instruct-MNN.zip",
            fileName = "Llama-3.2-3B-Instruct-MNN.zip",
            description = "Meta Llama 3.2 3B，推理能力强"
        )
    )

    fun findById(id: String): ModelInfo? = models.find { it.id == id }
}
