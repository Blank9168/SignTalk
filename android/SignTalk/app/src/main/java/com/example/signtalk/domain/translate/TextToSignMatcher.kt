package com.example.signtalk.domain.translate

import com.example.signtalk.domain.model.DictionaryEntry
import com.example.signtalk.domain.repository.DictionaryRepository

/** One token of a looked-up phrase: either a sign that was found, or a word with no matching sign. */
sealed class TranslationToken {
    data class Matched(val words: String, val entry: DictionaryEntry) : TranslationToken()
    /** [suggestions]: supported signs that look close to the typed word (spelling, meaning). */
    data class Unmatched(val word: String, val suggestions: List<DictionaryEntry> = emptyList()) : TranslationToken()
}

/**
 * Dictionary-based sign lookup for the "Text/Speech to Sign" screen: shows the dictionary
 * sign for each supported word or phrase, in order. It is NOT sentence-level FSL
 * translation (FSL has its own grammar and word order, which this does not model); the
 * screen says so.
 *
 * Each phrase is looked up, longest first (up to [MAX_PHRASE_WORDS] words), by:
 * 1. the sign's own label slug ("thank_you", "magandang_umaga", "isa"), then
 * 2. a synonym in English or Filipino ("thanks", "salamat", "one", "blue", "hindi ko alam").
 * Words with no sign get up to three suggestions of supported signs that are spelled
 * similarly ("thnk" -> Thank You) instead of a bare "not found".
 */
class TextToSignMatcher(private val repository: DictionaryRepository) {

    suspend fun match(rawText: String): List<TranslationToken> {
        val words = normalize(rawText)
        if (words.isEmpty()) return emptyList()

        val tokens = mutableListOf<TranslationToken>()
        var allEntries: List<DictionaryEntry>? = null
        var i = 0
        while (i < words.size) {
            val maxLen = minOf(MAX_PHRASE_WORDS, words.size - i)
            var matchedLen = 0
            var matchedEntry: DictionaryEntry? = null
            for (len in maxLen downTo 1) {
                val phraseWords = words.subList(i, i + len)
                val entry = repository.findByLabel(phraseWords.joinToString("_"))
                    ?: repository.findBySynonym(phraseWords.joinToString(" "))
                if (entry != null) {
                    matchedLen = len
                    matchedEntry = entry
                    break
                }
            }
            if (matchedEntry != null) {
                tokens += TranslationToken.Matched(
                    words = words.subList(i, i + matchedLen).joinToString(" "),
                    entry = matchedEntry
                )
                i += matchedLen
            } else {
                val entries = allEntries ?: repository.getAll().also { allEntries = it }
                tokens += TranslationToken.Unmatched(words[i], suggest(words[i], entries))
                i += 1
            }
        }
        return tokens
    }

    /** Up to three signs whose name, meaning or synonyms are within a small edit distance. */
    private fun suggest(word: String, entries: List<DictionaryEntry>): List<DictionaryEntry> {
        if (word.length < 3) return emptyList()
        val maxDistance = if (word.length <= 4) 1 else 2
        return entries.mapNotNull { entry ->
            val candidates = buildList {
                add(entry.label.replace('_', ' '))
                add(entry.displayName.lowercase())
                add(entry.meaningEn.lowercase())
                add(entry.meaningFil.lowercase())
                addAll(entry.synonyms)
                // also compare against each single word of a multi-word sign ("thank" in "thank you")
                addAll(entry.label.split('_'))
            }.map { normalize(it).joinToString(" ") }.filter { it.isNotBlank() }
            val best = candidates.minOfOrNull { levenshtein(word, it) } ?: return@mapNotNull null
            if (best <= maxDistance) entry to best else null
        }.sortedWith(compareBy({ it.second }, { it.first.displayName })).take(3).map { it.first }
    }

    /**
     * Lowercases, drops apostrophes (so "don't" -> "dont", matching the slug
     * convention used throughout DictionarySeed), strips other punctuation,
     * and splits on whitespace.
     */
    private fun normalize(text: String): List<String> =
        text.lowercase()
            .replace("'", "")
            .replace("’", "")
            .replace(Regex("[^a-z0-9ñ\\s]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }

    companion object {
        private const val MAX_PHRASE_WORDS = 4
    }
}
