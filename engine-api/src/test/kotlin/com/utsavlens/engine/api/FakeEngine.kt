package com.utsavlens.engine.api

import com.utsavlens.core.AppError
import com.utsavlens.core.EngineIds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Scriptable engine used by contract tests and by UI/view-model tests. */
class FakeEngine(
    override val id: String = EngineIds.LITE,
    private val caps: EngineCapabilities = EngineCapabilities(
        outfitChange = false, faceAlwaysPreserved = true, maxResolutionPx = 4096,
        needsNetwork = false, needsDownload = false,
    ),
    var readiness: Readiness = Readiness.Ready,
    var script: Script = Script.Succeed,
    private val stepDelayMs: Long = 0,
) : TransformEngine {

    sealed interface Script {
        data object Succeed : Script
        data class Fail(val kind: AppError.Kind) : Script
        data class Raw(val events: List<TransformProgress>) : Script
        data object Throw : Script
    }

    var started = 0; private set
    var completed = 0; private set

    override suspend fun capabilities() = caps
    override suspend fun isReady() = readiness

    override fun transform(request: TransformRequest): Flow<TransformProgress> = flow {
        started++
        when (val s = script) {
            Script.Succeed -> {
                for (p in listOf(0, 25, 50, 75, 100)) {
                    emit(TransformProgress.Running(p, TransformStage.COMPOSITING))
                    delay(stepDelayMs)
                }
                val n = OptionsPolicy.resolve(request.options, caps).variations
                emit(TransformProgress.Success((1..n).map { "content://fake/out/${request.jobId}/$it" }))
            }
            is Script.Fail -> {
                emit(TransformProgress.Running(10, TransformStage.LOADING))
                emit(TransformProgress.Failure(AppError(s.kind)))
            }
            is Script.Raw -> s.events.forEach { emit(it) }
            Script.Throw -> throw IllegalStateException("engine bug")
        }
        completed++
    }
}
