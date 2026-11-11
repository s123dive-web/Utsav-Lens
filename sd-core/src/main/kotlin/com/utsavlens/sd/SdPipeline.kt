package com.utsavlens.sd

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean

class SdCancelledException : RuntimeException("cancelled")
class SdModelException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * On-device text-guided background repainting with a Stable Diffusion 1.5-family LCM model in
 * ONNX format (diffusers layout: tokenizer/, text_encoder/, unet/, vae_encoder/, vae_decoder/).
 *
 * Inpainting is done by latent blending: at every step the latent cells that belong to the
 * protected (person) area are replaced with the original image's latent at the matching noise
 * level, so only the surroundings are generated. The caller additionally pastes the original
 * person pixels back in pixel space, which is what guarantees an identical face.
 *
 * Sessions are opened one at a time and closed before the next one, because the UNet alone needs
 * several GB of RAM. Pure JVM code: the same class is exercised with real weights in CI.
 */
class SdPipeline(private val modelDir: File, private val threads: Int = defaultThreads()) {

    class Request(
        /** Source image as CHW floats in [-1, 1], size 3 * width * height. */
        val imageChw: FloatArray,
        val width: Int,
        val height: Int,
        /** One value per latent cell (width/8 * height/8): 1 = repaint, 0 = keep original. */
        val repaintMask: FloatArray,
        val prompt: String,
        val steps: Int = 4,
        /** 0..1: how far the repainted area may move away from the original. */
        val strength: Float = 1f,
        val guidanceScale: Float = 8f,
        val seed: Long = 0L,
    )

    fun interface Listener {
        /** [step] runs 1..[totalSteps]; stage is one of "text", "encode", "denoise", "decode". */
        fun onProgress(stage: String, step: Int, totalSteps: Int)
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val cancelled = AtomicBoolean(false)
    @Volatile private var activeRun: OrtSession.RunOptions? = null

    /** Safe to call from any thread; the running UNet pass is asked to terminate. */
    fun cancel() {
        cancelled.set(true)
        try { activeRun?.setTerminate(true) } catch (_: Exception) {}
    }

    /** Returns the generated image as CHW floats in [-1, 1] at the request's size. */
    fun generate(request: Request, listener: Listener = Listener { _, _, _ -> }): FloatArray {
        val w = request.width
        val h = request.height
        require(w % 8 == 0 && h % 8 == 0) { "size must be a multiple of 8" }
        val lw = w / 8
        val lh = h / 8
        require(request.imageChw.size == 3 * w * h) { "image size mismatch" }
        require(request.repaintMask.size == lw * lh) { "mask size mismatch" }
        cancelled.set(false)
        val random = Random(request.seed)

        listener.onProgress("text", 0, 1)
        val tokenizer = ClipTokenizer(File(modelDir, "tokenizer/vocab.json").readText(), File(modelDir, "tokenizer/merges.txt").readText())
        val ids = tokenizer.encode(request.prompt)
        val hidden = open("text_encoder/model.onnx").use { session ->
            val name = session.inputNames.first()
            intTensor(session, name, ids, longArrayOf(1, ids.size.toLong())).use { input ->
                run(session, mapOf(name to input)) { it.first() }
            }
        }
        checkCancelled()

        listener.onProgress("encode", 0, 1)
        val z0 = open("vae_encoder/model.onnx").use { session ->
            val name = session.inputNames.first()
            floatTensor(request.imageChw, longArrayOf(1, 3, h.toLong(), w.toLong())).use { input ->
                val out = run(session, mapOf(name to input)) { it.first() }
                // Some exports return mean+logvar (8 channels): the mean is the first 4.
                val mean = if (out.size == 8 * lw * lh) out.copyOf(4 * lw * lh) else out
                if (mean.size != 4 * lw * lh) throw SdModelException("unexpected VAE encoder output size ${out.size}")
                FloatArray(mean.size) { i -> mean[i] * VAE_SCALE }
            }
        }
        checkCancelled()

        val scheduler = LcmScheduler(request.steps, request.strength)
        var latents = scheduler.addNoise(z0, LatentMath.gaussian(z0.size, random), scheduler.timesteps[0])
        var denoised = latents
        open("unet/model.onnx").use { session ->
            val inputs = session.inputInfo
            val condDim = (inputs["timestep_cond"]?.info as? TensorInfo)?.shape?.lastOrNull()?.toInt()?.takeIf { it > 0 } ?: 256
            val hiddenDim = hidden.size / ids.size
            for (i in 0 until scheduler.inferenceSteps) {
                checkCancelled()
                listener.onProgress("denoise", i + 1, scheduler.inferenceSteps)
                val t = scheduler.timesteps[i]
                val feeds = LinkedHashMap<String, OnnxTensor>()
                try {
                    feeds["sample"] = floatTensor(latents, longArrayOf(1, 4, lh.toLong(), lw.toLong()))
                    feeds["timestep"] = timestepTensor(session, t)
                    feeds["encoder_hidden_states"] = floatTensor(hidden, longArrayOf(1, ids.size.toLong(), hiddenDim.toLong()))
                    if (inputs.containsKey("timestep_cond")) {
                        feeds["timestep_cond"] = floatTensor(LcmScheduler.guidanceEmbedding(request.guidanceScale, condDim), longArrayOf(1, condDim.toLong()))
                    }
                    val unknown = inputs.keys - feeds.keys
                    if (unknown.isNotEmpty()) throw SdModelException("unsupported UNet inputs: $unknown")
                    val eps = run(session, feeds) { it.first() }
                    if (eps.size != latents.size) throw SdModelException("unexpected UNet output size ${eps.size}")
                    val last = i == scheduler.inferenceSteps - 1
                    val result = scheduler.step(i, latents, eps, if (last) null else LatentMath.gaussian(latents.size, random))
                    denoised = result.denoised
                    latents = if (last) {
                        LatentMath.blend(result.denoised, z0, request.repaintMask)
                    } else {
                        // Keep the protected area on the original image at the next noise level.
                        val known = scheduler.addNoise(z0, LatentMath.gaussian(z0.size, random), scheduler.timesteps[i + 1])
                        LatentMath.blend(result.prevSample, known, request.repaintMask)
                    }
                } finally {
                    feeds.values.forEach { it.close() }
                }
            }
        }
        checkCancelled()

        listener.onProgress("decode", 0, 1)
        val image = open("vae_decoder/model.onnx").use { session ->
            val name = session.inputNames.first()
            val scaled = FloatArray(latents.size) { i -> latents[i] / VAE_SCALE }
            floatTensor(scaled, longArrayOf(1, 4, lh.toLong(), lw.toLong())).use { input ->
                run(session, mapOf(name to input)) { it.first() }
            }
        }
        if (image.size != 3 * w * h) throw SdModelException("unexpected VAE decoder output size ${image.size}")
        for (i in image.indices) {
            val v = image[i]
            image[i] = if (v.isNaN()) 0f else v.coerceIn(-1f, 1f)
        }
        return image
    }

    private fun open(relative: String): OrtSession {
        val file = File(modelDir, relative)
        if (!file.isFile) throw SdModelException("missing model file $relative")
        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(threads)
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        return try {
            env.createSession(file.absolutePath, options)
        } catch (e: Exception) {
            throw SdModelException("cannot load $relative: ${e.javaClass.simpleName}", e)
        } finally {
            options.close()
        }
    }

    private fun <T> run(session: OrtSession, feeds: Map<String, OnnxTensor>, read: (List<FloatArray>) -> T): T {
        val options = OrtSession.RunOptions()
        activeRun = options
        try {
            session.run(feeds, options).use { result ->
                val outputs = ArrayList<FloatArray>()
                for (entry in result) {
                    val tensor = entry.value as? OnnxTensor ?: continue
                    val buffer = tensor.floatBuffer ?: throw SdModelException("non-float model output (fp16 models are not supported)")
                    val array = FloatArray(buffer.remaining())
                    buffer.get(array)
                    outputs += array
                }
                if (outputs.isEmpty()) throw SdModelException("model produced no output")
                return read(outputs)
            }
        } catch (e: SdModelException) {
            throw e
        } catch (e: Exception) {
            if (cancelled.get()) throw SdCancelledException()
            throw SdModelException("inference failed: ${e.javaClass.simpleName}", e)
        } finally {
            activeRun = null
            options.close()
        }
    }

    private fun floatTensor(data: FloatArray, shape: LongArray): OnnxTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)

