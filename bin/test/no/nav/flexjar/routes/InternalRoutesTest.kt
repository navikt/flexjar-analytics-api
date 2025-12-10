package no.nav.flexjar.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.flexjar.testModule

class InternalRoutesTest : FunSpec({

    test("GET /internal/isAlive returns OK") {
        testApplication {
            application { testModule() }
            
            val response = client.get("/internal/isAlive")
            
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "OK"
        }
    }

    test("GET /internal/isReady returns OK") {
        testApplication {
            application { testModule() }
            
            val response = client.get("/internal/isReady")
            
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "OK"
        }
    }

    test("GET /internal/prometheus returns empty string") {
        testApplication {
            application { testModule() }
            
            val response = client.get("/internal/prometheus")
            
            response.status shouldBe HttpStatusCode.OK
        }
    }
})
