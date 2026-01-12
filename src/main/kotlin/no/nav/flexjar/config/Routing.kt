package no.nav.flexjar.config

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.nav.flexjar.config.auth.ClientAuthorizationPlugin
import no.nav.flexjar.config.auth.TeamAuthorizationPlugin
import no.nav.flexjar.routes.discoveryRoutes
import no.nav.flexjar.routes.feedbackRoutes
import no.nav.flexjar.routes.exportRoutes
import no.nav.flexjar.routes.filterRoutes
import no.nav.flexjar.routes.surveyFacetRoutes
import no.nav.flexjar.routes.statsRoutes
import no.nav.flexjar.routes.internalRoutes
import no.nav.flexjar.routes.submissionRoutes
import no.nav.flexjar.routes.teamsRoutes

fun Application.configureRouting() {
    install(io.ktor.server.resources.Resources)
    routing {
        // Health checks - no auth
        internalRoutes()
        
        // Public submission API (for widget) - uses TokenX/Azure from submitting app
        submissionRoutes()
        
        // Protected analytics API - requires Azure AD from frontend
        authenticate(AZURE_REALM) {
            // Validate that caller is the allowed flexjar-analytics frontend
            install(ClientAuthorizationPlugin) {
                allowedClientId = getFlexjarAnalyticsClientId()
            }
            
            // Enforce team authorization based on user's AD groups
            install(TeamAuthorizationPlugin)
            
            filterRoutes()
            feedbackRoutes()
            surveyFacetRoutes()
            statsRoutes()
            exportRoutes()
            discoveryRoutes()
            teamsRoutes()
        }
    }
}

