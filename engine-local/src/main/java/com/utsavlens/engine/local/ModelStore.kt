package com.utsavlens.engine.local

import com.utsavlens.core.io.AtomicFileWriter
import java.io.File

data class ModelFile(val path: String, val bytes: Long, val sha256: String)

/** The exact model the app downloads. Checksums were taken from the hosting API and re-verified in CI. */
object LocalModelManifest {
    const val ID = "lcm-dreamshaper-v7-fp32"
    const val DISPLAY_NAME = "LCM Dreamshaper v7 (Stable Diffusion 1.5 family, ONNX)"
    const val SOURCE_PAGE = "https://huggingface.co/SimianLuo/LCM_Dreamshaper_v7"
    const val BASE_URL = "https://huggingface.co/SimianLuo/LCM_Dreamshaper_v7/resolve/main/"

    val files: List<ModelFile> = listOf(
        ModelFile("tokenizer/vocab.json", 1_059_962, "e089ad92ba36837a0d31433e555c8f45fe601ab5c221d4f607ded32d9f7a4349"),
        ModelFile("tokenizer/merges.txt", 524_619, "9fd691f7c8039210e0fced15865466c65820d09b63988b0174bfe25de299051a"),
        ModelFile("text_encoder/model.onnx", 492_588_002, "767d3fad6f9d5c0067f420f7227cb2b9816b6237216d35dcad006bcad9a1f65c"),
        ModelFile("unet/model.onnx", 1_951_153, "01c5869bc7cbf3fef1ec4a40415b1798860bb4bff4798bbef3c53e2f681471dd"),
        ModelFile("unet/model.onnx_data", 3_438_411_520, "ef99ccc336de0e79f247fcb3d1398b3f3d1a02796916b88a351d7a83f570a31a"),
        ModelFile("vae_encoder/model.onnx", 136_775_724, "f90150f8c77f4d7087939b1cbb74cc0fbcc2997b02559e750776cec0e210be52"),
        ModelFile("vae_decoder/model.onnx", 198_093_688, "0ea3df9f76ff9de5cea29106f246bf9b71297d418b013404f91dae5a0ddba98e"),
    )

    val totalBytes: Long = files.sumOf { it.bytes }
}

/** Model files on disk under files/models/<id>/ (excluded from backups; deletable from Settings). */
class ModelStore(root: File, private val manifest: List<ModelFile> = LocalModelManifest.files, id: String = LocalModelManifest.ID) {
    val dir: File = File(root, id)
    private val marker = File(dir, ".verified")

    fun fileFor(file: ModelFile): File = File(dir, file.path)
    fun partFor(file: ModelFile): File = File(dir, file.path + ".part")

    /** True only after every file was downloaded AND its checksum verified. */
    fun isComplete(): Boolean {
        if (!marker.isFile) return false
        val verified = try { marker.readLines().toSet() } catch (_: Exception) { return false }
        return manifest.all { f -> f.sha256 in verified && fileFor(f).let { it.isFile && it.length() == f.bytes } }
    }

    fun isVerified(file: ModelFile): Boolean {
        val target = fileFor(file)
        if (!target.isFile || target.length() != file.bytes || !marker.isFile) return false
        return try { file.sha256 in marker.readLines() } catch (_: Exception) { false }
    }

    fun markVerified(file: ModelFile) {
        val current = try { if (marker.isFile) marker.readLines() else emptyList() } catch (_: Exception) { emptyList() }
        AtomicFileWriter.writeText(marker, (current + file.sha256).distinct().joinToString("\n"))
    }

    /** Bytes already on disk (finished files plus partial downloads): what a resume will skip. */
    fun downloadedBytes(): Long = manifest.sumOf { f ->
        when {
            isVerified(f) -> f.bytes
            else -> partFor(f).takeIf { it.isFile }?.length()?.coerceAtMost(f.bytes) ?: 0L
        }
    }

    fun diskBytes(): Long = if (dir.isDirectory) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L

    fun delete(): Boolean = !dir.exists() || dir.deleteRecursively()
}
