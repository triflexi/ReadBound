package app.readbound.sync

import android.content.Context
import app.readbound.data.AppDatabase
import app.readbound.data.SyncConflictEntity
import app.readbound.data.SyncShadowEntity
import app.readbound.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

class WebDavSyncEngine(
    context: Context,
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
) {
    private val backup = BackupManager(context, database)

    suspend fun setPassword(password: String) = secrets.put("webdav.password", password)

    suspend fun sync(): Result<String> = withContext(Dispatchers.IO) { runCatching {
        val config = settings.sync.first()
        require(config.enabled && config.url.startsWith("https://")) { "Configure an HTTPS WebDAV endpoint first" }
        val password = secrets.get("webdav.password") ?: error("WebDAV password is missing")
        val remote = get(config.url, config.username, password)
        val local = backup.buildJson(includeLocalPaths = false)
        val merged = if (remote.body.isBlank()) local else merge(local, JSONObject(remote.body))
        val put = put(config.url, config.username, password, merged.toString(), remote.etag)
        if (put == 412) {
            val retryRemote = get(config.url, config.username, password)
            val retryMerged = merge(local, JSONObject(retryRemote.body))
            check(put(config.url, config.username, password, retryMerged.toString(), retryRemote.etag) in 200..299) { "WebDAV changed repeatedly during sync" }
            backup.applyJson(retryMerged, preserveLocalFiles = true)
            updateShadows(retryMerged)
        } else {
            check(put in 200..299) { "WebDAV PUT failed with HTTP $put" }
            backup.applyJson(merged, preserveLocalFiles = true)
            updateShadows(merged)
        }
        val now = System.currentTimeMillis()
        settings.markSynced(now)
        "Synced"
    } }

    suspend fun resolveConflict(conflict: SyncConflictEntity, chooseRemote: Boolean) {
        val payload = JSONObject(if (chooseRemote) conflict.remoteJson else conflict.localJson)
        resolveConflict(conflict, payload)
    }

    suspend fun resolveConflict(conflict: SyncConflictEntity, payload: JSONObject) {
        when (conflict.recordType) {
            "annotations" -> backup.applyJson(JSONObject().put("schemaVersion", 1).put("books", JSONArray()).put("annotations", JSONArray().put(payload)), true)
            "bookmarks" -> backup.applyJson(JSONObject().put("schemaVersion", 1).put("books", JSONArray()).put("bookmarks", JSONArray().put(payload)), true)
            "tags" -> backup.applyJson(JSONObject().put("schemaVersion", 1).put("books", JSONArray()).put("tags", JSONArray().put(payload)), true)
        }
        database.syncDao().upsertShadow(SyncShadowEntity(conflict.recordType, conflict.recordId, hash(payload.toString()), payload.toString(), System.currentTimeMillis()))
        database.syncDao().deleteConflict(conflict.id)
    }

    private suspend fun merge(local: JSONObject, remote: JSONObject): JSONObject {
        val output = JSONObject(local.toString())
        listOf("books", "readingStates", "annotations", "bookmarks", "tags", "annotationTags").forEach { type ->
            val localArray = local.optJSONArray(type) ?: JSONArray()
            val remoteArray = remote.optJSONArray(type) ?: JSONArray()
            val idKey = when (type) { "books" -> "sha256"; "readingStates" -> "bookId"; "annotationTags" -> null; else -> "id" }
            if (idKey == null) {
                val seen = linkedSetOf<String>()
                val merged = JSONArray()
                (localArray.objects() + remoteArray.objects()).forEach { value -> if (seen.add(value.toString())) merged.put(value) }
                output.put(type, merged)
            } else {
                val localMap = localArray.objects().associateBy { it.optString(idKey) }
                val remoteMap = remoteArray.objects().associateBy { it.optString(idKey) }
                val merged = JSONArray()
                (localMap.keys + remoteMap.keys).forEach { id ->
                    val l = localMap[id]
                    val r = remoteMap[id]
                    val chosen = when {
                        l == null -> r
                        r == null -> l
                        type == "readingStates" -> if (r.optLong("sessionEndedAt") > l.optLong("sessionEndedAt")) r else l
                        l.toString() == r.toString() -> l
                        else -> mergeRecord(type, id, l, r)
                    }
                    if (chosen != null) merged.put(chosen)
                }
                output.put(type, merged)
            }
        }
        output.put("generatedAt", System.currentTimeMillis())
        return output
    }

    private suspend fun mergeRecord(type: String, id: String, local: JSONObject, remote: JSONObject): JSONObject {
        val localHash = hash(local.toString())
        val remoteHash = hash(remote.toString())
        val shadow = database.syncDao().shadow(type, id)
        val localChanged = shadow == null || shadow.payloadHash != localHash
        val remoteChanged = shadow == null || shadow.payloadHash != remoteHash
        if (shadow != null && type in setOf("annotations", "bookmarks", "tags") && localChanged && remoteChanged) {
            database.syncDao().upsertConflict(SyncConflictEntity(UUID.randomUUID().toString(), type, id, local.toString(), remote.toString(), System.currentTimeMillis()))
            return local
        }
        val chosen = if (remote.optLong("updatedAt") > local.optLong("updatedAt")) remote else local
        database.syncDao().upsertShadow(SyncShadowEntity(type, id, hash(chosen.toString()), chosen.toString(), System.currentTimeMillis()))
        return chosen
    }

    private suspend fun updateShadows(root: JSONObject) {
        listOf("books", "readingStates", "annotations", "bookmarks", "tags").forEach { type ->
            val idKey = when (type) { "books" -> "sha256"; "readingStates" -> "bookId"; else -> "id" }
            root.optJSONArray(type)?.objects()?.forEach { value ->
                val id = value.optString(idKey)
                if (id.isNotBlank()) database.syncDao().upsertShadow(SyncShadowEntity(type, id, hash(value.toString()), value.toString(), System.currentTimeMillis()))
            }
        }
    }

    private data class Remote(val body: String, val etag: String?)

    private fun get(base: String, user: String, password: String): Remote {
        val connection = connection(base, user, password, "GET")
        return when (connection.responseCode) {
            404 -> Remote("", null)
            in 200..299 -> Remote(connection.inputStream.bufferedReader().readText(), connection.getHeaderField("ETag"))
            else -> error("WebDAV GET failed with HTTP ${connection.responseCode}")
        }
    }

    private fun put(base: String, user: String, password: String, body: String, etag: String?): Int {
        val connection = connection(base, user, password, "PUT")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        if (etag != null) connection.setRequestProperty("If-Match", etag) else connection.setRequestProperty("If-None-Match", "*")
        connection.outputStream.use { it.write(body.toByteArray()) }
        return connection.responseCode
    }

    private fun connection(base: String, user: String, password: String, method: String): HttpURLConnection {
        val endpoint = if (base.endsWith(".json")) base else "${base.trimEnd('/')}/reader-sync-v1.json"
        return (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("Authorization", "Basic ${Base64.getEncoder().encodeToString("$user:$password".toByteArray())}")
        }
    }

    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
