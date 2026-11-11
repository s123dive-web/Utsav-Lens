package com.utsavlens.themes

import com.utsavlens.core.AppLogger

/** A raw theme file: name plus a lazy reader, so one unreadable file cannot break the others. */
class ThemeSource(val fileName: String, val read: () -> String)

class ThemeCatalogue private constructor(
    val themes: List<Theme>,
    val skipped: List<ThemeLoadResult.Invalid>,
) {
    private val byId = themes.associateBy { it.id }

    operator fun get(id: String): Theme? = byId[id]

    companion object {
        private const val TAG = "ThemeCatalogue"

        /** Preferred display order; themes not listed keep file order after these. */
        val DISPLAY_ORDER = listOf(
            "diwali", "holi", "ganesh_chaturthi", "navratri", "durga_puja", "dussehra", "raksha_bandhan",
            "gudi_padwa", "makar_sankranti", "lohri", "pongal", "onam", "eid", "christmas",
            "wedding", "haldi", "mehendi", "independence_day", "birthday",
        )

        /** Loads every source. Malformed, unreadable or duplicate files are skipped with a warning, never thrown. */
        fun load(sources: List<ThemeSource>, logger: AppLogger = AppLogger.NONE): ThemeCatalogue {
            val valid = LinkedHashMap<String, Theme>()
            val skipped = ArrayList<ThemeLoadResult.Invalid>()
            for (source in sources.sortedBy { it.fileName }) {
                val result = try {
                    ThemeParser.parse(source.fileName, source.read())
                } catch (e: Exception) {
                    ThemeLoadResult.Invalid(source.fileName, listOf("unreadable: ${e.javaClass.simpleName}"))
                }
                when (result) {
                    is ThemeLoadResult.Valid ->
                        if (valid.containsKey(result.theme.id)) {
                            skipped += ThemeLoadResult.Invalid(source.fileName, listOf("duplicate id '${result.theme.id}'"))
                        } else valid[result.theme.id] = result.theme
                    is ThemeLoadResult.Invalid -> skipped += result
                }
            }
            skipped.forEach { logger.warn(TAG, "Skipping theme ${it.fileName}: ${it.problems.joinToString("; ")}") }
            val ordered = valid.values.sortedBy { t -> DISPLAY_ORDER.indexOf(t.id).let { if (it < 0) Int.MAX_VALUE else it } }
            return ThemeCatalogue(ordered, skipped)
        }
    }
}
