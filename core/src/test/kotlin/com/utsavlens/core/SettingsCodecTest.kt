package com.utsavlens.core

import com.google.common.truth.Truth.assertThat
import com.utsavlens.core.settings.AppSettings
import com.utsavlens.core.settings.CloudSettings
import com.utsavlens.core.settings.SettingsCodec
import com.utsavlens.core.settings.SettingsMigration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class SettingsCodecTest {
    private val codec = SettingsCodec()

    @Test fun nullBlankAndGarbageGiveDefaults() {
        assertThat(codec.decode(null)).isEqualTo(AppSettings())
        assertThat(codec.decode("  ")).isEqualTo(AppSettings())
        assertThat(codec.decode("{not json")).isEqualTo(AppSettings())
        assertThat(codec.decode("[1,2,3]")).isEqualTo(AppSettings())
    }

    @Test fun roundTrip() {
        val s = AppSettings(language = "mr", cloud = CloudSettings(modelId = "custom-model", uploadConsentProviders = listOf("gemini")))
        assertThat(codec.decode(codec.encode(s))).isEqualTo(s)
    }

    @Test fun deepMergeKeepsSiblingDefaultsWhenOnlyOneNestedKeyIsStored() {
        val stored = """{"schemaVersion":2,"cloud":{"modelId":"my-model"},"local":{"steps":30}}"""
        val s = codec.decode(stored)
        assertThat(s.cloud.modelId).isEqualTo("my-model")
        assertThat(s.cloud.baseUrl).isEqualTo(CloudSettings().baseUrl)
        assertThat(s.cloud.uploadMaxEdgePx).isEqualTo(CloudSettings().uploadMaxEdgePx)
        assertThat(s.local.steps).isEqualTo(30)
        assertThat(s.local.wifiOnly).isTrue()
    }

    @Test fun wrongTypesFallBackToDefaultsPerField() {
        val stored = """{"schemaVersion":2,"language":42,"showMaskDebug":"yes","cloud":"oops","local":{"wifiOnly":"no","steps":12}}"""
        val s = codec.decode(stored)
        assertThat(s.language).isEqualTo("system")
        assertThat(s.showMaskDebug).isFalse()
        assertThat(s.cloud).isEqualTo(CloudSettings())
        assertThat(s.local.wifiOnly).isTrue()
        assertThat(s.local.steps).isEqualTo(12)
    }

    @Test fun unknownKeysAreDropped() {
        val s = codec.decode("""{"schemaVersion":2,"future":{"a":1},"language":"hi"}""")
        assertThat(s.language).isEqualTo("hi")
        assertThat(codec.encode(s)).doesNotContain("future")
    }

    @Test fun migratesV1FlatModelIdIntoNestedCloud() {
        val s = codec.decode("""{"schemaVersion":1,"cloudModelId":"legacy-model","language":"hi"}""")
        assertThat(s.schemaVersion).isEqualTo(AppSettings.CURRENT_VERSION)
        assertThat(s.cloud.modelId).isEqualTo("legacy-model")
        assertThat(s.language).isEqualTo("hi")
    }

    @Test fun missingVersionIsTreatedAsV1() {
        val s = codec.decode("""{"cloudModelId":"legacy-model"}""")
        assertThat(s.cloud.modelId).isEqualTo("legacy-model")
    }

    @Test fun newerVersionIsReadWithoutMigrations() {
        val s = codec.decode("""{"schemaVersion":99,"language":"mr","brandNew":true}""")
        assertThat(s.language).isEqualTo("mr")
        assertThat(s.schemaVersion).isEqualTo(AppSettings.CURRENT_VERSION)
    }

    @Test fun missingMigrationStepFallsBackToDefaultsInsteadOfCrashing() {
        val broken = SettingsCodec(migrations = emptyList<SettingsMigration>())
        assertThat(broken.decode("""{"schemaVersion":1,"language":"hi"}""")).isEqualTo(AppSettings())
    }

    @Test fun valuesAreClamped() {
        val s = codec.decode(
            """{"schemaVersion":2,"cloud":{"uploadMaxEdgePx":99999,"timeoutSeconds":1,"baseUrl":"https://x.test/ "},
               "local":{"steps":500},"lastOptions":{"strength":7.5,"variations":40}}""",
        )
        assertThat(s.cloud.uploadMaxEdgePx).isEqualTo(4096)
        assertThat(s.cloud.timeoutSeconds).isEqualTo(15)
        assertThat(s.cloud.baseUrl).isEqualTo("https://x.test")
        assertThat(s.local.steps).isEqualTo(50)
        assertThat(s.lastOptions.strength).isEqualTo(1f)
        assertThat(s.lastOptions.variations).isEqualTo(4)
    }

    @Test fun deepMergeIsRecursiveAndTyped() {
        fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
        val merged = SettingsCodec.deepMerge(
            obj("""{"a":1,"b":{"c":true,"d":"x"},"e":[1]}"""),
            obj("""{"a":"wrong","b":{"c":false},"e":[2,3],"z":1}"""),
        )
        assertThat(merged).isEqualTo(obj("""{"a":1,"b":{"c":false,"d":"x"},"e":[2,3]}"""))
    }
}
