package com.utsavlens.engine.cloud

import com.google.common.truth.Truth.assertThat
import com.utsavlens.engine.api.TransformOptions
import com.utsavlens.themes.ThemeLoadResult
import com.utsavlens.themes.ThemeParser
import org.junit.Test
import java.io.File

class CloudPromptTest {
    private val theme = (ThemeParser.parse("diwali.json", File("../app/src/main/assets/themes/diwali.json").readText()) as ThemeLoadResult.Valid).theme

    @Test fun keepFaceIsStatedAndOutfitOnlyWhenAsked() {
        val keep = CloudPrompt.build(theme, TransformOptions(keepFace = true, changeOutfit = false))
        assertThat(keep).contains("face, identity")
        assertThat(keep).contains("Keep the clothing unchanged")
        assertThat(keep).doesNotContain(theme.prompt.outfit!!)
        val outfit = CloudPrompt.build(theme, TransformOptions(changeOutfit = true))
        assertThat(outfit).contains(theme.prompt.outfit!!)
    }

    @Test fun keepBackgroundReplacesTheScenePrompt() {
        val p = CloudPrompt.build(theme, TransformOptions(keepBackground = true))
        assertThat(p).contains("Keep the original background")
        assertThat(p).doesNotContain(theme.prompt.positive)
        assertThat(CloudPrompt.build(theme, TransformOptions())).contains(theme.prompt.positive)
    }

    @Test fun strengthAndAssistTextAreReflected() {
        assertThat(CloudPrompt.build(theme, TransformOptions(strength = 0.1f))).contains("subtle")
        assertThat(CloudPrompt.build(theme, TransformOptions(strength = 0.95f))).contains("dramatic")
        assertThat(CloudPrompt.build(theme, TransformOptions(promptOverride = "warm rim light"))).contains("Additional direction: warm rim light")
        assertThat(CloudPrompt.build(theme, TransformOptions())).contains("Do not add any text")
    }
}
