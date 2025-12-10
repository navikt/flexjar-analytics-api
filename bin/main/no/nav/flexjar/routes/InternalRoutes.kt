package no.nav.flexjar.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.internalRoutes() {
    route("/internal") {
        get("/isAlive") {
            call.respondText("OK", ContentType.Text.Plain)
        }
        
        get("/isReady") {
            call.respondText("OK", ContentType.Text.Plain)
        }
        
        get("/prometheus") {
            // TODO: Add prometheus metrics
            call.respondText("", ContentType.Text.Plain)
        }
    }
}
