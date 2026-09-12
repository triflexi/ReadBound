package app.readbound.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.readbound.AppContainer
import app.readbound.settings.ReaderPreferences
import app.readbound.ui.theme.ReaderAppTheme
import java.util.Locale

private sealed interface AppDestination {
    data object Library : AppDestination
    data object Notes : AppDestination
    data object Settings : AppDestination
    data class Reader(val bookId: String) : AppDestination
}

@Composable
fun ReaderApp(
    container: AppContainer,
    initialPreferences: ReaderPreferences = ReaderPreferences(),
) {
    val preferences by container.settings.reader.collectAsStateWithLifecycle(initialValue = initialPreferences)
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

    ReaderLocale(preferences.language) {
        ReaderAppTheme(preferences) {
            GlassBackground {
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
                    AppDestination.Settings -> AppSettingsScreen(
                        container = container,
                        initialReaderPreferences = preferences,
                        onBack = { navigateBack() },
                    )
                    is AppDestination.Reader -> ReaderScreen(
                        container = container,
                        bookId = destination.bookId,
                        initialReaderPreferences = preferences,
                        onBack = { navigateBack() },
                    )
                }
            }
        }
    }
}

/** Applies the in-app locale by recomposition, without flashing or recreating Activity. */
@Composable
internal fun ReaderLocale(language: String, content: @Composable () -> Unit) {
    val appLanguage = if (language == "en") "en" else "ru"
    val baseConfiguration = LocalConfiguration.current
    val localizedConfiguration = remember(baseConfiguration, appLanguage) {
        Configuration(baseConfiguration).apply {
            val locale = Locale.forLanguageTag(appLanguage)
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }
    CompositionLocalProvider(
        LocalReaderLanguage provides appLanguage,
        LocalConfiguration provides localizedConfiguration,
        content = content,
    )
}