    private fun intTensor(session: OrtSession, name: String, ids: IntArray, shape: LongArray): OnnxTensor =
        when (inputType(session, name)) {
            OnnxJavaType.INT64 -> OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { ids[it].toLong() }), shape)
            else -> OnnxTensor.createTensor(env, IntBuffer.wrap(ids), shape)
        }

    private fun timestepTensor(session: OrtSession, t: Int): OnnxTensor {
        val info = session.inputInfo["timestep"]?.info as? TensorInfo
        val shape = if (info != null && info.shape.isEmpty()) longArrayOf() else longArrayOf(1)
        return when (info?.type) {
            OnnxJavaType.FLOAT -> OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(t.toFloat())), shape)
            OnnxJavaType.INT32 -> OnnxTensor.createTensor(env, IntBuffer.wrap(intArrayOf(t)), shape)
            else -> OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(t.toLong())), shape)
        }
    }

    private fun inputType(session: OrtSession, name: String): OnnxJavaType? = (session.inputInfo[name]?.info as? TensorInfo)?.type

    private fun checkCancelled() {
        if (cancelled.get() || Thread.currentThread().isInterrupted) throw SdCancelledException()
    }

    /** Human-readable description of every model input (used by the CI validation log). */
    fun describe(): String = buildString {
        for (part in listOf("text_encoder", "vae_encoder", "unet", "vae_decoder")) {
            open("$part/model.onnx").use { s ->
                append(part).append(": in=")
                s.inputInfo.forEach { (k, v) -> val ti = v.info as? TensorInfo; append("$k:${ti?.type}${ti?.shape?.toList()} ") }
                append("out=")
                s.outputInfo.forEach { (k, v) -> val ti = v.info as? TensorInfo; append("$k:${ti?.type}${ti?.shape?.toList()} ") }
                append('\n')
            }
        }
    }

    companion object {
        const val VAE_SCALE = 0.18215f
        fun defaultThreads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    }
}
