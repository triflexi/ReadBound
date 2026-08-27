package app.readbound.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore("reader_settings")

enum class ReaderTheme { LIGHT, SEPIA, DARK }
enum class ReadingMode { PAGED, SCROLL }
enum class AnkiBackMode { CONCISE, ARTICLE }

data class ReaderPreferences(
    val followSystemTheme: Boolean = true,
    val theme: ReaderTheme = ReaderTheme.SEPIA,
    val fontFamily: String = "Literata",
    val fontSize: Int = 18,
    val lineHeight: Double = 1.72,
    val margin: Int = 24,
    val readingMode: ReadingMode = ReadingMode.PAGED,
    val language: String = "ru",
)

data class SyncPreferences(
    val enabled: Boolean = false,
    val url: String = "",
    val username: String = "",
    val lastSyncAt: Long = 0,
)

data class AnkiPreferences(
    val deckId: Long = 1,
    val deckName: String = "Default",
    val modelId: Long = -1,
    val modelName: String = "ReadBound Card",
    val fieldIndex: Int = 0,
    val backFieldIndex: Int = 1,
    val backMode: AnkiBackMode = AnkiBackMode.CONCISE,
    val includeReading: Boolean = true,
    val includeTags: Boolean = false,
    val includeDictionaryName: Boolean = false,
)

data class AiPreferences(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val model: String = "",
    val targetLanguage: String = "Русский",
    val prompt: String = "Translate the selected word or phrase. Give a concise translation, part of speech, and one short usage note when useful.",
)

class SettingsRepository(private val context: Context) {
    val reader: Flow<ReaderPreferences> = context.dataStore.data.map { p ->
        ReaderPreferences(
            followSystemTheme = p[AUTO_THEME] ?: true,
            theme = runCatching { ReaderTheme.valueOf(p[THEME] ?: "SEPIA") }.getOrDefault(ReaderTheme.SEPIA),
            fontFamily = p[FONT] ?: "Literata",
            fontSize = p[FONT_SIZE] ?: 18,
            lineHeight = p[LINE_HEIGHT] ?: 1.72,
            margin = p[MARGIN] ?: 24,
            readingMode = runCatching { ReadingMode.valueOf(p[MODE] ?: "PAGED") }.getOrDefault(ReadingMode.PAGED),
            language = p[LANGUAGE] ?: "ru",
        )
    }

    val sync: Flow<SyncPreferences> = context.dataStore.data.map { p ->
        SyncPreferences(p[SYNC_ENABLED] ?: false, p[SYNC_URL] ?: "", p[SYNC_USER] ?: "", p[LAST_SYNC] ?: 0)
    }

    val anki: Flow<AnkiPreferences> = context.dataStore.data.map { p ->
        val storedModelName = p[ANKI_MODEL_NAME] ?: "ReadBound Card"
        val needsTwoFieldMigration = storedModelName == "Reader Quote"
        AnkiPreferences(
            deckId = p[ANKI_DECK_ID] ?: 1,
            deckName = p[ANKI_DECK_NAME] ?: "Default",
            modelId = if (needsTwoFieldMigration) -1 else p[ANKI_MODEL_ID] ?: -1,
            modelName = if (needsTwoFieldMigration) "ReadBound Card" else storedModelName,
            fieldIndex = p[ANKI_FIELD] ?: 0,
            backFieldIndex = p[ANKI_BACK_FIELD] ?: 1,
            backMode = runCatching { AnkiBackMode.valueOf(p[ANKI_BACK_MODE] ?: "CONCISE") }.getOrDefault(AnkiBackMode.CONCISE),
            includeReading = p[ANKI_INCLUDE_READING] ?: true,
            includeTags = p[ANKI_INCLUDE_TAGS] ?: false,
            includeDictionaryName = p[ANKI_INCLUDE_DICTIONARY] ?: false,
        )
    }

    val ai: Flow<AiPreferences> = context.dataStore.data.map { p ->
        AiPreferences(
            enabled = p[AI_ENABLED] ?: false,
            endpoint = p[AI_ENDPOINT] ?: "",
            model = p[AI_MODEL] ?: "",
            targetLanguage = p[AI_TARGET_LANGUAGE] ?: "Русский",
            prompt = p[AI_PROMPT] ?: AiPreferences().prompt,
        )
    }

