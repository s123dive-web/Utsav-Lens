package com.utsavlens.engine.cloud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.utsavlens.core.AppError
import com.utsavlens.core.AppLogger
import com.utsavlens.core.AppResult
import com.utsavlens.core.BackoffPolicy
import com.utsavlens.core.DispatcherProvider
import com.utsavlens.core.EngineIds
import com.utsavlens.core.io.OutputNames
import com.utsavlens.core.secure.SecretStore
import com.utsavlens.core.settings.SettingsStore
import com.utsavlens.engine.api.ApiKey
import com.utsavlens.engine.api.EngineCapabilities
import com.utsavlens.engine.api.ImageEditProvider
import com.utsavlens.engine.api.ImageEditRequest
import com.utsavlens.engine.api.OptionsPolicy
import com.utsavlens.engine.api.ProviderConfig
import com.utsavlens.engine.api.Readiness
import com.utsavlens.engine.api.ResultNote
import com.utsavlens.engine.api.TransformEngine
import com.utsavlens.engine.api.TransformOptions
import com.utsavlens.engine.api.TransformProgress
import com.utsavlens.engine.api.TransformRequest
import com.utsavlens.engine.api.TransformStage
import com.utsavlens.engine.lite.art.GreetingPainter
import com.utsavlens.engine.lite.art.Pal
import com.utsavlens.platform.BitmapIo
import com.utsavlens.platform.MediaStoreWriter
import com.utsavlens.themes.Theme
import com.utsavlens.themes.ThemeLookup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException

/** Counts real provider requests so the UI can show "requests today". */
fun interface UsageCounter {
    suspend fun increment(providerId: String)
}

/** Optional hook (Phase 9) that refines the prompt for a specific photo; returns null to keep the base prompt. */
fun interface PromptRefiner {
    suspend fun refine(imageJpeg: ByteArray, theme: Theme, options: TransformOptions, basePrompt: String): String?
}

/**
 * HD CLOUD engine. Sends a downscaled, EXIF-free copy of the photo plus the theme prompt to the
 * configured [ImageEditProvider] using the user's own key.
 *
 * Privacy gates, all enforced here and not only in the UI:
 * - no key -> [Readiness.NeedsKey], nothing is sent;
 * - the one-time upload notice for this provider must have been accepted, otherwise the request
 *   fails with NOT_READY before any network call;
 * - only re-encoded pixels are uploaded (no EXIF / location).
 */
