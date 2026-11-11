package com.utsavlens.engine.lite

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.utsavlens.engine.api.Aspect
import com.utsavlens.engine.api.GreetingText
import com.utsavlens.engine.api.TransformOptions
import com.utsavlens.segmentation.FaceBox
import com.utsavlens.segmentation.PersonMask
import com.utsavlens.themes.ThemeCatalogue
import com.utsavlens.themes.ThemeSource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders every bundled theme with real (native) Canvas drawing and checks the LITE guarantees. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiteRendererTest {
    private val w = 240
    private val h = 320
    private val themes = ThemeCatalogue.load(
        File("../app/src/main/assets/themes").listFiles { f -> f.name.endsWith(".json") }!!
            .map { f -> ThemeSource("themes/${f.name}") { f.readText() } },
    ).themes

    private fun source(): Bitmap {
        val px = IntArray(w * h) { i -> Color.rgb((i * 7) % 256, (i / w * 3) % 256, (i * 13 + 40) % 256) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Person = solid block in the middle; face = its upper part. */
    private fun mask(): PersonMask {
        val alpha = ByteArray(w * h) { i ->
            val x = i % w; val y = i / w
            if (x in 80 until 160 && y in 60 until 300) 255.toByte() else 0
        }
        return PersonMask(w, h, alpha, listOf(FaceBox(100f, 80f, 140f, 130f)), personFound = true)
    }

    @Test fun allThemesRenderAndNeverChangeFacePixels() {
        assertThat(themes).hasSize(19)
        val src = source()
        for (theme in themes) {
            val out = LiteRenderer.render(src, mask(), theme, TransformOptions(strength = 1f, greeting = GreetingText("शुभ दीपावली")), seed = 7).bitmap
            assertWithMessage(theme.id).that(out.width).isEqualTo(w)
            assertWithMessage(theme.id).that(out.height).isEqualTo(h)
            var faceDiffs = 0
            for (y in 82 until 128) for (x in 102 until 138) if (out.getPixel(x, y) != src.getPixel(x, y)) faceDiffs++
            assertWithMessage("${theme.id}: face pixels changed").that(faceDiffs).isEqualTo(0)
            var bgDiffs = 0
            for (y in 0 until h step 4) for (x in 0 until 60 step 4) if (out.getPixel(x, y) != src.getPixel(x, y)) bgDiffs++
            assertWithMessage("${theme.id}: background was not themed").that(bgDiffs).isGreaterThan(50)
        }
    }

    @Test fun keepBackgroundStillPreservesPersonAndDecorates() {
        val src = source()
        val out = LiteRenderer.render(src, mask(), themes.first { it.id == "diwali" }, TransformOptions(keepBackground = true), seed = 1).bitmap
        for (y in 82 until 128 step 3) for (x in 102 until 138 step 3) assertThat(out.getPixel(x, y)).isEqualTo(src.getPixel(x, y))
    }

    @Test fun noPersonStillProducesThemedImage() {
        val src = source()
        val result = LiteRenderer.render(src, PersonMask.empty(w, h), themes.first { it.id == "holi" }, TransformOptions(), seed = 3)
        assertThat(result.personFound).isFalse()
        var diffs = 0
        for (y in 0 until h step 5) for (x in 0 until w step 5) if (result.bitmap.getPixel(x, y) != src.getPixel(x, y)) diffs++
        assertThat(diffs).isGreaterThan(20)
        assertThat(LiteRenderer.render(src, null, themes[0], TransformOptions(), seed = 3).personFound).isFalse()
    }

    @Test fun aspectOptionsChangeOutputShape() {
        val src = source()
        val square = LiteRenderer.render(src, mask(), themes[0], TransformOptions(aspect = Aspect.SQUARE), 1).bitmap
        assertThat(square.width).isEqualTo(square.height)
        val story = LiteRenderer.render(src, mask(), themes[0], TransformOptions(aspect = Aspect.STORY_9_16), 1).bitmap
        assertThat(story.width.toFloat() / story.height).isWithin(0.01f).of(9f / 16f)
    }

    @Test fun sameSeedIsDeterministicAndVariationsDiffer() {
        val src = source()
        val t = themes.first { it.id == "birthday" }
        val a = LiteRenderer.render(src, mask(), t, TransformOptions(), 5).bitmap
        val b = LiteRenderer.render(src, mask(), t, TransformOptions(), 5).bitmap
        val c = LiteRenderer.render(src, mask(), t, TransformOptions(), 5 + 7919).bitmap
        assertThat(a.sameAs(b)).isTrue()
        assertThat(a.sameAs(c)).isFalse()
    }

    @Test fun decorationsRenderAtAnyTimeForVideo() {
        for (theme in themes) {
            val bmp = Bitmap.createBitmap(180, 320, Bitmap.Config.ARGB_8888)
            LiteRenderer.drawDecorations(android.graphics.Canvas(bmp), 180, 320, theme, 0.8f, 9, t = 2.4f, greeting = "Happy!")
            var painted = 0
            for (y in 0 until 320 step 4) for (x in 0 until 180 step 4) if (Color.alpha(bmp.getPixel(x, y)) != 0) painted++
            assertWithMessage(theme.id).that(painted).isGreaterThan(10)
        }
    }
}
