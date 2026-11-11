package com.utsavlens.engine.api.testing

import com.utsavlens.engine.api.TransformEngine
import com.utsavlens.engine.api.TransformProgress
import com.utsavlens.engine.api.TransformRequest
import com.utsavlens.engine.api.isTerminal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList

class ContractViolation(message: String) : AssertionError(message)

/**
 * Reusable checks for the [TransformEngine.transform] contract. Every engine's tests run its
 * engine through [collectAndVerify]; it has no test-framework dependency on purpose.
 */
object EngineContract {

    suspend fun collectAndVerify(engine: TransformEngine, request: TransformRequest): List<TransformProgress> {
        val events = try {
            engine.transform(request).toList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw ContractViolation("transform() threw ${e.javaClass.simpleName} instead of emitting Failure")
        }
        verify(events, request)
        return events
    }

    fun verify(events: List<TransformProgress>, request: TransformRequest) {
        if (events.isEmpty()) throw ContractViolation("flow completed without a terminal event")
        val terminalCount = events.count { it.isTerminal }
        if (terminalCount != 1) throw ContractViolation("expected exactly 1 terminal event, got $terminalCount")
        if (!events.last().isTerminal) throw ContractViolation("terminal event must be the last emission")
        var last = -1
        for (e in events) {
            if (e is TransformProgress.Running) {
                if (e.percent !in 0..100) throw ContractViolation("percent ${e.percent} outside 0..100")
                if (e.percent < last) throw ContractViolation("percent went backwards: $last -> ${e.percent}")
                last = e.percent
            }
        }
        when (val end = events.last()) {
            is TransformProgress.Success -> {
                if (end.outputUris.isEmpty()) throw ContractViolation("Success without outputs")
                if (end.outputUris.size > request.options.variations.coerceAtLeast(1)) {
                    throw ContractViolation("more outputs (${end.outputUris.size}) than requested variations")
                }
                if (end.outputUris.any { it == request.sourceUri }) {
                    throw ContractViolation("output URI equals the source URI: originals must never be overwritten")
                }
                if (end.outputUris.toSet().size != end.outputUris.size) throw ContractViolation("duplicate output URIs")
            }
            is TransformProgress.Failure -> Unit
            is TransformProgress.Running -> throw ContractViolation("unreachable")
        }
    }
}
