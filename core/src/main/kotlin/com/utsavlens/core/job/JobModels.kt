package com.utsavlens.core.job

enum class JobKind { TRANSFORM, SLIDESHOW, VIDEO_OVERLAY, MODEL_DOWNLOAD }

enum class JobState {
    QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED;

    val isTerminal: Boolean get() = this == SUCCEEDED || this == FAILED || this == CANCELLED
}

data class JobSnapshot(
    val id: String,
    val kind: JobKind,
    val state: JobState,
    val progress: Int,
    val failureReason: String? = null,
)

object JobRecovery {
    const val INTERRUPTED = "interrupted"

    /**
     * Called on process start. Any job persisted as QUEUED/RUNNING whose worker is no longer alive
     * was killed with the process: it is failed cleanly (and can be retried from its stored request).
     */
    fun reconcile(persisted: List<JobSnapshot>, liveJobIds: Set<String>): List<JobSnapshot> =
        persisted.map { job ->
            if (!job.state.isTerminal && job.id !in liveJobIds) {
                job.copy(state = JobState.FAILED, failureReason = INTERRUPTED)
            } else job
        }
}
