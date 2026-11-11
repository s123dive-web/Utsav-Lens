package com.utsavlens.engine.api

/** Which option controls are usable for an engine, and the request values after applying its limits. */
data class OptionAvailability(
    val keepFaceEditable: Boolean,
    val outfitEnabled: Boolean,
    val keepBackgroundEnabled: Boolean,
    val maxVariations: Int,
)

object OptionsPolicy {
    fun availability(caps: EngineCapabilities): OptionAvailability = OptionAvailability(
        keepFaceEditable = !caps.faceAlwaysPreserved,
        outfitEnabled = caps.outfitChange,
        keepBackgroundEnabled = true,
        maxVariations = caps.maxVariations.coerceIn(1, 4),
    )

    /** Forces unsupported options off / locked values on. The UI and the service both apply this. */
    fun resolve(options: TransformOptions, caps: EngineCapabilities): TransformOptions = options.copy(
        keepFace = if (caps.faceAlwaysPreserved) true else options.keepFace,
        changeOutfit = options.changeOutfit && caps.outfitChange,
        strength = if (options.strength.isNaN()) 0.7f else options.strength.coerceIn(0f, 1f),
        variations = options.variations.coerceIn(1, caps.maxVariations.coerceIn(1, 4)),
        greeting = options.greeting?.takeIf { it.text.isNotBlank() }?.let { it.copy(text = it.text.trim().take(80)) },
        promptOverride = options.promptOverride?.trim()?.takeIf { it.isNotEmpty() }?.take(1200),
    )
}
