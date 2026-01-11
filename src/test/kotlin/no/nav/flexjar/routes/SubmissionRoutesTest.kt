package no.nav.flexjar.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.flexjar.createTestClient
import no.nav.flexjar.testModule

class SubmissionRoutesTest : FunSpec({

    test("should accept canonical schemaVersion=1 submissions") {
        testApplication {
            application { testModule() }
            val client = createTestClient()

            val response = client.post("/api/v1/feedback") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                                            "schemaVersion": 1,
                                            "surveyId": "dp-feedback",
                                            "surveyType": "rating",
                                            "startedAt": "2026-01-10T12:00:00Z",
                                            "submittedAt": "2026-01-10T12:00:12Z",
                                            "timeToCompleteMs": 12000,
                                            "context": {
                                                "pathname": "/soknad",
                                                "deviceType": "mobile",
                                                "viewport": { "width": 390, "height": 844 },
                                                "tags": { "rolle": "privatperson", "uke": 3, "harAktivSykmelding": true }
                      },
                                            "answers": [
                        {
                                                    "fieldId": "rating",
                                                    "fieldType": "RATING",
                                                    "question": { "label": "Hvor fornøyd er du?", "description": null, "options": null },
                                                    "value": { "type": "rating", "rating": 2, "ratingVariant": "emoji", "ratingScale": 5 }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            }

            response.status shouldBe HttpStatusCode.Created

            val parsed = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            parsed["id"]?.jsonPrimitive?.content?.isNotBlank() shouldBe true
        }
    }

    test("should reject invalid payload") {
        testApplication {
            application { testModule() }
            val client = createTestClient()

            val response = client.post("/api/v1/feedback") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "surveyId": "missing-required-fields"
                    }
                    """.trimIndent()
                )
            }

            response.status shouldBe HttpStatusCode.BadRequest
            val message = Json.parseToJsonElement(response.bodyAsText()).jsonObject["message"]?.jsonPrimitive?.content
            (message?.isNullOrBlank() == false) shouldBe true
        }
    }

    test("should reject empty answers") {
        testApplication {
            application { testModule() }
            val client = createTestClient()

            val response = client.post("/api/v1/feedback") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                                            "schemaVersion": 1,
                                            "surveyId": "dp-feedback",
                                            "surveyType": "rating",
                                            "submittedAt": "2026-01-10T12:00:12Z",
                                            "answers": []
                    }
                    """.trimIndent()
                )
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
