package app.readbound.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import app.readbound.anki.AnkiGateway
import app.readbound.data.PluginDao
import app.readbound.data.PluginEntity
import app.readbound.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume

class PluginManager(
    private val context: Context,
    private val dao: PluginDao,
    settings: SettingsRepository,
) {
    val plugins: Flow<List<PluginEntity>> = dao.observeAll()
    private val anki = AnkiGateway(context, settings)

    fun installBundledPlugins() {
        val root = File(context.filesDir, "plugins/app.readbound.anki").apply { mkdirs() }
        val manifest = JSONObject()
            .put("id", "app.readbound.anki")
            .put("name", "AnkiDroid")
            .put("version", "1.0.0")
            .put("apiVersion", 1)
            .put("entrypoint", "main.js")
            .put("permissions", org.json.JSONArray().put("anki.write"))
            .put("actions", org.json.JSONArray().put(JSONObject().put("id", "send_quote").put("title", "Send to Anki").put("context", "selection")))
            .toString()
        File(root, "manifest.json").writeText(manifest)
        File(root, "main.js").writeText("export async function handleSelection(context) { return { type: 'anki', quote: context.quote }; }")
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            if (dao.get("app.readbound.anki") == null) dao.upsert(PluginEntity("app.readbound.anki", "AnkiDroid", "1.0.0", 1, manifest, root.absolutePath, true, true, System.currentTimeMillis()))
        }
    }

    suspend fun install(uri: Uri): Result<PluginEntity> = runCatching {
        val staging = File(context.cacheDir, "plugin-${System.nanoTime()}").apply { mkdirs() }
        var total = 0L
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val output = File(staging, entry.name).canonicalFile
                    require(output.startsWith(staging.canonicalFile)) { "Unsafe plugin archive path" }
                    if (entry.isDirectory) output.mkdirs() else {
                        output.parentFile?.mkdirs()
                        output.outputStream().use { stream ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count <= 0) break
                                total += count
                                require(total <= 5L * 1024 * 1024) { "Plugin exceeds 5 MB" }
                                stream.write(buffer, 0, count)
                            }
                        }
                    }
                }
            }
        }
        val manifestFile = File(staging, "manifest.json")
        require(manifestFile.isFile) { "manifest.json is missing" }
        val manifestJson = manifestFile.readText()
        val manifest = PluginManifest.parse(manifestJson)
        require(manifest.apiVersion == 1) { "Unsupported plugin API ${manifest.apiVersion}" }
        require(File(staging, manifest.entrypoint).canonicalFile.startsWith(staging.canonicalFile)) { "Unsafe entrypoint" }
        require(File(staging, manifest.entrypoint).isFile) { "Plugin entrypoint is missing" }
        manifest.allowedDomains.forEach { domain -> require(!domain.contains('/') && domain.isNotBlank()) { "Invalid allowed domain" } }
        require(dao.get(manifest.id)?.bundled != true) { "Bundled plugins cannot be replaced" }
        val target = File(context.filesDir, "plugins/${manifest.id}")
        if (target.exists()) target.deleteRecursively()
        staging.copyRecursively(target, overwrite = true)
        staging.deleteRecursively()
        PluginEntity(manifest.id, manifest.name, manifest.version, manifest.apiVersion, manifestJson, target.absolutePath, true, false, System.currentTimeMillis())
            .also { dao.upsert(it) }
    }

    suspend fun inspectPackage(uri: Uri): Result<PluginManifest> = runCatching {
        var manifestText: String? = null
        var expanded = 0L
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.name.startsWith('/') && !entry.name.contains("..")) { "Unsafe plugin archive path" }
                    if (entry.name == "manifest.json") {
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count <= 0) break
                            expanded += count
                            require(expanded <= 64 * 1024) { "Plugin manifest is too large" }
                            output.write(buffer, 0, count)
                        }
                        manifestText = output.toString(Charsets.UTF_8.name())
                        break
                    }
                }
            }
        }
        PluginManifest.parse(requireNotNull(manifestText) { "manifest.json is missing" })
    }

    suspend fun selectionActions(): List<PluginAction> = dao.enabled().flatMap { entity ->
        runCatching { PluginManifest.parse(entity.manifestJson).actions.filter { it.context == "selection" } }.getOrDefault(emptyList())
    }

    suspend fun invoke(action: PluginAction, contextJson: String): Result<String> = runCatching {
        val entity = dao.get(action.pluginId) ?: error("Plugin is not installed")
        val manifest = PluginManifest.parse(entity.manifestJson)
        val code = File(entity.rootPath, manifest.entrypoint).readText()
        val raw = runInService(code, contextJson)
        handleResult(JSONObject(raw), manifest)
    }

    suspend fun sendCardToAnki(front: String, back: String = ""): Result<String> = anki.addCard(front, back)
    suspend fun ankiDecks() = anki.decks()
    suspend fun ankiModels() = anki.models()
    fun ankiAvailable() = anki.isAvailable()
    fun ankiPermissionGranted() = anki.hasPermission()

    private suspend fun runInService(code: String, contextJson: String): String = suspendCancellableCoroutine { continuation ->
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = Messenger(binder)
                val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
                    if (message.what != PluginRuntimeService.MSG_RESULT) return@Handler false
                    runCatching { this@PluginManager.context.applicationContext.unbindService(this) }
                    val error = message.data.getString("error")
                    if (error != null) continuation.resumeWith(Result.failure(IllegalStateException(error)))
                    else continuation.resume(message.data.getString("result") ?: "{}")
                    true
                })
                service.send(Message.obtain(null, PluginRuntimeService.MSG_RUN).apply {
                    data = Bundle().apply { putString("code", code); putString("context", contextJson) }
                    replyTo = reply
                })
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException("Plugin runtime stopped")))
            }
        }
        if (!this.context.bindService(Intent(this.context, PluginRuntimeService::class.java), connection, Context.BIND_AUTO_CREATE)) {
            continuation.resumeWith(Result.failure(IllegalStateException("Cannot start plugin runtime")))
        }
        continuation.invokeOnCancellation { runCatching { this.context.unbindService(connection) } }
    }

    private suspend fun handleResult(result: JSONObject, manifest: PluginManifest): String = when (result.optString("type")) {
        "anki" -> {
            require("anki.write" in manifest.permissions) { "Plugin did not declare Anki write permission" }
            anki.addCard(result.getString("quote"), result.optString("back")).getOrThrow()
        }
        "message" -> result.optString("message", "Done")
        "http" -> executeHttp(result, manifest)
        else -> result.optString("message", "Plugin completed")
    }

    private fun executeHttp(result: JSONObject, manifest: PluginManifest): String {
        require("network" in manifest.permissions) { "Plugin did not declare network permission" }
        val uri = URI(result.getString("url"))
        require(uri.scheme == "https" && uri.host in manifest.allowedDomains) { "Domain is not allowed by the plugin manifest" }
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        connection.requestMethod = result.optString("method", "GET").uppercase()
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        if (result.has("body")) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(result.getString("body").toByteArray()) }
        }
        val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.readText().orEmpty()
        check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}: ${response.take(160)}" }
        return response.take(400)
    }

    fun hash(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
