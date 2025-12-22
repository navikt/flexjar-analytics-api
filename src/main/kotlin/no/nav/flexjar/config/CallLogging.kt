package no.nav.flexjar.config

import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import io.ktor.http.HttpHeaders
import org.slf4j.event.Level

fun Application.configureCallLogging() {
    install(CallLogging) {
        disableDefaultColors()
        level = Level.INFO
        filter { call -> 
            !call.request.path().startsWith("/internal/")
        }
        mdc("callId") { call ->
            call.request.header(HttpHeaders.XCorrelationId) ?: call.request.header("Nav-Call-Id")
        }
        mdc("navIdent") { call ->
            call.getBrukerPrincipal()?.navIdent
        }
    }
}
