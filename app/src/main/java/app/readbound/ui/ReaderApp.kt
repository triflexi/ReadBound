package app.readbound.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.readbound.AppContainer
import app.readbound.settings.ReaderPreferences
import app.readbound.ui.theme.ReaderAppTheme

private sealed interface AppDestination {
    data object Library : AppDestination
    data object Notes : AppDestination
    data object Settings : AppDestination
    data class Reader(val bookId: String) : AppDestination
}

@Composable
fun ReaderApp(container: AppContainer) {
    val preferences by container.settings.reader.collectAsStateWithLifecycle(initialValue = ReaderPreferences())
    var destinationKey by rememberSaveable { mutableStateOf("library") }
    var readerBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var backStack by rememberSaveable { mutableStateOf(listOf<String>()) }
    val destination = remember(destinationKey, readerBookId) {
        when (destinationKey) {
            "notes" -> AppDestination.Notes
            "settings" -> AppDestination.Settings
            "reader" -> readerBookId?.let(AppDestination::Reader) ?: AppDestination.Library
            else -> AppDestination.Library
        }
    }

    fun navigate(key: String, bookId: String? = null) {
        backStack = backStack + (destinationKey + "|" + readerBookId.orEmpty())
        destinationKey = key
        readerBookId = bookId
    }

    fun navigateBack() {
        val previous = backStack.lastOrNull()
        backStack = backStack.dropLast(1)
        destinationKey = previous?.substringBefore("|") ?: "library"
        readerBookId = previous?.substringAfter("|")?.ifEmpty { null }
    }

    BackHandler(enabled = destinationKey != "library") { navigateBack() }

    ReaderAppTheme(preferences) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (destination) {
                AppDestination.Library -> LibraryScreen(
                    container = container,
                    onOpenBook = { navigate("reader", it) },
                    onNotes = { navigate("notes") },
                    onSettings = { navigate("settings") },
                )
                AppDestination.Notes -> NotesScreen(
                    container = container,
                    onLibrary = { navigateBack() },
                    onOpenBook = { navigate("reader", it) },
                )
                AppDestination.Settings -> AppSettingsScreen(container) { navigateBack() }
                is AppDestination.Reader -> ReaderScreen(container, destination.bookId) { navigateBack() }
            }
        }
    }
}
