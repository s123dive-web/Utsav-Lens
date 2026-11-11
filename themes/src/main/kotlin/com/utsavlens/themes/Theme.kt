package com.utsavlens.themes

import com.utsavlens.core.AppLanguage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One festive theme. Mirrors assets/themes/<id>.json (schema documented in CLAUDE.md). */
@Serializable
data class Theme(
    val schemaVersion: Int,
    val id: String,
    val name: LocalizedText,
    val category: ThemeCategory,
    val palette: Palette,
    val lite: LiteRecipe,
    val greetings: LocalizedList = LocalizedList(),
    val prompt: PromptSpec,
)

@Serializable
data class LocalizedText(val en: String, val hi: String, val mr: String) {
    fun get(language: AppLanguage): String = when (language) {
        AppLanguage.HI -> hi
        AppLanguage.MR -> mr
        else -> en
    }
}

@Serializable
data class LocalizedList(
    val en: List<String> = emptyList(),
    val hi: List<String> = emptyList(),
    val mr: List<String> = emptyList(),
) {
    fun get(language: AppLanguage): List<String> = when (language) {
        AppLanguage.HI -> hi
        AppLanguage.MR -> mr
        else -> en
    }
}

@Serializable
enum class ThemeCategory {
    @SerialName("festival") FESTIVAL,
    @SerialName("celebration") CELEBRATION,
    @SerialName("national") NATIONAL,
}

/** Colours as #RRGGBB or #AARRGGBB. */
@Serializable
data class Palette(val primary: String, val secondary: String, val accent: String, val background: String)

@Serializable
data class LiteRecipe(
    val background: BackgroundSpec,
    val overlays: List<OverlaySpec> = emptyList(),
    val border: BorderSpec = BorderSpec(),
    val grade: GradeSpec = GradeSpec(),
    val particles: ParticleSpec = ParticleSpec(),
)

@Serializable
data class BackgroundSpec(val style: BackgroundStyle, val intensity: Float = 1f)

@Serializable
data class OverlaySpec(
    val type: OverlayType,
    val anchor: Anchor = Anchor.BOTTOM,
    val scale: Float = 1f,
    val opacity: Float = 1f,
)

@Serializable
data class BorderSpec(val style: BorderStyle = BorderStyle.NONE, val widthPct: Float = 4f)

@Serializable
data class GradeSpec(
    val warmth: Float = 0f,
    val saturation: Float = 1f,
    val contrast: Float = 1f,
    val vignette: Float = 0f,
    val glow: Float = 0f,
)

@Serializable
data class ParticleSpec(val type: ParticleType = ParticleType.NONE, val density: Float = 0.5f)

@Serializable
data class PromptSpec(
    val positive: String,
    val negative: String,
    val outfit: String? = null,
    val recommendedStrength: Float = 0.6f,
)

// The vocabularies below are the contract between theme files and the procedural renderers in
// :engine-lite, which implement every value with an exhaustive `when`. A theme using a name that
// is not listed here fails to parse and is skipped.

@Serializable
enum class BackgroundStyle {
    @SerialName("night_bokeh") NIGHT_BOKEH,
    @SerialName("color_splash") COLOR_SPLASH,
    @SerialName("sunburst") SUNBURST,
    @SerialName("sky") SKY,
    @SerialName("gradient_glow") GRADIENT_GLOW,
    @SerialName("floral_wall") FLORAL_WALL,
    @SerialName("temple_arch") TEMPLE_ARCH,
    @SerialName("snowy_night") SNOWY_NIGHT,
    @SerialName("confetti_party") CONFETTI_PARTY,
}

@Serializable
enum class OverlayType {
    @SerialName("diya_row") DIYA_ROW,
    @SerialName("marigold_garland") MARIGOLD_GARLAND,
    @SerialName("rangoli_corner") RANGOLI_CORNER,
    @SerialName("fireworks") FIREWORKS,
    @SerialName("lanterns") LANTERNS,
    @SerialName("kites") KITES,
    @SerialName("crescent_stars") CRESCENT_STARS,
    @SerialName("snowflakes") SNOWFLAKES,
    @SerialName("pine_tree") PINE_TREE,
    @SerialName("string_lights") STRING_LIGHTS,
    @SerialName("tricolor_ribbons") TRICOLOR_RIBBONS,
    @SerialName("balloons") BALLOONS,
    @SerialName("paisley") PAISLEY,
    @SerialName("flower_shower") FLOWER_SHOWER,
    @SerialName("rakhi") RAKHI,
    @SerialName("kolam") KOLAM,
    @SerialName("pookalam") POOKALAM,
    @SerialName("bonfire") BONFIRE,
    @SerialName("sugarcane_pot") SUGARCANE_POT,
    @SerialName("gudi") GUDI,
    @SerialName("dandiya") DANDIYA,
    @SerialName("bow_arrow") BOW_ARROW,
    @SerialName("lotus") LOTUS,
    @SerialName("color_clouds") COLOR_CLOUDS,
}

@Serializable
enum class Anchor {
    @SerialName("top") TOP,
    @SerialName("bottom") BOTTOM,
    @SerialName("corners") CORNERS,
    @SerialName("sides") SIDES,
    @SerialName("full") FULL,
    @SerialName("top_left") TOP_LEFT,
    @SerialName("top_right") TOP_RIGHT,
    @SerialName("bottom_left") BOTTOM_LEFT,
    @SerialName("bottom_right") BOTTOM_RIGHT,
}

@Serializable
enum class BorderStyle {
    @SerialName("none") NONE,
    @SerialName("simple") SIMPLE,
    @SerialName("rangoli") RANGOLI,
    @SerialName("floral") FLORAL,
    @SerialName("zari") ZARI,
    @SerialName("lights") LIGHTS,
    @SerialName("tricolor") TRICOLOR,
    @SerialName("dotted") DOTTED,
}

@Serializable
enum class ParticleType {
    @SerialName("none") NONE,
    @SerialName("sparkle") SPARKLE,
    @SerialName("color_powder") COLOR_POWDER,
    @SerialName("petals") PETALS,
    @SerialName("snow") SNOW,
    @SerialName("confetti") CONFETTI,
    @SerialName("embers") EMBERS,
}
