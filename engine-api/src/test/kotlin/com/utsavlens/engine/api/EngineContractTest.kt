package com.utsavlens.engine.api

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.utsavlens.core.AppError
import com.utsavlens.engine.api.testing.ContractViolation
import com.utsavlens.engine.api.testing.EngineContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class EngineContractTest {
    private val request = TransformRequest(jobId = "job1", sourceUri = "content://media/1", themeId = "diwali")

    @Test fun successfulRunSatisfiesContract() = runTest {
        val events = EngineContract.collectAndVerify(FakeEngine(), request.copy(options = TransformOptions(variations = 3)))
        assertThat((events.last() as TransformProgress.Success).outputUris).hasSize(3)
    }

    @Test fun failureIsEmittedNotThrownAndOffersLiteFallback() = runTest {
        val events = EngineContract.collectAndVerify(FakeEngine(script = FakeEngine.Script.Fail(AppError.Kind.QUOTA)), request)
        val failure = events.last() as TransformProgress.Failure
        assertThat(failure.error.kind).isEqualTo(AppError.Kind.QUOTA)
        assertThat(failure.canFallbackToLite).isTrue()
    }

    @Test fun throwingEngineViolatesContract() = runTest {
        val error = runCatching { EngineContract.collectAndVerify(FakeEngine(script = FakeEngine.Script.Throw), request) }.exceptionOrNull()
        assertThat(error).isInstanceOf(ContractViolation::class.java)
    }

    private fun violates(vararg events: TransformProgress) {
        assertThrows(ContractViolation::class.java) { EngineContract.verify(events.toList(), request) }
    }

    @Test fun detectsMissingTerminalEvent() = violates(TransformProgress.Running(50, TransformStage.LOADING))

    @Test fun detectsEmptyFlow() = violates()

    @Test fun detectsTwoTerminalEvents() =
        violates(TransformProgress.Success(listOf("a")), TransformProgress.Failure(AppError(AppError.Kind.UNKNOWN)))

    @Test fun detectsEventsAfterTerminal() =
        violates(TransformProgress.Success(listOf("a")), TransformProgress.Running(100, TransformStage.SAVING))

    @Test fun detectsProgressGoingBackwards() = violates(
        TransformProgress.Running(60, TransformStage.LOADING),
        TransformProgress.Running(40, TransformStage.LOADING),
        TransformProgress.Success(listOf("a")),
    )

    @Test fun detectsOutOfRangeProgress() =
        violates(TransformProgress.Running(101, TransformStage.LOADING), TransformProgress.Success(listOf("a")))

    @Test fun detectsOverwritingTheOriginal() = violates(TransformProgress.Success(listOf(request.sourceUri)))

    @Test fun detectsTooManyOrNoOutputs() {
        violates(TransformProgress.Success(listOf("a", "b")))
        violates(TransformProgress.Success(emptyList()))
    }

    @Test fun cancellingTheCollectorStopsTheEngine() = runTest {
        val engine = FakeEngine(stepDelayMs = 1_000)
        engine.transform(request).test {
            assertThat(awaitItem()).isInstanceOf(TransformProgress.Running::class.java)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(engine.started).isEqualTo(1)
        assertThat(engine.completed).isEqualTo(0)
    }

    @Test fun optionsPolicyLocksAndDisablesByCapability() {
        val lite = EngineCapabilities(outfitChange = false, faceAlwaysPreserved = true, maxResolutionPx = 4096, needsNetwork = false, needsDownload = false)
        val cloud = lite.copy(outfitChange = true, faceAlwaysPreserved = false, needsNetwork = true, maxVariations = 2)
        val wanted = TransformOptions(keepFace = false, changeOutfit = true, strength = 3f, variations = 9, greeting = GreetingText("  "))

        val forLite = OptionsPolicy.resolve(wanted, lite)
        assertThat(forLite.keepFace).isTrue()
        assertThat(forLite.changeOutfit).isFalse()
        assertThat(forLite.strength).isEqualTo(1f)
        assertThat(forLite.variations).isEqualTo(4)
        assertThat(forLite.greeting).isNull()
        assertThat(OptionsPolicy.availability(lite).keepFaceEditable).isFalse()
        assertThat(OptionsPolicy.availability(lite).outfitEnabled).isFalse()

        val forCloud = OptionsPolicy.resolve(wanted, cloud)
        assertThat(forCloud.keepFace).isFalse()
        assertThat(forCloud.changeOutfit).isTrue()
        assertThat(forCloud.variations).isEqualTo(2)
        assertThat(OptionsPolicy.availability(cloud).outfitEnabled).isTrue()
    }

    @Test fun apiKeyNeverLeaksThroughToString() {
        val key = ApiKey("AIza-super-secret")
        assertThat(key.toString()).doesNotContain("secret")
        assertThat("$key ${listOf(key)}").doesNotContain("AIza")
        assertThat(key.reveal()).isEqualTo("AIza-super-secret")
    }

    @Test fun aspectKeysRoundTrip() {
        Aspect.entries.forEach { assertThat(Aspect.fromKey(it.key)).isEqualTo(it) }
        assertThat(Aspect.fromKey("weird")).isEqualTo(Aspect.ORIGINAL)
    }
}
