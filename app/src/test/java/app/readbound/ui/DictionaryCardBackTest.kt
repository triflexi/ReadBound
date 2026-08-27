package app.readbound.ui

import app.readbound.data.DictionaryLookupRow
import app.readbound.settings.AnkiBackMode
import app.readbound.settings.AnkiPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryCardBackTest {
    @Test
    fun usesBestTermDefinitionAsEditableBackSide() {
        val result = DictionaryLookupRow(
            entryId = 1,
            dictionaryId = "dictionary",
            dictionaryTitle = "English–Russian",
            term = "book",
            normalizedTerm = "book",
            reading = "bʊk",
            definition = "книга\nWiktionary\n|\nKaikki",
            tags = "noun",
            kind = "term",
            score = 10,
        )

        assertEquals("bʊk\nкнига", dictionaryCardBack(listOf(result)))
    }

    @Test
    fun conciseModeSkipsEtymologyExamplesAndAttribution() {
        val result = lookup(
            term = "here",
            definition = """
                Этимология
                Происходит от др.-англ. hēr.
                здесь, тут
                1 пример
                For the three months of location filming here in Boston.
                Синонимы
                at that place
                Wiktionary
                |
                Kaikki
            """.trimIndent(),
        )

        assertEquals("здесь, тут", dictionaryCardBack(listOf(result)))
    }

    @Test
    fun articleAndOptionalFieldsAreExplicitlyComposable() {
        val result = lookup(term = "book", reading = "bʊk", tags = "noun", definition = "книга\nтом")
        val option = dictionaryCardOptions(listOf(result), "book").single()
        val preferences = AnkiPreferences(
            backMode = AnkiBackMode.ARTICLE,
            includeReading = true,
            includeTags = true,
            includeDictionaryName = true,
        )

        assertEquals(
            "bʊk\nкнига\nтом\nnoun\nEnglish–Russian",
            dictionaryCardBack(option, defaultDictionaryBlockIds(option, preferences.backMode), preferences),
        )
    }

    private fun lookup(
        term: String,
        reading: String = "",
        definition: String,
        tags: String = "adv",
    ) = DictionaryLookupRow(
        entryId = 1,
        dictionaryId = "dictionary",
        dictionaryTitle = "English–Russian",
        term = term,
        normalizedTerm = term,
        reading = reading,
        definition = definition,
        tags = tags,
        kind = "term",
        score = 10,
    )
}
