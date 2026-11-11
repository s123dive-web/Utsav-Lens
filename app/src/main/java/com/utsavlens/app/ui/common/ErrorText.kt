package com.utsavlens.app.ui.common

import androidx.annotation.StringRes
import com.utsavlens.app.R
import com.utsavlens.core.AppError
import com.utsavlens.core.EngineIds
import com.utsavlens.engine.api.TransformStage

@StringRes
fun errorText(kind: AppError.Kind?, interrupted: Boolean = false): Int = when {
    interrupted -> R.string.err_interrupted
    else -> when (kind) {
        AppError.Kind.NO_NETWORK -> R.string.err_no_network
        AppError.Kind.BAD_KEY -> R.string.err_bad_key
        AppError.Kind.QUOTA -> R.string.err_quota
        AppError.Kind.SAFETY_BLOCK -> R.string.err_safety
        AppError.Kind.TIMEOUT -> R.string.err_timeout
        AppError.Kind.SERVER -> R.string.err_server
        AppError.Kind.BAD_RESPONSE -> R.string.err_bad_response
        AppError.Kind.OUT_OF_MEMORY -> R.string.err_oom
        AppError.Kind.UNSUPPORTED -> R.string.err_unsupported
        AppError.Kind.NOT_READY -> R.string.err_not_ready
        AppError.Kind.IO -> R.string.err_io
        AppError.Kind.CANCELLED -> R.string.progress_cancelled
        AppError.Kind.INVALID_INPUT -> R.string.err_invalid
        AppError.Kind.UNKNOWN, null -> R.string.err_unknown
    }
}

@StringRes
fun stageText(stage: TransformStage?): Int = when (stage) {
    TransformStage.LOADING, null -> R.string.stage_loading
    TransformStage.SEGMENTING -> R.string.stage_segmenting
    TransformStage.UPLOADING -> R.string.stage_uploading
    TransformStage.DOWNLOADING -> R.string.stage_downloading
    TransformStage.GENERATING -> R.string.stage_generating
    TransformStage.COMPOSITING -> R.string.stage_compositing
    TransformStage.SAVING -> R.string.stage_saving
}

@StringRes
fun engineName(id: String): Int = when (id) {
    EngineIds.LOCAL -> R.string.engine_local
    EngineIds.CLOUD -> R.string.engine_cloud
    else -> R.string.engine_lite
}

@StringRes
fun engineDescription(id: String): Int = when (id) {
    EngineIds.LOCAL -> R.string.engine_local_desc
    EngineIds.CLOUD -> R.string.engine_cloud_desc
    else -> R.string.engine_lite_desc
}
