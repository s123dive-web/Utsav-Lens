package com.utsavlens.themes

import com.google.common.truth.Truth.assertThat
import com.utsavlens.core.AppLanguage
import com.utsavlens.core.AppLogger
import org.junit.Test

class ThemeParserTest {
    private val valid = """
        {
          "schemaVersion": 1,
          "id": "diwali",
          "name": {"en": "Diwali", "hi": "दीवाली", "mr": "दिवाळी"},
          "category": "festival",
          "palette": {"primary": "#FF9933", "secondary": "#7B1FA2", "accent": "#FFD54F", "background": "#1A0B2E"},
          "lite": {
            "background": {"style": "night_bokeh"},
            "overlays": [{"type": "diya_row", "anchor": "bottom"}],
            "border": {"style": "rangoli", "widthPct": 4},
            "grade": {"warmth": 0.3, "saturation": 1.1},
            "particles": {"type": "sparkle", "density": 0.5}
          },
          "greetings": {"en": ["Happy Diwali!"], "hi": ["शुभ दीपावली"], "mr": ["शुभ दीपावली"]},
          "prompt": {"positive": "festive diwali night", "negative": "blurry", "recommendedStrength": 0.65}
        }
    """.trimIndent()

    private fun problems(fileName: String, text: String): List<String> =
        (ThemeParser.parse(fileName, text) as ThemeLoadResult.Invalid).problems

    @Test fun parsesValidTheme() {
        val theme = (ThemeParser.parse("diwali.json", valid) as ThemeLoadResult.Valid).theme
        assertThat(theme.name.get(AppLanguage.MR)).isEqualTo("दिवाळी")
        assertThat(theme.lite.background.style).isEqualTo(BackgroundStyle.NIGHT_BOKEH)
        assertThat(theme.lite.grade.contrast).isEqualTo(1f)
        assertThat(theme.prompt.outfit).isNull()
    }

    @Test fun rejectsBrokenJson() {
        assertThat(problems("diwali.json", "{ \"id\": ")).isNotEmpty()
        assertThat(problems("diwali.json", "")).isNotEmpty()
        assertThat(problems("diwali.json", "[]")).isNotEmpty()
    }

    @Test fun rejectsMissingRequiredField() {
        assertThat(problems("diwali.json", valid.replace("\"mr\": \"दिवाळी\"", "\"xx\": \"y\""))).isNotEmpty()
        assertThat(problems("diwali.json", valid.replace("\"positive\": \"festive diwali night\",", ""))).isNotEmpty()
    }

    @Test fun rejectsUnknownRendererName() {
        assertThat(problems("diwali.json", valid.replace("night_bokeh", "laser_show"))).isNotEmpty()
        assertThat(problems("diwali.json", valid.replace("diya_row", "ufo"))).isNotEmpty()
    }

    @Test fun rejectsWrongTypes() {
        assertThat(problems("diwali.json", valid.replace("\"widthPct\": 4", "\"widthPct\": \"wide\""))).isNotEmpty()
    }

    @Test fun rejectsIdNotMatchingFileName() {
        assertThat(problems("holi.json", valid).single()).contains("must equal the file name")
    }

    @Test fun rejectsBadColourRangeAndBlankName() {
        val bad = valid.replace("#FF9933", "orange").replace("\"warmth\": 0.3", "\"warmth\": 5").replace("\"en\": \"Diwali\"", "\"en\": \" \"")
        val p = problems("diwali.json", bad)
        assertThat(p.any { "palette.primary" in it }).isTrue()
        assertThat(p.any { "lite.grade.warmth" in it }).isTrue()
        assertThat(p.any { "name.en" in it }).isTrue()
    }

    @Test fun rejectsUnsupportedSchemaVersion() {
        assertThat(problems("diwali.json", valid.replace("\"schemaVersion\": 1", "\"schemaVersion\": 7")).single()).contains("schemaVersion")
    }

    @Test fun catalogueSkipsBadFilesLogsThemAndNeverThrows() {
        val warnings = mutableListOf<String>()
        val catalogue = ThemeCatalogue.load(
            listOf(
                ThemeSource("themes/diwali.json") { valid },
                ThemeSource("themes/broken.json") { "{{{" },
                ThemeSource("themes/unreadable.json") { throw java.io.IOException("io") },
                ThemeSource("themes/holi.json") { valid.replace("\"id\": \"diwali\"", "\"id\": \"holi\"") },
                ThemeSource("themes/zz_copy.json") { valid },
            ),
            AppLogger { _, m -> warnings += m },
        )
        assertThat(catalogue.themes.map { it.id }).containsExactly("diwali", "holi").inOrder()
        assertThat(catalogue.skipped.map { it.fileName })
            .containsExactly("themes/broken.json", "themes/unreadable.json", "themes/zz_copy.json")
        assertThat(warnings).hasSize(3)
        assertThat(catalogue["holi"]).isNotNull()
        assertThat(catalogue["nope"]).isNull()
    }
}
