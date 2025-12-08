package no.nav.flexjar

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.flexjar.config.configureAuth
import no.nav.flexjar.config.configureDatabase
import no.nav.flexjar.config.configureRouting
import no.nav.flexjar.config.configureSerialization
import no.nav.flexjar.config.configureStatusPages
import no.nav.flexjar.config.configureCallLogging

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureStatusPages()
    configureCallLogging()
    configureAuth()
    configureDatabase()
    configureRouting()
}
