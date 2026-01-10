package no.nav.flexjar.repository

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.flexjar.TestDatabase

class FeedbackSecurityTest : DescribeSpec({

    beforeSpec {
        TestDatabase.initialize()
    }
    
    beforeTest {
        TestDatabase.clearAllData()
    }

    describe("escapeLikePattern") {
        it("should escape percent sign") {
            val input = "100% complete"
            val escaped = input.escapeLikePattern()
            
            escaped shouldBe "100\\% complete"
        }
        
        it("should escape underscore") {
            val input = "test_value"
            val escaped = input.escapeLikePattern()
            
            escaped shouldBe "test\\_value"
        }
        
        it("should escape backslash") {
            val input = "path\\to\\file"
            val escaped = input.escapeLikePattern()
            
            escaped shouldBe "path\\\\to\\\\file"
        }
        
        it("should escape multiple special characters") {
            val input = "100% of _users have \\ in path"
            val escaped = input.escapeLikePattern()
            
            escaped shouldBe "100\\% of \\_users have \\\\ in path"
            // Check that raw % is escaped to \%
            escaped shouldContain "\\%"
        }
        
        it("should not modify text without special characters") {
            val input = "normal text without special chars"
            val escaped = input.escapeLikePattern()
            
            escaped shouldBe input
        }
        
        it("should handle empty string") {
            val input = ""
            val escaped = input.escapeLikePattern()
            
            escaped shouldBe ""
        }
        
        it("should handle SQL injection attempt with LIKE wildcards") {
            val malicious = "' OR tags LIKE '%admin%' --"
            val escaped = malicious.escapeLikePattern()
            
            escaped shouldContain "\\%"
            escaped shouldNotContain "%admin%"
        }
        
        it("should escape consecutive special characters") {
            val input = "%%__\\\\"
            val escaped = input.escapeLikePattern()
            
            escaped shouldBe "\\%\\%\\_\\_\\\\\\\\"
        }
    }

    describe("redactFeedbackJson - integration") {
        val repository = FeedbackRepository()
        
        it("should redact fødselsnummer from answers") {
            val feedbackJson = """
                {
                    "surveyId": "test-survey",
                    "answers": [
                        {
                            "fieldId": "feedback",
                            "value": {
                                "type": "text",
                                "text": "Min fødselsnummer er 12345678901 og jeg har problemer"
                            }
                        }
                    ]
                }
            """.trimIndent()
            
            val saved = repository.save(feedbackJson, "team-test", "test-app")
            val retrieved = repository.findRawById(saved)
            
            retrieved?.feedbackJson shouldNotContain "12345678901"
        }
        
        it("should redact email from text answers") {
            val feedbackJson = """
                {
                    "surveyId": "test-survey",
                    "answers": [
                        {
                            "fieldId": "feedback",
                            "value": {
                                "type": "text",
                                "text": "Kontakt meg på test.user@example.com"
                            }
                        }
                    ]
                }
            """.trimIndent()
            
            val saved = repository.save(feedbackJson, "team-test", "test-app")
            val retrieved = repository.findRawById(saved)
            
            retrieved?.feedbackJson shouldNotContain "test.user@example.com"
            retrieved?.feedbackJson shouldContain "[E-POST FJERNET]"
        }
        
        it("should redact phone numbers") {
            val feedbackJson = """
                {
                    "surveyId": "test-survey",
                    "answers": [
                        {
                            "fieldId": "comment",
                            "value": {
                                "type": "text",
                                "text": "Ring meg på 98765432 for mer info"
                            }
                        }
                    ]
                }
            """.trimIndent()
            
            val saved = repository.save(feedbackJson, "team-test", "test-app")
            val retrieved = repository.findRawById(saved)
            
            retrieved?.feedbackJson shouldNotContain "98765432"
        }
        
        it("should preserve non-text answers") {
            val feedbackJson = """
                {
                    "surveyId": "test-survey",
                    "answers": [
                        {
                            "fieldId": "rating",
                            "value": {
                                "type": "rating",
                                "rating": 5
                            }
                        }
                    ]
                }
            """.trimIndent()
            
            val saved = repository.save(feedbackJson, "team-test", "test-app")
            val retrieved = repository.findRawById(saved)
            
            retrieved?.feedbackJson shouldContain "\"rating\":5"
        }
        
        it("should handle JSON without answers gracefully") {
            val feedbackJson = """{"surveyId": "simple"}"""
            
            val saved = repository.save(feedbackJson, "team-test", "test-app")
            val retrieved = repository.findRawById(saved)
            
            retrieved?.feedbackJson shouldContain "simple"
        }
    }
})
