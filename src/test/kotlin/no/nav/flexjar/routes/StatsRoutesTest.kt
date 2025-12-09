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

class StatsRoutesTest : FunSpec({

    beforeTest {
        TestDatabase.clearAllData()
    }

    test("GET /api/v1/intern/stats requires authentication") {
        testApplication {
            application { testModule() }
            
            val response = client.get("/api/v1/intern/stats")
            
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /api/v1/intern/stats returns statistics with auth") {
        testApplication {
            application { testModule() }
            
            // Insert test data
            insertTestFeedback(team = "flex", app = "spinnsyn", svar = 4)
            insertTestFeedback(team = "flex", app = "spinnsyn", svar = 5)
            
            val response = createTestClient().get("/api/v1/intern/stats?team=flex") {
                header(HttpHeaders.Authorization, "Bearer test-token")
            }
            
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "totalCount"
            response.bodyAsText() shouldContain "byRating"
        }
    }

    test("GET /api/v1/intern/stats/ratings returns rating distribution") {
        testApplication {
            application { testModule() }
            
            insertTestFeedback(team = "flex", svar = 3)
            insertTestFeedback(team = "flex", svar = 4)
            insertTestFeedback(team = "flex", svar = 5)
            
            val response = createTestClient().get("/api/v1/intern/stats/ratings?team=flex") {
                header(HttpHeaders.Authorization, "Bearer test-token")
            }
            
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "distribution"
            response.bodyAsText() shouldContain "average"
        }
    }

    test("GET /api/v1/intern/stats/timeline returns timeline data") {
        testApplication {
            application { testModule() }
            
            insertTestFeedback(team = "flex")
            
            val response = createTestClient().get("/api/v1/intern/stats/timeline?team=flex") {
                header(HttpHeaders.Authorization, "Bearer test-token")
            }
            
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "data"
        }
    }
})
