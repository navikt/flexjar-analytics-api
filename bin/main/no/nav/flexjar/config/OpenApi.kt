package no.nav.flexjar.config

import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*

fun Application.configureOpenApi() {
    routing {
        // Swagger UI at /swagger
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml") {
            version = "5.17.14"
        }
        
        // OpenAPI spec at /openapi
        openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml")
    }
}
