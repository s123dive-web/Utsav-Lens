package com.utsavlens.core

import com.google.common.truth.Truth.assertThat
import com.utsavlens.core.job.JobKind
import com.utsavlens.core.job.JobRecovery
import com.utsavlens.core.job.JobSnapshot
import com.utsavlens.core.job.JobState
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BackoffAndJobTest {
    private val transient = AppResult.Failure(AppError(AppError.Kind.SERVER, retryable = true))
    private val permanent = AppResult.Failure(AppError(AppError.Kind.BAD_KEY, retryable = false))

    @Test fun retriesTransientUntilSuccess() = runTest {
        val sleeps = mutableListOf<Long>()
        var calls = 0
        val result = BackoffPolicy(maxAttempts = 3, baseDelayMs = 100).run(sleep = { sleeps += it }) {
            calls++
            if (calls < 3) transient else AppResult.Success("ok")
        }
        assertThat(result).isEqualTo(AppResult.Success("ok"))
        assertThat(calls).isEqualTo(3)
        assertThat(sleeps).hasSize(2)
        assertThat(sleeps[0]).isIn(50L..100L)
        assertThat(sleeps[1]).isIn(100L..200L)
    }

    @Test fun neverRetriesPermanentErrors() = runTest {
        var calls = 0
        val result = BackoffPolicy(maxAttempts = 5).run<String>(sleep = {}) { calls++; permanent }
        assertThat(result).isEqualTo(permanent)
        assertThat(calls).isEqualTo(1)
    }

    @Test fun givesUpAfterMaxAttempts() = runTest {
        var calls = 0
        val result = BackoffPolicy(maxAttempts = 3).run<String>(sleep = {}) { calls++; transient }
        assertThat(result).isEqualTo(transient)
        assertThat(calls).isEqualTo(3)
    }

    @Test fun delayIsCapped() {
        val policy = BackoffPolicy(maxAttempts = 10, baseDelayMs = 1_000, maxDelayMs = 4_000)
        repeat(50) { assertThat(policy.delayForAttempt(9)).isAtMost(4_000) }
    }

    @Test fun interruptedJobsAreFailedCleanlyOnRestart() {
        val jobs = listOf(
            JobSnapshot("a", JobKind.TRANSFORM, JobState.RUNNING, 40),
            JobSnapshot("b", JobKind.SLIDESHOW, JobState.QUEUED, 0),
            JobSnapshot("c", JobKind.TRANSFORM, JobState.SUCCEEDED, 100),
            JobSnapshot("d", JobKind.MODEL_DOWNLOAD, JobState.RUNNING, 10),
        )
        val out = JobRecovery.reconcile(jobs, liveJobIds = setOf("d")).associateBy { it.id }
        assertThat(out.getValue("a").state).isEqualTo(JobState.FAILED)
        assertThat(out.getValue("a").failureReason).isEqualTo(JobRecovery.INTERRUPTED)
        assertThat(out.getValue("b").state).isEqualTo(JobState.FAILED)
        assertThat(out.getValue("c").state).isEqualTo(JobState.SUCCEEDED)
        assertThat(out.getValue("d").state).isEqualTo(JobState.RUNNING)
    }
}
