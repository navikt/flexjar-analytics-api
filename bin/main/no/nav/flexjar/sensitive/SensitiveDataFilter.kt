package no.nav.flexjar.sensitive

import org.slf4j.LoggerFactory

/**
 * Service for detecting and redacting sensitive data from text.
 */
class SensitiveDataFilter(
    private val patterns: List<SensitivePattern> = SensitiveDataPatterns.HIGH_CONFIDENCE_PATTERNS,
    private val logMatches: Boolean = true
) {
    private val log = LoggerFactory.getLogger(SensitiveDataFilter::class.java)
    
    /**
     * Detect sensitive data in text without modifying it.
     * Returns list of matches found.
     */
    fun detect(text: String?): List<SensitiveDataMatch> {
        if (text.isNullOrBlank()) return emptyList()
        
        val matches = mutableListOf<SensitiveDataMatch>()
        
        for (pattern in patterns) {
            pattern.pattern.findAll(text).forEach { matchResult ->
                matches.add(
                    SensitiveDataMatch(
                        patternName = pattern.name,
                        matchedValue = matchResult.value,
                        startIndex = matchResult.range.first,
                        endIndex = matchResult.range.last + 1
                    )
                )
            }
        }
        
        return matches.sortedBy { it.startIndex }
    }
    
    /**
     * Redact sensitive data from text.
     * Returns the redacted text and information about what was redacted.
     */
    fun redact(text: String?): RedactionResult {
        if (text.isNullOrBlank()) {
            return RedactionResult(
                originalText = text ?: "",
                redactedText = text ?: "",
                matches = emptyList(),
                wasRedacted = false
            )
        }
        
        val matches = detect(text)
        
        if (matches.isEmpty()) {
            return RedactionResult(
                originalText = text,
                redactedText = text,
                matches = emptyList(),
                wasRedacted = false
            )
        }
        
        var redactedText: String = text
        
        // Apply replacements in reverse order to preserve indices
        val sortedMatches = matches.sortedByDescending { it.startIndex }
        
        for (match in sortedMatches) {
            val pattern = patterns.find { it.name == match.patternName }
            val replacement = pattern?.replacement ?: "[SENSITIVT DATA FJERNET]"
            
            redactedText = redactedText.replaceRange(
                match.startIndex,
                match.endIndex,
                replacement
            )
        }
        
        if (logMatches) {
            val patternCounts = matches.groupBy { it.patternName }.mapValues { it.value.size }
            log.info("Redacted sensitive data: $patternCounts")
        }
        
        return RedactionResult(
            originalText = text,
            redactedText = redactedText,
            matches = matches,
            wasRedacted = true
        )
    }
    
    /**
     * Check if text contains any sensitive data.
     */
    fun containsSensitiveData(text: String?): Boolean {
        return detect(text).isNotEmpty()
    }
    
    /**
     * Redact sensitive data from a map of values (like feedback JSON).
     * Only processes string values.
     */
    fun redactMap(data: Map<String, Any?>): Pair<Map<String, Any?>, List<SensitiveDataMatch>> {
        val allMatches = mutableListOf<SensitiveDataMatch>()
        
        val redactedData = data.mapValues { (key, value) ->
            when (value) {
                is String -> {
                    val result = redact(value)
                    allMatches.addAll(result.matches.map { 
                        it.copy(patternName = "$key.${it.patternName}") 
                    })
                    result.redactedText
                }
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val nestedMap = value as Map<String, Any?>
                    val (redacted, matches) = redactMap(nestedMap)
                    allMatches.addAll(matches)
                    redacted
                }
                else -> value
            }
        }
        
        return redactedData to allMatches
    }
    
    companion object {
        /**
         * Default instance with high-confidence patterns only.
         * Use this for production to minimize false positives.
         */
        val DEFAULT = SensitiveDataFilter(
            patterns = SensitiveDataPatterns.HIGH_CONFIDENCE_PATTERNS
        )
        
        /**
         * Strict instance with all patterns including lower-confidence ones.
         * Use this when maximum protection is needed, accepting more false positives.
         */
        val STRICT = SensitiveDataFilter(
            patterns = SensitiveDataPatterns.ALL_PATTERNS
        )
    }
}
