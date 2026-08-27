package app.readbound.importer

import org.w3c.dom.Element
import java.io.File
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

internal data class Fb2Result(
    val title: String,
    val author: String,
    val chapters: List<PublicationChapter>,
    val coverPath: String?,
)

internal object Fb2Converter {
    fun convert(source: File, root: File, fallbackTitle: String): Fb2Result {
        val document = secureFactory().newDocumentBuilder().parse(source)
        val documentRoot = document.documentElement
        require(documentRoot.nodeName.substringAfter(':').equals("FictionBook", ignoreCase = true)) { "Invalid FB2: FictionBook root is missing" }

        val title = firstText(documentRoot, "book-title").ifBlank { fallbackTitle }
        val authorNode = elements(documentRoot, "author").firstOrNull()
        val author = authorNode?.let {
            listOf(firstText(it, "first-name"), firstText(it, "middle-name"), firstText(it, "last-name"))
                .filter(String::isNotBlank).joinToString(" ")
        }.orEmpty()
        val body = elements(documentRoot, "body").firstOrNull { it.getAttribute("name").isBlank() }
            ?: elements(documentRoot, "body").firstOrNull()
            ?: error("Invalid FB2: body is missing")

        val topSections = childSections(body)
        val chapterSections = topSections.flatMap(::leafSections)
        val chapters = if (chapterSections.isNotEmpty()) {
            chapterSections.mapIndexed { index, section -> writeSection(root, section, index) }
        } else {
            listOf(writeElement(root, body, 0, title))
        }
        return Fb2Result(title, author, chapters, extractCover(documentRoot, root))
    }

    private fun writeSection(root: File, section: Element, index: Int): PublicationChapter {
        val title = firstText(section, "title").replace(Regex("\\s+"), " ").trim().ifBlank { "Chapter ${index + 1}" }
        return writeElement(root, section, index, title)
    }

    private fun writeElement(root: File, element: Element, index: Int, title: String): PublicationChapter {
        val paragraphs = elements(element, "p").map { it.textContent.replace(Regex("\\s+"), " ").trim() }.filter(String::isNotBlank)
        val html = buildString {
            append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head><body><h1>")
            append(escape(title)).append("</h1>")
            paragraphs.forEach { append("<p>").append(escape(it)).append("</p>") }
            append("</body></html>")
        }
        val file = File(root, "chapter-$index.html").apply { writeText(html) }
        return PublicationChapter(title, file.name)
    }

    private fun extractCover(rootElement: Element, root: File): String? {
        val cover = elements(rootElement, "coverpage").firstOrNull()
        val image = cover?.let { elements(it, "image").firstOrNull() } ?: elements(rootElement, "image").firstOrNull() ?: return null
        val href = image.getAttributeNS("http://www.w3.org/1999/xlink", "href").ifBlank { image.getAttribute("l:href") }.removePrefix("#")
        if (href.isBlank()) return null
        val binary = elements(rootElement, "binary").firstOrNull { it.getAttribute("id") == href } ?: return null
        return runCatching {
            val media = binary.getAttribute("content-type")
            val extension = when { media.contains("png", true) -> "png"; media.contains("webp", true) -> "webp"; else -> "jpg" }
            File(root, "cover.$extension").apply { writeBytes(Base64.getMimeDecoder().decode(binary.textContent)) }.absolutePath
        }.getOrNull()
    }

    private fun elements(element: Element, localName: String): List<Element> {
        val namespaced = element.getElementsByTagNameNS("*", localName)
        if (namespaced.length > 0) return List(namespaced.length) { namespaced.item(it) as Element }
        val plain = element.getElementsByTagName(localName)
        return List(plain.length) { plain.item(it) as Element }
    }

    private fun childElements(element: Element): List<Element> = buildList {
        val children = element.childNodes
        for (index in 0 until children.length) (children.item(index) as? Element)?.let(::add)
    }

    private fun childSections(element: Element): List<Element> = childElements(element)
        .filter { it.nodeName.substringAfter(':').equals("section", ignoreCase = true) }

    private fun leafSections(section: Element): List<Element> {
        val children = childSections(section)
        return if (children.isEmpty()) listOf(section) else children.flatMap(::leafSections)
    }

    private fun firstText(element: Element, localName: String): String = elements(element, localName).firstOrNull()?.textContent?.trim().orEmpty()

    private fun secureFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { setExpandEntityReferences(false) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
    }

    private fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
