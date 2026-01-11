package no.nav.flexjar.service

/**
 * Utility for processing and stemming Norwegian text for analytics.
 * Consolidates text processing logic used across different services and repositories.
 */
object TextProcessor {

    /** Norwegian stop words to filter from word clouds and analysis */
    val STOP_WORDS = setOf(
        "og", "i", "jeg", "det", "at", "en", "et", "den", "til", "er", "som",
        "på", "de", "med", "han", "av", "ikke", "der", "så", "var", "meg",
        "seg", "men", "ett", "har", "om", "vi", "min", "mitt", "ha", "hadde",
        "hun", "nå", "over", "da", "ved", "fra", "du", "ut", "sin", "dem",
        "oss", "opp", "man", "kan", "hans", "hvor", "eller", "hva", "skal",
        "selv", "sjøl", "her", "alle", "vil", "bli", "ble", "blitt", "kunne",
        "inn", "når", "være", "kom", "noe", "ville", "dere", "deres",
        "kun", "ja", "etter", "ned", "skulle", "denne", "for", "deg", "to",
        "måtte", "få", "fikk", "fått", "gjøre", "gjort", "gjør"
    )

    /**
     * Extract words from text, filtering stop words and short words.
     */
    fun extractWords(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-zæøåA-ZÆØÅ0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in STOP_WORDS }
    }

    /**
     * Simple Norwegian stemmer that removes common suffixes.
     * Handles definite articles, plurals, and verb forms.
     */
    fun stemNorwegian(word: String): String {
        var stem = word.lowercase().trim()

        // Order matters: check longer suffixes first
        val suffixes = listOf(
            // Definite plural
            "ene", "ane",
            // Definite singular
            "en", "et", "a",
            // Indefinite plural
            "er", "ar",
            // Verb past tense
            "te", "de",
            // Adjective endings
            "ere", "est"
        )

        for (suffix in suffixes) {
            if (stem.length > suffix.length + 2 && stem.endsWith(suffix)) {
                return stem.dropLast(suffix.length)
            }
        }

        return stem
    }
}
