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
 * All models are downloaded from the taobao-mnn namespace on ModelScope
 * (魔搭社区 modelscope.cn) — accessible from mainland China without a VPN.
 * Each ZIP contains MNN INT4-quantised weights + config.json ready for
 * MNN-LLM runtime inference.
 *
 * Coverage: DeepSeek-R1 Distill, Qwen2.5, Qwen3 — 0.5 B → 9 B.
 */
object ModelCatalogue {

    private const val MS = "https://modelscope.cn/models/taobao-mnn"

    val models: List<ModelInfo> = listOf(

        // ════════════════════════════════════════════════════════════════
        // DeepSeek-R1 Distill (Qwen-base, reasoning-enhanced)
        // ════════════════════════════════════════════════════════════════

        ModelInfo(
            id = "deepseek-r1-distill-qwen-1.5b",
            name = "DeepSeek-R1 Distill Qwen 1.5B",
            sizeLabel = "1.5B",
            fileSizeMb = 1100,
            downloadUrl = "$MS/DeepSeek-R1-Distill-Qwen-1.5B-MNN/resolve/master/DeepSeek-R1-Distill-Qwen-1.5B-MNN.zip",
            fileName = "DeepSeek-R1-Distill-Qwen-1.5B-MNN.zip",
            description = "DeepSeek-R1 推理增强蒸馏 1.5B，逻辑推理能力强"
        ),

        ModelInfo(
            id = "deepseek-r1-distill-qwen-7b",
            name = "DeepSeek-R1 Distill Qwen 7B",
            sizeLabel = "7B",
            fileSizeMb = 4800,
            downloadUrl = "$MS/DeepSeek-R1-Distill-Qwen-7B-MNN/resolve/master/DeepSeek-R1-Distill-Qwen-7B-MNN.zip",
            fileName = "DeepSeek-R1-Distill-Qwen-7B-MNN.zip",
            description = "DeepSeek-R1 推理增强蒸馏 7B，旗舰推理模型，需约 6 GB RAM"
        ),

        ModelInfo(
            id = "deepseek-r1-distill-llama-8b",
            name = "DeepSeek-R1 Distill Llama 8B",
            sizeLabel = "8B",
            fileSizeMb = 5200,
            downloadUrl = "$MS/DeepSeek-R1-Distill-Llama-8B-MNN/resolve/master/DeepSeek-R1-Distill-Llama-8B-MNN.zip",
            fileName = "DeepSeek-R1-Distill-Llama-8B-MNN.zip",
            description = "DeepSeek-R1 推理增强蒸馏 Llama 8B，英中双语推理，需约 8 GB RAM"
        ),

        // ════════════════════════════════════════════════════════════════
        // Qwen3 (2025 generation — recommended for chat)
        // ════════════════════════════════════════════════════════════════

        ModelInfo(
            id = "qwen3-0.6b",
            name = "Qwen3 0.6B",
            sizeLabel = "0.6B",
            fileSizeMb = 500,
            downloadUrl = "$MS/Qwen3-0.6B-MNN/resolve/master/Qwen3-0.6B-MNN.zip",
            fileName = "Qwen3-0.6B-MNN.zip",
            description = "超轻量 Qwen3 0.6B，速度最快，适合低端设备"
        ),

        ModelInfo(
            id = "qwen3-1.7b",
            name = "Qwen3 1.7B",
            sizeLabel = "1.7B",
            fileSizeMb = 1200,
            downloadUrl = "$MS/Qwen3-1.7B-MNN/resolve/master/Qwen3-1.7B-MNN.zip",
            fileName = "Qwen3-1.7B-MNN.zip",
            description = "轻量 Qwen3 1.7B，速度与效果兼顾"
        ),

        ModelInfo(
            id = "qwen3-4b",
            name = "Qwen3 4B",
            sizeLabel = "4B",
            fileSizeMb = 2800,
            downloadUrl = "$MS/Qwen3-4B-MNN/resolve/master/Qwen3-4B-MNN.zip",
            fileName = "Qwen3-4B-MNN.zip",
            description = "中量 Qwen3 4B，效果优秀，需约 4 GB RAM"
        ),

        ModelInfo(
            id = "qwen3-8b",
            name = "Qwen3 8B",
            sizeLabel = "8B",
            fileSizeMb = 5300,
            downloadUrl = "$MS/Qwen3-8B-MNN/resolve/master/Qwen3-8B-MNN.zip",
            fileName = "Qwen3-8B-MNN.zip",
            description = "Qwen3 8B 旗舰聊天模型，需约 7 GB RAM"
        ),

        // ════════════════════════════════════════════════════════════════
        // Qwen2.5 (stable generation — broad compatibility)
        // ════════════════════════════════════════════════════════════════

        ModelInfo(
            id = "qwen2.5-0.5b",
            name = "Qwen2.5 0.5B",
            sizeLabel = "0.5B",
            fileSizeMb = 450,
            downloadUrl = "$MS/Qwen2.5-0.5B-Instruct-MNN/resolve/master/Qwen2.5-0.5B-Instruct-MNN.zip",
            fileName = "Qwen2.5-0.5B-Instruct-MNN.zip",
            description = "Qwen2.5 超轻量 0.5B，速度极快，适合入门设备"
        ),

        ModelInfo(
            id = "qwen2.5-1.5b",
            name = "Qwen2.5 1.5B",
            sizeLabel = "1.5B",
            fileSizeMb = 1100,
            downloadUrl = "$MS/Qwen2.5-1.5B-Instruct-MNN/resolve/master/Qwen2.5-1.5B-Instruct-MNN.zip",
            fileName = "Qwen2.5-1.5B-Instruct-MNN.zip",
            description = "Qwen2.5 轻量 1.5B，平衡速度与效果"
        ),

        ModelInfo(
            id = "qwen2.5-3b",
            name = "Qwen2.5 3B",
            sizeLabel = "3B",
            fileSizeMb = 2100,
            downloadUrl = "$MS/Qwen2.5-3B-Instruct-MNN/resolve/master/Qwen2.5-3B-Instruct-MNN.zip",
            fileName = "Qwen2.5-3B-Instruct-MNN.zip",
            description = "Qwen2.5 中量 3B，效果较好，需约 4 GB RAM"
        ),

        ModelInfo(
            id = "qwen2.5-7b",
            name = "Qwen2.5 7B",
            sizeLabel = "7B",
            fileSizeMb = 4800,
            downloadUrl = "$MS/Qwen2.5-7B-Instruct-MNN/resolve/master/Qwen2.5-7B-Instruct-MNN.zip",
            fileName = "Qwen2.5-7B-Instruct-MNN.zip",
            description = "Qwen2.5 旗舰 7B，综合效果最佳，需约 6 GB RAM"
        )
    )

    fun findById(id: String): ModelInfo? = models.find { it.id == id }
}
