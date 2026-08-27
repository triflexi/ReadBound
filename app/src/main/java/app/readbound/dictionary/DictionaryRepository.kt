package app.readbound.dictionary

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import app.readbound.data.DictionaryDao
import app.readbound.data.DictionaryEntity
import app.readbound.data.DictionaryEntryEntity
import app.readbound.data.DictionaryLookupRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile

data class DictionaryImportProgress(val dictionaryTitle: String, val filesDone: Int, val filesTotal: Int, val entries: Int)

class DictionaryRepository(
    private val context: Context,
    private val dao: DictionaryDao,
) {
    val dictionaries: Flow<List<DictionaryEntity>> = dao.observeAll()

    suspend fun importDictionary(
        uri: Uri,
        onProgress: (DictionaryImportProgress) -> Unit = {},
    ): DictionaryEntity = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "dictionary-imports").apply { mkdirs() }
        val tempFile = File(tempDir, "${UUID.randomUUID()}.zip")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedInputStream(input).use { source ->
                    BufferedOutputStream(tempFile.outputStream()).use { target ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                            target.write(buffer, 0, read)
                        }
                    }
                }
            } ?: error("Could not open the selected dictionary")

            val sourceHash = digest.digest().joinToString("") { "%02x".format(it) }
            dao.findByHash(sourceHash)?.let { existing ->
                if (existing.entryCount > 0) return@withContext existing
                dao.delete(existing.id)
            }

            ZipFile(tempFile).use { archive ->
                val indexEntry = archive.getEntry("index.json") ?: error("Not a supported dictionary: index.json is missing")
                val index = archive.getInputStream(indexEntry).bufferedReader().use { JSONObject(it.readText()) }
                val format = index.optInt("format", index.optInt("version", 0))
                require(format in 1..3) { "Unsupported dictionary format: $format" }
                val title = index.optString("title").trim().ifEmpty { "Dictionary" }
                val dictionary = DictionaryEntity(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    revision = index.optString("revision"),
                    sourceLanguage = index.optString("sourceLanguage"),
                    targetLanguage = index.optString("targetLanguage"),
                    format = format,
                    entryCount = 0,
                    enabled = false,
                    importedAt = System.currentTimeMillis(),
                    sourceHash = sourceHash,
                )
                dao.upsert(dictionary)

                val banks = archive.entries().asSequence()
                    .filter { !it.isDirectory && (TERM_BANK.matches(it.name) || META_BANK.matches(it.name)) }
                    .sortedWith(compareBy({ bankOrder(it.name) }, { bankNumber(it.name) }))
                    .toList()
                require(banks.isNotEmpty()) { "The archive contains no dictionary entries" }

                var count = 0
                try {
                    banks.forEachIndexed { fileIndex, entry ->
                        archive.getInputStream(entry).buffered().use { input ->
                            parseBank(input, dictionary.id, entry.name.startsWith("term_meta_bank_")) { batch ->
                                dao.insertEntries(batch)
                                count += batch.size
                                onProgress(DictionaryImportProgress(title, fileIndex, banks.size, count))
                            }
                        }
                        onProgress(DictionaryImportProgress(title, fileIndex + 1, banks.size, count))
                    }
                    dao.updateEntryCount(dictionary.id, count)
                    dao.setEnabled(dictionary.id, true)
                    dictionary.copy(entryCount = count, enabled = true)
                } catch (error: Throwable) {
                    dao.delete(dictionary.id)
                    throw error
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    suspend fun lookup(selection: String, limit: Int = 80): List<DictionaryLookupRow> = withContext(Dispatchers.IO) {
        val candidates = lookupCandidates(selection)
        if (candidates.isEmpty()) emptyList() else dao.lookup(candidates, limit).sortedWith(
            compareBy<DictionaryLookupRow> { candidates.indexOf(it.normalizedTerm).let { index -> if (index < 0) Int.MAX_VALUE else index } }
                .thenBy { if (it.kind == "term") 0 else 1 }
                .thenByDescending { it.score },
        )
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) { dao.setEnabled(id, enabled) }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { dao.delete(id) }

    companion object {
        private val TERM_BANK = Regex("term_bank_\\d+\\.json", RegexOption.IGNORE_CASE)
        private val META_BANK = Regex("term_meta_bank_\\d+\\.json", RegexOption.IGNORE_CASE)
        private val WHITESPACE = Regex("\\s+")
        private val EDGE_PUNCTUATION = Regex("^[^\\p{L}\\p{N}'’]+|[^\\p{L}\\p{N}'’]+$")

        fun normalizeTerm(value: String): String = value
            .trim()
            .replace(EDGE_PUNCTUATION, "")
            .replace(WHITESPACE, " ")
            .lowercase(Locale.ROOT)

        fun lookupCandidates(selection: String): List<String> {
            val exact = normalizeTerm(selection)
            if (exact.isEmpty()) return emptyList()
            val result = linkedSetOf(exact)
            if (' ' in exact) exact.split(' ').filter { it.isNotBlank() }.take(12).forEach(result::add)
            return result.toList()
        }

        private fun bankOrder(name: String) = if (name.startsWith("term_bank_", ignoreCase = true)) 0 else 1
        private fun bankNumber(name: String) = name.substringAfterLast('_').substringBefore('.').toIntOrNull() ?: Int.MAX_VALUE

        private suspend fun parseBank(
            input: InputStream,
            dictionaryId: String,
            metadata: Boolean,
            insert: suspend (List<DictionaryEntryEntity>) -> Unit,
        ) {
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.beginArray()
                val batch = ArrayList<DictionaryEntryEntity>(300)
                while (reader.hasNext()) {
                    val parsed = if (metadata) readMetadataEntry(reader, dictionaryId) else readTermEntry(reader, dictionaryId)
                    if (parsed != null) batch += parsed
                    if (batch.size >= 300) {
                        insert(batch.toList())
                        batch.clear()
                    }
                }
                reader.endArray()
                if (batch.isNotEmpty()) insert(batch)
            }
        }

        private fun readTermEntry(reader: JsonReader, dictionaryId: String): DictionaryEntryEntity? {
            reader.beginArray()
            val term = nextString(reader)
            val reading = nextString(reader)
            val definitionTags = nextString(reader)
            val rules = nextString(reader)
            val score = nextInt(reader)
            val definition = readPlainText(reader)
            while (reader.hasNext()) reader.skipValue()
            reader.endArray()
            val normalized = normalizeTerm(term)
            if (normalized.isEmpty() || definition.isBlank()) return null
            return DictionaryEntryEntity(
                dictionaryId = dictionaryId,
                term = term,
                normalizedTerm = normalized,
                reading = reading,
                definition = definition.take(12_000),
                tags = listOf(definitionTags, rules).filter { it.isNotBlank() }.joinToString(" · "),
                kind = "term",
                score = score,
            )
        }

        private fun readMetadataEntry(reader: JsonReader, dictionaryId: String): DictionaryEntryEntity? {
            reader.beginArray()
            val term = nextString(reader)
            val mode = nextString(reader).ifBlank { "meta" }
            val value = readPlainText(reader)
            while (reader.hasNext()) reader.skipValue()
            reader.endArray()
            val normalized = normalizeTerm(term)
            if (normalized.isEmpty() || value.isBlank()) return null
            return DictionaryEntryEntity(
                dictionaryId = dictionaryId,
                term = term,
                normalizedTerm = normalized,
                reading = "",
                definition = value.take(4_000),
                tags = "",
                kind = mode,
                score = 0,
            )
        }

        private fun nextString(reader: JsonReader): String = when (reader.peek()) {
            JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
            JsonToken.NULL -> { reader.nextNull(); "" }
            else -> { reader.skipValue(); "" }
        }

        private fun nextInt(reader: JsonReader): Int = when (reader.peek()) {
            JsonToken.NUMBER, JsonToken.STRING -> reader.nextString().toDoubleOrNull()?.toInt() ?: 0
            else -> { reader.skipValue(); 0 }
        }

        private fun readPlainText(reader: JsonReader, property: String? = null): String {
            val parts = ArrayList<String>()
            collectPlainText(reader, property, parts)
            return parts.asSequence()
                .map { it.replace(WHITESPACE, " ").trim() }
                .filter { it.isNotBlank() }
                .fold(mutableListOf<String>()) { result, value ->
                    if (result.lastOrNull() != value) result += value
                    result
                }
                .joinToString("\n")
        }

        private fun collectPlainText(reader: JsonReader, property: String?, output: MutableList<String>) {
            when (reader.peek()) {
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray()
                    while (reader.hasNext()) collectPlainText(reader, property, output)
                    reader.endArray()
                }
                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val name = reader.nextName()
                        if (name in DECORATIVE_FIELDS) reader.skipValue() else collectPlainText(reader, name, output)
                    }
                    reader.endObject()
                }
                JsonToken.STRING, JsonToken.NUMBER -> {
                    val value = reader.nextString()
                    if (property !in DECORATIVE_FIELDS) output += value
                }
                JsonToken.BOOLEAN -> output += reader.nextBoolean().toString()
                JsonToken.NULL -> reader.nextNull()
                else -> reader.skipValue()
            }
        }

        private val DECORATIVE_FIELDS = setOf("type", "tag", "style", "data", "lang", "href", "path", "width", "height")
    }
}