class CloudEngine(
    private val context: Context,
    private val themes: ThemeLookup,
    private val providers: Map<String, ImageEditProvider>,
    private val secrets: SecretStore,
    private val settings: SettingsStore,
    private val usage: UsageCounter,
    private val writer: MediaStoreWriter,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger = AppLogger.NONE,
    private val backoff: BackoffPolicy = BackoffPolicy(maxAttempts = 3, baseDelayMs = 1_500, maxDelayMs = 12_000),
    private val refiner: PromptRefiner? = null,
) : TransformEngine {

    override val id: String = EngineIds.CLOUD

    private suspend fun provider(): ImageEditProvider? = providers[settings.load().cloud.providerId]

    override suspend fun capabilities(): EngineCapabilities = EngineCapabilities(
        outfitChange = provider()?.supportsOutfitChange ?: false,
        faceAlwaysPreserved = false,
        maxResolutionPx = 2048,
        needsNetwork = true,
        needsDownload = false,
        maxVariations = 4,
    )

    override suspend fun isReady(): Readiness {
        val p = provider() ?: return Readiness.Unsupported(com.utsavlens.engine.api.UnsupportedReason.OTHER, "provider")
        return if (secrets.has(SecretStore.imageKeyName(p.id))) Readiness.Ready else Readiness.NeedsKey
    }

    override fun transform(request: TransformRequest): Flow<TransformProgress> = flow {
        try {
            execute(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            System.gc()
            emit(TransformProgress.Failure(AppError(AppError.Kind.OUT_OF_MEMORY, "cloud")))
        } catch (e: IOException) {
            emit(TransformProgress.Failure(AppError(AppError.Kind.IO, e.javaClass.simpleName)))
        } catch (e: Exception) {
            logger.warn(TAG, "Cloud engine failed: ${e.javaClass.simpleName}")
            emit(TransformProgress.Failure(AppError(AppError.Kind.UNKNOWN, e.javaClass.simpleName)))
        }
    }.flowOn(dispatchers.io)

    private suspend fun FlowCollector<TransformProgress>.execute(request: TransformRequest) {
        emit(TransformProgress.Running(0, TransformStage.LOADING))
        val cloud = settings.load().cloud
        val provider = providers[cloud.providerId]
        val theme = themes.theme(request.themeId)
        if (provider == null || theme == null) {
            emit(TransformProgress.Failure(AppError(AppError.Kind.INVALID_INPUT, "provider or theme")))
            return
        }
        val keyText = secrets.get(SecretStore.imageKeyName(provider.id))
        if (keyText == null) {
            emit(TransformProgress.Failure(AppError(AppError.Kind.BAD_KEY, "no key")))
            return
        }
        if (provider.id !in cloud.uploadConsentProviders) {
            // Defence in depth: the UI asks first, but nothing leaves the device without the stored consent.
            emit(TransformProgress.Failure(AppError(AppError.Kind.NOT_READY, "upload consent missing")))
            return
        }
        if (!hasNetwork()) {
            emit(TransformProgress.Failure(AppError(AppError.Kind.NO_NETWORK, "offline")))
            return
        }
        val caps = capabilities()
        val options = OptionsPolicy.resolve(request.options, caps)
        val key = ApiKey(keyText)
        val config = ProviderConfig(cloud.baseUrl, cloud.modelId, cloud.timeoutSeconds)

        // Downscale and strip metadata: only freshly encoded pixels are uploaded.
        val upload: ByteArray = run {
            val bitmap = BitmapIo.decode(context, Uri.parse(request.sourceUri), cloud.uploadMaxEdgePx)
            try {
                BitmapIo.encodeJpeg(bitmap, 90)
            } finally {
                bitmap.recycle()
            }
        }
        currentCoroutineContext().ensureActive()

        val notes = HashSet<ResultNote>()
        var prompt = CloudPrompt.build(theme, options)
        if (refiner != null) {
            emit(TransformProgress.Running(5, TransformStage.UPLOADING))
            val refined = try {
                refiner.refine(upload, theme, options, prompt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // Prompt assist is optional: any failure silently falls back to the theme prompt.
            }
            if (!refined.isNullOrBlank()) {
                prompt = CloudPrompt.build(theme, options.copy(promptOverride = refined))
                notes += ResultNote.PROMPT_ASSISTED
            }
        }

        val outputs = ArrayList<String>(options.variations)
        for (i in 0 until options.variations) {
            currentCoroutineContext().ensureActive()
            val base = 10 + i * 85 / options.variations
            emit(TransformProgress.Running(base, TransformStage.UPLOADING, step = i + 1, totalSteps = options.variations))
            val result = backoff.run { attempt ->
                if (attempt > 1) logger.warn(TAG, "Retrying transient provider error, attempt $attempt")
                usage.increment(provider.id)
                provider.edit(ImageEditRequest(key, config, upload, prompt, theme.prompt.negative, options.aspect, request.seed + i))
            }
            when (result) {
                is AppResult.Failure -> {
                    if (outputs.isEmpty()) {
                        emit(TransformProgress.Failure(result.error))
                        return
                    }
                    // Some variations already succeeded: keep them instead of throwing the work away.
                    break
                }
                is AppResult.Success -> {
                    emit(TransformProgress.Running(base + 60 / options.variations, TransformStage.SAVING, step = i + 1, totalSteps = options.variations))
                    val saved = saveResult(request, theme, options, i, result.value.imageBytes)
                    if (saved == null) {
                        if (outputs.isEmpty()) {
                            emit(TransformProgress.Failure(AppError(AppError.Kind.BAD_RESPONSE, "undecodable image")))
                            return
                        }
                        break
                    }
                    outputs += saved
                }
            }
        }
        emit(TransformProgress.Running(100, TransformStage.SAVING))
        emit(TransformProgress.Success(outputs, notes))
    }

    private fun saveResult(request: TransformRequest, theme: Theme, options: TransformOptions, index: Int, bytes: ByteArray): String? {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val bitmap = if (options.greeting != null) {
            val mutable = decoded.copy(Bitmap.Config.ARGB_8888, true)
            decoded.recycle()
            GreetingPainter.draw(Canvas(mutable), mutable.width.toFloat(), mutable.height.toFloat(), options.greeting!!.text, Pal.of(theme.palette), minOf(mutable.width, mutable.height) * 0.04f)
            mutable
        } else decoded
        try {
            val write: (java.io.OutputStream) -> Unit = { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) throw IOException("JPEG encode failed")
            }
            return if (request.saveToGallery) {
                writer.saveImage(OutputNames.build(theme.id + "_hd", "jpg", index = index), "image/jpeg", write).uri.toString()
            } else {
                writer.saveToCache("previews", "${request.jobId}_${theme.id}_hd_$index.jpg", write).toString()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun hasNetwork(): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) {
        true // If the check itself fails, let the request decide.
    }

    companion object {
        private const val TAG = "CloudEngine"
    }
}