    suspend fun updateReader(transform: (ReaderPreferences) -> ReaderPreferences) {
        var current: ReaderPreferences? = null
        context.dataStore.edit { p ->
            current = ReaderPreferences(
                p[AUTO_THEME] ?: true,
                runCatching { ReaderTheme.valueOf(p[THEME] ?: "SEPIA") }.getOrDefault(ReaderTheme.SEPIA),
                p[FONT] ?: "Literata",
                p[FONT_SIZE] ?: 18,
                p[LINE_HEIGHT] ?: 1.72,
                p[MARGIN] ?: 24,
                runCatching { ReadingMode.valueOf(p[MODE] ?: "PAGED") }.getOrDefault(ReadingMode.PAGED),
                p[LANGUAGE] ?: "ru",
            )
            val next = transform(requireNotNull(current))
            p[AUTO_THEME] = next.followSystemTheme
            p[THEME] = next.theme.name
            p[FONT] = next.fontFamily
            p[FONT_SIZE] = next.fontSize.coerceIn(12, 34)
            p[LINE_HEIGHT] = next.lineHeight.coerceIn(1.1, 2.2)
            p[MARGIN] = next.margin.coerceIn(8, 64)
            p[MODE] = next.readingMode.name
            p[LANGUAGE] = next.language
        }
    }

    suspend fun updateSync(config: SyncPreferences) = context.dataStore.edit { p ->
        p[SYNC_ENABLED] = config.enabled
        p[SYNC_URL] = config.url.trimEnd('/')
        p[SYNC_USER] = config.username
        p[LAST_SYNC] = config.lastSyncAt
    }

    suspend fun markSynced(timestamp: Long) = context.dataStore.edit { it[LAST_SYNC] = timestamp }

    suspend fun updateAnki(config: AnkiPreferences) = context.dataStore.edit { p ->
        p[ANKI_DECK_ID] = config.deckId
        p[ANKI_DECK_NAME] = config.deckName
        p[ANKI_MODEL_ID] = config.modelId
        p[ANKI_MODEL_NAME] = config.modelName
        p[ANKI_FIELD] = config.fieldIndex
        p[ANKI_BACK_FIELD] = config.backFieldIndex
        p[ANKI_BACK_MODE] = config.backMode.name
        p[ANKI_INCLUDE_READING] = config.includeReading
        p[ANKI_INCLUDE_TAGS] = config.includeTags
        p[ANKI_INCLUDE_DICTIONARY] = config.includeDictionaryName
    }

    suspend fun updateAi(config: AiPreferences) = context.dataStore.edit { p ->
        p[AI_ENABLED] = config.enabled
        p[AI_ENDPOINT] = config.endpoint.trim()
        p[AI_MODEL] = config.model.trim()
        p[AI_TARGET_LANGUAGE] = config.targetLanguage.trim().ifEmpty { "Русский" }
        p[AI_PROMPT] = config.prompt.trim().ifEmpty { AiPreferences().prompt }
    }

    suspend fun exportNonSecret(): JSONObject {
        val r = reader.first()
        val s = sync.first()
        val a = anki.first()
        val ai = ai.first()
        return JSONObject()
            .put("reader", JSONObject().put("followSystemTheme", r.followSystemTheme).put("theme", r.theme.name).put("fontFamily", r.fontFamily).put("fontSize", r.fontSize).put("lineHeight", r.lineHeight).put("margin", r.margin).put("readingMode", r.readingMode.name).put("language", r.language))
            .put("sync", JSONObject().put("enabled", s.enabled).put("url", s.url).put("username", s.username))
            .put("anki", JSONObject().put("deckId", a.deckId).put("deckName", a.deckName).put("modelId", a.modelId).put("modelName", a.modelName).put("fieldIndex", a.fieldIndex).put("backFieldIndex", a.backFieldIndex).put("backMode", a.backMode.name).put("includeReading", a.includeReading).put("includeTags", a.includeTags).put("includeDictionaryName", a.includeDictionaryName))
            .put("ai", JSONObject().put("enabled", ai.enabled).put("endpoint", ai.endpoint).put("model", ai.model).put("targetLanguage", ai.targetLanguage).put("prompt", ai.prompt))
    }

