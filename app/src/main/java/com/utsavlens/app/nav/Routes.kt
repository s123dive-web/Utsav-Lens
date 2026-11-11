package com.utsavlens.app.nav

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val THEMES = "themes?src={src}&kind={kind}"
    const val OPTIONS = "options?src={src}&kind={kind}&theme={theme}"
    const val PROGRESS = "progress/{jobId}"
    const val RESULT = "result/{jobId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val SLIDESHOW = "slideshow?batch={batch}"

    const val ARG_SRC = "src"
    const val ARG_KIND = "kind"
    const val ARG_THEME = "theme"
    const val ARG_JOB = "jobId"
    const val ARG_BATCH = "batch"
    const val KIND_PHOTO = "photo"
    const val KIND_VIDEO = "video"

    fun themes(sourceUri: String, kind: String) = "themes?src=${Uri.encode(sourceUri)}&kind=$kind"
    fun options(sourceUri: String, kind: String, themeId: String) = "options?src=${Uri.encode(sourceUri)}&kind=$kind&theme=$themeId"
    fun progress(jobId: String) = "progress/$jobId"
    fun result(jobId: String) = "result/$jobId"
    fun slideshow(batchId: String = "") = "slideshow?batch=$batchId"
}
