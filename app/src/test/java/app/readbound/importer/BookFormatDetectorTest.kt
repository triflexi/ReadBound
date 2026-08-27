package app.readbound.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class BookFormatDetectorTest {
    @Test fun detectsFb2FromXmlContentWhenProviderHidesExtension() {
        val header = "<?xml version=\"1.0\"?><FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\">".toByteArray()
        assertEquals("fb2", BookFormatDetector.detect("download", "application/xml", header))
    }

    @Test fun keepsExplicitSupportedExtension() {
        assertEquals("fb2", BookFormatDetector.detect("Книга.FB2", "application/octet-stream", byteArrayOf()))
    }
}
