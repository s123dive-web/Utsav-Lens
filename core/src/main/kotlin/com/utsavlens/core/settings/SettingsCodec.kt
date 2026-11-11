package com.utsavlens.core.settings

import com.utsavlens.core.AppLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One step from [fromVersion] to fromVersion + 1, operating on raw JSON. */
class SettingsMigration(val fromVersion: Int, val migrate: (JsonObject) -> JsonObject)

/**
 * Versioned, typed, deep settings loading:
 * 1. run migrations oldVersion → CURRENT on the raw JSON,
 * 2. deep-merge the stored document onto current defaults (unknown keys dropped, wrong types
 *    replaced by the default, nested objects merged key by key),
 * 3. decode into [AppSettings] and clamp ranges.
 * Corrupt input never throws: defaults are returned.
 */
class SettingsCodec(
    private val migrations: List<SettingsMigration> = DEFAULT_MIGRATIONS,
    private val logger: AppLogger = AppLogger.NONE,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun encode(settings: AppSettings): String =
        json.encodeToString(AppSettings.serializer(), settings.copy(schemaVersion = AppSettings.CURRENT_VERSION))

    fun decode(stored: String?): AppSettings {
        if (stored.isNullOrBlank()) return AppSettings()
        return try {
            val raw = json.parseToJsonElement(stored) as? JsonObject ?: return AppSettings()
            val migrated = migrate(raw)
            val defaults = json.encodeToJsonElement(AppSettings()).jsonObject
            val merged = deepMerge(defaults, migrated)
            sanitize(json.decodeFromJsonElement(AppSettings.serializer(), merged))
        } catch (e: Exception) {
            logger.warn(TAG, "Settings unreadable, using defaults: ${e.javaClass.simpleName}")
            AppSettings()
        }
    }

    internal fun migrate(raw: JsonObject): JsonObject {
        var version = raw["schemaVersion"]?.let { (it as? JsonPrimitive)?.intOrNull } ?: 1
        var doc = raw
        if (version > AppSettings.CURRENT_VERSION) {
            // Written by a newer app: keep what still type-checks, do not run migrations.
            return JsonObject(doc + ("schemaVersion" to JsonPrimitive(AppSettings.CURRENT_VERSION)))
        }
        while (version < AppSettings.CURRENT_VERSION) {
            val step = migrations.firstOrNull { it.fromVersion == version }
                ?: error("No settings migration from version $version")
            doc = step.migrate(doc)
            version++
        }
        return JsonObject(doc + ("schemaVersion" to JsonPrimitive(version)))
    }

    private fun sanitize(s: AppSettings): AppSettings = s.copy(
        schemaVersion = AppSettings.CURRENT_VERSION,
        cloud = s.cloud.copy(
            uploadMaxEdgePx = s.cloud.uploadMaxEdgePx.coerceIn(512, 4096),
            timeoutSeconds = s.cloud.timeoutSeconds.coerceIn(15, 600),
            baseUrl = s.cloud.baseUrl.trim().trimEnd('/').ifEmpty { CloudSettings().baseUrl },
            modelId = s.cloud.modelId.trim().ifEmpty { CloudSettings().modelId },
        ),
        local = s.local.copy(steps = s.local.steps.coerceIn(2, 8)),
        promptAssist = s.promptAssist.copy(
            baseUrl = s.promptAssist.baseUrl.trim().trimEnd('/').ifEmpty { PromptAssistSettings().baseUrl },
            modelId = s.promptAssist.modelId.trim().ifEmpty { PromptAssistSettings().modelId },
        ),
        lastOptions = s.lastOptions.copy(
            strength = s.lastOptions.strength.coerceIn(0f, 1f),
            variations = s.lastOptions.variations.coerceIn(1, 4),
        ),
    )

    companion object {
        private const val TAG = "SettingsCodec"

        val DEFAULT_MIGRATIONS: List<SettingsMigration> = listOf(
            // v1 kept the cloud model as a flat "cloudModelId" key; v2 nests it under "cloud".
            SettingsMigration(1) { old ->
                val flat = old["cloudModelId"]
                val cloud = (old["cloud"] as? JsonObject) ?: JsonObject(emptyMap())
                val newCloud = if (flat is JsonPrimitive && flat.isString && "modelId" !in cloud) {
                    JsonObject(cloud + ("modelId" to flat))
                } else cloud
                JsonObject(old - "cloudModelId" + ("cloud" to newCloud))
            },
        )

        /**
         * Typed deep merge. The shape and types come from [defaults]; values come from [stored]
         * where they have the same JSON type. Arrays are replaced wholesale.
         */
        fun deepMerge(defaults: JsonObject, stored: JsonObject): JsonObject {
            val out = LinkedHashMap<String, JsonElement>(defaults.size)
            for ((key, def) in defaults) {
                val value = stored[key]
                out[key] = when {
                    value == null || value is JsonNull -> def
                    def is JsonObject -> if (value is JsonObject) deepMerge(def, value) else def
                    def is JsonArray -> if (value is JsonArray) value else def
                    def is JsonPrimitive -> if (value is JsonPrimitive && sameKind(def, value)) value else def
                    else -> def
                }
            }
            return JsonObject(out)
        }

        private fun sameKind(a: JsonPrimitive, b: JsonPrimitive): Boolean = when {
            a.isString || b.isString -> a.isString && b.isString
            a.booleanOrNull != null -> b.booleanOrNull != null
            a.doubleOrNull != null -> b.doubleOrNull != null && b.booleanOrNull == null
            else -> false
        }
    }
}
