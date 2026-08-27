package app.readbound.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Transaction
    @Query("SELECT * FROM books ORDER BY COALESCE(lastOpenedAt, addedAt) DESC")
    fun observeLibrary(): Flow<List<BookWithState>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE sha256 = :sha LIMIT 1")
    suspend fun findByHash(sha: String): BookEntity?

    @Query("SELECT * FROM books ORDER BY addedAt")
    suspend fun all(): List<BookEntity>

    @Upsert suspend fun upsert(book: BookEntity)

    @Query("UPDATE books SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun markOpened(id: String, timestamp: Long)
}

@Dao
interface ReadingStateDao {
    @Query("SELECT * FROM reading_states WHERE bookId = :bookId")
    fun observe(bookId: String): Flow<ReadingStateEntity?>

    @Query("SELECT * FROM reading_states WHERE bookId = :bookId")
    suspend fun get(bookId: String): ReadingStateEntity?

    @Query("SELECT * FROM reading_states") suspend fun all(): List<ReadingStateEntity>
    @Upsert suspend fun upsert(state: ReadingStateEntity)
}

@Dao
interface AnnotationDao {
    @Transaction
    @Query("SELECT * FROM annotations WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AnnotationWithBook>>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY createdAt")
    fun observeForBook(bookId: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY createdAt")
    suspend fun forBook(bookId: String): List<AnnotationEntity>

    @Query("SELECT * FROM annotations") suspend fun allIncludingDeleted(): List<AnnotationEntity>
    @Query("SELECT * FROM annotations WHERE id = :id") suspend fun get(id: String): AnnotationEntity?
    @Upsert suspend fun upsert(annotation: AnnotationEntity)
    @Query("UPDATE annotations SET deletedAt = :now, updatedAt = :now WHERE id = :id") suspend fun softDelete(id: String, now: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY createdAt")
    fun observeForBook(bookId: String): Flow<List<BookmarkEntity>>
    @Query("SELECT * FROM bookmarks") suspend fun allIncludingDeleted(): List<BookmarkEntity>
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND chapterIndex = :chapter AND deletedAt IS NULL LIMIT 1")
    suspend fun activeAtChapter(bookId: String, chapter: Int): BookmarkEntity?
    @Upsert suspend fun upsert(bookmark: BookmarkEntity)
    @Query("UPDATE bookmarks SET deletedAt = :now, updatedAt = :now WHERE id = :id") suspend fun softDelete(id: String, now: Long)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE") fun observeTags(): Flow<List<TagEntity>>
    @Query("SELECT * FROM tags") suspend fun allIncludingDeleted(): List<TagEntity>
    @Query("SELECT tags.* FROM tags INNER JOIN annotation_tags ON tags.id = annotation_tags.tagId WHERE annotation_tags.annotationId = :annotationId AND tags.deletedAt IS NULL")
    suspend fun forAnnotation(annotationId: String): List<TagEntity>
    @Upsert suspend fun upsert(tag: TagEntity)
    @Upsert suspend fun link(link: AnnotationTagEntity)
    @Query("SELECT * FROM annotation_tags") suspend fun allLinks(): List<AnnotationTagEntity>
    @Query("DELETE FROM annotation_tags WHERE annotationId = :annotationId AND tagId = :tagId") suspend fun unlink(annotationId: String, tagId: String)
    @Query("DELETE FROM annotation_tags WHERE annotationId = :annotationId") suspend fun unlinkAll(annotationId: String)
}

@Dao
interface PluginDao {
    @Query("SELECT * FROM plugins ORDER BY name") fun observeAll(): Flow<List<PluginEntity>>
    @Query("SELECT * FROM plugins ORDER BY name") suspend fun all(): List<PluginEntity>
    @Query("SELECT * FROM plugins WHERE enabled = 1 ORDER BY name") suspend fun enabled(): List<PluginEntity>
    @Query("SELECT * FROM plugins WHERE id = :id") suspend fun get(id: String): PluginEntity?
    @Upsert suspend fun upsert(plugin: PluginEntity)
    @Query("UPDATE plugins SET enabled = :enabled WHERE id = :id") suspend fun setEnabled(id: String, enabled: Boolean)
    @Upsert suspend fun upsertSetting(setting: PluginSettingEntity)
    @Query("SELECT * FROM plugin_settings WHERE pluginId = :pluginId") suspend fun settings(pluginId: String): List<PluginSettingEntity>
}

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionaries ORDER BY importedAt, title COLLATE NOCASE")
    fun observeAll(): Flow<List<DictionaryEntity>>

    @Query("SELECT * FROM dictionaries WHERE sourceHash = :sourceHash LIMIT 1")
    suspend fun findByHash(sourceHash: String): DictionaryEntity?

    @Query("SELECT * FROM dictionaries WHERE id = :id")
    suspend fun get(id: String): DictionaryEntity?

    @Upsert
    suspend fun upsert(dictionary: DictionaryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntries(entries: List<DictionaryEntryEntity>)

    @Query("UPDATE dictionaries SET entryCount = :entryCount WHERE id = :id")
    suspend fun updateEntryCount(id: String, entryCount: Int)

    @Query("UPDATE dictionaries SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM dictionaries WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        """
        SELECT e.id AS entryId, e.dictionaryId AS dictionaryId, d.title AS dictionaryTitle,
               e.term AS term, e.normalizedTerm AS normalizedTerm, e.reading AS reading,
               e.definition AS definition, e.tags AS tags, e.kind AS kind, e.score AS score
        FROM dictionary_entries e
        INNER JOIN dictionaries d ON d.id = e.dictionaryId
        WHERE d.enabled = 1 AND e.normalizedTerm IN (:normalizedTerms)
        ORDER BY CASE WHEN e.kind = 'term' THEN 0 ELSE 1 END, e.score DESC, d.importedAt, e.id
        LIMIT :limit
        """,
    )
    suspend fun lookup(normalizedTerms: List<String>, limit: Int = 80): List<DictionaryLookupRow>
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_shadows") suspend fun shadows(): List<SyncShadowEntity>
    @Query("SELECT * FROM sync_shadows WHERE recordType = :type AND recordId = :id") suspend fun shadow(type: String, id: String): SyncShadowEntity?
    @Upsert suspend fun upsertShadow(shadow: SyncShadowEntity)
    @Query("SELECT * FROM sync_conflicts ORDER BY createdAt DESC") fun observeConflicts(): Flow<List<SyncConflictEntity>>
    @Upsert suspend fun upsertConflict(conflict: SyncConflictEntity)
    @Query("DELETE FROM sync_conflicts WHERE id = :id") suspend fun deleteConflict(id: String)
}
