package app.readbound.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Fb2ConverterTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun convertsNamespacedFb2IntoReadableChapters() {
        val source = temporaryFolder.newFile("book.fb2").apply { writeText(SAMPLE_FB2) }
        val output = temporaryFolder.newFolder("publication")

        val result = Fb2Converter.convert(source, output, "Fallback")

        assertEquals("Тестовая книга", result.title)
        assertEquals("Иван Иванов", result.author)
        assertEquals(listOf("Первая глава", "Вторая глава"), result.chapters.map { it.title })
        val firstChapter = output.resolve(result.chapters.first().href).readText()
        assertTrue(firstChapter.contains("Первый абзац"))
        assertTrue(firstChapter.contains("name=\"viewport\""))
    }

    @Test fun exposesNestedFb2SectionsAsChapters() {
        val source = temporaryFolder.newFile("nested.fb2").apply { writeText(NESTED_FB2) }
        val output = temporaryFolder.newFolder("nested-publication")

        val result = Fb2Converter.convert(source, output, "Fallback")

        assertEquals(listOf("Глава один", "Глава два"), result.chapters.map { it.title })
        assertTrue(output.resolve(result.chapters[1].href).readText().contains("Текст второй главы"))
    }

    companion object {
        private val SAMPLE_FB2 = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description><title-info><genre>prose</genre><author><first-name>Иван</first-name><last-name>Иванов</last-name></author><book-title>Тестовая книга</book-title></title-info></description>
              <body>
                <section><title><p>Первая глава</p></title><p>Первый абзац.</p></section>
                <section><title><p>Вторая глава</p></title><p>Второй абзац.</p></section>
              </body>
            </FictionBook>
        """.trimIndent()

        private val NESTED_FB2 = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description><title-info><book-title>Вложенная книга</book-title></title-info></description>
              <body>
                <section>
                  <title><p>Часть первая</p></title>
                  <section><title><p>Глава один</p></title><p>Текст первой главы.</p></section>
                  <section><title><p>Глава два</p></title><p>Текст второй главы.</p></section>
                </section>
              </body>
            </FictionBook>
        """.trimIndent()
    }
}
