package no.nav.flexjar.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import no.nav.flexjar.TestDatabase
import no.nav.flexjar.createTestClient
import no.nav.flexjar.insertTestFeedback
import no.nav.flexjar.testModule
import java.sql.Timestamp
import java.util.UUID

class FeedbackRoutesTest : FunSpec({

    fun insertTestFeedbackWithJson(
        id: String = UUID.randomUUID().toString(),
        team: String = "team-test",
        app: String = "app-test",
        feedbackJson: String,
    ) {
        TestDatabase.dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO feedback (id, opprettet, feedback_json, team, app, tags)
                VALUES (?, ?, ?::jsonb, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, id)
                stmt.setObject(2, Timestamp.from(java.time.Instant.now()))
                stmt.setString(3, feedbackJson)
                stmt.setString(4, team)
                stmt.setString(5, app)
                stmt.setString(6, null)
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    beforeSpec {
        TestDatabase.initialize()
    }

    beforeTest {
        TestDatabase.clearAllData()
    }

    test("GET /api/v1/intern/feedback requires authentication") {
        testApplication {
            application { testModule() }
            
            val response = client.get("/api/v1/intern/feedback")
            
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /api/v1/intern/feedback returns paginated results with auth") {
        testApplication {
            application { testModule() }
            
            // Insert test data
            insertTestFeedback(team = "team-test", app = "app-test")
            
            val response = createTestClient().get("/api/v1/intern/feedback?team=team-test") {
                header(HttpHeaders.Authorization, "Bearer test-token")
            }
            
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "content"
            response.bodyAsText() shouldContain "totalPages"
        }
    }

    test("GET /api/v1/intern/feedback/tags returns list of tags") {
        testApplication {
            application { testModule() }
            
            // Insert feedback with tags
            insertTestFeedback(tags = "bug,feature")
            
            val response = createTestClient().get("/api/v1/intern/feedback/tags") {
                header(HttpHeaders.Authorization, "Bearer test-token")
            }
            
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "bug"
            response.bodyAsText() shouldContain "feature"
        }
    }

    test("GET /api/v1/intern/feedback/teams returns teams and apps") {
        testApplication {
            application { testModule() }
            
            insertTestFeedback(team = "flex", app = "spinnsyn")
            
            val response = createTestClient().get("/api/v1/intern/feedback/teams") {
                header(HttpHeaders.Authorization, "Bearer test-token")
            }
            
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "teams"
        }
    }

    test("GET /api/v1/intern/feedback/{id} returns feedback by id") {
        testApplication {
            application { testModule() }
            
            val feedbackId = UUID.randomUUID().toString()
            insertTestFeedback(id = feedbackId, team = "flex")
            
            val response = createTestClient().get("/api/v1/intern/feedback/$feedbackId") {
                header(HttpHeaders.Authorization, "Bearer test-token")
            }
            
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain feedbackId
        }
    }

    test("GET /api/v1/intern/feedback/{id} returns 404 for unknown id") {
        testApplication {
            application { testModule() }
            
            val response = createTestClient().get("/api/v1/intern/feedback/unknown-id") {
                header(HttpHeaders.Authorization, "Bearer test-token")
            }
            
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("DELETE /api/v1/intern/feedback/{id} returns 404 for unknown id") {
        testApplication {
            application { testModule() }
            
            val response = createTestClient().delete("/api/v1/intern/feedback/unknown-id") {
                header(HttpHeaders.Authorization, "Bearer test-token")
            }
            
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /api/v1/intern/feedback/{id}/tags adds tag to feedback") {
        testApplication {
            application { testModule() }
            
            val feedbackId = UUID.randomUUID().toString()
            insertTestFeedback(id = feedbackId)
            
            val response = createTestClient().post("/api/v1/intern/feedback/$feedbackId/tags") {
                header(HttpHeaders.Authorization, "Bearer test-token")
                contentType(ContentType.Application.Json)
                setBody("""{"tag": "test-tag"}""")
            }
            
            response.status shouldBe HttpStatusCode.Created
        }
    }

    test("POST /api/v1/intern/feedback/{id}/tags returns 404 for unknown id") {
        testApplication {
            application { testModule() }
            
            val response = createTestClient().post("/api/v1/intern/feedback/unknown-id/tags") {
                header(HttpHeaders.Authorization, "Bearer test-token")
                contentType(ContentType.Application.Json)
                setBody("""{"tag": "test-tag"}""")
            }
            
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/intern/feedback/context-tags returns tag values with counts") {
        testApplication {
            application { testModule() }

            val feedbackId = "survey-ctx-1"

            // 2x Ja, 1x Nei
            insertTestFeedbackWithJson(
                team = "team-test",
                app = "app-test",
                feedbackJson = """
                    {
                      "feedbackId": "$feedbackId",
                      "context": {"tags": {"harAktivSykmelding": "Ja"}},
                      "answers": [
                        {"fieldId": "svar", "fieldType": "RATING", "question": {"label": "Hvordan?"}, "value": {"type": "rating", "rating": 4}}
                      ]
                    }
                """.trimIndent(),
            )
            insertTestFeedbackWithJson(
                team = "team-test",
                app = "app-test",
                feedbackJson = """
                    {
                      "feedbackId": "$feedbackId",
                      "context": {"tags": {"harAktivSykmelding": "Ja"}},
                      "answers": [
                        {"fieldId": "svar", "fieldType": "RATING", "question": {"label": "Hvordan?"}, "value": {"type": "rating", "rating": 5}}
                      ]
                    }
                """.trimIndent(),
            )
            insertTestFeedbackWithJson(
                team = "team-test",
                app = "app-test",
                feedbackJson = """
                    {
                      "feedbackId": "$feedbackId",
                      "context": {"tags": {"harAktivSykmelding": "Nei"}},
                      "answers": [
                        {"fieldId": "svar", "fieldType": "RATING", "question": {"label": "Hvordan?"}, "value": {"type": "rating", "rating": 3}}
                      ]
                    }
                """.trimIndent(),
            )

            val response = createTestClient().get(
                "/api/v1/intern/feedback/context-tags?team=team-test&feedbackId=$feedbackId&maxCardinality=10"
            ) {
                header(HttpHeaders.Authorization, "Bearer test-token")
            }

            response.status shouldBe HttpStatusCode.OK

            val json = Json { ignoreUnknownKeys = true }
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject

            body["feedbackId"]?.jsonPrimitive?.content shouldBe feedbackId
            val contextTags = body["contextTags"].shouldNotBeNull().jsonObject
            contextTags shouldContainKey "harAktivSykmelding"

            val values = contextTags["harAktivSykmelding"].shouldNotBeNull()
            val valuesText = values.toString()
            valuesText shouldContain "\"value\":\"Ja\""
            valuesText shouldContain "\"count\":2"
            valuesText shouldContain "\"value\":\"Nei\""
            valuesText shouldContain "\"count\":1"
        }
    }

    test("GET /api/v1/intern/feedback/{id} parses nested context.viewport") {
        testApplication {
            application { testModule() }

            val rowId = UUID.randomUUID().toString()
            val feedbackId = "survey-viewport-1"

            insertTestFeedbackWithJson(
                id = rowId,
                team = "team-test",
                app = "app-test",
                feedbackJson = """
                    {
                      "feedbackId": "$feedbackId",
                      "context": {"viewport": {"width": 777, "height": 555}},
                      "answers": [
                        {"fieldId": "svar", "fieldType": "RATING", "question": {"label": "Hvordan?"}, "value": {"type": "rating", "rating": 4}}
                      ]
                    }
                """.trimIndent(),
            )

            val response = createTestClient().get("/api/v1/intern/feedback/$rowId") {
                header(HttpHeaders.Authorization, "Bearer test-token")
            }

            response.status shouldBe HttpStatusCode.OK

            val json = Json { ignoreUnknownKeys = true }
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val context = body["context"].shouldNotBeNull().jsonObject

            context["viewportWidth"].shouldNotBeNull().jsonPrimitive.int shouldBe 777
            context["viewportHeight"].shouldNotBeNull().jsonPrimitive.int shouldBe 555
        }
    }
})
