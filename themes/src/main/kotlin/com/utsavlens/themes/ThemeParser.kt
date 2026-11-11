package com.utsavlens.themes

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface ThemeLoadResult {
    data class Valid(val theme: Theme) : ThemeLoadResult
    data class Invalid(val fileName: String, val problems: List<String>) : ThemeLoadResult
}

/** Parses and validates one theme file. Never throws: every problem becomes [ThemeLoadResult.Invalid]. */
object ThemeParser {
    const val SUPPORTED_SCHEMA_VERSION = 1
    private val ID_REGEX = Regex("^[a-z][a-z0-9_]{1,39}$")
    private val COLOR_REGEX = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    private val json = Json { ignoreUnknownKeys = true; isLenient = false; coerceInputValues = false }

    fun parse(fileName: String, text: String): ThemeLoadResult {
        val theme = try {
            json.decodeFromString(Theme.serializer(), text)
        } catch (e: SerializationException) {
            return ThemeLoadResult.Invalid(fileName, listOf("malformed JSON or schema: ${e.message?.lineSequence()?.firstOrNull()}"))
        } catch (e: IllegalArgumentException) {
            return ThemeLoadResult.Invalid(fileName, listOf("malformed JSON or schema: ${e.message?.lineSequence()?.firstOrNull()}"))
        }
        val problems = validate(fileName, theme)
        return if (problems.isEmpty()) ThemeLoadResult.Valid(theme) else ThemeLoadResult.Invalid(fileName, problems)
    }

    fun validate(fileName: String, t: Theme): List<String> = buildList {
        if (t.schemaVersion != SUPPORTED_SCHEMA_VERSION) add("schemaVersion ${t.schemaVersion} is not supported")
        if (!ID_REGEX.matches(t.id)) add("id '${t.id}' must match ${ID_REGEX.pattern}")
        val expected = fileName.substringAfterLast('/').removeSuffix(".json")
        if (expected != t.id) add("id '${t.id}' must equal the file name '$expected'")
        for ((lang, value) in listOf("en" to t.name.en, "hi" to t.name.hi, "mr" to t.name.mr)) {
            if (value.isBlank()) add("name.$lang is blank")
            if (value.length > 40) add("name.$lang is longer than 40 characters")
        }
        for ((key, value) in listOf(
            "primary" to t.palette.primary, "secondary" to t.palette.secondary,
            "accent" to t.palette.accent, "background" to t.palette.background,
        )) {
            if (!COLOR_REGEX.matches(value)) add("palette.$key '$value' is not #RRGGBB or #AARRGGBB")
        }
        val lite = t.lite
        range("lite.background.intensity", lite.background.intensity, 0f, 2f)
        if (lite.overlays.size > 6) add("lite.overlays has more than 6 entries")
        lite.overlays.forEachIndexed { i, o ->
            range("lite.overlays[$i].scale", o.scale, 0.2f, 3f)
            range("lite.overlays[$i].opacity", o.opacity, 0f, 1f)
        }
        range("lite.border.widthPct", lite.border.widthPct, 0f, 12f)
        range("lite.grade.warmth", lite.grade.warmth, -1f, 1f)
        range("lite.grade.saturation", lite.grade.saturation, 0f, 2f)
        range("lite.grade.contrast", lite.grade.contrast, 0.5f, 1.5f)
        range("lite.grade.vignette", lite.grade.vignette, 0f, 1f)
        range("lite.grade.glow", lite.grade.glow, 0f, 1f)
        range("lite.particles.density", lite.particles.density, 0f, 1f)
        for ((lang, list) in listOf("en" to t.greetings.en, "hi" to t.greetings.hi, "mr" to t.greetings.mr)) {
            if (list.size > 8) add("greetings.$lang has more than 8 entries")
            list.forEachIndexed { i, g ->
                if (g.isBlank()) add("greetings.$lang[$i] is blank")
                if (g.length > 80) add("greetings.$lang[$i] is longer than 80 characters")
            }
        }
        if (t.prompt.positive.isBlank()) add("prompt.positive is blank")
        if (t.prompt.positive.length > 1200) add("prompt.positive is longer than 1200 characters")
        if (t.prompt.negative.length > 600) add("prompt.negative is longer than 600 characters")
        t.prompt.outfit?.let { if (it.isBlank() || it.length > 600) add("prompt.outfit must be 1..600 characters when present") }
        range("prompt.recommendedStrength", t.prompt.recommendedStrength, 0f, 1f)
    }

    private fun MutableList<String>.range(name: String, value: Float, min: Float, max: Float) {
        if (value.isNaN() || value < min || value > max) add("$name=$value is outside $min..$max")
    }
}
