package app.readbound.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.readbound.AppContainer
import app.readbound.R
import app.readbound.anki.AnkiGateway
import app.readbound.data.AnnotationEntity
import app.readbound.data.BookEntity
import app.readbound.data.ReadingStateEntity
import app.readbound.data.DictionaryLookupRow
import app.readbound.data.globalProgress
import app.readbound.importer.PublicationManifest
import app.readbound.plugin.PluginAction
import app.readbound.settings.ReaderPreferences
import app.readbound.settings.AnkiPreferences
import app.readbound.settings.AiPreferences
import app.readbound.settings.ReaderTheme
import app.readbound.settings.ReadingMode
import app.readbound.settings.effectiveReaderTheme
import app.readbound.ui.theme.ReaderColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlin.math.roundToInt

private data class TextSelection(
    val quote: String,
    val before: String,
    val after: String,
    val offset: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(container: AppContainer, bookId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val russian = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "ru"
    fun localized(english: String, russianText: String) = if (russian) russianText else english
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val book by produceState<BookEntity?>(null, bookId) { value = container.repository.book(bookId) }
    val preferences by container.settings.reader.collectAsStateWithLifecycle(initialValue = ReaderPreferences())
    val ankiPreferences by container.settings.anki.collectAsStateWithLifecycle(initialValue = AnkiPreferences())
    val aiPreferences by container.settings.ai.collectAsStateWithLifecycle(initialValue = AiPreferences())
    val annotations by container.repository.annotationsForBook(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val bookmarks by container.repository.bookmarksForBook(bookId).collectAsStateWithLifecycle(initialValue = emptyList())

    var chapterIndex by rememberSaveable(bookId) { mutableIntStateOf(-1) }
    var chapterProgress by rememberSaveable(bookId) { mutableDoubleStateOf(0.0) }
    var chapterTextOffset by rememberSaveable(bookId) { mutableIntStateOf(0) }
    var chapterPage by rememberSaveable(bookId) { mutableIntStateOf(1) }
    var chapterPageCount by rememberSaveable(bookId) { mutableIntStateOf(1) }
    var seekRevision by rememberSaveable(bookId) { mutableIntStateOf(0) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var contentsVisible by rememberSaveable { mutableStateOf(false) }
    var contentsTab by rememberSaveable { mutableStateOf("chapters") }
    var selection by remember { mutableStateOf<TextSelection?>(null) }
    var translationSelection by remember { mutableStateOf<TextSelection?>(null) }
    var dictionaryResults by remember { mutableStateOf<List<DictionaryLookupRow>>(emptyList()) }
    var dictionaryLoading by remember { mutableStateOf(false) }
    var dictionaryError by remember { mutableStateOf<String?>(null) }
    var aiTranslation by remember { mutableStateOf<String?>(null) }
    var aiTranslationError by remember { mutableStateOf<String?>(null) }
    var aiTranslationLoading by remember { mutableStateOf(false) }
    var selectionColor by remember { mutableStateOf("yellow") }
    var noteSelection by remember { mutableStateOf<TextSelection?>(null) }
    var noteText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pluginActions by remember { mutableStateOf<List<PluginAction>>(emptyList()) }
    var pendingAnkiCard by remember { mutableStateOf<Pair<String, String>?>(null) }
    var ankiDialogQuote by remember { mutableStateOf<String?>(null) }
    var ankiDialogBack by remember { mutableStateOf("") }
    var ankiSelection by remember { mutableStateOf<TextSelection?>(null) }
    var ankiDictionaryResults by remember { mutableStateOf<List<DictionaryLookupRow>>(emptyList()) }
    var ankiDictionaryLoading by remember { mutableStateOf(false) }
    var ankiDictionaryMessage by remember { mutableStateOf<String?>(null) }
    var ankiAiResult by remember { mutableStateOf<String?>(null) }
    var ankiAiLoading by remember { mutableStateOf(false) }
    var ankiAiError by remember { mutableStateOf<String?>(null) }
    var savedStateLoaded by remember(bookId) { mutableStateOf(false) }
    var savedState by remember(bookId) { mutableStateOf<ReadingStateEntity?>(null) }
    var clearSelectionRevision by remember(bookId) { mutableIntStateOf(0) }

    fun clearActiveSelection() {
        selection = null
        clearSelectionRevision++
    }

    val publication = remember(book?.readingOrderJson) { book?.let { PublicationManifest.fromJson(it.readingOrderJson) } }
    val totalChapters = publication?.chapters?.size ?: 1
    val globalProgress = globalProgress(chapterIndex, chapterProgress, totalChapters)
    val chapter = publication?.chapters?.getOrNull(chapterIndex.coerceAtLeast(0))

    LaunchedEffect(bookId) {
        savedState = container.database.readingStateDao().get(bookId)
        savedStateLoaded = true
    }
    LaunchedEffect(savedStateLoaded, publication) {
        if (savedStateLoaded && chapterIndex < 0 && publication != null) {
            chapterIndex = savedState?.chapterIndex?.coerceIn(0, publication.chapters.lastIndex) ?: 0
            chapterProgress = savedState?.let { parseLocatorProgress(it.locatorJson) } ?: 0.0
            chapterTextOffset = savedState?.let { parseLocatorOffset(it.locatorJson) } ?: 0
        }
    }
    LaunchedEffect(selection) { if (selection != null) pluginActions = container.plugins.selectionActions() }
    LaunchedEffect(translationSelection) {
        val selected = translationSelection ?: return@LaunchedEffect
        dictionaryLoading = true
        dictionaryError = null
        aiTranslation = null
        aiTranslationError = null
        dictionaryResults = runCatching { container.dictionaries.lookup(selected.quote) }
            .onFailure { dictionaryError = it.message ?: localized("Dictionary lookup failed", "Ошибка поиска в словаре") }
            .getOrDefault(emptyList())
        dictionaryLoading = false
    }

    suspend fun persistPosition() {
        val currentBook = book ?: return
        val currentChapter = publication?.chapters?.getOrNull(chapterIndex) ?: return
        val now = System.currentTimeMillis()
        val locator = JSONObject().put("chapter", chapterIndex).put("progress", chapterProgress).put("textOffset", chapterTextOffset).toString()
        container.repository.saveReadingState(
            ReadingStateEntity(
                currentBook.id,
                locator,
                globalProgress,
                chapterIndex,
                currentChapter.title,
                if (preferences.readingMode == ReadingMode.PAGED) chapterPage else (chapterProgress * 100).toInt(),
                if (preferences.readingMode == ReadingMode.PAGED) chapterPageCount else 100,
                now,
                now,
            ),
        )
    }

    LaunchedEffect(chapterIndex, chapterProgress, chapterTextOffset) {
        if (chapterIndex >= 0) {
            delay(650)
            persistPosition()
        }
    }
    val persistLatest by rememberUpdatedState { lifecycleOwner.lifecycleScope.launch { persistPosition() } }
    DisposableEffect(lifecycleOwner, bookId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) persistLatest()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            persistLatest()
        }
    }

    val requestAnkiPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val card = pendingAnkiCard
        pendingAnkiCard = null
        if (granted && card != null) scope.launch { statusMessage = container.plugins.sendCardToAnki(card.first, card.second).getOrElse { it.message ?: "Anki error" } }
        else if (!granted) statusMessage = "AnkiDroid permission was denied"
    }

    fun sendToAnki(front: String, back: String) {
        if (!container.plugins.ankiAvailable()) statusMessage = "Install and configure AnkiDroid first"
        else if (!container.plugins.ankiPermissionGranted()) {
            pendingAnkiCard = front to back
            requestAnkiPermission.launch(AnkiGateway.READ_WRITE_PERMISSION)
        } else scope.launch { statusMessage = container.plugins.sendCardToAnki(front, back).getOrElse { it.message ?: "Anki error" } }
    }

    fun openAnkiDraft(selected: TextSelection, preferredBack: String? = null) {
        ankiDialogQuote = selected.quote
        ankiSelection = selected
        ankiDialogBack = preferredBack.orEmpty()
        ankiDictionaryResults = emptyList()
        ankiDictionaryMessage = null
        ankiDictionaryLoading = true
        ankiAiResult = preferredBack
        ankiAiError = null
        ankiAiLoading = false
        scope.launch {
            runCatching { container.dictionaries.lookup(selected.quote) }
                .onSuccess { results ->
                    if (ankiDialogQuote == selected.quote) {
                        ankiDictionaryResults = results
                        if (results.isEmpty()) ankiDictionaryMessage = localized(
                            "Translation was not found. You can use AI or enter text manually.",
                            "Перевод не найден. Можно использовать ИИ или ввести текст вручную.",
                        )
                    }
                }
                .onFailure {
                    ankiDictionaryMessage = it.message ?: localized(
                        "Could not look up the translation.",
                        "Не удалось найти перевод.",
                    )
                }
            if (ankiDialogQuote == selected.quote) ankiDictionaryLoading = false
        }
    }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val resolvedReaderTheme = effectiveReaderTheme(preferences, systemDark)
    val pageBackground = when (resolvedReaderTheme) {
        ReaderTheme.DARK -> Color(0xFF171611)
        ReaderTheme.LIGHT -> Color(0xFFFFFEFB)
        ReaderTheme.SEPIA -> ReaderColors.ReaderBackground
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(pageBackground)) {
        val readerWidth = if (maxWidth >= 720.dp) 680.dp else maxWidth
        val currentBook = book
        if (currentBook == null || publication == null || chapter == null || chapterIndex < 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(readerString("Opening book…", "Открываем книгу…")) }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                key(chapterIndex) {
                    WebReader(
                        book = currentBook,
                        chapterIndex = chapterIndex,
                        chapterHref = chapter.href,
                        initialProgress = chapterProgress,
                        initialTextOffset = chapterTextOffset,
                        seekRevision = seekRevision,
                        darkTheme = resolvedReaderTheme == ReaderTheme.DARK,
                        preferences = preferences,
                        annotations = annotations.filter { it.chapterIndex == chapterIndex },
                        clearSelectionRevision = clearSelectionRevision,
                        modifier = Modifier.width(readerWidth).fillMaxHeight(),
                        onProgress = { progress, offset, page, pageCount ->
                            chapterProgress = progress
                            chapterTextOffset = offset
                            chapterPage = page
                            chapterPageCount = pageCount
                        },
                        onToggleControls = { controlsVisible = !controlsVisible },
                        onSelection = { selection = it },
                        onNavigate = { delta ->
                            val next = chapterIndex + delta
                            if (next in publication.chapters.indices) {
                                chapterIndex = next
                                chapterProgress = if (delta > 0) 0.0 else 1.0
                                chapterTextOffset = 0
                                chapterPage = 1
                                chapterPageCount = 1
                            }
                        },
                    )
                }
            }

            if (controlsVisible) {
                ReaderTopBar(
                    book = currentBook,
                    bookmarked = bookmarks.any { it.chapterIndex == chapterIndex },
                    onBack = { scope.launch { persistPosition(); onBack() } },
                    onBookmark = {
                        scope.launch {
                            val added = container.repository.toggleBookmark(currentBook.id, chapterIndex, JSONObject().put("chapter", chapterIndex).put("progress", chapterProgress).put("textOffset", chapterTextOffset).toString(), chapter.title)
                            statusMessage = if (added) "Bookmark added" else "Bookmark removed"
                        }
                    },
                )
                ReaderProgressPanel(
                    overallProgress = globalProgress.toFloat(),
                    chapterProgress = chapterProgress.toFloat(),
                    page = chapterPage,
                    pageCount = chapterPageCount,
                    chapterIndex = chapterIndex,
                    chapterCount = totalChapters,
                    chapterTitle = chapter.title,
                    mode = preferences.readingMode,
                    onProgress = { value ->
                        chapterProgress = value.toDouble().coerceIn(0.0, 1.0)
                        chapterTextOffset = 0
                        seekRevision++
                    },
                    onContents = { contentsVisible = true },
                    onSettings = { settingsVisible = true },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        ReaderTransientMessage(
            message = statusMessage,
            onDismiss = { statusMessage = null },
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 20.dp, vertical = 80.dp),
        )
    }

    if (settingsVisible) ReaderSettingsSheet(preferences, container, onDismiss = { settingsVisible = false })
    if (contentsVisible && publication != null) ContentsSheet(
        publication = publication,
        current = chapterIndex,
        tab = contentsTab,
        onTab = { contentsTab = it },
        bookmarks = bookmarks,
        annotations = annotations,
        onLocation = { index, progress, offset ->
            chapterIndex = index
            chapterProgress = progress
            chapterTextOffset = offset
            chapterPage = 1
            chapterPageCount = 1
            seekRevision++
            contentsVisible = false
        },
        onDismiss = { contentsVisible = false },
    )
    selection?.let { selected ->
        SelectionSheet(
            selection = selected,
            color = selectionColor,
            plugins = pluginActions,
            onColor = { selectionColor = it },
            onHighlight = {
                clearActiveSelection()
                scope.launch {
                    runCatching {
                        container.repository.addAnnotation(bookId, chapterIndex, selectionLocator(chapterIndex, chapterProgress, selected), selected.quote, color = selectionColor, before = selected.before, after = selected.after)
                    }.onSuccess {
                        statusMessage = localized("Highlight saved", "Выделение сохранено")
                    }.onFailure {
                        statusMessage = it.message ?: localized("Could not save highlight", "Не удалось сохранить выделение")
                    }
                }
            },
            onNote = { noteText = ""; noteSelection = selected; clearActiveSelection() },
            onAnki = { openAnkiDraft(selected); clearActiveSelection() },
            onTranslate = { translationSelection = selected; clearActiveSelection() },
            onPlugin = { action ->
                clearActiveSelection()
                scope.launch {
                    val payload = JSONObject().put("quote", selected.quote).put("book", JSONObject().put("id", bookId).put("title", book?.title)).toString()
                    statusMessage = container.plugins.invoke(action, payload).getOrElse { it.message ?: "Plugin error" }
                }
            },
            onDismiss = { clearActiveSelection() },
        )
    }
    noteSelection?.let { selected ->
        AlertDialog(
            onDismissRequest = { noteSelection = null },
            title = { Text(readerString("Add note", "Добавить заметку")) },
            text = { Column { Text("“${selected.quote}”", maxLines = 4); Spacer(Modifier.height(12.dp)); TextField(noteText, { noteText = it }, label = { Text(readerString("Personal note", "Личная заметка")) }) } },
            confirmButton = { Button(onClick = { scope.launch { container.repository.addAnnotation(bookId, chapterIndex, selectionLocator(chapterIndex, chapterProgress, selected), selected.quote, noteText, selectionColor, selected.before, selected.after); noteSelection = null; statusMessage = localized("Note saved", "Заметка сохранена") } }) { Text(readerString("Save", "Сохранить")) } },
            dismissButton = { TextButton(onClick = { noteSelection = null }) { Text(readerString("Cancel", "Отмена")) } },
        )
    }
    translationSelection?.let { selected ->
        TranslationSheet(
            selection = selected,
            results = dictionaryResults,
            loading = dictionaryLoading,
            error = dictionaryError,
            aiResult = aiTranslation,
            aiLoading = aiTranslationLoading,
            aiError = aiTranslationError,
            onAiTranslate = {
                if (!aiTranslationLoading) scope.launch {
                    aiTranslationLoading = true
                    aiTranslationError = null
                    runCatching { container.aiTranslation.translate(selected.quote, selected.before, selected.after) }
                        .onSuccess { aiTranslation = it }
                        .onFailure { aiTranslationError = it.message ?: localized("AI translation failed", "Ошибка перевода ИИ") }
                    aiTranslationLoading = false
                }
            },
            onAnki = { back -> translationSelection = null; openAnkiDraft(selected, back) },
            onDismiss = { translationSelection = null },
        )
    }
    ankiDialogQuote?.let { quote ->
        AnkiExportDialog(
            initialFront = quote,
            initialBack = ankiDialogBack,
            dictionaryResults = ankiDictionaryResults,
            dictionaryLoading = ankiDictionaryLoading,
            dictionaryMessage = ankiDictionaryMessage,
            preferences = ankiPreferences,
            aiEnabled = aiPreferences.enabled,
            aiResult = ankiAiResult,
            aiLoading = ankiAiLoading,
            aiError = ankiAiError,
            onPreferencesChange = { next -> scope.launch { container.settings.updateAnki(next) } },
            onAiTranslate = {
                val selected = ankiSelection
                if (selected != null && !ankiAiLoading) scope.launch {
                    ankiAiLoading = true
                    ankiAiError = null
                    runCatching { container.aiTranslation.translate(selected.quote, selected.before, selected.after) }
                        .onSuccess { ankiAiResult = it }
                        .onFailure { ankiAiError = it.message ?: localized("AI translation failed", "Ошибка перевода ИИ") }
                    ankiAiLoading = false
                }
            },
            onDismiss = { ankiDialogQuote = null; ankiDictionaryLoading = false; ankiAiLoading = false },
            onSend = { front, back -> ankiDialogQuote = null; ankiDictionaryLoading = false; ankiAiLoading = false; sendToAnki(front, back) },
        )
    }
}

