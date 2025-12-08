package no.nav.flexjar.sensitive

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class SensitiveDataFilterTest : FunSpec({

    val filter = SensitiveDataFilter.DEFAULT

    context("Fødselsnummer detection") {
        test("should detect 11-digit fødselsnummer") {
            val text = "Min fødselsnummer er 12345678901"
            val matches = filter.detect(text)
            
            // Note: 11 digits also matches kontonummer pattern (4+2+5 digits)
            matches.size shouldBe 2
            matches.any { it.patternName == "fødselsnummer" && it.matchedValue == "12345678901" } shouldBe true
        }
        
        test("should redact fødselsnummer") {
            val text = "Bruker 12345678901 har sendt inn søknad"
            val result = filter.redact(text)
            
            result.redactedText shouldNotContain "12345678901"
            result.wasRedacted shouldBe true
        }
    }

    context("NAVident detection") {
        test("should detect NAVident") {
            val text = "Saksbehandler A123456 har behandlet saken"
            val matches = filter.detect(text)
            
            matches.size shouldBe 1
            matches[0].patternName shouldBe "navident"
        }
        
        test("should redact NAVident") {
            val result = filter.redact("Kontakt Z999999 for hjelp")
            
            result.redactedText shouldContain "[NAVIDENT FJERNET]"
            result.wasRedacted shouldBe true
        }
    }

    context("Email detection") {
        test("should detect email addresses") {
            val text = "Send mail til test.bruker@nav.no for mer info"
            val matches = filter.detect(text)
            
            matches.size shouldBe 1
            matches[0].patternName shouldBe "e-post"
            matches[0].matchedValue shouldBe "test.bruker@nav.no"
        }
        
        test("should detect multiple emails") {
            val text = "Kontakt ola@gmail.com eller kari@hotmail.com"
            val matches = filter.detect(text)
            
            matches.size shouldBe 2
        }
    }

    context("Phone number detection") {
        test("should detect 8-digit phone numbers") {
            val text = "Ring meg på 98765432"
            val matches = filter.detect(text)
            
            matches.size shouldBe 1
            matches[0].patternName shouldBe "telefonnummer"
        }
        
        test("should not match UUIDs or hex strings") {
            val text = "ID: a1234567-89ab-cdef-0123-456789abcdef"
            val matches = filter.detect(text)
            
            // Should not match parts of UUID as phone numbers
            matches.none { it.patternName == "telefonnummer" } shouldBe true
        }
    }

    context("Bank account detection") {
        test("should detect bank account with dots") {
            val text = "Overfør til konto 1234.56.12345"
            val matches = filter.detect(text)
            
            matches.any { it.patternName == "kontonummer" } shouldBe true
        }
        
        test("should detect bank account without dots") {
            val text = "Kontonummer: 12345612345"
            val matches = filter.detect(text)
            
            matches.any { it.patternName == "kontonummer" || it.patternName == "fødselsnummer" } shouldBe true
        }
    }

    context("Multiple patterns") {
        test("should detect multiple different patterns") {
            val text = "Bruker 12345678901 (test@nav.no) ringte 98765432"
            val result = filter.redact(text)
            
            // Note: 11 digits matches both fødselsnummer and kontonummer patterns
            result.matchCount shouldBe 4
            result.matchedPatterns shouldContainExactlyInAnyOrder setOf("fødselsnummer", "kontonummer", "e-post", "telefonnummer")
            result.redactedText shouldNotContain "12345678901"
            result.redactedText shouldNotContain "test@nav.no"
            result.redactedText shouldNotContain "98765432"
        }
    }

    context("No sensitive data") {
        test("should not modify text without sensitive data") {
            val text = "Dette er en vanlig tilbakemelding uten sensitiv info"
            val result = filter.redact(text)
            
            result.wasRedacted shouldBe false
            result.redactedText shouldBe text
            result.matchCount shouldBe 0
        }
    }

    context("Empty or null input") {
        test("should handle null input") {
            val result = filter.redact(null)
            
            result.wasRedacted shouldBe false
            result.redactedText shouldBe ""
        }
        
        test("should handle empty string") {
            val result = filter.redact("")
            
            result.wasRedacted shouldBe false
            result.redactedText shouldBe ""
        }
    }

    context("Map redaction") {
        test("should redact values in a map") {
            val data = mapOf(
                "name" to "Ola Nordmann",
                "email" to "ola@nav.no",
                "feedback" to "Bruker 12345678901 hadde problemer"
            )
            
            val (redacted, matches) = filter.redactMap(data)
            
            (redacted["email"] as String) shouldContain "[E-POST FJERNET]"
            // Feedback value is redacted (either fødselsnummer or kontonummer pattern matched)
            (redacted["feedback"] as String) shouldNotContain "12345678901"
            // Note: 11 digits matches both fødselsnummer and kontonummer patterns
            matches.size shouldBe 3
        }
    }
})
