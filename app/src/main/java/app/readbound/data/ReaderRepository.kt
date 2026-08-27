package app.readbound.data

import android.net.Uri
import app.readbound.importer.BookImporter
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ReaderRepository(
    private val database: AppDatabase,
    private val importer: BookImporter,
) {
    val library: Flow<List<BookWithState>> = database.bookDao().observeLibrary()
    val annotations: Flow<List<AnnotationWithBook>> = database.annotationDao().observeAll()
    val tags: Flow<List<TagEntity>> = database.tagDao().observeTags()

    suspend fun import(uri: Uri): Result<BookEntity> = runCatching {
        val parsed = importer.import(uri)
        database.bookDao().findByHash(parsed.sha256)?.let { return@runCatching it }
        database.bookDao().upsert(parsed)
        parsed
    }

    suspend fun reattach(bookId: String, uri: Uri): Result<BookEntity> = runCatching {
        val existing = database.bookDao().get(bookId) ?: error("Book metadata was not found")
        val imported = importer.import(uri)
        require(imported.sha256 == existing.sha256) { "Choose the same book file (checksum does not match)" }
        existing.copy(
            title = imported.title.ifBlank { existing.title },
            author = imported.author.ifBlank { existing.author },
            format = imported.format,
            originalPath = imported.originalPath,
            contentRoot = imported.contentRoot,
            readingOrderJson = imported.readingOrderJson,
            coverPath = imported.coverPath,
            converterVersion = imported.converterVersion,
        ).also { database.bookDao().upsert(it) }
    }

    suspend fun book(id: String) = database.bookDao().get(id)
    fun readingState(id: String) = database.readingStateDao().observe(id)
    fun annotationsForBook(id: String) = database.annotationDao().observeForBook(id)
    fun bookmarksForBook(id: String) = database.bookmarkDao().observeForBook(id)

    suspend fun saveReadingState(state: ReadingStateEntity) {
        database.readingStateDao().upsert(state)
        database.bookDao().markOpened(state.bookId, state.updatedAt)
    }

    suspend fun addAnnotation(
        bookId: String,
        chapterIndex: Int,
        locatorJson: String,
        quote: String,
        note: String = "",
        color: String = "yellow",
        before: String = "",
        after: String = "",
    ): AnnotationEntity {
        val now = System.currentTimeMillis()
        return AnnotationEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            kind = if (note.isBlank()) "highlight" else "note",
            chapterIndex = chapterIndex,
            locatorJson = locatorJson,
            quote = quote,
            contextBefore = before,
            contextAfter = after,
            noteText = note,
            color = color,
            createdAt = now,
            updatedAt = now,
        ).also { database.annotationDao().upsert(it) }
    }

    suspend fun updateAnnotation(annotation: AnnotationEntity) =
        database.annotationDao().upsert(annotation.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteAnnotation(id: String) = database.annotationDao().softDelete(id, System.currentTimeMillis())

    suspend fun toggleBookmark(bookId: String, chapter: Int, locator: String, title: String): Boolean {
        val now = System.currentTimeMillis()
        val existing = database.bookmarkDao().activeAtChapter(bookId, chapter)
        if (existing != null) {
            database.bookmarkDao().softDelete(existing.id, now)
            return false
        }
        database.bookmarkDao().upsert(BookmarkEntity(UUID.randomUUID().toString(), bookId, chapter, locator, title, now, now))
        return true
    }

    suspend fun createTag(name: String): TagEntity {
        val now = System.currentTimeMillis()
        return TagEntity(UUID.randomUUID().toString(), name.trim(), now, now).also { database.tagDao().upsert(it) }
    }

    suspend fun linkTag(annotationId: String, tagId: String) = database.tagDao().link(AnnotationTagEntity(annotationId, tagId))
    suspend fun tagsForAnnotation(annotationId: String) = database.tagDao().forAnnotation(annotationId)
    suspend fun setAnnotationTags(annotationId: String, tagIds: Set<String>) {
        database.tagDao().unlinkAll(annotationId)
        tagIds.forEach { database.tagDao().link(AnnotationTagEntity(annotationId, it)) }
    }
}
