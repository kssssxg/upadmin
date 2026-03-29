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
 * Catalogue of all downloadable MNN-format models published by the
 * MNN team on ModelScope (魔搭社区, modelscope.cn/organization/MNN).
 *
 * All models are INT4-quantised MNN weights + config.json packaged as a ZIP.
 * They are downloaded to the app's private files directory and run fully
 * on-device via the MNN LLM runtime.
 *
 * ModelScope API download URL format:
 *   https://modelscope.cn/models/MNN/<repo>/resolve/master/<file.zip>
 */
object ModelCatalogue {

    private const val MS = "https://modelscope.cn/models/MNN"

    private fun zip(repo: String): String = "$MS/$repo/resolve/master/$repo.zip"

    val models: List<ModelInfo> = listOf(

        // ═══════════════════════════════════════════════════════════════
        // Qwen3  (2025 generation – best overall quality)
        // ═══════════════════════════════════════════════════════════════

        ModelInfo(
            id = "qwen3-0.6b",
            name = "Qwen3 0.6B",
            sizeLabel = "0.6B",
            fileSizeMb = 500,
            downloadUrl = zip("Qwen3-0.6B-MNN"),
            fileName = "Qwen3-0.6B-MNN.zip",
            description = "超轻量 Qwen3，速度极快，适合低端设备"
        ),
        ModelInfo(
            id = "qwen3-1.7b",
            name = "Qwen3 1.7B",
            sizeLabel = "1.7B",
            fileSizeMb = 1200,
            downloadUrl = zip("Qwen3-1.7B-MNN"),
            fileName = "Qwen3-1.7B-MNN.zip",
            description = "轻量 Qwen3，速度与效果兼顾"
        ),
        ModelInfo(
            id = "qwen3-4b",
            name = "Qwen3 4B",
            sizeLabel = "4B",
            fileSizeMb = 2800,
            downloadUrl = zip("Qwen3-4B-MNN"),
            fileName = "Qwen3-4B-MNN.zip",
            description = "中量 Qwen3，效果优秀，需约 4 GB RAM"
        ),
        ModelInfo(
            id = "qwen3-8b",
            name = "Qwen3 8B",
            sizeLabel = "8B",
            fileSizeMb = 5300,
            downloadUrl = zip("Qwen3-8B-MNN"),
            fileName = "Qwen3-8B-MNN.zip",
            description = "Qwen3 旗舰聊天模型，需约 7 GB RAM"
        ),
        ModelInfo(
            id = "qwen3-14b",
            name = "Qwen3 14B",
            sizeLabel = "14B",
            fileSizeMb = 9000,
            downloadUrl = zip("Qwen3-14B-MNN"),
            fileName = "Qwen3-14B-MNN.zip",
            description = "Qwen3 高参数量模型，需约 12 GB RAM"
        ),

        // ═══════════════════════════════════════════════════════════════
        // Qwen2.5  (stable, broad compatibility)
        // ═══════════════════════════════════════════════════════════════

        ModelInfo(
            id = "qwen2.5-0.5b",
            name = "Qwen2.5 0.5B",
            sizeLabel = "0.5B",
            fileSizeMb = 450,
            downloadUrl = zip("Qwen2.5-0.5B-Instruct-MNN"),
            fileName = "Qwen2.5-0.5B-Instruct-MNN.zip",
            description = "Qwen2.5 超轻量，速度极快，适合入门设备"
        ),
        ModelInfo(
            id = "qwen2.5-1.5b",
            name = "Qwen2.5 1.5B",
            sizeLabel = "1.5B",
            fileSizeMb = 1100,
            downloadUrl = zip("Qwen2.5-1.5B-Instruct-MNN"),
            fileName = "Qwen2.5-1.5B-Instruct-MNN.zip",
            description = "Qwen2.5 轻量，速度与效果兼顾"
        ),
        ModelInfo(
            id = "qwen2.5-3b",
            name = "Qwen2.5 3B",
            sizeLabel = "3B",
            fileSizeMb = 2100,
            downloadUrl = zip("Qwen2.5-3B-Instruct-MNN"),
            fileName = "Qwen2.5-3B-Instruct-MNN.zip",
            description = "Qwen2.5 中量，效果较好，需约 4 GB RAM"
        ),
        ModelInfo(
            id = "qwen2.5-7b",
            name = "Qwen2.5 7B",
            sizeLabel = "7B",
            fileSizeMb = 4800,
            downloadUrl = zip("Qwen2.5-7B-Instruct-MNN"),
            fileName = "Qwen2.5-7B-Instruct-MNN.zip",
            description = "Qwen2.5 旗舰，综合效果最佳，需约 6 GB RAM"
        ),
        ModelInfo(
            id = "qwen2.5-14b",
            name = "Qwen2.5 14B",
            sizeLabel = "14B",
            fileSizeMb = 9000,
            downloadUrl = zip("Qwen2.5-14B-Instruct-MNN"),
            fileName = "Qwen2.5-14B-Instruct-MNN.zip",
            description = "Qwen2.5 大参数量，需约 12 GB RAM"
        ),

        // ═══════════════════════════════════════════════════════════════
        // DeepSeek-R1 Distill  (reasoning-enhanced)
        // ═══════════════════════════════════════════════════════════════

        ModelInfo(
            id = "deepseek-r1-distill-qwen-1.5b",
            name = "DeepSeek-R1 Distill Qwen 1.5B",
            sizeLabel = "1.5B",
            fileSizeMb = 1100,
            downloadUrl = zip("DeepSeek-R1-Distill-Qwen-1.5B-MNN"),
            fileName = "DeepSeek-R1-Distill-Qwen-1.5B-MNN.zip",
            description = "DeepSeek-R1 推理蒸馏 1.5B，逻辑推理能力强"
        ),
        ModelInfo(
            id = "deepseek-r1-distill-qwen-7b",
            name = "DeepSeek-R1 Distill Qwen 7B",
            sizeLabel = "7B",
            fileSizeMb = 4800,
            downloadUrl = zip("DeepSeek-R1-Distill-Qwen-7B-MNN"),
            fileName = "DeepSeek-R1-Distill-Qwen-7B-MNN.zip",
            description = "DeepSeek-R1 推理蒸馏 7B，旗舰推理，需约 6 GB RAM"
        ),
        ModelInfo(
            id = "deepseek-r1-distill-llama-8b",
            name = "DeepSeek-R1 Distill Llama 8B",
            sizeLabel = "8B",
            fileSizeMb = 5200,
            downloadUrl = zip("DeepSeek-R1-Distill-Llama-8B-MNN"),
            fileName = "DeepSeek-R1-Distill-Llama-8B-MNN.zip",
            description = "DeepSeek-R1 Llama 蒸馏 8B，英中双语推理，需约 8 GB RAM"
        ),

        // ═══════════════════════════════════════════════════════════════
        // Llama 3.2  (Meta, multilingual)
        // ═══════════════════════════════════════════════════════════════

        ModelInfo(
            id = "llama3.2-1b",
            name = "Llama 3.2 1B",
            sizeLabel = "1B",
            fileSizeMb = 800,
            downloadUrl = zip("Llama3.2-1B-Instruct-MNN"),
            fileName = "Llama3.2-1B-Instruct-MNN.zip",
            description = "Meta Llama 3.2 1B，轻量多语言，速度快"
        ),
        ModelInfo(
            id = "llama3.2-3b",
            name = "Llama 3.2 3B",
            sizeLabel = "3B",
            fileSizeMb = 2200,
            downloadUrl = zip("Llama3.2-3B-Instruct-MNN"),
            fileName = "Llama3.2-3B-Instruct-MNN.zip",
            description = "Meta Llama 3.2 3B，多语言效果好，需约 4 GB RAM"
        ),

        // ═══════════════════════════════════════════════════════════════
        // MiniCPM  (OpenBMB, mobile-optimised)
        // ═══════════════════════════════════════════════════════════════

        ModelInfo(
            id = "minicpm3-4b",
            name = "MiniCPM3 4B",
            sizeLabel = "4B",
            fileSizeMb = 2800,
            downloadUrl = zip("MiniCPM3-4B-MNN"),
            fileName = "MiniCPM3-4B-MNN.zip",
            description = "OpenBMB MiniCPM3 4B，专为移动端优化，中文效果突出"
        ),

        // ═══════════════════════════════════════════════════════════════
        // Gemma 2  (Google)
        // ═══════════════════════════════════════════════════════════════

        ModelInfo(
            id = "gemma2-2b",
            name = "Gemma 2 2B",
            sizeLabel = "2B",
            fileSizeMb = 1600,
            downloadUrl = zip("gemma-2-2b-it-MNN"),
            fileName = "gemma-2-2b-it-MNN.zip",
            description = "Google Gemma 2 2B，英文效果优秀，适合移动端"
        ),

        // ═══════════════════════════════════════════════════════════════
        // Phi-3.5  (Microsoft)
        // ═══════════════════════════════════════════════════════════════

        ModelInfo(
            id = "phi3.5-mini",
            name = "Phi-3.5 Mini 3.8B",
            sizeLabel = "3.8B",
            fileSizeMb = 2600,
            downloadUrl = zip("Phi-3.5-mini-instruct-MNN"),
            fileName = "Phi-3.5-mini-instruct-MNN.zip",
            description = "Microsoft Phi-3.5 Mini 3.8B，英文推理强，需约 4 GB RAM"
        ),

        // ═══════════════════════════════════════════════════════════════
        // SmolLM2  (Hugging Face, very small)
        // ═══════════════════════════════════════════════════════════════

        ModelInfo(
            id = "smollm2-135m",
            name = "SmolLM2 135M",
            sizeLabel = "135M",
            fileSizeMb = 120,
            downloadUrl = zip("SmolLM2-135M-Instruct-MNN"),
            fileName = "SmolLM2-135M-Instruct-MNN.zip",
            description = "HuggingFace SmolLM2 135M，极小模型，低端设备首选"
        ),
        ModelInfo(
            id = "smollm2-360m",
            name = "SmolLM2 360M",
            sizeLabel = "360M",
            fileSizeMb = 300,
            downloadUrl = zip("SmolLM2-360M-Instruct-MNN"),
            fileName = "SmolLM2-360M-Instruct-MNN.zip",
            description = "HuggingFace SmolLM2 360M，超轻量，速度快"
        ),
        ModelInfo(
            id = "smollm2-1.7b",
            name = "SmolLM2 1.7B",
            sizeLabel = "1.7B",
            fileSizeMb = 1200,
            downloadUrl = zip("SmolLM2-1.7B-Instruct-MNN"),
            fileName = "SmolLM2-1.7B-Instruct-MNN.zip",
            description = "HuggingFace SmolLM2 1.7B，英文效果好，速度快"
        ),

        // ═══════════════════════════════════════════════════════════════
        // InternLM2.5  (Shanghai AI Lab)
        // ═══════════════════════════════════════════════════════════════

        ModelInfo(
            id = "internlm2.5-1.8b",
            name = "InternLM2.5 1.8B",
            sizeLabel = "1.8B",
            fileSizeMb = 1300,
            downloadUrl = zip("internlm2_5-1_8b-chat-MNN"),
            fileName = "internlm2_5-1_8b-chat-MNN.zip",
            description = "上海AI实验室 InternLM2.5 1.8B，中英文均衡"
        ),
        ModelInfo(
            id = "internlm2.5-7b",
            name = "InternLM2.5 7B",
            sizeLabel = "7B",
            fileSizeMb = 4800,
            downloadUrl = zip("internlm2_5-7b-chat-MNN"),
            fileName = "internlm2_5-7b-chat-MNN.zip",
            description = "上海AI实验室 InternLM2.5 7B，中文旗舰，需约 6 GB RAM"
        )
    )

    fun findById(id: String): ModelInfo? = models.find { it.id == id }
}