    suspend fun importNonSecret(value: JSONObject) {
        value.optJSONObject("reader")?.let { r ->
            updateReader {
                ReaderPreferences(
                    followSystemTheme = r.optBoolean("followSystemTheme", true),
                    theme = runCatching { ReaderTheme.valueOf(r.optString("theme", "SEPIA")) }.getOrDefault(ReaderTheme.SEPIA),
                    fontFamily = r.optString("fontFamily", "Literata"),
                    fontSize = r.optInt("fontSize", 18),
                    lineHeight = r.optDouble("lineHeight", 1.72),
                    margin = r.optInt("margin", 24),
                    readingMode = runCatching { ReadingMode.valueOf(r.optString("readingMode", "PAGED")) }.getOrDefault(ReadingMode.PAGED),
                    language = r.optString("language", "ru"),
                )
            }
        }
        value.optJSONObject("sync")?.let { s ->
            updateSync(SyncPreferences(s.optBoolean("enabled"), s.optString("url"), s.optString("username"), 0))
        }
        value.optJSONObject("anki")?.let { a ->
            updateAnki(AnkiPreferences(
                deckId = a.optLong("deckId", 1), deckName = a.optString("deckName", "Default"),
                modelId = a.optLong("modelId", -1), modelName = a.optString("modelName", "ReadBound Card"),
                fieldIndex = a.optInt("fieldIndex", 0), backFieldIndex = a.optInt("backFieldIndex", 1),
                backMode = runCatching { AnkiBackMode.valueOf(a.optString("backMode", "CONCISE")) }.getOrDefault(AnkiBackMode.CONCISE),
                includeReading = a.optBoolean("includeReading", true), includeTags = a.optBoolean("includeTags", false),
                includeDictionaryName = a.optBoolean("includeDictionaryName", false),
            ))
        }
        value.optJSONObject("ai")?.let { ai ->
            updateAi(AiPreferences(ai.optBoolean("enabled"), ai.optString("endpoint"), ai.optString("model"), ai.optString("targetLanguage", "Русский"), ai.optString("prompt", AiPreferences().prompt)))
        }
    }

    companion object {
        private val AUTO_THEME = booleanPreferencesKey("reader.auto_theme")
        private val THEME = stringPreferencesKey("reader.theme")
        private val FONT = stringPreferencesKey("reader.font")
        private val FONT_SIZE = intPreferencesKey("reader.font_size")
        private val LINE_HEIGHT = doublePreferencesKey("reader.line_height")
        private val MARGIN = intPreferencesKey("reader.margin")
        private val MODE = stringPreferencesKey("reader.mode")
        private val LANGUAGE = stringPreferencesKey("app.language")
        private val SYNC_ENABLED = booleanPreferencesKey("sync.enabled")
        private val SYNC_URL = stringPreferencesKey("sync.url")
        private val SYNC_USER = stringPreferencesKey("sync.user")
        private val LAST_SYNC = longPreferencesKey("sync.last")
        private val ANKI_DECK_ID = longPreferencesKey("anki.deck_id")
        private val ANKI_DECK_NAME = stringPreferencesKey("anki.deck_name")
        private val ANKI_MODEL_ID = longPreferencesKey("anki.model_id")
        private val ANKI_MODEL_NAME = stringPreferencesKey("anki.model_name")
        private val ANKI_FIELD = intPreferencesKey("anki.field")
        private val ANKI_BACK_FIELD = intPreferencesKey("anki.back_field")
        private val ANKI_BACK_MODE = stringPreferencesKey("anki.back_mode")
        private val ANKI_INCLUDE_READING = booleanPreferencesKey("anki.include_reading")
        private val ANKI_INCLUDE_TAGS = booleanPreferencesKey("anki.include_tags")
        private val ANKI_INCLUDE_DICTIONARY = booleanPreferencesKey("anki.include_dictionary")
        private val AI_ENABLED = booleanPreferencesKey("ai.enabled")
        private val AI_ENDPOINT = stringPreferencesKey("ai.endpoint")
        private val AI_MODEL = stringPreferencesKey("ai.model")
        private val AI_TARGET_LANGUAGE = stringPreferencesKey("ai.target_language")
        private val AI_PROMPT = stringPreferencesKey("ai.prompt")
    }
}