@Composable
private fun ReaderTopBar(
    book: BookEntity,
    bookmarked: Boolean,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp).height(64.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ReaderControlIcon(R.raw.icon_back, "Back", onBack)
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(book.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(book.author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            ReaderControlIcon(R.raw.icon_bookmark, "Bookmark", onBookmark, if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReaderControlIcon(resource: Int, description: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(Modifier.size(43.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        ReaderIcon(resource, description, Modifier.size(24.dp), ColorFilter.tint(tint))
    }
}

@Composable
private fun ReaderProgressPanel(
    overallProgress: Float,
    chapterProgress: Float,
    page: Int,
    pageCount: Int,
    chapterIndex: Int,
    chapterCount: Int,
    chapterTitle: String,
    mode: ReadingMode,
    onProgress: (Float) -> Unit,
    onContents: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safePageCount = pageCount.coerceAtLeast(1)
    val reportedProgress = if (mode == ReadingMode.PAGED && safePageCount > 1) {
        (page - 1).toFloat() / (safePageCount - 1)
    } else chapterProgress
    var sliderProgress by remember { mutableFloatStateOf(reportedProgress) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(reportedProgress, dragging) { if (!dragging) sliderProgress = reportedProgress }
    Surface(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(chapterTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    Text(
                        readerString("Chapter ${chapterIndex + 1} of $chapterCount", "Глава ${chapterIndex + 1} из $chapterCount"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${(overallProgress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = sliderProgress,
                onValueChange = {
                    dragging = true
                    sliderProgress = if (mode == ReadingMode.PAGED && safePageCount > 1) {
                        val pageIndex = (it * (safePageCount - 1)).roundToInt().coerceIn(0, safePageCount - 1)
                        pageIndex.toFloat() / (safePageCount - 1)
                    } else it
                },
                onValueChangeFinished = { dragging = false; onProgress(sliderProgress) },
            )
            Text(
                if (mode == ReadingMode.PAGED) {
                    val displayedPage = if (dragging) (sliderProgress * (safePageCount - 1)).roundToInt() + 1 else page.coerceIn(1, safePageCount)
                    readerString("Page $displayedPage of $safePageCount", "Страница $displayedPage из $safePageCount")
                } else {
                    readerString("${(sliderProgress * 100).toInt()}% of this chapter", "${(sliderProgress * 100).toInt()}% текущей главы")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReaderPanelAction(R.raw.icon_contents, readerString("Contents", "Содержание"), Modifier.weight(1f), onContents)
                Surface(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp), onClick = onSettings) {
                    Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) { Text(readerString("Aa   Settings", "Aa   Настройки"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
    }
}

@Composable
private fun ReaderPanelAction(icon: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp), onClick = onClick) {
        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            ReaderIcon(icon, null, Modifier.size(24.dp), ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)); Spacer(Modifier.width(8.dp)); Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun WebReader(
    book: BookEntity,
    chapterIndex: Int,
    chapterHref: String,
    initialProgress: Double,
    initialTextOffset: Int,
    seekRevision: Int,
    darkTheme: Boolean,
    preferences: ReaderPreferences,
    annotations: List<AnnotationEntity>,
    clearSelectionRevision: Int,
    modifier: Modifier,
    onProgress: (Double, Int, Int, Int) -> Unit,
    onToggleControls: () -> Unit,
    onSelection: (TextSelection) -> Unit,
    onNavigate: (Int) -> Unit,
) {
    val chapterFile = remember(book.contentRoot, chapterHref) { File(book.contentRoot, chapterHref) }
    val restoreProgress = remember(book.id, chapterIndex, chapterHref) { initialProgress }
    val restoreTextOffset = remember(book.id, chapterIndex, chapterHref) { initialTextOffset }
    val bridge = remember { ReaderJsBridge(onProgress, onToggleControls, onSelection, onNavigate) }
    bridge.callbacks = ReaderJsCallbacks(onProgress, onToggleControls, onSelection, onNavigate)
    val styleScript = remember(preferences, darkTheme, restoreProgress, restoreTextOffset, annotations) { readerScript(preferences, darkTheme, restoreProgress, restoreTextOffset, annotations) }
    bridge.pageScript = styleScript
    val renderState = remember { ReaderWebRenderState() }
    var appliedSeekRevision by remember { mutableIntStateOf(seekRevision) }
    var appliedClearSelectionRevision by remember { mutableIntStateOf(clearSelectionRevision) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                addJavascriptInterface(bridge, "ReaderHost")
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = request?.url?.scheme !in setOf("file", "about")
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val scheme = request?.url?.scheme
                        return if (scheme == "http" || scheme == "https") WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) else null
                    }
                    override fun onPageFinished(view: WebView, url: String?) {
                        val script = bridge.pageScript
                        view.evaluateJavascript(script, null)
                        renderState.appliedScript = script
                    }
                }
                loadUrl(Uri.fromFile(chapterFile).toString())
            }
        },
        update = { view ->
            if (view.url != Uri.fromFile(chapterFile).toString()) {
                renderState.appliedScript = null
                view.loadUrl(Uri.fromFile(chapterFile).toString())
            }
            else {
                if (renderState.appliedScript != styleScript) {
                    view.evaluateJavascript(styleScript, null)
                    renderState.appliedScript = styleScript
                }
                if (appliedClearSelectionRevision != clearSelectionRevision) {
                    appliedClearSelectionRevision = clearSelectionRevision
                    view.evaluateJavascript("window.readerClearSelection && window.readerClearSelection();", null)
                }
                if (appliedSeekRevision != seekRevision) {
                    appliedSeekRevision = seekRevision
                    view.evaluateJavascript(
                        "window.readerGoOffset && (window.readerGoOffset(${initialTextOffset.coerceAtLeast(0)}) || window.readerGoProgress(${initialProgress.coerceIn(0.0, 1.0)}));",
                        null,
                    )
                }
            }
        },
        onRelease = { it.removeJavascriptInterface("ReaderHost"); it.destroy() },
    )
}

private class ReaderWebRenderState(var appliedScript: String? = null)

private data class ReaderJsCallbacks(
    val progress: (Double, Int, Int, Int) -> Unit,
    val toggle: () -> Unit,
    val selection: (TextSelection) -> Unit,
    val navigate: (Int) -> Unit,
)

private class ReaderJsBridge(
    progress: (Double, Int, Int, Int) -> Unit,
    toggle: () -> Unit,
    selection: (TextSelection) -> Unit,
    navigate: (Int) -> Unit,
) {
    var callbacks = ReaderJsCallbacks(progress, toggle, selection, navigate)
    var pageScript: String = ""
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    @JavascriptInterface fun progress(value: Double, textOffset: Int, page: Int, pageCount: Int) = main.post {
        callbacks.progress(
            value.coerceIn(0.0, 1.0),
            textOffset.coerceAtLeast(0),
            page.coerceAtLeast(1),
            pageCount.coerceAtLeast(1),
        )
    }
    @JavascriptInterface fun toggleControls() = main.post { callbacks.toggle() }
    @JavascriptInterface fun navigate(delta: Int) = main.post { callbacks.navigate(delta.coerceIn(-1, 1)) }
    @JavascriptInterface fun selection(json: String) = main.post {
        runCatching {
            val value = JSONObject(json)
            callbacks.selection(TextSelection(value.getString("quote").trim(), value.optString("before"), value.optString("after"), value.optInt("offset")))
        }
    }
}

private fun readerScript(preferences: ReaderPreferences, darkTheme: Boolean, initialProgress: Double, initialTextOffset: Int, annotations: List<AnnotationEntity>): String {
    val background = when {
        darkTheme -> "#171611"
        preferences.followSystemTheme || preferences.theme == ReaderTheme.LIGHT -> "#fffefb"
        else -> "#fbf6ea"
    }
    val text = if (darkTheme) "#eae4da" else "#342f29"
    val font = if (preferences.fontFamily == "Sans") "sans-serif" else "Literata, Georgia, serif"
    val paged = preferences.readingMode == ReadingMode.PAGED
    val highlights = JSONArray().apply {
        annotations.forEach {
            put(
                JSONObject()
                    .put("q", it.quote)
                    .put("c", highlightColor(it.color))
                    .put("n", it.color.takeIf { color -> color in setOf("yellow", "green", "blue", "coral") } ?: "yellow")
                    .put("o", parseLocatorOffset(it.locatorJson))
                    .put("b", it.contextBefore)
                    .put("a", it.contextAfter),
            )
        }
    }.toString()
    return """
        (function() {
          const resumeOffset=window.__readerInitialized&&window.readerAnchorOffset?window.readerAnchorOffset():${initialTextOffset.coerceAtLeast(0)};
          let viewportMeta = document.querySelector('meta[name="viewport"]');
          if (!viewportMeta) { viewportMeta = document.createElement('meta'); viewportMeta.name='viewport'; document.head.appendChild(viewportMeta); }
          viewportMeta.content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
          let content = document.getElementById('reader-content');
          if (!content) {
            content = document.createElement('main');
            content.id='reader-content';
            while(document.body.firstChild)content.appendChild(document.body.firstChild);
            document.body.appendChild(content);
          }
          let style = document.getElementById('reader-host-style');
          if (!style) { style = document.createElement('style'); style.id='reader-host-style'; document.head.appendChild(style); }
          style.textContent = `
            :root { color-scheme: ${if (darkTheme) "dark" else "light"}; }
            @font-face { font-family: Literata; src: url('file:///android_res/font/literata.ttf'); }
            @font-face { font-family: Manrope; src: url('file:///android_res/font/manrope.ttf'); }
            html, body { box-sizing:border-box !important; width:100% !important; height:100% !important; background:$background !important; color:$text !important; margin:0 !important; padding:0 !important; }
            html { ${if (paged) "overflow:hidden !important;" else "overflow-x:hidden !important; overflow-y:auto !important;"} }
            body { display:block !important; min-width:0 !important; font-family:$font !important; font-size:${preferences.fontSize}px !important; line-height:${preferences.lineHeight} !important; ${if (paged) "overflow:hidden !important;" else "height:auto !important; min-height:100% !important; overflow:visible !important;"} }
            #reader-content { box-sizing:border-box !important; display:block !important; position:relative !important; width:100vw !important; padding:${preferences.margin}px !important; font-family:$font !important; font-size:${preferences.fontSize}px !important; line-height:${preferences.lineHeight} !important; ${if (paged) "height:100vh !important; column-fill:auto !important; column-width:calc(100vw - ${preferences.margin * 2}px) !important; column-gap:${preferences.margin * 2}px !important; overflow:visible !important;" else "min-height:100vh !important; transform:none !important;"} }
            #reader-content * { font-family:inherit !important; }
            #reader-content p,#reader-content li,#reader-content blockquote { font-size:inherit !important; line-height:inherit !important; }
            p,li,blockquote,div { white-space:normal !important; overflow-wrap:break-word !important; word-break:normal !important; }
            pre { white-space:pre-wrap !important; overflow-wrap:anywhere !important; }
            h1,h2,h3 { line-height:1.3 !important; }
            img,svg { max-width:100% !important; height:auto !important; }
            a { color:#5b55d8 !important; }
            mark.reader-highlight { border-radius:4px; padding:1px 0; color:inherit; }
            ::highlight(reader-yellow) { background:#ffe58f; color:#342f29; }
            ::highlight(reader-green) { background:#d4f1e1; color:#342f29; }
            ::highlight(reader-blue) { background:#dceaff; color:#342f29; }
            ::highlight(reader-coral) { background:#ffd7cf; color:#342f29; }
          `;
          const paged=${paged};
          window.__readerPaged=paged;
          const viewport=()=>Math.max(1,window.innerWidth||document.documentElement.clientWidth);
          const syncViewportSize=()=>{
            if(paged){
              const height=Math.max(1,window.innerHeight)+'px';
              const width=viewport()+'px';
              document.documentElement.style.setProperty('height',height,'important');
              document.body.style.setProperty('height',height,'important');
              content.style.setProperty('height',height,'important');
              content.style.setProperty('width',width,'important');
            }else{
              document.documentElement.style.removeProperty('height');
              document.body.style.removeProperty('height');
              content.style.removeProperty('height');
              content.style.removeProperty('width');
            }
          };
          syncViewportSize();
          const pageCount=()=>paged?Math.max(1,Math.ceil((content.scrollWidth-1)/viewport())):1;
          const pageIndex=()=>paged?Math.max(0,Math.min(pageCount()-1,window.__readerPageIndex||0)):0;
          const metric=()=>paged?Math.max(1,(pageCount()-1)*viewport()):Math.max(1,document.documentElement.scrollHeight-window.innerHeight);
          const current=()=>paged?pageIndex()*viewport():window.scrollY;
          const goPage=(requested,animate)=>{
            const previous=pageIndex();
            const index=Math.max(0,Math.min(pageCount()-1,Math.round(requested)));
            window.__readerPageIndex=index;
            content.style.transition=animate?'transform 180ms ease-out':'none';
            content.style.transform='translate3d('+(-index*viewport())+'px,0,0)';
            setTimeout(()=>{content.style.transition='none';report();},animate?200:20);
            return index!==previous;
          };
          if(!paged){window.__readerPageIndex=0;content.style.transition='none';content.style.transform='none';}
          const anchorOffset=()=>{
            const x=${preferences.margin.coerceAtLeast(4)}, y=Math.min(window.innerHeight-2,${preferences.margin.coerceAtLeast(4)}+8);
            const range=document.caretRangeFromPoint?document.caretRangeFromPoint(x,y):null;
            if(!range)return 0;
            const prefix=document.createRange();prefix.selectNodeContents(content);prefix.setEnd(range.startContainer,range.startOffset);
            return prefix.toString().length;
          };
          window.readerAnchorOffset=anchorOffset;
          const report=()=>{
            if(window.__readerRestoring)return;
            const pages=pageCount(), index=pageIndex();
            const progress=paged?(pages<=1?0:index/(pages-1)):Math.max(0,Math.min(1,current()/metric()));
            ReaderHost.progress(progress,anchorOffset(),index+1,pages);
          };
          window.onscroll=paged?null:report;
          window.readerGoProgress=(p)=>{
            const progress=Math.max(0,Math.min(1,p));
            if(paged)goPage(progress*(pageCount()-1),false);
            else window.scrollTo({top:metric()*progress,left:0,behavior:'auto'});
            setTimeout(report,40);
          };
          window.readerGoOffset=(target)=>{
            if(target<=0)return false;
            const walker=document.createTreeWalker(content,NodeFilter.SHOW_TEXT);let consumed=0,node;
            while(node=walker.nextNode()){
              const next=consumed+node.nodeValue.length;
              if(target<=next){
                const range=document.createRange();range.setStart(node,Math.max(0,target-consumed));range.collapse(true);const rect=range.getBoundingClientRect();
                if(paged){const targetPage=Math.max(0,Math.round((pageIndex()*viewport()+rect.left-${preferences.margin})/viewport()));goPage(targetPage,false);}
                else window.scrollTo({top:window.scrollY+rect.top-${preferences.margin},left:0,behavior:'auto'});
                setTimeout(report,40);return true;
              }
              consumed=next;
            }
            return false;
          };
          window.readerTurnPage=(direction)=>{
            if(paged){if(!goPage(pageIndex()+direction,true))ReaderHost.navigate(direction);}
            else {const before=current();window.scrollBy({top:direction*window.innerHeight*.82,left:0,behavior:'smooth'});setTimeout(()=>{if(Math.abs(current()-before)<3)ReaderHost.navigate(direction);else report();},320);}
          };
          window.readerClearSelection=()=>{
            const activeSelection=window.getSelection();
            if(activeSelection)activeSelection.removeAllRanges();
          };
          const firstRun=!window.__readerInitialized;
          if (firstRun) document.addEventListener('click',(e)=>{
            if (window.getSelection().toString().trim()) return;
            const x=e.clientX/window.innerWidth;
            if(x>.34&&x<.66){ReaderHost.toggleControls();return;}
            const direction=x<.34?-1:1;
            window.readerTurnPage(direction);
          });
          if (firstRun) document.addEventListener('selectionchange',()=>{
            clearTimeout(window.__readerSelectionTimer);
            window.__readerSelectionTimer=setTimeout(()=>{
              const s=window.getSelection();
              if(!s||!s.rangeCount)return;
              const selectedRange=s.getRangeAt(0);
              if(!content.contains(selectedRange.startContainer)||!content.contains(selectedRange.endContainer))return;
              const raw=s.toString(); const q=raw.trim(); if(!q)return;
              const leading=raw.length-raw.trimStart().length;
              const prefix=document.createRange();prefix.selectNodeContents(content);prefix.setEnd(selectedRange.startContainer,selectedRange.startOffset);
              const offset=prefix.toString().length+leading;
              const all=content.textContent||'';
              ReaderHost.selection(JSON.stringify({quote:q,before:all.slice(Math.max(0,offset-80),offset),after:all.slice(offset+q.length,offset+q.length+80),offset}));
            },350);
          });
          if (firstRun) window.addEventListener('resize',()=>{clearTimeout(window.__readerResizeTimer);window.__readerResizeTimer=setTimeout(()=>{syncViewportSize();if(paged)goPage(pageIndex(),false);else report();},100);});
          document.querySelectorAll('mark.reader-highlight').forEach(mark=>mark.replaceWith(...mark.childNodes));
          content.normalize();
          const highlights=$highlights;
          const highlightWalker=document.createTreeWalker(content,NodeFilter.SHOW_TEXT);
          const textNodes=[]; let textNode, textOffset=0;
          while(textNode=highlightWalker.nextNode()){
            const length=textNode.nodeValue.length;
            textNodes.push({node:textNode,start:textOffset,end:textOffset+length});
            textOffset+=length;
          }
          const fullText=content.textContent||'';
          const suffixScore=(text,expected)=>{let score=0,i=text.length-1,j=expected.length-1;while(i>=0&&j>=0&&text[i]===expected[j]&&score<80){score++;i--;j--;}return score;};
          const prefixScore=(text,expected)=>{let score=0;while(score<text.length&&score<expected.length&&score<80&&text[score]===expected[score])score++;return score;};
          const locateHighlight=(h)=>{
            const quote=String(h.q||''); if(!quote)return -1;
            const saved=Number(h.o);
            if(Number.isFinite(saved)&&saved>=0&&fullText.slice(saved,saved+quote.length)===quote)return saved;
            let best=-1,bestScore=-Infinity,at=fullText.indexOf(quote);
            while(at>=0){
              const before=fullText.slice(Math.max(0,at-80),at),after=fullText.slice(at+quote.length,at+quote.length+80);
              const context=suffixScore(before,String(h.b||''))+prefixScore(after,String(h.a||''));
              const proximity=Number.isFinite(saved)?Math.max(0,40-Math.abs(at-saved)/20):0;
              const score=context*10+proximity;
              if(score>bestScore){bestScore=score;best=at;}
              at=fullText.indexOf(quote,at+Math.max(1,quote.length));
            }
            return best;
          };
          const makeRange=(start,length)=>{
            const end=start+length;
            const first=textNodes.find(item=>start>=item.start&&start<item.end);
            const last=textNodes.find(item=>end>item.start&&end<=item.end);
            if(!first||!last)return null;
            const range=document.createRange();
            range.setStart(first.node,start-first.start);range.setEnd(last.node,end-last.start);
            return range;
          };
          const located=highlights.map(h=>({h,start:locateHighlight(h)})).filter(item=>item.start>=0);
          if(window.CSS&&CSS.highlights&&window.Highlight){
            (window.__readerHighlightNames||[]).forEach(name=>CSS.highlights.delete(name));
            const names=[],rules=[];
            located.forEach((item,index)=>{
              const range=makeRange(item.start,String(item.h.q).length);if(!range)return;
              const name='reader-annotation-'+index;
              CSS.highlights.set(name,new Highlight(range));
              names.push(name);rules.push('::highlight('+name+'){background:'+item.h.c+';color:#342f29 !important;}');
            });
            window.__readerHighlightNames=names;
            let highlightStyle=document.getElementById('reader-highlight-styles');
            if(!highlightStyle){highlightStyle=document.createElement('style');highlightStyle.id='reader-highlight-styles';document.head.appendChild(highlightStyle);}
            highlightStyle.textContent=rules.join('');
          }else{
            located.sort((left,right)=>right.start-left.start).forEach(item=>{
              const end=item.start+String(item.h.q).length;
              textNodes.filter(part=>part.end>item.start&&part.start<end).reverse().forEach(part=>{
                const from=Math.max(item.start,part.start)-part.start,to=Math.min(end,part.end)-part.start;
                if(to<=from)return;
                const range=document.createRange();range.setStart(part.node,from);range.setEnd(part.node,to);
                const mark=document.createElement('mark');mark.className='reader-highlight';mark.style.background=item.h.c;mark.style.color='#342f29';
                try{range.surroundContents(mark);}catch(e){}
              });
            });
          }
          const layoutKey='${if (paged) "paged" else "scroll"}-${preferences.fontFamily}-${preferences.fontSize}-${preferences.lineHeight}-${preferences.margin}';
          const layoutChanged=window.__readerLayoutKey!==layoutKey;
          window.__readerLayoutKey=layoutKey;
          window.__readerInitialized=true;
          if(firstRun||layoutChanged){
            window.__readerRestoring=true;
            requestAnimationFrame(()=>setTimeout(()=>{
              if(!window.readerGoOffset(resumeOffset))window.readerGoProgress(${initialProgress.coerceIn(0.0,1.0)});
              window.__readerRestoring=false;
              report();
            },60));
          }
          else requestAnimationFrame(report);
          if(document.fonts&&document.fonts.ready)document.fonts.ready.then(()=>setTimeout(()=>{if(paged)goPage(pageIndex(),false);else report();},20));
          document.querySelectorAll('img').forEach(img=>{if(!img.complete)img.addEventListener('load',()=>{if(paged)goPage(pageIndex(),false);else report();},{once:true});});
        })();
    """.trimIndent()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(preferences: ReaderPreferences, container: AppContainer, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(readerString("Reading settings", "Настройки чтения"), style = MaterialTheme.typography.headlineMedium)
            Text(readerString("Theme and text appearance", "Тема и вид текста"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(readerString("Theme", "Тема"), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReaderTheme.entries.forEach { theme ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = when (theme) { ReaderTheme.LIGHT -> Color.White; ReaderTheme.SEPIA -> ReaderColors.ReaderBackground; ReaderTheme.DARK -> ReaderColors.SurfaceDark },
                        shape = RoundedCornerShape(18.dp),
                        border = if (!preferences.followSystemTheme && preferences.theme == theme) androidx.compose.foundation.BorderStroke(2.dp, ReaderColors.Primary) else null,
                        onClick = { scope.launch { container.settings.updateReader { it.copy(theme = theme, followSystemTheme = false) } } },
                    ) { Column(Modifier.padding(14.dp)) { Text("Aa", color = if (theme == ReaderTheme.DARK) Color.White else ReaderColors.TextPrimary); Text(theme.name.lowercase().replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.labelMedium, color = if (theme == ReaderTheme.DARK) Color.White else ReaderColors.TextPrimary) } }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text(readerString("Follow system theme", "Как в системе"), modifier = Modifier.weight(1f)); Switch(preferences.followSystemTheme, { value -> scope.launch { container.settings.updateReader { it.copy(followSystemTheme = value) } } }) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(preferences.fontFamily, modifier = Modifier.weight(1f)); TextButton(onClick = { scope.launch { container.settings.updateReader { it.copy(fontFamily = if (it.fontFamily == "Literata") "Sans" else "Literata") } } }) { Text("Change") } }
                }
                Surface(Modifier.width(120.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { scope.launch { container.settings.updateReader { it.copy(fontSize = it.fontSize - 1) } } }) { Text("−") }
                        Text("${preferences.fontSize}")
                        TextButton(onClick = { scope.launch { container.settings.updateReader { it.copy(fontSize = it.fontSize + 1) } } }) { Text("+") }
                    }
                }
            }
            Text(readerString("Line spacing", "Межстрочный интервал") + " · ${"%.2f".format(Locale.US, preferences.lineHeight)}")
            Slider(preferences.lineHeight.toFloat(), { value -> scope.launch { container.settings.updateReader { it.copy(lineHeight = value.toDouble()) } } }, valueRange = 1.1f..2.2f)
            Text(readerString("Page margins", "Поля страницы"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    12 to readerString("Narrow", "Узкие"),
                    24 to readerString("Medium", "Средние"),
                    40 to readerString("Wide", "Широкие"),
                ).forEach { (value, label) ->
                    ReaderSettingOption(label, preferences.margin == value, Modifier.weight(1f)) {
                        scope.launch { container.settings.updateReader { it.copy(margin = value) } }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingMode.entries.forEach { mode ->
                    ReaderSettingOption(
                        if (mode == ReadingMode.PAGED) readerString("Pages", "Страницы") else readerString("Scroll", "Прокрутка"),
                        preferences.readingMode == mode,
                        Modifier.weight(1f),
                    ) { scope.launch { container.settings.updateReader { it.copy(readingMode = mode) } } }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(19.dp)) { Text(readerString("Done", "Готово")) }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun ReaderSettingOption(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(52.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 0.dp else 2.dp,
            color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
        ),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentsSheet(
    publication: PublicationManifest,
    current: Int,
    tab: String,
    onTab: (String) -> Unit,
    bookmarks: List<app.readbound.data.BookmarkEntity>,
    annotations: List<AnnotationEntity>,
    onLocation: (Int, Double, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().height(660.dp).padding(horizontal = 24.dp)) {
            Text(readerString("Contents", "Содержание"), style = MaterialTheme.typography.headlineMedium)
            Text(readerString("${publication.chapters.size} chapters", "Глав: ${publication.chapters.size}"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("chapters" to readerString("Chapters", "Главы"), "bookmarks" to readerString("Bookmarks", "Закладки"), "notes" to readerString("Notes", "Заметки")).forEach { (id, label) -> FilterChip(tab == id, onClick = { onTab(id) }, label = { Text(label) }) }
            }
            LazyColumn(Modifier.weight(1f)) {
                when (tab) {
                    "bookmarks" -> items(bookmarks.size) { index ->
                        val item = bookmarks[index]
                        ChapterRow(item.chapterIndex, item.title, item.chapterIndex == current) { onLocation(item.chapterIndex, parseLocatorProgress(item.locatorJson), parseLocatorOffset(item.locatorJson)) }
                    }
                    "notes" -> items(annotations.size) { index ->
                        val item = annotations[index]
                        ChapterRow(item.chapterIndex, item.quote.take(60), item.chapterIndex == current) { onLocation(item.chapterIndex, parseLocatorProgress(item.locatorJson), parseLocatorOffset(item.locatorJson)) }
                    }
                    else -> itemsIndexed(publication.chapters) { index, item -> ChapterRow(index, item.title, index == current) { onLocation(index, 0.0, 0) } }
                }
            }
            Surface(color = ReaderColors.SurfaceDark, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(Modifier.padding(16.dp)) { Text(readerString("Current position · chapter ${current + 1}", "Текущая позиция · глава ${current + 1}"), color = Color.White, modifier = Modifier.weight(1f)); Text("${((current + 1f) / publication.chapters.size * 100).toInt()}%", color = ReaderColors.Lime) }
            }
        }
    }
}

@Composable
private fun ChapterRow(number: Int, title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
    ) {
        Row(Modifier.height(68.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(if (selected) ReaderColors.Primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Text("${number + 1}", color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(title, modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 2)
            if (selected) ReaderIcon(R.raw.icon_current, null, Modifier.size(24.dp), ColorFilter.tint(ReaderColors.Primary))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionSheet(
    selection: TextSelection,
    color: String,
    plugins: List<PluginAction>,
    onColor: (String) -> Unit,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
    onAnki: () -> Unit,
    onTranslate: () -> Unit,
    onPlugin: (PluginAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(readerString("Work with selection", "Действия с выделением"), style = MaterialTheme.typography.titleMedium)
            Text(selection.quote.take(90), maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectionAction(R.raw.icon_language, readerString("Translate", "Перевести"), onTranslate)
                SelectionAction(R.raw.icon_highlight, readerString("Highlight", "Выделить"), onHighlight)
                SelectionAction(R.raw.icon_note, readerString("Note", "Заметка"), onNote)
                SelectionAction(R.raw.icon_anki, "Anki", onAnki)
                SelectionAction(R.raw.icon_copy, readerString("Copy", "Копия")) { (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("quote", selection.quote)); onDismiss() }
                SelectionAction(R.raw.icon_share, readerString("Share", "Поделиться")) { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, selection.quote) }, null)); onDismiss() }
            }
            if (plugins.any { it.pluginId != "app.readbound.anki" }) {
                Text(readerString("Plugin actions", "Действия плагинов"), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { plugins.filter { it.pluginId != "app.readbound.anki" }.forEach { action -> FilterChip(false, onClick = { onPlugin(action) }, label = { Text(action.title) }) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(readerString("Highlight color", "Цвет выделения"), modifier = Modifier.weight(1f))
                listOf("yellow" to ReaderColors.HighlightYellow, "green" to ReaderColors.HighlightGreen, "blue" to ReaderColors.HighlightBlue, "coral" to ReaderColors.HighlightCoral).forEach { (id, value) ->
                    Surface(
                        modifier = Modifier.padding(start = 10.dp).size(if (color == id) 36.dp else 32.dp),
                        color = value,
                        shape = RoundedCornerShape(50),
                        border = if (color == id) androidx.compose.foundation.BorderStroke(2.dp, ReaderColors.Primary) else null,
                        onClick = { onColor(id) },
                    ) {}
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(48.dp), shape = RoundedCornerShape(19.dp)) { Text(readerString("Done", "Готово")) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslationSheet(
    selection: TextSelection,
    results: List<DictionaryLookupRow>,
    loading: Boolean,
    error: String?,
    aiResult: String?,
    aiLoading: Boolean,
    aiError: String?,
    onAiTranslate: () -> Unit,
    onAnki: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(selection.quote.take(180), style = MaterialTheme.typography.headlineSmall, maxLines = 3)
            Text(readerString("Dictionary lookup", "Поиск по словарям"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            when {
                loading -> Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                error != null -> Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp))
                results.isEmpty() -> Text(
                    readerString("No matches. Import or enable a dictionary in Settings.", "Совпадений нет. Импортируйте или включите словарь в настройках."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(results.size, key = { results[it].entryId }) { index ->
                        val entry = results[index]
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.term, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Text(entry.dictionaryTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (entry.reading.isNotBlank()) Text(entry.reading, style = MaterialTheme.typography.labelMedium)
                                if (entry.kind != "term") Text(entry.kind.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(entry.definition, style = MaterialTheme.typography.bodyMedium)
                                if (entry.tags.isNotBlank()) Text(entry.tags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(readerString("AI translation", "Перевод с помощью ИИ"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (aiResult != null) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(aiResult, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("translation", aiResult)) }) { Text(readerString("Copy", "Копировать")) }
                    TextButton(onClick = { onAnki(aiResult) }) { Text(readerString("To Anki", "В Anki")) }
                }
            } else {
                if (aiError != null) Text(aiError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                Button(onClick = onAiTranslate, enabled = !aiLoading, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    if (aiLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(readerString("Translate with AI", "Перевести с ИИ"))
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text(readerString("Done", "Готово")) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SelectionAction(icon: Int, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(58.dp), onClick = onClick) {
            Box(contentAlignment = Alignment.Center) { ReaderIcon(icon, label, Modifier.size(24.dp), ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)) }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
    }
}

private fun selectionLocator(chapter: Int, progress: Double, selection: TextSelection) = JSONObject()
    .put("chapter", chapter).put("progress", progress).put("offset", selection.offset).put("quote", selection.quote).toString()

private fun parseLocatorProgress(json: String): Double = runCatching { JSONObject(json).optDouble("progress", 0.0) }.getOrDefault(0.0)
private fun parseLocatorOffset(json: String): Int = runCatching { JSONObject(json).optInt("textOffset", JSONObject(json).optInt("offset", 0)) }.getOrDefault(0)
private fun highlightColor(name: String) = when (name) { "green" -> "#d4f1e1"; "blue" -> "#dceaff"; "coral" -> "#ffd7cf"; else -> "#ffe58f" }
