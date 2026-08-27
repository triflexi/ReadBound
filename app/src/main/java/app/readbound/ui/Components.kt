package app.readbound.ui

import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import app.readbound.R
import app.readbound.data.DictionaryLookupRow
import app.readbound.settings.AnkiBackMode
import app.readbound.settings.AnkiPreferences
import app.readbound.ui.theme.ReaderColors
import kotlinx.coroutines.delay

@Composable
fun readerString(english: String, russian: String): String = if (Locale.current.language == "ru") russian else english

@Composable
fun ReaderTransientMessage(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message == null) return
    LaunchedEffect(message) {
        delay(3_000)
        onDismiss()
    }
    Surface(
        modifier = modifier.widthIn(max = 420.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 10.dp,
        onClick = onDismiss,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                ReaderIcon(
                    R.raw.icon_clear,
                    readerString("Dismiss", "Закрыть"),
                    Modifier.size(20.dp),
                    ColorFilter.tint(MaterialTheme.colorScheme.inverseOnSurface),
                )
            }
        }
    }
}

@Composable
fun RoundIconButton(
    @RawRes icon: Int,
    description: String,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surface,
    foreground: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.size(48.dp).shadow(4.dp, RoundedCornerShape(18.dp)),
        color = background,
        shape = RoundedCornerShape(18.dp),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ReaderIcon(icon, description, Modifier.size(24.dp), ColorFilter.tint(foreground))
        }
    }
}

@Composable
fun ReaderBottomNavigation(selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f), shape),
    ) {
        Row(Modifier.height(72.dp).padding(10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            BottomNavigationItem("library", R.raw.icon_library, readerString("Library", "Библиотека"), selected == "library", Modifier.weight(1f), onSelect)
            Spacer(Modifier.width(8.dp))
            BottomNavigationItem("notes", R.raw.icon_notes, readerString("Notes", "Заметки"), selected == "notes", Modifier.weight(1f), onSelect)
        }
    }
}

