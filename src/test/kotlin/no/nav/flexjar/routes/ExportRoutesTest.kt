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

class ExportRoutesTest : FunSpec({

    beforeSpec {
        TestDatabase.initialize()
    }

    beforeTest {
        TestDatabase.clearAllData()
    }

    test("GET /api/v1/intern/export requires authentication") {
        testApplication {
            application { testModule() }
            
            val response = client.get("/api/v1/intern/export")
            
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /api/v1/intern/export returns CSV by default") {
        testApplication {
            application { testModule() }
            
            insertTestFeedback(team = "flex", app = "spinnsyn", text = "Great app!")
            
            val response = createTestClient().get("/api/v1/intern/export?team=flex") {
                header(HttpHeaders.Authorization, "Bearer test-token")
            }
            
            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.match(ContentType.Text.CSV) shouldBe true
            response.bodyAsText() shouldContain "id,submittedAt"
        }
    }

    test("GET /api/v1/intern/export?format=JSON returns JSON") {
        testApplication {
            application { testModule() }
            
            insertTestFeedback(team = "flex", text = "Nice feature")
            
            val response = createTestClient().get("/api/v1/intern/export?team=flex&format=JSON") {
                header(HttpHeaders.Authorization, "Bearer test-token")
            }
            
            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.match(ContentType.Application.Json) shouldBe true
        }
    }

    test("GET /api/v1/intern/export?format=EXCEL returns Excel file") {
        testApplication {
            application { testModule() }
            
            insertTestFeedback(team = "flex")
            
            val response = createTestClient().get("/api/v1/intern/export?team=flex&format=EXCEL") {
                header(HttpHeaders.Authorization, "Bearer test-token")
            }
            
            response.status shouldBe HttpStatusCode.OK
            response.headers[HttpHeaders.ContentType] shouldContain "application/vnd.openxmlformats-officedocument"
        }
    }
})
