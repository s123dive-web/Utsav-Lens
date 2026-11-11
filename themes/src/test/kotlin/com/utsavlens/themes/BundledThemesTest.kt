package com.utsavlens.themes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/** Validates the real theme files shipped in the APK (app/src/main/assets/themes). */
class BundledThemesTest {
    private val dir = sequenceOf("../app/src/main/assets/themes", "app/src/main/assets/themes").map(::File).first { it.isDirectory }
    private val sources = dir.listFiles { f -> f.name.endsWith(".json") }!!.map { f -> ThemeSource("themes/${f.name}") { f.readText() } }

    @Test fun everyBundledThemeIsValid() {
        val catalogue = ThemeCatalogue.load(sources)
        assertWithMessage(catalogue.skipped.joinToString("\n") { "${it.fileName}: ${it.problems}" }).that(catalogue.skipped).isEmpty()
    }

    @Test fun allRequiredThemesShipInDisplayOrder() {
        val ids = ThemeCatalogue.load(sources).themes.map { it.id }
        assertThat(ids).containsExactlyElementsIn(ThemeCatalogue.DISPLAY_ORDER).inOrder()
        assertThat(ids).hasSize(19)
    }

    @Test fun everyThemeHasGreetingsInAllThreeLanguages() {
        ThemeCatalogue.load(sources).themes.forEach { t ->
            assertWithMessage(t.id).that(t.greetings.en).isNotEmpty()
            assertWithMessage(t.id).that(t.greetings.hi).isNotEmpty()
            assertWithMessage(t.id).that(t.greetings.mr).isNotEmpty()
            assertWithMessage(t.id).that(t.prompt.outfit).isNotNull()
        }
    }

    @Test fun aBrokenFileAmongRealOnesIsSkippedWithoutAffectingTheRest() {
        val catalogue = ThemeCatalogue.load(sources + ThemeSource("themes/zzz_broken.json") { "{ \"id\": 5, " })
        assertThat(catalogue.themes).hasSize(19)
        assertThat(catalogue.skipped.single().fileName).isEqualTo("themes/zzz_broken.json")
    }
}
