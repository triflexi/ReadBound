package app.readbound.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class PublicationManifestTest {
    @Test fun roundTripPreservesOrderAndUnicode() {
        val source = PublicationManifest(listOf(PublicationChapter("Глава 1", "chapter-1.html"), PublicationChapter("Two", "two.xhtml")))
        assertEquals(source, PublicationManifest.fromJson(source.toJson()))
    }
}
