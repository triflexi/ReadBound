package app.readbound.anki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiFieldsTest {
    @Test fun mapsFrontAndBackIntoSeparateFields() {
        assertEquals(listOf("front", "back"), buildAnkiFields("front", "back", 2, 0, 1))
    }

    @Test fun keepsUnusedFieldsEmpty() {
        assertEquals(listOf("", "back", "front"), buildAnkiFields("front", "back", 3, 2, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOneFieldNoteType() {
        buildAnkiFields("front", "back", 1, 0, 0)
    }

    @Test fun acceptsOnlyOneTemplateModelsToPreventDuplicateCards() {
        assertTrue(isEligibleSingleCardModel(fieldCount = 2, cardTemplateCount = 1))
        assertFalse(isEligibleSingleCardModel(fieldCount = 2, cardTemplateCount = 2))
    }
}
