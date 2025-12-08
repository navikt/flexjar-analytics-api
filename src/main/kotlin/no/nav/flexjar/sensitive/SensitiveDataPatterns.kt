package no.nav.flexjar.sensitive

/**
 * Sensitive data patterns for Norwegian personal information.
 * These patterns are used to detect and redact PII from feedback text.
 */
object SensitiveDataPatterns {
    
    /**
     * Norwegian national identity number (fødselsnummer) - 11 digits
     * Format: DDMMYYXXXXX
     */
    val FODSELSNUMMER = Regex("""\b\d{11}\b""")
    
    /**
     * NAV employee identifier (NAVident) - letter followed by 6 digits
     * Format: X123456
     */
    val NAVIDENT = Regex("""\b[a-zA-Z]\d{6}\b""")
    
    /**
     * Email addresses
     */
    val EMAIL = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")
    
    /**
     * IP addresses (IPv4)
     */
    val IP_ADDRESS = Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""")
    
    /**
     * Norwegian phone numbers (8 digits starting with 2-9)
     * Negative lookbehind/ahead to avoid matching hex strings or UUIDs
     */
    val PHONE_NUMBER = Regex("""(?<![0-9a-fA-F-])[2-9]\d{7}(?![0-9a-fA-F-])""")
    
    /**
     * Credit/debit card numbers (16 digits with optional separators)
     */
    val BANK_CARD = Regex("""(?<![0-9a-fA-F]-)\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b(?!-[0-9a-fA-F])""")
    
    /**
     * Possible Norwegian names (two or three capitalized words)
     * This has false positives but catches many real names
     */
    val POSSIBLE_NAME = Regex("""\b[A-ZÆØÅ][a-zæøå]{1,20}\s[A-ZÆØÅ][a-zæøå]{1,20}(?:\s[A-ZÆØÅ][a-zæøå]{1,20})?\b""")
    
    /**
     * Norwegian postal code followed by place name
     * Format: 1234 Placename
     */
    val POSSIBLE_ADDRESS = Regex("""\b\d{4}\s[A-ZÆØÅ][A-ZÆØÅa-zæøå]+(?:\s[A-ZÆØÅa-zæøå]+)*\b""")
    
    /**
     * "Hemmelig adresse" (secret/protected address marker)
     */
    val SECRET_ADDRESS = Regex("""hemmelig(?:%20|\s+)(?:20\s*%(?:%20|\s+))?adresse""", RegexOption.IGNORE_CASE)
    
    /**
     * Norwegian bank account number (11 digits with optional dots)
     * Format: 1234.56.12345 or 12345612345
     */
    val BANK_ACCOUNT = Regex("""\b\d{4}\.?\d{2}\.?\d{5}\b""")
    
    /**
     * Norwegian organization number (9 digits)
     */
    val ORG_NUMBER = Regex("""\b\d{9}\b""")
    
    /**
     * Norwegian license plate (2 letters + 5 digits)
     * Format: AB 12345 or AB12345
     */
    val LICENSE_PLATE = Regex("""\b[A-Z]{2}\s?\d{5}\b""")
    
    /**
     * Search query parameters in URLs
     */
    val SEARCH_QUERY = Regex("""[?&](?:q|query|search|k|ord)=[^&]+""")
    
    /**
     * All patterns with their labels for reporting
     */
    val ALL_PATTERNS: List<SensitivePattern> = listOf(
        SensitivePattern("fødselsnummer", FODSELSNUMMER, "[FØDSELSNUMMER FJERNET]"),
        SensitivePattern("navident", NAVIDENT, "[NAVIDENT FJERNET]"),
        SensitivePattern("e-post", EMAIL, "[E-POST FJERNET]"),
        SensitivePattern("ip-adresse", IP_ADDRESS, "[IP-ADRESSE FJERNET]"),
        SensitivePattern("telefonnummer", PHONE_NUMBER, "[TELEFON FJERNET]"),
        SensitivePattern("bankkort", BANK_CARD, "[KORTNUMMER FJERNET]"),
        SensitivePattern("mulig_navn", POSSIBLE_NAME, "[MULIG NAVN FJERNET]"),
        SensitivePattern("mulig_adresse", POSSIBLE_ADDRESS, "[MULIG ADRESSE FJERNET]"),
        SensitivePattern("hemmelig_adresse", SECRET_ADDRESS, "[HEMMELIG ADRESSE]"),
        SensitivePattern("kontonummer", BANK_ACCOUNT, "[KONTONUMMER FJERNET]"),
        SensitivePattern("organisasjonsnummer", ORG_NUMBER, "[ORGNUMMER FJERNET]"),
        SensitivePattern("bilnummer", LICENSE_PLATE, "[BILNUMMER FJERNET]"),
        SensitivePattern("søkeparameter", SEARCH_QUERY, "[SØKEPARAMETER FJERNET]"),
    )
    
    /**
     * High-confidence patterns that should always be filtered
     */
    val HIGH_CONFIDENCE_PATTERNS: List<SensitivePattern> = listOf(
        SensitivePattern("fødselsnummer", FODSELSNUMMER, "[FØDSELSNUMMER FJERNET]"),
        SensitivePattern("navident", NAVIDENT, "[NAVIDENT FJERNET]"),
        SensitivePattern("e-post", EMAIL, "[E-POST FJERNET]"),
        SensitivePattern("telefonnummer", PHONE_NUMBER, "[TELEFON FJERNET]"),
        SensitivePattern("bankkort", BANK_CARD, "[KORTNUMMER FJERNET]"),
        SensitivePattern("kontonummer", BANK_ACCOUNT, "[KONTONUMMER FJERNET]"),
        SensitivePattern("hemmelig_adresse", SECRET_ADDRESS, "[HEMMELIG ADRESSE]"),
    )
}

data class SensitivePattern(
    val name: String,
    val pattern: Regex,
    val replacement: String
)

data class SensitiveDataMatch(
    val patternName: String,
    val matchedValue: String,
    val startIndex: Int,
    val endIndex: Int
)

data class RedactionResult(
    val originalText: String,
    val redactedText: String,
    val matches: List<SensitiveDataMatch>,
    val wasRedacted: Boolean
) {
    val matchCount: Int get() = matches.size
    val matchedPatterns: Set<String> get() = matches.map { it.patternName }.toSet()
}
