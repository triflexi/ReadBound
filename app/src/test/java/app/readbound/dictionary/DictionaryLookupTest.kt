package app.readbound.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryLookupTest {
    @Test
    fun normalizesCaseWhitespaceAndEdgePunctuation() {
        assertEquals("don't stop", DictionaryRepository.normalizeTerm("  “Don't   stop!”  "))
    }

    @Test
    fun phraseLookupPrefersWholeSelectionThenWords() {
        assertEquals(
            listOf("take off", "take", "off"),
            DictionaryRepository.lookupCandidates("Take off"),
        )
    }
}
