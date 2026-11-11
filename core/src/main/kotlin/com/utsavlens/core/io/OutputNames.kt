package com.utsavlens.core.io

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OutputNames {
    const val ALBUM = "UtsavLens"

    /** Display name for a new MediaStore entry. Always a new name: outputs never replace originals. */
    fun build(themeId: String, extension: String, nowMs: Long = System.currentTimeMillis(), index: Int = 0): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(nowMs))
        val safeTheme = themeId.filter { it.isLetterOrDigit() || it == '_' }.take(32).ifEmpty { "theme" }
        val suffix = if (index > 0) "_$index" else ""
        return "UtsavLens_${safeTheme}_$stamp$suffix.${extension.trimStart('.').lowercase(Locale.US)}"
    }
}
