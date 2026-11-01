package com.jarvis.assistant.model

/** Public, Apache-2.0 model weights pinned to immutable upstream revisions. */
object RecommendedModels {
    val all = listOf(
        RemoteModel(
            id = "unsloth/gemma-4-E2B-it-GGUF",
            name = "Recommended · Gemma 4 E2B",
            downloads = 0,
            fileName = "gemma-4-E2B-it-Q4_K_M.gguf",
            sizeBytes = 3106738272L,
            downloadUrl = "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/resolve/0314792d7f1f7e229411f620751375812bb9faf2/gemma-4-E2B-it-Q4_K_M.gguf?download=true",
            quant = "Q4_K_M · effective 2.3B · text-only · 6 GB+ RAM recommended",
            sha256 = "740185b21d22ceb83a11c3aa62ad5842ef32c70f6096d756bbee85a1e4ec34b8"
        ),
        RemoteModel(
            id = "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF",
            name = "Jarvis Lite · TinyLlama 1.1B",
            downloads = 0,
            fileName = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            sizeBytes = 668788096,
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/52e7645ba7c309695bec7ac98f4f005b139cf465/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf?download=true",
            quant = "Q4_K_M",
            sha256 = "9fecc3b3cd76bba89d504f29b616eedf7da85b96540e490ca5824d3f7d2776a0"
        ),
        RemoteModel(
            id = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            name = "Jarvis Plus · Qwen2.5 1.5B",
            downloads = 0,
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sizeBytes = 1117320736,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true",
            quant = "Q4_K_M",
            sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
        )
    )
}