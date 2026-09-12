package app.readbound

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.readbound.ui.ReaderApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ReaderApplication
        // Seed Compose with the persisted value. DataStore is asynchronous, so using
        // its default here would briefly paint the wrong theme and locale on launch.
        val initialPreferences = runBlocking { app.container.settings.reader.first() }
        enableEdgeToEdge()
        handleIncoming(intent)
        setContent { ReaderApp(app.container, initialPreferences) }
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
