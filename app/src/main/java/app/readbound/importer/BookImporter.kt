package app.readbound.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.readbound.data.BookEntity
import org.w3c.dom.Element
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class BookImporter(private val context: Context) {
    suspend fun import(uri: Uri): BookEntity {
        val displayName = queryName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "book"
        val mimeType = context.contentResolver.getType(uri)
        val staging = File(context.cacheDir, "book-import-${UUID.randomUUID()}").apply { mkdirs() }
        val downloaded = File(staging, "source.bin")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open selected file" }
            downloaded.outputStream().use { output -> input.copyTo(output) }
        }
        val prefix = downloaded.inputStream().use { input ->
            val buffer = ByteArray(16_384)
            val count = input.read(buffer).coerceAtLeast(0)
            buffer.copyOf(count)
        }
        val extension = BookFormatDetector.detect(displayName, mimeType, prefix)
            ?: error("Unsupported book format. Choose EPUB, FB2 or TXT")
        val source = File(staging, "source.$extension")
        require(downloaded.renameTo(source)) { "Could not prepare selected book" }
        require(source.length() in 1..MAX_BOOK_BYTES) { "Book is empty or exceeds 250 MB" }
        val sha = sha256(source)

        val originals = File(context.filesDir, "books").apply { mkdirs() }
        val original = File(originals, "$sha.$extension")
        if (!original.exists()) source.copyTo(original)
        val root = File(context.filesDir, "publications/$sha").apply { mkdirs() }

        val parsed = when (extension) {
            "epub" -> parseEpub(source, root)
            "fb2" -> Fb2Converter.convert(source, root, displayName.substringBeforeLast('.')).let { Parsed(it.title, it.author, it.chapters, it.coverPath) }
            else -> parseTxt(source, root, displayName.substringBeforeLast('.'))
        }
        staging.deleteRecursively()

        return BookEntity(
            id = UUID.randomUUID().toString(),
            sha256 = sha,
            title = parsed.title.ifBlank { displayName.substringBeforeLast('.') },
            author = parsed.author.ifBlank { "Unknown author" },
            format = extension.uppercase(),
            originalPath = original.absolutePath,
            contentRoot = root.absolutePath,
            readingOrderJson = PublicationManifest(parsed.chapters).toJson(),
            coverPath = parsed.coverPath,
            addedAt = System.currentTimeMillis(),
        )
    }

    private fun parseEpub(source: File, root: File): Parsed {
        unzipSafely(source, root)
        val container = File(root, "META-INF/container.xml")
        require(container.isFile) { "Invalid EPUB: META-INF/container.xml is missing" }
        val containerDoc = secureFactory().newDocumentBuilder().parse(container)
        val rootFile = containerDoc.getElementsByTagNameNS("*", "rootfile").item(0) as? Element
            ?: error("Invalid EPUB: package file is missing")
        val opfRelative = rootFile.getAttribute("full-path")
        val opf = File(root, opfRelative).canonicalFile
        require(opf.startsWith(root.canonicalFile) && opf.isFile) { "Invalid EPUB package path" }
        val doc = secureFactory().newDocumentBuilder().parse(opf)
        val title = firstText(doc.documentElement, "title")
        val author = firstText(doc.documentElement, "creator")
        val packageDir = opf.parentFile ?: root

        val manifest = mutableMapOf<String, Pair<String, String>>()
        var coverHref: String? = null
        val items = doc.getElementsByTagNameNS("*", "item")
        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            val id = item.getAttribute("id")
            val href = item.getAttribute("href").substringBefore('#')
            val media = item.getAttribute("media-type")
            manifest[id] = href to media
            if (item.getAttribute("properties").split(' ').contains("cover-image")) coverHref = href
        }
        if (coverHref == null) {
            val metas = doc.getElementsByTagNameNS("*", "meta")
            var coverId: String? = null
            for (i in 0 until metas.length) {
                val meta = metas.item(i) as Element
                if (meta.getAttribute("name") == "cover") coverId = meta.getAttribute("content")
            }
            coverHref = coverId?.let { manifest[it]?.first }
        }

        val chapters = mutableListOf<PublicationChapter>()
        val spine = doc.getElementsByTagNameNS("*", "itemref")
        for (i in 0 until spine.length) {
            val idRef = (spine.item(i) as Element).getAttribute("idref")
            val href = manifest[idRef]?.first ?: continue
            val target = File(packageDir, href).canonicalFile
            if (!target.startsWith(root.canonicalFile) || !target.isFile) continue
            sanitizeHtmlFile(target)
            chapters += PublicationChapter(extractHtmlTitle(target) ?: "Chapter ${chapters.size + 1}", target.relativeTo(root).invariantSeparatorsPath)
        }
        require(chapters.isNotEmpty()) { "Invalid EPUB: reading order is empty" }
        val cover = coverHref?.let { File(packageDir, it).takeIf(File::isFile)?.absolutePath }
        return Parsed(title, author, chapters, cover)
    }

    private fun parseTxt(source: File, root: File, title: String): Parsed {
        val bytes = source.readBytes()
        val text = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
            else -> bytes.toString(Charsets.UTF_8)
        }
        return Parsed(title, "", writeTextChapters(root, title, text), null)
    }

    private fun writeTextChapters(root: File, title: String, text: String): List<PublicationChapter> {
        val marker = Regex("(?im)^(глава|chapter)\\s+[0-9ivxlcdmа-яё-]+.*$")
        val matches = marker.findAll(text).toList()
        val chunks = if (matches.isEmpty()) {
            text.chunked(MAX_CHAPTER_CHARS)
        } else {
            matches.mapIndexed { index, match -> text.substring(match.range.first, matches.getOrNull(index + 1)?.range?.first ?: text.length) }
        }
        return chunks.filter(String::isNotBlank).mapIndexed { index, chunk ->
            val heading = chunk.lineSequence().firstOrNull()?.take(100)?.ifBlank { null }
                ?: if (index == 0) title else "Chapter ${index + 1}"
            val paragraphs = chunk.split(Regex("\\r?\\n\\s*\\r?\\n"))
            val html = buildString {
                append("<!doctype html><html><head><meta charset=\"utf-8\"></head><body><h1>")
                append(escape(heading)).append("</h1>")
                paragraphs.forEach { paragraph ->
                    val clean = paragraph.trim()
                    if (clean.isNotBlank() && clean != heading) append("<p>").append(escape(clean).replace("\n", "<br>" )).append("</p>")
                }
                append("</body></html>")
            }
            val file = File(root, "chapter-$index.html").apply { writeText(html) }
            PublicationChapter(heading, file.name)
        }.ifEmpty {
            val file = File(root, "chapter-0.html").apply { writeText("<html><body><p></p></body></html>") }
            listOf(PublicationChapter(title, file.name))
        }
    }

    private fun unzipSafely(source: File, destination: File) {
        var total = 0L
        ZipInputStream(FileInputStream(source)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = File(destination, entry.name).canonicalFile
                require(output.startsWith(destination.canonicalFile)) { "Unsafe path in EPUB" }
                if (entry.isDirectory) output.mkdirs() else {
                    output.parentFile?.mkdirs()
                    output.outputStream().use { stream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count <= 0) break
                            total += count
                            require(total <= MAX_EXTRACTED_BYTES) { "EPUB expands beyond 500 MB" }
                            stream.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun sanitizeHtmlFile(file: File) {
        if (file.length() > 8_000_000) return
        val original = file.readText()
        val safe = original
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
            .replace(Regex("(?i)\\son[a-z]+\\s*=\\s*(['\"]).*?\\1"), "")
            .replace(Regex("(?i)javascript:"), "")
        if (safe != original) file.writeText(safe)
    }

    private fun extractHtmlTitle(file: File): String? {
        if (file.length() > 2_000_000) return null
        val html = file.readText()
        return Regex("(?is)<h[12][^>]*>(.*?)</h[12]>").find(html)?.groupValues?.get(1)
            ?.replace(Regex("<[^>]+>"), "")?.trim()?.takeIf(String::isNotBlank)
            ?: Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)?.trim()
    }

    private fun secureFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { setExpandEntityReferences(false) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
    }

    private fun firstText(element: Element, localName: String): String =
        element.getElementsByTagNameNS("*", localName).item(0)?.textContent?.trim().orEmpty()

    private fun queryName(uri: Uri): String? = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private data class Parsed(
        val title: String,
        val author: String,
        val chapters: List<PublicationChapter>,
        val coverPath: String?,
    )

    companion object {
        private const val MAX_BOOK_BYTES = 250L * 1024 * 1024
        private const val MAX_EXTRACTED_BYTES = 500L * 1024 * 1024
        private const val MAX_CHAPTER_CHARS = 80_000
    }
}
