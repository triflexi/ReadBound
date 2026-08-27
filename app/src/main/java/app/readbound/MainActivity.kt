package app.readbound

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.readbound.ui.ReaderApp
import app.readbound.settings.LocaleController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ReaderApplication
        LocaleController.apply(this, runBlocking { app.container.settings.reader.first().language })
        enableEdgeToEdge()
        handleIncoming(intent)
        setContent { ReaderApp(app.container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let { uri ->
            lifecycleScope.launch {
                (application as ReaderApplication).container.repository.import(uri)
                    .onFailure { Log.w("MainActivity", "Book import failed for $uri", it) }
            }
        }
    }
}
