package app.readbound.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "books", indices = [Index(value = ["sha256"], unique = true)])
data class BookEntity(
    @androidx.room.PrimaryKey val id: String,
    val sha256: String,
    val title: String,
    val author: String,
    val format: String,
    val originalPath: String,
    val contentRoot: String,
    val readingOrderJson: String,
    val coverPath: String?,
    val converterVersion: Int = 1,
    val addedAt: Long,
    val lastOpenedAt: Long? = null,
)

@Entity(
    tableName = "reading_states",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
)
data class ReadingStateEntity(
    @androidx.room.PrimaryKey val bookId: String,
    val locatorJson: String,
    val progression: Double,
    val chapterIndex: Int,
    val chapterTitle: String,
    val position: Int,
    val totalPositions: Int,
    val updatedAt: Long,
    val sessionEndedAt: Long,
)

@Entity(
    tableName = "annotations",
    indices = [Index("bookId")],
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
)
data class AnnotationEntity(
    @androidx.room.PrimaryKey val id: String,
    val bookId: String,
    val kind: String,
    val chapterIndex: Int,
    val locatorJson: String,
    val quote: String,
    val contextBefore: String = "",
    val contextAfter: String = "",
    val noteText: String = "",
    val color: String = "yellow",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "bookmarks",
    indices = [Index("bookId")],
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
)
data class BookmarkEntity(
    @androidx.room.PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val locatorJson: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "annotation_tags",
    primaryKeys = ["annotationId", "tagId"],
    indices = [Index("tagId")],
    foreignKeys = [
        ForeignKey(AnnotationEntity::class, ["id"], ["annotationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class AnnotationTagEntity(val annotationId: String, val tagId: String)

@Entity(tableName = "plugins")
data class PluginEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val version: String,
    val apiVersion: Int,
    val manifestJson: String,
    val rootPath: String,
    val enabled: Boolean,
    val bundled: Boolean,
    val installedAt: Long,
)

@Entity(tableName = "plugin_settings", primaryKeys = ["pluginId", "key"])
data class PluginSettingEntity(val pluginId: String, val key: String, val value: String, val secret: Boolean)

@Entity(tableName = "dictionaries", indices = [Index(value = ["sourceHash"], unique = true)])
data class DictionaryEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val revision: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val format: Int,
    val entryCount: Int,
    val enabled: Boolean,
    val importedAt: Long,
    val sourceHash: String,
)

@Entity(
    tableName = "dictionary_entries",
    indices = [Index("dictionaryId"), Index("normalizedTerm")],
    foreignKeys = [ForeignKey(DictionaryEntity::class, ["id"], ["dictionaryId"], onDelete = ForeignKey.CASCADE)],
)
data class DictionaryEntryEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dictionaryId: String,
    val term: String,
    val normalizedTerm: String,
    val reading: String,
    val definition: String,
    val tags: String,
    val kind: String,
    val score: Int,
)

data class DictionaryLookupRow(
    val entryId: Long,
    val dictionaryId: String,
    val dictionaryTitle: String,
    val term: String,
    val normalizedTerm: String,
    val reading: String,
    val definition: String,
    val tags: String,
    val kind: String,
    val score: Int,
)

@Entity(tableName = "sync_shadows", primaryKeys = ["recordType", "recordId"])
data class SyncShadowEntity(
    val recordType: String,
    val recordId: String,
    val payloadHash: String,
    val payloadJson: String,
    val updatedAt: Long,
)

@Entity(tableName = "sync_conflicts", indices = [Index("recordType"), Index("recordId")])
data class SyncConflictEntity(
    @androidx.room.PrimaryKey val id: String,
    val recordType: String,
    val recordId: String,
    val localJson: String,
    val remoteJson: String,
    val createdAt: Long,
)

data class BookWithState(
    @androidx.room.Embedded val book: BookEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "bookId") val state: ReadingStateEntity?,
)

data class AnnotationWithBook(
    @androidx.room.Embedded val annotation: AnnotationEntity,
    @androidx.room.Relation(parentColumn = "bookId", entityColumn = "id") val book: BookEntity,
)
