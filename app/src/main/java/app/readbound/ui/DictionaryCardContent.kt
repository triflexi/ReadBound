package app.readbound.ui

import app.readbound.data.DictionaryLookupRow
import app.readbound.settings.AnkiBackMode
import app.readbound.settings.AnkiPreferences
import java.util.Locale

internal data class DictionaryCardBlock(
    val id: String,
    val text: String,
)

internal data class DictionaryCardOption(
    val entry: DictionaryLookupRow,
    val blocks: List<DictionaryCardBlock>,
    val recommendedBlockId: String?,
) {
    val conciseText: String
        get() = blocks.firstOrNull { it.id == recommendedBlockId }?.text.orEmpty()
}

internal fun dictionaryCardOptions(results: List<DictionaryLookupRow>, front: String): List<DictionaryCardOption> {
    val entries = results.filter { it.kind == "term" }.ifEmpty { results }
    return entries.distinctBy { it.entryId }.map { entry ->
        val blocks = entry.definition.lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() && !isDictionaryAttribution(it) }
            .distinct()
            .mapIndexed { index, line -> DictionaryCardBlock("definition:$index", line) }
            .toList()
        DictionaryCardOption(
            entry = entry,
            blocks = blocks,
            recommendedBlockId = blocks.maxByOrNull { translationLineScore(it.text, front) }?.id,
        )
    }
}

internal fun defaultDictionaryBlockIds(option: DictionaryCardOption, mode: AnkiBackMode): Set<String> = when (mode) {
    AnkiBackMode.CONCISE -> setOfNotNull(option.recommendedBlockId)
    AnkiBackMode.ARTICLE -> option.blocks.mapTo(linkedSetOf()) { it.id }
}

internal fun dictionaryCardBack(
    option: DictionaryCardOption,
    selectedBlockIds: Set<String>,
    preferences: AnkiPreferences,
): String = buildList {
    if (preferences.includeReading && option.entry.reading.isNotBlank() &&
        !option.entry.reading.equals(option.entry.term, ignoreCase = true)
    ) add(option.entry.reading.trim())
    option.blocks.filter { it.id in selectedBlockIds }.forEach { add(it.text) }
    if (preferences.includeTags && option.entry.tags.isNotBlank()) add(option.entry.tags.trim())
    if (preferences.includeDictionaryName && option.entry.dictionaryTitle.isNotBlank()) add(option.entry.dictionaryTitle.trim())
}.filter(String::isNotBlank).distinct().joinToString("\n")

internal fun dictionaryCardBack(results: List<DictionaryLookupRow>): String {
    val option = dictionaryCardOptions(results, results.firstOrNull()?.term.orEmpty()).firstOrNull() ?: return ""
    return dictionaryCardBack(option, defaultDictionaryBlockIds(option, AnkiBackMode.CONCISE), AnkiPreferences())
}

private fun translationLineScore(line: String, front: String): Int {
    val normalized = line.trim()
    val lower = normalized.lowercase(Locale.ROOT)
    if (normalized.isBlank()) return Int.MIN_VALUE
    var score = 0
    val length = normalized.length
    val words = normalized.split(Regex("\\s+")).size
    if (length in 2..80) score += 45
    if (length in 2..35) score += 20
    if (words <= 8) score += 15
    if (',' in normalized || ';' in normalized || '、' in normalized || '，' in normalized) score += 12
    if (dominantScript(normalized) != null && dominantScript(normalized) != dominantScript(front)) score += 70
    if (normalized.equals(front, ignoreCase = true)) score -= 250
    if (normalized.matches(Regex("^\\d+\\s+.+"))) score -= 120
    if (normalized.startsWith("http://") || normalized.startsWith("https://")) score -= 250
    if (HEADING_WORDS.any { lower == it || lower.removeSuffix(":") == it }) score -= 220
    if (LOW_VALUE_PHRASES.any(lower::contains)) score -= 170
    if (length > 140) score -= 80
    if (words > 18) score -= 50
    return score
}

private fun dominantScript(value: String): Character.UnicodeScript? = value.asSequence()
    .filter(Char::isLetter)
    .map { Character.UnicodeScript.of(it.code) }
    .filter { it !in setOf(Character.UnicodeScript.COMMON, Character.UnicodeScript.INHERITED) }
    .groupingBy { it }
    .eachCount()
    .maxByOrNull { it.value }
    ?.key

private fun isDictionaryAttribution(line: String): Boolean {
    val normalized = line.trim()
    val lower = normalized.lowercase(Locale.ROOT)
    return normalized.all { it in "|·—- " } ||
        lower in setOf("wiktionary", "kaikki") ||
        lower.startsWith("© ") || lower.startsWith("source: http")
}

private val HEADING_WORDS = setOf(
    "etymology", "этимология", "examples", "example", "пример", "примеры",
    "synonyms", "synonym", "синоним", "синонимы", "antonyms", "antonym", "антоним", "антонимы",
    "pronunciation", "произношение", "usage notes", "usage", "употребление", "значение", "meanings",
    "derived terms", "related terms", "quotations", "источники", "source", "references",
)

private val LOW_VALUE_PHRASES = setOf(
    "происходит от", "происходит из", "заимствовано из", "от др.-", "из ??",
    "derived from", "borrowed from", "from old ", "from middle ",
    "[источник", "copyright", "all rights reserved",
)
