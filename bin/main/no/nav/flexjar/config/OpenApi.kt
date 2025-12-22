package no.nav.flexjar.config

import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("OpenApi")

fun Application.configureOpenApi() {
    // Check if OpenAPI spec file exists in resources
    val specExists = object {}.javaClass.getResource("/openapi/documentation.yaml") != null
    
    if (!specExists) {
        log.warn("OpenAPI spec not found, skipping Swagger UI setup")
        return
    }
    
    routing {
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml") {
            version = "5.17.14"
        }
    }
}
