package app.readbound

import android.app.Application
import androidx.work.Configuration
import app.readbound.data.AppDatabase
import app.readbound.data.ReaderRepository
import app.readbound.importer.BookImporter
import app.readbound.dictionary.DictionaryRepository
import app.readbound.plugin.PluginManager
import app.readbound.settings.SettingsRepository
import app.readbound.sync.BackupManager
import app.readbound.sync.SecretStore
import app.readbound.sync.WebDavSyncEngine
import app.readbound.translation.AiTranslationService

class ReaderApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.create(this)
        val settings = SettingsRepository(this)
        val repository = ReaderRepository(database, BookImporter(this))
        val secrets = SecretStore(this)
        val dictionaries = DictionaryRepository(this, database.dictionaryDao())
        val aiTranslation = AiTranslationService(settings, secrets)
        val plugins = PluginManager(this, database.pluginDao(), settings)
        container = AppContainer(
            database = database,
            repository = repository,
            settings = settings,
            plugins = plugins,
            backup = BackupManager(this, database, settings),
            sync = WebDavSyncEngine(this, database, settings, secrets),
            dictionaries = dictionaries,
            aiTranslation = aiTranslation,
        )
        plugins.installBundledPlugins()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
}

data class AppContainer(
    val database: AppDatabase,
    val repository: ReaderRepository,
    val settings: SettingsRepository,
    val plugins: PluginManager,
    val backup: BackupManager,
    val sync: WebDavSyncEngine,
    val dictionaries: DictionaryRepository,
    val aiTranslation: AiTranslationService,
)
