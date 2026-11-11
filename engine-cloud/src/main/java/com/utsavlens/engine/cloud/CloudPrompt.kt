package com.utsavlens.engine.cloud

import com.utsavlens.engine.api.TransformOptions
import com.utsavlens.themes.Theme

/** Builds the edit instruction sent with the photo. Pure and unit-tested. */
object CloudPrompt {
    fun build(theme: Theme, options: TransformOptions): String = buildString {
        append("Edit this photo for the occasion \"").append(theme.name.en).append("\". ")
        if (options.keepFace) {
            append("Keep every person's face, identity, expression, skin tone, hairstyle, age and body shape exactly the same as in the original photo; do not beautify or alter facial features. ")
        } else {
            append("You may restyle the people artistically, but keep them recognisable. ")
        }
        if (options.keepBackground) {
            append("Keep the original background and composition; only add festive decorations, lighting and colour mood that match the theme. ")
        } else {
            append(theme.prompt.positive.trim()).append(' ')
        }
        val outfit = theme.prompt.outfit
        if (options.changeOutfit && !outfit.isNullOrBlank()) {
            append("Change the clothing to ").append(outfit.trim()).append(", fitted naturally to the same pose. ")
        } else {
            append("Keep the clothing unchanged. ")
        }
        options.promptOverride?.takeIf { it.isNotBlank() }?.let { append("Additional direction: ").append(it.trim()).append(' ') }
        val strength = options.strength.coerceIn(0f, 1f)
        append(
            when {
                strength < 0.34f -> "Make the change subtle. "
                strength < 0.67f -> "Make the change clearly visible but natural. "
                else -> "Make the festive transformation rich and dramatic. "
            },
        )
        if (theme.prompt.negative.isNotBlank()) append("Avoid: ").append(theme.prompt.negative.trim()).append(". ")
        append("Do not add any text, captions, logos or watermarks. Return one photorealistic image.")
    }
}
