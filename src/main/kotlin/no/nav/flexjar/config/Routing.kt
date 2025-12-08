package no.nav.flexjar.config

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.nav.flexjar.routes.feedbackRoutes
import no.nav.flexjar.routes.exportRoutes
import no.nav.flexjar.routes.statsRoutes
import no.nav.flexjar.routes.internalRoutes
import no.nav.flexjar.routes.submissionRoutes

fun Application.configureRouting() {
    routing {
        // Health checks - no auth
        internalRoutes()
        
        // Public submission API (for widget) - uses TokenX/Azure from submitting app
        submissionRoutes()
        
        // Protected analytics API - requires Azure AD from frontend
        authenticate(AZURE_REALM) {
            feedbackRoutes()
            statsRoutes()
            exportRoutes()
        }
    }
}
