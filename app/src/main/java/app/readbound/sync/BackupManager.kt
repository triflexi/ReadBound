package app.readbound.sync

import android.content.Context
import android.net.Uri
import app.readbound.data.AnnotationEntity
import app.readbound.data.AppDatabase
import app.readbound.data.BookEntity
import app.readbound.data.BookmarkEntity
import app.readbound.data.ReadingStateEntity
import app.readbound.data.TagEntity
import app.readbound.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(private val context: Context, private val database: AppDatabase, private val settings: SettingsRepository? = null) {
    suspend fun export(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        val json = buildJson(includeLocalPaths = false).toString(2)
        context.contentResolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output)
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(JSONObject().put("schemaVersion", 1).put("createdAt", System.currentTimeMillis()).toString().toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(json.toByteArray())
                zip.closeEntry()
            }
        }
    } }

    suspend fun restore(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        var data: String? = null
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "data.json") {
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = zip.read(buffer)
                            if (count <= 0) break
                            total += count
                            require(total <= 20L * 1024 * 1024) { "Backup metadata exceeds 20 MB" }
                            output.write(buffer, 0, count)
                        }
                        data = output.toString(Charsets.UTF_8.name())
                    }
                    zip.closeEntry()
                }
            }
        }
        applyJson(JSONObject(requireNotNull(data) { "Backup data is missing" }), preserveLocalFiles = true)
    } }

    suspend fun buildJson(includeLocalPaths: Boolean): JSONObject {
        val books = database.bookDao().all()
        return JSONObject()
            .put("schemaVersion", 1)
            .put("generatedAt", System.currentTimeMillis())
            .put("books", JSONArray().apply { books.forEach { put(it.toJson(includeLocalPaths)) } })
            .put("readingStates", JSONArray().apply { database.readingStateDao().all().forEach { put(it.toJson()) } })
            .put("annotations", JSONArray().apply { database.annotationDao().allIncludingDeleted().forEach { put(it.toJson()) } })
            .put("bookmarks", JSONArray().apply { database.bookmarkDao().allIncludingDeleted().forEach { put(it.toJson()) } })
            .put("tags", JSONArray().apply { database.tagDao().allIncludingDeleted().forEach { put(it.toJson()) } })
            .put("annotationTags", JSONArray().apply { database.tagDao().allLinks().forEach { put(JSONObject().put("annotationId", it.annotationId).put("tagId", it.tagId)) } })
            .put("plugins", JSONArray().apply { database.pluginDao().all().forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("version", it.version).put("enabled", it.enabled)) } })
            .also { root -> settings?.let { root.put("settings", it.exportNonSecret()) } }
    }

    suspend fun applyJson(root: JSONObject, preserveLocalFiles: Boolean) {
        require(root.optInt("schemaVersion") == 1) { "Unsupported backup version" }
        val localByHash = database.bookDao().all().associateBy { it.sha256 }
        val idMap = mutableMapOf<String, String>()
        root.getJSONArray("books").objects().forEach { item ->
            val remote = item.toBook()
            val local = localByHash[remote.sha256]
            val merged = if (local != null && preserveLocalFiles) remote.copy(
                id = local.id,
                originalPath = local.originalPath,
                contentRoot = local.contentRoot,
                readingOrderJson = local.readingOrderJson,
                coverPath = local.coverPath,
            ) else remote
            idMap[remote.id] = merged.id
            database.bookDao().upsert(merged)
        }
        root.optJSONArray("readingStates")?.objects()?.forEach { database.readingStateDao().upsert(it.toReadingState(idMap)) }
        root.optJSONArray("annotations")?.objects()?.forEach { database.annotationDao().upsert(it.toAnnotation(idMap)) }
        root.optJSONArray("bookmarks")?.objects()?.forEach { database.bookmarkDao().upsert(it.toBookmark(idMap)) }
        root.optJSONArray("tags")?.objects()?.forEach { database.tagDao().upsert(it.toTag()) }
        root.optJSONArray("annotationTags")?.objects()?.forEach { database.tagDao().link(app.readbound.data.AnnotationTagEntity(it.getString("annotationId"), it.getString("tagId"))) }
        root.optJSONArray("plugins")?.objects()?.forEach { plugin ->
            database.pluginDao().get(plugin.getString("id"))?.let { database.pluginDao().setEnabled(it.id, plugin.optBoolean("enabled", true)) }
        }
        root.optJSONObject("settings")?.let { settings?.importNonSecret(it) }
    }
}

