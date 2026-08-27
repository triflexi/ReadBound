package app.readbound.anki

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import app.readbound.settings.AnkiPreferences
import app.readbound.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AnkiGateway(private val context: Context, private val settings: SettingsRepository) {
    private val authority = "com.ichi2.anki.flashcards"
    private val authorityUri = Uri.parse("content://$authority")
    private val submissionMutex = Mutex()
    private var lastSubmission: Pair<String, Long>? = null

    fun isAvailable(): Boolean = context.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA) != null

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context, READ_WRITE_PERMISSION) == PackageManager.PERMISSION_GRANTED

    suspend fun addCard(front: String, back: String = ""): Result<String> = withContext(Dispatchers.IO) {
        submissionMutex.withLock { runCatching {
            require(front.isNotBlank()) { "The front side is empty" }
            val submissionKey = "$front\u0000$back"
            val now = System.currentTimeMillis()
            if (lastSubmission?.let { it.first == submissionKey && now - it.second < 3_000 } == true) {
                return@runCatching "Card already sent"
            }
            check(isAvailable()) { "AnkiDroid is not installed or not configured" }
            check(hasPermission()) { "AnkiDroid permission is required" }
            val configured = settings.anki.first()
            val deckId = deckList().keys.firstOrNull { it == configured.deckId } ?: 1L
            val models = modelList()
            val fallbackModelName = "ReadBound Card"
            val modelId = configured.modelId.takeIf { it in models }
                ?: models.entries.firstOrNull { it.value == configured.modelName }?.key
                ?: models.entries.firstOrNull { it.value == fallbackModelName }?.key
                ?: createReaderCardModel(fallbackModelName, deckId)
                ?: error("AnkiDroid did not create the note type")
            val fieldCount = modelFieldCount(modelId).coerceAtLeast(2)
            val fields = buildAnkiFields(front, back, fieldCount, configured.fieldIndex, configured.backFieldIndex)
            val noteUri = context.contentResolver.insert(notesUri, ContentValues().apply {
                put("mid", modelId)
                put("flds", fields.joinToString("\u001f"))
                put("tags", "reader")
            }) ?: error("AnkiDroid rejected the note")
            moveGeneratedCards(noteUri, deckId)
            lastSubmission = submissionKey to System.currentTimeMillis()
            settings.updateAnki(configured.copy(deckId = deckId, modelId = modelId, modelName = models[modelId] ?: fallbackModelName))
            "Sent to ${deckList()[deckId] ?: configured.deckName}"
        } }
    }

    suspend fun decks(): Map<Long, String> = withContext(Dispatchers.IO) { deckList() }
    suspend fun models(): Map<Long, String> = withContext(Dispatchers.IO) { modelList() }

    private fun deckList(): Map<Long, String> {
        val result = linkedMapOf<Long, String>()
        context.contentResolver.query(decksUri, null, null, null, null)?.use { cursor ->
            val id = cursor.getColumnIndex("deck_id")
            val name = cursor.getColumnIndex("deck_name")
            while (cursor.moveToNext()) if (id >= 0 && name >= 0) result[cursor.getLong(id)] = cursor.getString(name)
        }
        if (result.isEmpty()) result[1L] = "Default"
        return result
    }

    private fun modelList(): Map<Long, String> {
        val result = linkedMapOf<Long, String>()
        context.contentResolver.query(modelsUri, null, null, null, null)?.use { cursor ->
            val id = cursor.getColumnIndex("_id")
            val name = cursor.getColumnIndex("name")
            val fieldNames = cursor.getColumnIndex("field_names")
            val cardCount = cursor.getColumnIndex("num_cards")
            while (cursor.moveToNext()) {
                val count = if (fieldNames >= 0) cursor.getString(fieldNames).split('\u001f').size else 0
                val templates = if (cardCount >= 0) cursor.getInt(cardCount) else 0
                if (id >= 0 && name >= 0 && isEligibleSingleCardModel(count, templates)) result[cursor.getLong(id)] = cursor.getString(name)
            }
        }
        return result
    }

    private fun modelFieldCount(modelId: Long): Int {
        val uri = Uri.withAppendedPath(modelsUri, modelId.toString())
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val fieldNames = cursor.getColumnIndex("field_names")
            if (cursor.moveToFirst() && fieldNames >= 0) cursor.getString(fieldNames).split('\u001f').size else 0
        } ?: 0
    }

    private fun createReaderCardModel(name: String, deckId: Long): Long? {
        val uri = context.contentResolver.insert(modelsUri, ContentValues().apply {
            put("name", name)
            put("field_names", "Front\u001fBack")
            put("num_cards", 1)
            put("css", ".card { font-family: sans-serif; font-size: 22px; text-align: left; }")
            put("deck_id", deckId)
            put("sort_field_index", 0)
        }) ?: return null
        val template = Uri.withAppendedPath(Uri.withAppendedPath(uri, "templates"), "0")
        context.contentResolver.update(template, ContentValues().apply {
            put("card_template_name", "ReadBound card")
            put("question_format", "{{Front}}")
            put("answer_format", "{{FrontSide}}<hr id=answer>{{Back}}")
        }, null, null)
        return uri.lastPathSegment?.toLongOrNull()
    }

    private fun moveGeneratedCards(noteUri: Uri, deckId: Long) {
        val cards = Uri.withAppendedPath(noteUri, "cards")
        context.contentResolver.query(cards, null, null, null, null)?.use { cursor ->
            val ordColumn = cursor.getColumnIndex("ord")
            while (cursor.moveToNext()) {
                val ord = if (ordColumn >= 0) cursor.getString(ordColumn) else "0"
                context.contentResolver.update(Uri.withAppendedPath(cards, ord), ContentValues().apply { put("deck_id", deckId) }, null, null)
            }
        }
    }

    private val notesUri get() = Uri.withAppendedPath(authorityUri, "notes")
    private val modelsUri get() = Uri.withAppendedPath(authorityUri, "models")
    private val decksUri get() = Uri.withAppendedPath(authorityUri, "decks")

    companion object {
        const val READ_WRITE_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    }
}