@Composable
fun ReaderNavigationRail(selected: String, onSelect: (String) -> Unit) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        header = { Text("R", style = MaterialTheme.typography.headlineMedium, color = ReaderColors.Primary, modifier = Modifier.padding(vertical = 18.dp)) },
    ) {
        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.weight(1f)) {
            NavigationRailItem(
                selected = selected == "library",
                onClick = { onSelect("library") },
                icon = { ReaderIcon(R.raw.icon_library, "Library", Modifier.size(24.dp), ColorFilter.tint(if (selected == "library") ReaderColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant)) },
                label = { Text(readerString("Library", "Библиотека")) },
            )
            NavigationRailItem(
                selected = selected == "notes",
                onClick = { onSelect("notes") },
                icon = { ReaderIcon(R.raw.icon_notes, "Notes", Modifier.size(24.dp), ColorFilter.tint(if (selected == "notes") ReaderColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant)) },
                label = { Text(readerString("Notes", "Заметки")) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnkiExportDialog(
    initialFront: String,
    initialBack: String = "",
    dictionaryResults: List<DictionaryLookupRow> = emptyList(),
    dictionaryLoading: Boolean = false,
    dictionaryMessage: String? = null,
    preferences: AnkiPreferences = AnkiPreferences(),
    aiEnabled: Boolean = false,
    aiResult: String? = null,
    aiLoading: Boolean = false,
    aiError: String? = null,
    onPreferencesChange: (AnkiPreferences) -> Unit = {},
    onAiTranslate: () -> Unit = {},
    onDismiss: () -> Unit,
    onSend: (String, String) -> Unit,
) {
    var front by remember(initialFront) { mutableStateOf(initialFront) }
    var localPreferences by remember(initialFront) { mutableStateOf(preferences) }
    var selectedEntryId by remember(initialFront) { mutableStateOf<Long?>(null) }
    var selectedBlockIds by remember(initialFront) { mutableStateOf<Set<String>>(emptySet()) }
    var detailsExpanded by remember(initialFront) { mutableStateOf(false) }
    var manualBack by remember(initialFront, initialBack) { mutableStateOf(initialBack.takeIf(String::isNotBlank)) }
    var editingManually by remember(initialFront, initialBack) { mutableStateOf(initialBack.isNotBlank()) }
    val options = remember(dictionaryResults, initialFront) { dictionaryCardOptions(dictionaryResults, initialFront) }
    val selectedOption = options.firstOrNull { it.entry.entryId == selectedEntryId } ?: options.firstOrNull()

    LaunchedEffect(preferences) { localPreferences = preferences }
    LaunchedEffect(selectedOption?.entry?.entryId) {
        val option = selectedOption ?: return@LaunchedEffect
        selectedEntryId = option.entry.entryId
        selectedBlockIds = defaultDictionaryBlockIds(option, localPreferences.backMode)
    }

    fun updatePreferences(next: AnkiPreferences) {
        localPreferences = next
        onPreferencesChange(next)
    }

    val generatedBack = selectedOption?.let { dictionaryCardBack(it, selectedBlockIds, localPreferences) }.orEmpty()
    val back = manualBack ?: generatedBack

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(),
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(readerString("New Anki card", "Новая карточка Anki"), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        readerString("The translation is ready. Choose only what should go on the card.", "Перевод уже готов. Выберите только то, что попадёт в карточку."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                AnkiCardIsland {
                    Text(readerString("Front", "Лицевая сторона"), style = MaterialTheme.typography.titleMedium)
                    TextField(
                        value = front,
                        onValueChange = { front = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                    )
                }

                AnkiCardIsland {
                    Text(readerString("Dictionary", "Словарь"), style = MaterialTheme.typography.titleMedium)
                    when {
                        dictionaryLoading -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                        options.isEmpty() -> Text(
                            dictionaryMessage ?: readerString("No dictionary translation found.", "Словарный перевод не найден."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> {
                            if (options.size > 1) {
                                Text(readerString("Choose an entry", "Выберите вариант"), style = MaterialTheme.typography.labelMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    options.take(10).forEach { option ->
                                        val label = option.conciseText.ifBlank { option.entry.term }.take(34)
                                        FilterChip(
                                            selected = option.entry.entryId == selectedOption?.entry?.entryId,
                                            onClick = {
                                                selectedEntryId = option.entry.entryId
                                                selectedBlockIds = defaultDictionaryBlockIds(option, localPreferences.backMode)
                                                manualBack = null
                                                editingManually = false
                                            },
                                            label = { Text(label, maxLines = 1) },
                                        )
                                    }
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = localPreferences.backMode == AnkiBackMode.CONCISE && selectedOption != null && selectedBlockIds == defaultDictionaryBlockIds(selectedOption, AnkiBackMode.CONCISE) && manualBack == null,
                                    onClick = {
                                        val option = selectedOption ?: return@FilterChip
                                        val next = localPreferences.copy(backMode = AnkiBackMode.CONCISE)
                                        updatePreferences(next)
                                        selectedBlockIds = defaultDictionaryBlockIds(option, next.backMode)
                                        manualBack = null
                                        editingManually = false
                                    },
                                    label = { Text(readerString("Concise", "Кратко")) },
                                )
                                FilterChip(
                                    selected = localPreferences.backMode == AnkiBackMode.ARTICLE && selectedOption != null && selectedBlockIds == defaultDictionaryBlockIds(selectedOption, AnkiBackMode.ARTICLE) && manualBack == null,
                                    onClick = {
                                        val option = selectedOption ?: return@FilterChip
                                        val next = localPreferences.copy(backMode = AnkiBackMode.ARTICLE)
                                        updatePreferences(next)
                                        selectedBlockIds = defaultDictionaryBlockIds(option, next.backMode)
                                        manualBack = null
                                        editingManually = false
                                    },
                                    label = { Text(readerString("Full article", "Вся статья")) },
                                )
                                FilterChip(
                                    selected = detailsExpanded,
                                    onClick = { detailsExpanded = !detailsExpanded },
                                    label = { Text(readerString("Details", "Состав")) },
                                )
                            }
                            if (detailsExpanded && selectedOption != null) {
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                        selectedOption.blocks.forEach { block ->
                                            Row(
                                                Modifier.fillMaxWidth().clickable {
                                                    selectedBlockIds = if (block.id in selectedBlockIds) selectedBlockIds - block.id else selectedBlockIds + block.id
                                                    manualBack = null
                                                    editingManually = false
                                                }.padding(horizontal = 10.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Checkbox(block.id in selectedBlockIds, onCheckedChange = null)
                                                Text(block.text, Modifier.weight(1f), maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }
                            }
                            Text(readerString("Always include", "Добавлять всегда"), style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(localPreferences.includeReading, { updatePreferences(localPreferences.copy(includeReading = !localPreferences.includeReading)); manualBack = null; editingManually = false }, label = { Text(readerString("Reading", "Произношение")) })
                                FilterChip(localPreferences.includeTags, { updatePreferences(localPreferences.copy(includeTags = !localPreferences.includeTags)); manualBack = null; editingManually = false }, label = { Text(readerString("Grammar", "Грамматика")) })
                                FilterChip(localPreferences.includeDictionaryName, { updatePreferences(localPreferences.copy(includeDictionaryName = !localPreferences.includeDictionaryName)); manualBack = null; editingManually = false }, label = { Text(readerString("Dictionary name", "Название словаря")) })
                            }
                        }
                    }
                    if (dictionaryMessage != null && options.isNotEmpty()) {
                        Text(dictionaryMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                AnkiCardIsland {
                    Text(readerString("Card back", "Обратная сторона"), style = MaterialTheme.typography.titleMedium)
                    if (editingManually) {
                        TextField(
                            value = manualBack.orEmpty(),
                            onValueChange = { manualBack = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                            minLines = 3,
                            maxLines = 8,
                            shape = RoundedCornerShape(18.dp),
                            label = { Text(readerString("Manual text", "Текст вручную")) },
                        )
                        if (selectedOption != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { manualBack = null; editingManually = false }) {
                                    Text(readerString("Return to builder", "Вернуться к конструктору"))
                                }
                            }
                        }
                    } else {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                back.ifBlank { readerString("Choose dictionary blocks or enter text manually.", "Выберите блоки словаря или введите текст вручную.") },
                                Modifier.padding(16.dp),
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { manualBack = back; editingManually = true }) {
                                Text(readerString("Edit manually", "Изменить вручную"))
                            }
                        }
                    }

                    if (aiResult != null) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(aiResult)
                                TextButton(onClick = { manualBack = aiResult; editingManually = false }) {
                                    Text(readerString("Use AI translation", "Использовать перевод ИИ"))
                                }
                            }
                        }
                    } else if (aiEnabled) {
                        OutlinedButton(onClick = onAiTranslate, enabled = !aiLoading, modifier = Modifier.fillMaxWidth()) {
                            if (aiLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(readerString("Translate with AI", "Перевести с помощью ИИ"))
                        }
                    }
                    if (aiError != null) Text(aiError, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(88.dp))
            }

            val actionShape = RoundedCornerShape(24.dp)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .clip(actionShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f), actionShape),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(68.dp).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text(readerString("Cancel", "Отмена")) }
                    TextButton(
                        onClick = { onSend(front.trim(), back.trim()) },
                        enabled = front.isNotBlank() && back.isNotBlank(),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text(readerString("Send", "Отправить")) }
                }
            }
        }
    }
}

@Composable
private fun AnkiCardIsland(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f),
            RoundedCornerShape(24.dp),
        ),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .9f),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun BottomNavigationItem(
    id: String,
    @RawRes icon: Int,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = modifier
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp)) else Modifier)
            .clickable { onSelect(id) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        ReaderIcon(icon, label, Modifier.size(24.dp), ColorFilter.tint(if (selected) ReaderColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) ReaderColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
