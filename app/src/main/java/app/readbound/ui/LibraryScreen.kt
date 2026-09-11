package app.readbound.ui

import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import app.readbound.ui.GlassButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import app.readbound.ui.GlassModalBottomSheet as ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import app.readbound.ui.GlassSurface as Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.readbound.ui.GlassTextField as TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.readbound.AppContainer
import app.readbound.R
import app.readbound.data.BookWithState
import app.readbound.ui.theme.ReaderColors
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

private enum class LibrarySort { RECENT, TITLE, AUTHOR, PROGRESS }

private val BOOK_MIME_TYPES = arrayOf(
    "application/epub+zip",
    "application/x-fictionbook+xml",
    "application/fb2+xml",
    "application/xml",
    "text/xml",
    "text/plain",
    "application/octet-stream",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    container: AppContainer,
    onOpenBook: (String) -> Unit,
    onNotes: () -> Unit,
    onSettings: () -> Unit,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val navigationBackdrop = rememberLayerBackdrop { drawRect(backgroundColor); drawContent() }
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() >= 720.dp }
    val books by container.repository.library.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var showSort by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.RECENT) }
    var formatFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var inProgressOnly by rememberSaveable { mutableStateOf(false) }
    var reattachBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                val reattachId = reattachBookId
                reattachBookId = null
                if (reattachId == null) {
                    container.repository.import(it).onFailure { error -> importError = error.message ?: "Import failed" }
                } else {
                    container.repository.reattach(reattachId, it)
                        .onSuccess { book -> onOpenBook(book.id) }
                        .onFailure { error -> importError = error.message ?: "Could not attach book file" }
                }
            }
        }
    }

    fun openOrAttach(item: BookWithState) {
        if (item.book.contentRoot.isBlank() || !File(item.book.contentRoot).isDirectory) {
            reattachBookId = item.book.id
            importLauncher.launch(BOOK_MIME_TYPES)
        } else onOpenBook(item.book.id)
    }

    val filtered = remember(books, query, sort, formatFilter, inProgressOnly) {
        books.filter {
            (query.isBlank() || it.book.title.contains(query, true) || it.book.author.contains(query, true)) &&
                (formatFilter == null || it.book.format.equals(formatFilter, true)) &&
                (!inProgressOnly || (it.state?.progression ?: 0.0) in 0.001..0.999)
        }
            .let { list ->
                when (sort) {
                    LibrarySort.RECENT -> list.sortedByDescending { it.book.lastOpenedAt ?: it.book.addedAt }
                    LibrarySort.TITLE -> list.sortedBy { it.book.title.lowercase() }
                    LibrarySort.AUTHOR -> list.sortedBy { it.book.author.lowercase() }
                    LibrarySort.PROGRESS -> list.sortedByDescending { it.state?.progression ?: 0.0 }
                }
            }
    }
    val continueBook = books.filter { it.book.contentRoot.isNotBlank() && File(it.book.contentRoot).isDirectory }
        .maxByOrNull { it.book.lastOpenedAt ?: Long.MIN_VALUE }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            floatingActionButton = {
                Surface(
                    onClick = { importLauncher.launch(BOOK_MIME_TYPES) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(bottom = if (wide) 0.dp else 88.dp).size(60.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ReaderIcon(R.raw.icon_add, readerString("Add book", "Добавить книгу"), Modifier.size(26.dp), ColorFilter.tint(MaterialTheme.colorScheme.onPrimary))
                    }
                }
            },
        ) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {

            val contentWidth = (maxWidth - if (wide) 108.dp else 0.dp).coerceAtMost(680.dp)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(start = if (wide) 108.dp else 0.dp).layerBackdrop(navigationBackdrop),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
            ) {
                item {
                    Column(Modifier.width(contentWidth).padding(horizontal = 20.dp, vertical = 12.dp)) {
                        if (searchActive) {
                            SearchField(query, { query = it }, { query = ""; searchActive = false })
                            Spacer(Modifier.height(20.dp))
                            Text(readerString("Results", "Результаты"), style = MaterialTheme.typography.headlineMedium)
                            Text(readerString("${filtered.size} books", "Книг: ${filtered.size}"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(readerString("YOUR COLLECTION", "ВАША КОЛЛЕКЦИЯ"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(readerString("My library", "Моя библиотека"), style = MaterialTheme.typography.displayLarge)
                                    Text(readerString("${books.size} books · ${books.count { (it.state?.progression ?: 0.0) in 0.001..0.999 }} in progress", "Книг: ${books.size} · читаются: ${books.count { (it.state?.progression ?: 0.0) in 0.001..0.999 }}"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                RoundIconButton(R.raw.icon_search, "Search") { searchActive = true }
                                Spacer(Modifier.width(8.dp))
                                RoundIconButton(R.raw.icon_settings, readerString("Settings", "Настройки")) { onSettings() }
                            }
                            continueBook?.let {
                                Spacer(Modifier.height(18.dp))
                                ContinueCard(it) { openOrAttach(it) }
                            }
                            FlowRow(
                                modifier = Modifier.padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                listOf<String?>(null, "EPUB", "FB2", "TXT").forEach { format ->
                                    LibraryFilterChip(
                                        selected = formatFilter == format,
                                        onClick = { formatFilter = format },
                                        label = format ?: readerString("All formats", "Все форматы"),
                                    )
                                }
                                LibraryFilterChip(
                                    selected = inProgressOnly,
                                    onClick = { inProgressOnly = !inProgressOnly },
                                    label = readerString("In progress", "В процессе"),
                                )
                            }
                            Spacer(Modifier.height(22.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(readerString("All books", "Все книги"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(16.dp),
                                    onClick = { showSort = true },
                                ) {
                                    Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(sort.label(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(6.dp))
                                        ReaderIcon(R.raw.icon_chevron, null, Modifier.size(16.dp), ColorFilter.tint(MaterialTheme.colorScheme.primary))
                                    }
                                }
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item { EmptyLibrary(query.isNotBlank()) { importLauncher.launch(arrayOf("*/*")) } }
                } else {
                    items(filtered, key = { it.book.id }) { book ->
                        Box(Modifier.width(contentWidth).padding(horizontal = 20.dp, vertical = 7.dp)) {
                            BookCard(book) { openOrAttach(book) }
                        }
                    }
                }
                importError?.let { message ->
                    item {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(20.dp)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                                TextButton(onClick = { importError = null }) { Text("OK") }
                            }
                        }
                    }
                }
            }
            }
        }
        if (wide) ReaderNavigationRail("library", backdrop = navigationBackdrop) { if (it == "notes") onNotes() }
        if (!wide) ReaderBottomNavigation("library", Modifier.align(Alignment.BottomCenter), backdrop = navigationBackdrop) { if (it == "notes") onNotes() }
    }

    if (showSort) ModalBottomSheet(onDismissRequest = { showSort = false }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text(readerString("Sorting", "Сортировка"), style = MaterialTheme.typography.headlineMedium)
            Text(readerString("How to arrange the library", "Порядок книг в библиотеке"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            LibrarySort.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable { sort = option }.padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(sort == option, onClick = { sort = option })
                    Text(option.fullLabel(), modifier = Modifier.padding(start = 8.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showSort = false },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(19.dp),
            ) { Text(readerString("Apply", "Применить")) }
            TextButton(onClick = { showSort = false; onSettings() }, modifier = Modifier.fillMaxWidth()) { Text(readerString("Storage, sync and extensions", "Хранилище, синхронизация и расширения")) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LibraryFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    GlassFilterChip(selected, onClick, label = { Text(label) })
}

@Composable
private fun SearchField(value: String, onValue: (String) -> Unit, onClose: () -> Unit) {
    TextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(readerString("Book or author", "Книга или автор")) },
        leadingIcon = { ReaderIcon(R.raw.icon_search, null, Modifier.size(24.dp), ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)) },
        trailingIcon = { Box(Modifier.clickable(onClick = onClose).padding(12.dp)) { ReaderIcon(R.raw.icon_clear, "Close", Modifier.size(24.dp), ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)) } },
        shape = RoundedCornerShape(20.dp),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@Composable
private fun ContinueCard(item: BookWithState, onClick: () -> Unit) {
    val progress = (item.state?.progression ?: 0.0).toFloat().coerceIn(0f, 1f)
    Surface(
        modifier = Modifier.fillMaxWidth().height(164.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp),
        onClick = onClick,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(item, Modifier.width(82.dp).height(118.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(readerString("CONTINUE", "ПРОДОЛЖИТЬ"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(item.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${item.book.author} · ${item.book.format}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(10.dp))
                ProgressBar(progress, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("${(progress * 100).toInt()}% · ${item.state?.chapterTitle.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { ReaderIcon(R.raw.icon_continue, null, Modifier.size(24.dp), ColorFilter.tint(MaterialTheme.colorScheme.onPrimary)) }
            }
        }
    }
}

@Composable
private fun BookCard(item: BookWithState, onClick: () -> Unit) {
    val progress = (item.state?.progression ?: 0.0).toFloat().coerceIn(0f, 1f)
    val attached = item.book.contentRoot.isNotBlank() && File(item.book.contentRoot).isDirectory
    Surface(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        onClick = onClick,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(item, Modifier.width(60.dp).height(86.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.book.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(11.dp)) {
                    Text(if (attached) item.book.format else readerString("${item.book.format} · file needed", "${item.book.format} · нужен файл"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(item.book.lastOpenedAt?.let { "· ${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it))}" } ?: "· new", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressBar(progress, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            ReaderIcon(R.raw.icon_open_book, "Open", Modifier.size(20.dp), ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}

@Composable
private fun BookCover(item: BookWithState, modifier: Modifier) {
    val colors = remember(item.book.sha256) {
        val seed = item.book.sha256.take(6).toIntOrNull(16) ?: 0x5B55D8
        listOf(Color(0xFF000000 or seed.toLong()), ReaderColors.SurfaceDark)
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
        item.book.coverPath?.let { AsyncImage(File(it), item.book.title, Modifier.fillMaxSize()) }
            ?: Text(item.book.title.take(18).uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(8.dp), maxLines = 3)
    }
}

@Composable
private fun ProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .12f), RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxWidth(progress.coerceAtLeast(.01f)).height(5.dp).background(color, RoundedCornerShape(3.dp)))
    }
}

@Composable
private fun EmptyLibrary(filtered: Boolean, onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (filtered) readerString("Nothing found", "Ничего не найдено") else readerString("Your library is empty", "Библиотека пуста"), style = MaterialTheme.typography.titleMedium)
        Text(if (filtered) readerString("Try another query", "Попробуйте другой запрос") else readerString("Add an EPUB, FB2 or TXT book to start reading", "Добавьте EPUB, FB2 или TXT, чтобы начать чтение"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!filtered) Button(onAdd, modifier = Modifier.padding(top = 18.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text(readerString("Add book", "Добавить книгу")) }
    }
}

@Composable
private fun LibrarySort.label() = when (this) {
    LibrarySort.RECENT -> readerString("Recent", "Недавние")
    LibrarySort.TITLE -> readerString("Title", "Название")
    LibrarySort.AUTHOR -> readerString("Author", "Автор")
    LibrarySort.PROGRESS -> readerString("Progress", "Прогресс")
}

@Composable
private fun LibrarySort.fullLabel() = when (this) {
    LibrarySort.RECENT -> readerString("Recently read", "Недавно читали")
    LibrarySort.TITLE -> readerString("By title", "По названию")
    LibrarySort.AUTHOR -> readerString("By author", "По автору")
    LibrarySort.PROGRESS -> readerString("By progress", "По прогрессу")
}