internal fun BookEntity.toJson(includePaths: Boolean) = JSONObject()
    .put("id", id).put("sha256", sha256).put("title", title).put("author", author).put("format", format)
    .put("originalPath", if (includePaths) originalPath else "").put("contentRoot", if (includePaths) contentRoot else "")
    .put("readingOrderJson", if (includePaths) readingOrderJson else "[]").put("coverPath", if (includePaths) coverPath else JSONObject.NULL)
    .put("converterVersion", converterVersion).put("addedAt", addedAt).put("lastOpenedAt", lastOpenedAt ?: JSONObject.NULL)

internal fun ReadingStateEntity.toJson() = JSONObject().put("bookId", bookId).put("locatorJson", locatorJson).put("progression", progression)
    .put("chapterIndex", chapterIndex).put("chapterTitle", chapterTitle).put("position", position).put("totalPositions", totalPositions)
    .put("updatedAt", updatedAt).put("sessionEndedAt", sessionEndedAt)

internal fun AnnotationEntity.toJson() = JSONObject().put("id", id).put("bookId", bookId).put("kind", kind).put("chapterIndex", chapterIndex)
    .put("locatorJson", locatorJson).put("quote", quote).put("contextBefore", contextBefore).put("contextAfter", contextAfter)
    .put("noteText", noteText).put("color", color).put("createdAt", createdAt).put("updatedAt", updatedAt).put("deletedAt", deletedAt ?: JSONObject.NULL)

internal fun BookmarkEntity.toJson() = JSONObject().put("id", id).put("bookId", bookId).put("chapterIndex", chapterIndex).put("locatorJson", locatorJson)
    .put("title", title).put("createdAt", createdAt).put("updatedAt", updatedAt).put("deletedAt", deletedAt ?: JSONObject.NULL)

internal fun TagEntity.toJson() = JSONObject().put("id", id).put("name", name).put("createdAt", createdAt).put("updatedAt", updatedAt).put("deletedAt", deletedAt ?: JSONObject.NULL)

private fun JSONObject.toBook() = BookEntity(getString("id"), getString("sha256"), getString("title"), getString("author"), getString("format"),
    optString("originalPath"), optString("contentRoot"), optString("readingOrderJson", "[]"), optString("coverPath").takeIf(String::isNotBlank),
    optInt("converterVersion", 1), getLong("addedAt"), optLongOrNull("lastOpenedAt"))
private fun JSONObject.toReadingState(ids: Map<String, String>) = ReadingStateEntity(ids[getString("bookId")] ?: getString("bookId"), getString("locatorJson"), getDouble("progression"), getInt("chapterIndex"), optString("chapterTitle"), getInt("position"), getInt("totalPositions"), getLong("updatedAt"), getLong("sessionEndedAt"))
private fun JSONObject.toAnnotation(ids: Map<String, String>) = AnnotationEntity(getString("id"), ids[getString("bookId")] ?: getString("bookId"), getString("kind"), getInt("chapterIndex"), getString("locatorJson"), getString("quote"), optString("contextBefore"), optString("contextAfter"), optString("noteText"), optString("color", "yellow"), getLong("createdAt"), getLong("updatedAt"), optLongOrNull("deletedAt"))
private fun JSONObject.toBookmark(ids: Map<String, String>) = BookmarkEntity(getString("id"), ids[getString("bookId")] ?: getString("bookId"), getInt("chapterIndex"), getString("locatorJson"), getString("title"), getLong("createdAt"), getLong("updatedAt"), optLongOrNull("deletedAt"))
private fun JSONObject.toTag() = TagEntity(getString("id"), getString("name"), getLong("createdAt"), getLong("updatedAt"), optLongOrNull("deletedAt"))
internal fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
internal fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
