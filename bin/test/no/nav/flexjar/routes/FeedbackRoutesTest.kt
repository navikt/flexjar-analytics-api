package no.nav.flexjar.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.flexjar.TestDatabase
import no.nav.flexjar.createTestClient
import no.nav.flexjar.insertTestFeedback
import no.nav.flexjar.testModule
import java.util.UUID

class FeedbackRoutesTest : FunSpec({

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
})
