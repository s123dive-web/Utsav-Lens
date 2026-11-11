package com.utsavlens.core

object EngineIds {
    const val LITE = "lite"
    const val LOCAL = "local"
    const val CLOUD = "cloud"
}

enum class AppLanguage(val tag: String) {
    SYSTEM("system"), EN("en"), HI("hi"), MR("mr");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM

        /** Maps any BCP-47 tag to one of the three content languages. */
        fun contentLanguage(tag: String?): AppLanguage = when (tag?.lowercase()?.substringBefore('-')) {
            "hi" -> HI
            "mr" -> MR
            else -> EN
        }
    }
}

/** Logging seam so pure modules never touch android.util.Log. Never pass secrets to it. */
fun interface AppLogger {
    fun warn(tag: String, message: String)

    companion object {
        val NONE = AppLogger { _, _ -> }
    }
}
