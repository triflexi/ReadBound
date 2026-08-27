package app.readbound.plugin

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class PluginRuntimeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what != MSG_RUN) return@Handler false
        val code = message.data.getString("code").orEmpty()
        val context = message.data.getString("context").orEmpty()
        val reply = message.replyTo
        scope.launch {
            val result = runCatching { execute(code, context) }
            reply.send(Message.obtain(null, MSG_RESULT).apply {
                data = Bundle().apply {
                    putString("result", result.getOrNull())
                    putString("error", result.exceptionOrNull()?.message)
                }
            })
        }
        true
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private suspend fun execute(code: String, contextJson: String): String = withTimeout(2_500) {
        var result: String? = null
        quickJs {
            function<String, Unit>("readerReturn") { result = it }
            evaluate<Any?>(
                "$code\nconst __readerResult = await handleSelection($contextJson); readerReturn(JSON.stringify(__readerResult));",
                filename = "plugin/main.js",
                asModule = true,
            )
        }
        result ?: error("Plugin returned no result")
    }

    companion object {
        const val MSG_RUN = 1
        const val MSG_RESULT = 2
    }
}
