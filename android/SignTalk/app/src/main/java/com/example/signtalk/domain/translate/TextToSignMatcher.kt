package com.example.signtalk.domain.translate

import com.example.signtalk.domain.model.DictionaryEntry
import com.example.signtalk.domain.repository.DictionaryRepository

/** One token of a translated phrase: either a sign that was found, or a word with no matching sign. */
sealed class TranslationToken {
    data class Matched(val words: String, val entry: DictionaryEntry) : TranslationToken()
    data class Unmatched(val word: String) : TranslationToken()
}

/**
 * Converts free-form typed or spoken text into an ordered sequence of
 * dictionary signs, for the "Text/Speech to Sign" screen -- the reverse
 * direction of the camera recognizer, so a hearing person can be understood
 * by a deaf/mute one without either party needing to already know sign
 * language.
 *
 * Matching works directly off [DictionaryEntry.label], which is already a
 * language-specific slug (e.g. "good_morning" for the ASL sign vs
 * "magandang_umaga" for the FSL one, "thank_you", "dont_understand", "isa",
 * "asul"...) -- so typing/speaking in either English or Filipino naturally
 * resolves to that language's own sign, with no separate language toggle
 * needed. Matching is greedy-longest-phrase-first (up to [MAX_PHRASE_WORDS]
 * words) so multi-word signs like "Nice To Meet You" or "Don't Understand"
 * are recognized as one sign rather than as several unmatched single words.
 */
class TextToSignMatcher(private val repository: DictionaryRepository) {

    suspend fun match(rawText: String): List<TranslationToken> {
        val words = normalize(rawText)
        if (words.isEmpty()) return emptyList()

        val tokens = mutableListOf<TranslationToken>()
        var i = 0
        while (i < words.size) {
            val maxLen = minOf(MAX_PHRASE_WORDS, words.size - i)
            var matchedLen = 0
            var matchedEntry: DictionaryEntry? = null
            for (len in maxLen downTo 1) {
                val candidateSlug = words.subList(i, i + len).joinToString("_")
                val entry = repository.findByLabel(candidateSlug)
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
                tokens += TranslationToken.Unmatched(words[i])
                i += 1
            }
        }
        return tokens
    }

    /**
     * Lowercases, drops apostrophes (so "don't" -> "dont", matching the slug
     * convention used throughout DictionarySeed), strips other punctuation,
     * and splits on whitespace.
     */
    private fun normalize(text: String): List<String> =
        text.lowercase()
            .replace("'", "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    companion object {
        private const val MAX_PHRASE_WORDS = 4
    }
}
