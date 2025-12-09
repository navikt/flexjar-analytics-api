package no.nav.flexjar.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.nav.flexjar.config.exception.ApiError
import no.nav.flexjar.config.exception.BadRequestException
import no.nav.flexjar.config.exception.ForbiddenException
import no.nav.flexjar.config.exception.NotFoundException
import no.nav.flexjar.config.exception.UnauthorizedException
import org.slf4j.LoggerFactory

private val statusPageLog = LoggerFactory.getLogger("StatusPages")

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logException(call, cause)
            val apiError = determineApiError(cause, call.request.path())
            call.respond(HttpStatusCode.fromValue(apiError.status), apiError)
        }
        
        status(HttpStatusCode.Forbidden) { call, status ->
            val path = call.request.path()
            val apiError = ApiError.forbidden(status.description, path)
            call.respond(HttpStatusCode.Forbidden, apiError)
        }
        
        status(HttpStatusCode.Unauthorized) { call, status ->
            val path = call.request.path()
            val apiError = ApiError.unauthorized(status.description, path)
            call.respond(HttpStatusCode.Unauthorized, apiError)
        }
        
        status(HttpStatusCode.NotFound) { call, status ->
            val path = call.request.path()
            val apiError = ApiError.notFound(status.description, path)
            call.respond(HttpStatusCode.NotFound, apiError)
        }
    }
}

private fun logException(call: ApplicationCall, cause: Throwable) {
    val logMessage = "Caught ${cause::class.simpleName} exception on ${call.request.path()}"
    statusPageLog.error(logMessage, cause)
}

private fun determineApiError(cause: Throwable, path: String?): ApiError {
    return when (cause) {
        is BadRequestException -> ApiError.badRequest(cause.message ?: "Bad request", path)
        is IllegalArgumentException -> ApiError.badRequest(cause.message ?: "Illegal argument", path)
        is NotFoundException -> ApiError.notFound(cause.message ?: "Not found", path)
        is ForbiddenException -> ApiError.forbidden(cause.message ?: "Forbidden", path)
        is UnauthorizedException -> ApiError.unauthorized(cause.message ?: "Unauthorized", path)
        else -> ApiError.internalServerError(
            if (isDev()) cause.message ?: "Internal server error" else "Internal server error",
            path
        )
    }
}