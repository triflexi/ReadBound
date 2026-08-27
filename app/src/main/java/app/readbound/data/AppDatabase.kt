package app.readbound.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        ReadingStateEntity::class,
        AnnotationEntity::class,
        BookmarkEntity::class,
        TagEntity::class,
        AnnotationTagEntity::class,
        PluginEntity::class,
        PluginSettingEntity::class,
        DictionaryEntity::class,
        DictionaryEntryEntity::class,
        SyncShadowEntity::class,
        SyncConflictEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingStateDao(): ReadingStateDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun tagDao(): TagDao
    abstract fun pluginDao(): PluginDao
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun syncDao(): SyncDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "reader.db",
        ).addMigrations(MIGRATION_1_2).build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `dictionaries` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `revision` TEXT NOT NULL, `sourceLanguage` TEXT NOT NULL, `targetLanguage` TEXT NOT NULL, `format` INTEGER NOT NULL, `entryCount` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `importedAt` INTEGER NOT NULL, `sourceHash` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dictionaries_sourceHash` ON `dictionaries` (`sourceHash`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `dictionary_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dictionaryId` TEXT NOT NULL, `term` TEXT NOT NULL, `normalizedTerm` TEXT NOT NULL, `reading` TEXT NOT NULL, `definition` TEXT NOT NULL, `tags` TEXT NOT NULL, `kind` TEXT NOT NULL, `score` INTEGER NOT NULL, FOREIGN KEY(`dictionaryId`) REFERENCES `dictionaries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dictionary_entries_dictionaryId` ON `dictionary_entries` (`dictionaryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dictionary_entries_normalizedTerm` ON `dictionary_entries` (`normalizedTerm`)")
            }
        }
    }
}
