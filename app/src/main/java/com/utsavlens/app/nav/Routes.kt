package com.utsavlens.app.nav

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val THEMES = "themes?src={src}&kind={kind}"
    const val ARG_SRC = "src"
    const val ARG_KIND = "kind"
    const val KIND_PHOTO = "photo"
    const val KIND_VIDEO = "video"

    fun themes(sourceUri: String, kind: String) = "themes?src=${Uri.encode(sourceUri)}&kind=$kind"
}
