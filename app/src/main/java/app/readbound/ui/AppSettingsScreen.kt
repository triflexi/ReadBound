package app.readbound.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.readbound.AppContainer
import app.readbound.R
import app.readbound.anki.AnkiGateway
import app.readbound.settings.ReaderPreferences
import app.readbound.settings.ReaderTheme
import app.readbound.settings.AnkiPreferences
import app.readbound.settings.AnkiBackMode
import app.readbound.settings.SyncPreferences
import app.readbound.settings.AiPreferences
import app.readbound.settings.LocaleController
import app.readbound.plugin.PluginManifest
import app.readbound.sync.WebDavSyncWorker
import app.readbound.data.SyncConflictEntity
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val russian = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "ru"
    fun localized(english: String, russianText: String) = if (russian) russianText else english
    val scope = rememberCoroutineScope()
    val reader by container.settings.reader.collectAsStateWithLifecycle(initialValue = ReaderPreferences())
    val anki by container.settings.anki.collectAsStateWithLifecycle(initialValue = AnkiPreferences())
    val sync by container.settings.sync.collectAsStateWithLifecycle(initialValue = SyncPreferences())
    val ai by container.settings.ai.collectAsStateWithLifecycle(initialValue = AiPreferences())
    val dictionaries by container.dictionaries.dictionaries.collectAsStateWithLifecycle(initialValue = emptyList())
    val plugins by container.plugins.plugins.collectAsStateWithLifecycle(initialValue = emptyList())
    val conflicts by container.database.syncDao().observeConflicts().collectAsStateWithLifecycle(initialValue = emptyList())
    var syncUrl by remember(sync.url) { mutableStateOf(sync.url) }
    var syncUser by remember(sync.username) { mutableStateOf(sync.username) }
    var syncPassword by remember { mutableStateOf("") }
    var aiEnabled by remember(ai.enabled) { mutableStateOf(ai.enabled) }
    var aiEndpoint by remember(ai.endpoint) { mutableStateOf(ai.endpoint) }
    var aiModel by remember(ai.model) { mutableStateOf(ai.model) }
    var aiTargetLanguage by remember(ai.targetLanguage) { mutableStateOf(ai.targetLanguage) }
    var aiPrompt by remember(ai.prompt) { mutableStateOf(ai.prompt) }
    var aiApiKey by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingPluginUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPlugin by remember { mutableStateOf<PluginManifest?>(null) }
    var ankiChoices by remember { mutableStateOf<Pair<Map<Long, String>, Map<Long, String>>?>(null) }
    var editingConflict by remember { mutableStateOf<SyncConflictEntity?>(null) }
    var dictionaryProgress by remember { mutableStateOf<String?>(null) }
    var dictionaryToDelete by remember { mutableStateOf<app.readbound.data.DictionaryEntity?>(null) }

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { scope.launch { message = container.backup.export(it).fold({ "Backup created" }, { it.message ?: "Backup error" }) } }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch { message = container.backup.restore(it).fold({ "Backup restored" }, { it.message ?: "Restore error" }) } }
    }
    val installPlugin = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selected -> scope.launch {
            container.plugins.inspectPackage(selected).fold(
                onSuccess = { manifest -> pendingPluginUri = selected; pendingPlugin = manifest },
                onFailure = { message = it.message ?: "Plugin error" },
            )
        } }
    }
    val importDictionary = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selected ->
            dictionaryProgress = localized("Preparing dictionary…", "Подготовка словаря…")
            scope.launch {
                runCatching {
                    container.dictionaries.importDictionary(selected) { progress ->
                        scope.launch {
                            dictionaryProgress = "${progress.dictionaryTitle}: ${progress.filesDone}/${progress.filesTotal} · ${progress.entries}"
                        }
                    }
                }.onSuccess {
                    message = localized("Dictionary imported: ${it.title} (${it.entryCount})", "Словарь импортирован: ${it.title} (${it.entryCount})")
                }.onFailure {
                    message = it.message ?: localized("Dictionary import failed", "Ошибка импорта словаря")
                }
                dictionaryProgress = null
            }
        }
    }
    val ankiPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if (granted) "AnkiDroid connected" else "AnkiDroid permission was denied"
    }

    // Top island occupies status bar inset + 8.dp padding + 64.dp height; keep the first card clear of it.
    val topIslandClearance = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 96.dp

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = topIslandClearance, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsCard(readerString("Appearance and language", "Оформление и язык")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = reader.followSystemTheme,
                            onClick = { scope.launch { container.settings.updateReader { it.copy(followSystemTheme = true) } } },
                            label = { Text(readerString("System", "Как в системе")) },
                        )
                        FilterChip(
                            selected = !reader.followSystemTheme && reader.theme != ReaderTheme.DARK,
                            onClick = { scope.launch { container.settings.updateReader { it.copy(followSystemTheme = false, theme = if (it.theme == ReaderTheme.DARK) ReaderTheme.LIGHT else it.theme) } } },
                            label = { Text(readerString("Light", "Светлая")) },
                        )
                        FilterChip(
                            selected = !reader.followSystemTheme && reader.theme == ReaderTheme.DARK,
                            onClick = { scope.launch { container.settings.updateReader { it.copy(followSystemTheme = false, theme = ReaderTheme.DARK) } } },
                            label = { Text(readerString("Dark", "Тёмная")) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch { container.settings.updateReader { it.copy(language = "ru") } }
                            LocaleController.apply(context, "ru", recreate = true)
                        }, modifier = Modifier.weight(1f)) { Text("Русский") }
                        OutlinedButton(onClick = {
                            scope.launch { container.settings.updateReader { it.copy(language = "en") } }
                            LocaleController.apply(context, "en", recreate = true)
                        }, modifier = Modifier.weight(1f)) { Text("English") }
                    }
                }
            }
            item {
                SettingsCard(readerString("Dictionaries", "Словари")) {
                    OutlinedButton(
                        onClick = { importDictionary.launch(arrayOf("application/zip", "application/octet-stream")) },
                        enabled = dictionaryProgress == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(dictionaryProgress ?: readerString("Import dictionary", "Импортировать словарь")) }
                    if (dictionaries.isEmpty()) {
                        Text(readerString("No dictionaries imported yet.", "Словари ещё не импортированы."), style = MaterialTheme.typography.bodyMedium)
                    }
                    dictionaries.forEach { dictionary ->
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(dictionary.title, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        listOf(dictionary.sourceLanguage, dictionary.targetLanguage).filter { it.isNotBlank() }.joinToString(" → ") +
                                            if (dictionary.entryCount > 0) " · ${dictionary.entryCount}" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(dictionary.enabled, { enabled -> scope.launch { container.dictionaries.setEnabled(dictionary.id, enabled) } })
                            }
                            TextButton(onClick = { dictionaryToDelete = dictionary }, modifier = Modifier.align(Alignment.End)) {
                                Text(readerString("Delete dictionary", "Удалить словарь"))
                            }
                        }
                    }
                }
            }
            item {
                SettingsCard(readerString("AI translation", "Перевод с помощью ИИ")) {
                    SettingSwitch(readerString("Enable AI translation", "Включить перевод ИИ"), aiEnabled) { aiEnabled = it }
                    Text(
                        readerString(
                            "OpenAI-compatible Chat Completions endpoint. Selected text is sent only when you tap AI Translate.",
                            "OpenAI-совместимый endpoint Chat Completions. Выделенный текст отправляется только после нажатия «Перевести с ИИ».",
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextField(aiEndpoint, { aiEndpoint = it }, label = { Text("Endpoint, e.g. https://api.openai.com/v1") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    TextField(aiModel, { aiModel = it }, label = { Text(readerString("Model ID", "ID модели")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    TextField(aiTargetLanguage, { aiTargetLanguage = it }, label = { Text(readerString("Translation language", "Язык перевода")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    TextField(aiPrompt, { aiPrompt = it }, label = { Text(readerString("Translation instruction", "Инструкция для перевода")) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6)
                    TextField(
                        aiApiKey,
                        { aiApiKey = it },
                        label = { Text(if (container.aiTranslation.hasApiKey()) readerString("API key (saved; enter to replace)", "API-ключ (сохранён; введите для замены)") else "API key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Button(onClick = {
                        scope.launch {
                            container.settings.updateAi(AiPreferences(aiEnabled, aiEndpoint, aiModel, aiTargetLanguage, aiPrompt))
                            if (aiApiKey.isNotBlank()) {
                                container.aiTranslation.setApiKey(aiApiKey)
                                aiApiKey = ""
                            }
                            message = localized("AI settings saved", "Настройки ИИ сохранены")
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(readerString("Save AI settings", "Сохранить настройки ИИ")) }
                    if (container.aiTranslation.hasApiKey()) {
                        TextButton(onClick = { container.aiTranslation.setApiKey(""); message = localized("API key removed", "API-ключ удалён") }) {
                            Text(readerString("Remove saved API key", "Удалить сохранённый API-ключ"))
                        }
                    }
                }
            }
            item {
                SettingsCard(readerString("Backup", "Резервная копия")) {
                    Text(readerString("Portable metadata backup excludes book files, WebDAV password and plugin secrets.", "Переносимая копия метаданных не содержит файлы книг, пароль WebDAV и секреты плагинов."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { exportBackup.launch("reader-backup.zip") }, modifier = Modifier.weight(1f)) { Text(readerString("Export", "Экспорт")) }
                        OutlinedButton(onClick = { importBackup.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.weight(1f)) { Text(readerString("Restore", "Восстановить")) }
                    }
                }
            }
            item {
                SettingsCard(readerString("WebDAV sync", "Синхронизация WebDAV")) {
                    SettingSwitch(readerString("Enable background sync", "Фоновая синхронизация"), sync.enabled) { enabled ->
                        scope.launch {
                            container.settings.updateSync(sync.copy(enabled = enabled, url = syncUrl, username = syncUser))
                            configurePeriodicSync(context, enabled)
                        }
                    }
                    TextField(syncUrl, { syncUrl = it }, label = { Text("HTTPS URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    TextField(syncUser, { syncUser = it }, label = { Text(readerString("Username", "Имя пользователя")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    TextField(syncPassword, { syncPassword = it }, label = { Text(readerString("Password", "Пароль")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = {
                        scope.launch {
                            container.settings.updateSync(sync.copy(enabled = true, url = syncUrl, username = syncUser))
                            if (syncPassword.isNotBlank()) container.sync.setPassword(syncPassword)
                            message = container.sync.sync().fold({ it }, { it.message ?: "Sync error" })
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(readerString("Sync now", "Синхронизировать")) }
                    if (sync.lastSyncAt > 0) Text("Last sync: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(sync.lastSyncAt))}", style = MaterialTheme.typography.labelSmall)
                }
            }
            item {
                SettingsCard("AnkiDroid") {
                    Text(if (container.plugins.ankiAvailable()) readerString("AnkiDroid detected", "AnkiDroid найден") else readerString("AnkiDroid is not installed", "AnkiDroid не установлен"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { ankiPermission.launch(AnkiGateway.READ_WRITE_PERMISSION) },
                        enabled = container.plugins.ankiAvailable(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (container.plugins.ankiPermissionGranted()) readerString("Permission granted", "Доступ разрешён") else readerString("Grant permission", "Разрешить доступ")) }
                    Text(readerString("Each card has editable front and back sides.", "У каждой карточки можно отредактировать лицевую и обратную стороны."), style = MaterialTheme.typography.bodyMedium)
                    Text(readerString("Default dictionary content", "Содержимое из словаря по умолчанию"), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = anki.backMode == AnkiBackMode.CONCISE,
                            onClick = { scope.launch { container.settings.updateAnki(anki.copy(backMode = AnkiBackMode.CONCISE)) } },
                            label = { Text(readerString("Concise", "Кратко")) },
                        )
                        FilterChip(
                            selected = anki.backMode == AnkiBackMode.ARTICLE,
                            onClick = { scope.launch { container.settings.updateAnki(anki.copy(backMode = AnkiBackMode.ARTICLE)) } },
                            label = { Text(readerString("Full article", "Вся статья")) },
                        )
                    }
                    SettingSwitch(readerString("Add pronunciation or reading", "Добавлять произношение"), anki.includeReading) { value -> scope.launch { container.settings.updateAnki(anki.copy(includeReading = value)) } }
                    SettingSwitch(readerString("Add grammar tags", "Добавлять грамматику"), anki.includeTags) { value -> scope.launch { container.settings.updateAnki(anki.copy(includeTags = value)) } }
                    SettingSwitch(readerString("Add dictionary name", "Добавлять название словаря"), anki.includeDictionaryName) { value -> scope.launch { container.settings.updateAnki(anki.copy(includeDictionaryName = value)) } }
                    Text(readerString("Deck", "Колода") + ": ${anki.deckName} · " + readerString("note type", "тип заметки") + ": ${anki.modelName}", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(onClick = {
                        scope.launch {
                            ankiChoices = runCatching { container.plugins.ankiDecks() to container.plugins.ankiModels() }
                                .getOrElse { message = it.message ?: "Could not read Anki configuration"; null }
                        }
                    }, enabled = container.plugins.ankiPermissionGranted(), modifier = Modifier.fillMaxWidth()) { Text(readerString("Choose deck and note type", "Выбрать колоду и тип заметки")) }
                }
            }
            item {
                SettingsCard(readerString("Extensions", "Расширения")) {
                    OutlinedButton(onClick = { installPlugin.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text(readerString("Install .readbound-plugin", "Установить .readbound-plugin")) }
                    plugins.forEach { plugin ->
                        SettingSwitch("${plugin.name} · ${plugin.version}", plugin.enabled) { enabled -> scope.launch { container.database.pluginDao().setEnabled(plugin.id, enabled) } }
                    }
                }
            }
            if (conflicts.isNotEmpty()) {
                item { Text(readerString("Sync conflicts", "Конфликты синхронизации"), style = MaterialTheme.typography.titleMedium) }
                items(conflicts, key = { it.id }) { conflict ->
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${conflict.recordType}: ${conflict.recordId.take(8)}", style = MaterialTheme.typography.labelLarge)
                            Text(readerString("Both devices changed this item. Choose which version to keep.", "Элемент изменён на обоих устройствах. Выберите итоговую версию."))
                            Row {
                                TextButton(onClick = { scope.launch { container.sync.resolveConflict(conflict, false) } }) { Text(readerString("Keep local", "Оставить локальную")) }
                                TextButton(onClick = { scope.launch { container.sync.resolveConflict(conflict, true) } }) { Text(readerString("Keep remote", "Оставить удалённую")) }
                                TextButton(onClick = { editingConflict = conflict }) { Text(readerString("Edit merged", "Объединить")) }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        SettingsTopIsland(
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .widthIn(max = 680.dp)
                .fillMaxWidth(),
        )
        ReaderTransientMessage(
            message = message,
            onDismiss = { message = null },
            modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
        )
    }

    pendingPlugin?.let { manifest ->
        AlertDialog(
            onDismissRequest = { pendingPlugin = null; pendingPluginUri = null },
            title = { Text("Install ${manifest.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${manifest.id} · ${manifest.version}")
                    Text(if (manifest.permissions.isEmpty()) "No privileged capabilities requested." else "Capabilities: ${manifest.permissions.joinToString()}")
                    if (manifest.allowedDomains.isNotEmpty()) Text("Allowed HTTPS domains: ${manifest.allowedDomains.joinToString()}")
                }
            },
            confirmButton = { Button(onClick = {
                val uri = pendingPluginUri ?: return@Button
                scope.launch {
                    message = container.plugins.install(uri).fold({ "Installed ${it.name}" }, { it.message ?: "Plugin error" })
                    pendingPlugin = null; pendingPluginUri = null
                }
            }) { Text("Install") } },
            dismissButton = { TextButton(onClick = { pendingPlugin = null; pendingPluginUri = null }) { Text("Cancel") } },
        )
    }
    ankiChoices?.let { (decks, models) ->
        AlertDialog(
            onDismissRequest = { ankiChoices = null },
            title = { Text("Anki destination") },
            text = {
                LazyColumn(modifier = Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { Text("Deck", style = MaterialTheme.typography.titleMedium) }
                    items(decks.entries.toList(), key = { "d${it.key}" }) { entry ->
                        TextButton(onClick = { scope.launch { container.settings.updateAnki(anki.copy(deckId = entry.key, deckName = entry.value)) } }, modifier = Modifier.fillMaxWidth()) { Text(if (entry.key == anki.deckId) "✓ ${entry.value}" else entry.value) }
                    }
                    item { Text("Note type", style = MaterialTheme.typography.titleMedium) }
                    items(models.entries.toList(), key = { "m${it.key}" }) { entry ->
                        TextButton(onClick = { scope.launch { container.settings.updateAnki(anki.copy(modelId = entry.key, modelName = entry.value, fieldIndex = 0, backFieldIndex = 1)) } }, modifier = Modifier.fillMaxWidth()) { Text(if (entry.key == anki.modelId) "✓ ${entry.value}" else entry.value) }
                    }
                }
            },
            confirmButton = { Button(onClick = { ankiChoices = null }) { Text("Done") } },
        )
    }
    editingConflict?.let { conflict ->
        var mergedJson by remember(conflict.id) { mutableStateOf(conflict.localJson) }
        AlertDialog(
            onDismissRequest = { editingConflict = null },
            title = { Text("Resolve conflict") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Edit the final JSON record. Invalid data will not be saved.")
                    TextField(mergedJson, { mergedJson = it }, modifier = Modifier.fillMaxWidth().height(260.dp), minLines = 8)
                }
            },
            confirmButton = { Button(onClick = {
                scope.launch {
                    runCatching { org.json.JSONObject(mergedJson) }
                        .onSuccess { container.sync.resolveConflict(conflict, it); editingConflict = null }
                        .onFailure { message = "Invalid JSON: ${it.message}" }
                }
            }) { Text("Use merged") } },
            dismissButton = { TextButton(onClick = { editingConflict = null }) { Text("Cancel") } },
        )
    }
    dictionaryToDelete?.let { dictionary ->
        AlertDialog(
            onDismissRequest = { dictionaryToDelete = null },
            title = { Text(readerString("Delete dictionary?", "Удалить словарь?")) },
            text = { Text(dictionary.title) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        container.dictionaries.delete(dictionary.id)
                        dictionaryToDelete = null
                        message = localized("Dictionary deleted", "Словарь удалён")
                    }
                }) { Text(readerString("Delete", "Удалить")) }
            },
            dismissButton = { TextButton(onClick = { dictionaryToDelete = null }) { Text(readerString("Cancel", "Отмена")) } },
        )
    }
}

@Composable
private fun SettingsTopIsland(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f), shape),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                ReaderIcon(
                    R.raw.icon_back,
                    readerString("Back", "Назад"),
                    Modifier.size(24.dp),
                    ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                )
            }
            Spacer(Modifier.size(4.dp))
            Text(
                readerString("Settings", "Настройки"),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked, onChecked)
    }
}

private fun configurePeriodicSync(context: android.content.Context, enabled: Boolean) {
    val manager = WorkManager.getInstance(context)
    if (!enabled) manager.cancelUniqueWork("webdav-sync") else {
        val work = PeriodicWorkRequestBuilder<WebDavSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork("webdav-sync", ExistingPeriodicWorkPolicy.UPDATE, work)
    }
}
