package app.readbound.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.readbound.AppContainer
import app.readbound.R
import app.readbound.anki.AnkiGateway
import app.readbound.data.AnnotationEntity
import app.readbound.data.AnnotationWithBook
import app.readbound.data.TagEntity
import app.readbound.ui.theme.ReaderColors
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(container: AppContainer, onLibrary: () -> Unit, onOpenBook: (String) -> Unit) {
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() >= 720.dp }
    val notes by container.repository.annotations.collectAsStateWithLifecycle(initialValue = emptyList())
    val tags by container.repository.tags.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<AnnotationWithBook?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var ankiDialogQuote by remember { mutableStateOf<String?>(null) }
    var pendingAnkiCard by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()
    val ankiPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val card = pendingAnkiCard
        pendingAnkiCard = null
        if (granted && card != null) scope.launch { message = container.plugins.sendCardToAnki(card.first, card.second).getOrElse { it.message ?: "Anki error" } }
        else if (!granted) message = "AnkiDroid permission was denied"
    }

    fun sendToAnki(front: String, back: String) {
        when {
            !container.plugins.ankiAvailable() -> message = "Install and configure AnkiDroid first"
            !container.plugins.ankiPermissionGranted() -> { pendingAnkiCard = front to back; ankiPermission.launch(AnkiGateway.READ_WRITE_PERMISSION) }
            else -> scope.launch { message = container.plugins.sendCardToAnki(front, back).getOrElse { it.message ?: "Anki error" } }
        }
    }

    val filtered = remember(notes, query, selectedTag) {
        notes.filter { item ->
            query.isBlank() || item.annotation.quote.contains(query, true) || item.annotation.noteText.contains(query, true) || item.book.title.contains(query, true)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (wide) ReaderNavigationRail("notes") { if (it == "library") onLibrary() }
            val contentWidth = if (maxWidth > 760.dp) 720.dp else maxWidth
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(start = if (wide) 96.dp else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
            ) {
                item {
                    Column(Modifier.width(contentWidth).padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(readerString("YOUR THOUGHTS", "ВАШИ МЫСЛИ"), style = MaterialTheme.typography.labelMedium, color = ReaderColors.Primary)
                        Text(readerString("Notes", "Заметки"), style = MaterialTheme.typography.displayLarge)
                        Text(readerString("${notes.size} highlights", "Выделений: ${notes.size}"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(18.dp))
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(readerString("Search notes", "Поиск по заметкам")) },
                            leadingIcon = { ReaderIcon(R.raw.icon_search, null, Modifier.size(24.dp), ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)) },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        )
                        if (tags.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selectedTag == null, onClick = { selectedTag = null }, label = { Text(readerString("All tags", "Все теги")) })
                                tags.forEach { tag -> FilterChip(selectedTag == tag.id, onClick = { selectedTag = if (selectedTag == tag.id) null else tag.id }, label = { Text(tag.name) }) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(readerString("All highlights", "Все выделения"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(readerString("Newest first", "Сначала новые"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (filtered.isEmpty()) item {
                    Column(Modifier.width(contentWidth).padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(readerString("No notes yet", "Заметок пока нет"), style = MaterialTheme.typography.titleMedium)
                        Text(readerString("Select text in a book to create the first highlight", "Выделите текст в книге, чтобы создать первую заметку"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(filtered, key = { it.annotation.id }) { item ->
                    Box(Modifier.width(contentWidth).padding(horizontal = 20.dp, vertical = 7.dp)) {
                        AnnotationCard(
                            item = item,
                            container = container,
                            selectedTag = selectedTag,
                            onOpen = { onOpenBook(item.book.id) },
                            onEdit = { editing = item },
                            onMessage = { message = it },
                            onAnki = { ankiDialogQuote = item.annotation.quote },
                        )
                    }
                }
            }
                ReaderTransientMessage(
                    message = message,
                    onDismiss = { message = null },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 100.dp),
                )
            }
        }
        if (!wide) ReaderBottomNavigation("notes", Modifier.align(Alignment.BottomCenter)) { if (it == "library") onLibrary() }
    }

    editing?.let { item ->
        EditAnnotationDialog(container, item.annotation, tags, onDismiss = { editing = null })
    }
    ankiDialogQuote?.let { quote ->
        AnkiExportDialog(quote, onDismiss = { ankiDialogQuote = null }) { front, back ->
            ankiDialogQuote = null
            sendToAnki(front, back)
        }
    }
}

@Composable
private fun AnnotationCard(
    item: AnnotationWithBook,
    container: AppContainer,
    selectedTag: String?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onMessage: (String) -> Unit,
    onAnki: () -> Unit,
) {
    val context = LocalContext.current
    var itemTags by remember(item.annotation.id) { mutableStateOf<List<TagEntity>>(emptyList()) }
    LaunchedEffect(item.annotation.id, selectedTag) { itemTags = container.repository.tagsForAnnotation(item.annotation.id) }
    if (selectedTag != null && itemTags.none { it.id == selectedTag }) return
    val accent = when (item.annotation.color) {
        "green" -> ReaderColors.HighlightGreen
        "blue" -> ReaderColors.HighlightBlue
        "coral" -> ReaderColors.HighlightCoral
        else -> ReaderColors.HighlightYellow
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(accent, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text(item.book.title.take(2).uppercase(), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.book.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(readerString("Chapter ${item.annotation.chapterIndex + 1}", "Глава ${item.annotation.chapterIndex + 1}"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(item.annotation.updatedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Box(Modifier.width(4.dp).height(72.dp).background(accent, RoundedCornerShape(2.dp)))
                Text(
                    "“${item.annotation.quote}”",
                    modifier = Modifier.padding(start = 14.dp).clickable(onClick = onOpen),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.annotation.noteText.isNotBlank()) {
                Surface(color = ReaderColors.HighlightGreen, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 10.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(readerString("PERSONAL NOTE", "ЛИЧНАЯ ЗАМЕТКА"), style = MaterialTheme.typography.labelSmall, color = ReaderColors.Success)
                        Text(item.annotation.noteText, style = MaterialTheme.typography.bodyMedium, color = ReaderColors.TextPrimary)
                    }
                }
            }
            if (itemTags.isNotEmpty()) FlowRow(Modifier.padding(start = 18.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemTags.forEach { AssistChip(onClick = {}, label = { Text(it.name) }) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                val actionPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                TextButton(onClick = onEdit, modifier = Modifier.weight(1f), contentPadding = actionPadding) {
                    Text(readerString("Edit", "Изменить"), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("quote", item.annotation.quote)); onMessage("Copied") }, modifier = Modifier.weight(1f), contentPadding = actionPadding) {
                    Text(readerString("Copy", "Копировать"), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, item.annotation.quote) }, null))
                }, modifier = Modifier.weight(1f), contentPadding = actionPadding) {
                    Text(readerString("Share", "Поделиться"), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onAnki, modifier = Modifier.weight(1f), contentPadding = actionPadding) {
                    ReaderIcon(R.raw.icon_anki, null, Modifier.size(18.dp), ColorFilter.tint(ReaderColors.Primary)); Spacer(Modifier.width(2.dp)); Text("Anki", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun EditAnnotationDialog(
    container: AppContainer,
    annotation: AnnotationEntity,
    tags: List<TagEntity>,
    onDismiss: () -> Unit,
) {
    var note by remember(annotation.id) { mutableStateOf(annotation.noteText) }
    var newTag by remember { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(annotation.id) { selectedTags = container.repository.tagsForAnnotation(annotation.id).map { it.id }.toSet() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(readerString("Edit highlight", "Изменить выделение")) },
        text = {
            Column {
                Text("“${annotation.quote}”", maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                TextField(note, { note = it }, label = { Text(readerString("Personal note", "Личная заметка")) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag -> FilterChip(tag.id in selectedTags, onClick = { selectedTags = if (tag.id in selectedTags) selectedTags - tag.id else selectedTags + tag.id }, label = { Text(tag.name) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(newTag, { newTag = it }, label = { Text(readerString("New tag", "Новый тег")) }, modifier = Modifier.weight(1f), singleLine = true)
                    TextButton(onClick = {
                        if (newTag.isNotBlank()) scope.launch {
                            val tag = container.repository.createTag(newTag)
                            container.repository.linkTag(annotation.id, tag.id)
                            selectedTags = selectedTags + tag.id
                            newTag = ""
                        }
                    }) { Text(readerString("Add", "Добавить")) }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    container.repository.updateAnnotation(annotation.copy(noteText = note, kind = if (note.isBlank()) "highlight" else "note"))
                    container.repository.setAnnotationTags(annotation.id, selectedTags)
                    onDismiss()
                }
            }) { Text(readerString("Save", "Сохранить")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { scope.launch { container.repository.deleteAnnotation(annotation.id); onDismiss() } }) { Text(readerString("Delete", "Удалить")) }
                TextButton(onClick = onDismiss) { Text(readerString("Cancel", "Отмена")) }
            }
        },
    )
}
