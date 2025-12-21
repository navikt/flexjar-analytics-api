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
            call.respondText(no.nav.flexjar.config.appMicrometerRegistry.scrape(), ContentType.parse(io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT.get("contentType") ?: "text/plain; version=0.0.4; charset=utf-8"))
        }
    }
}
