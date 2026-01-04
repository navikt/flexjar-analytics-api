package no.nav.flexjar.routes

import io.ktor.http.*
import io.ktor.server.resources.get
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.flexjar.config.auth.authorizedTeam
import no.nav.flexjar.domain.ExportFormat
import no.nav.flexjar.domain.FeedbackQuery
import no.nav.flexjar.domain.FILTER_ALL
import no.nav.flexjar.service.ExportService

private val defaultExportService = ExportService()

/**
 * Routes for exporting feedback data.
 * Delegates export logic to ExportService.
 */
fun Route.exportRoutes(exportService: ExportService = defaultExportService) {
    // Export feedback data
    get<ApiV1Intern.Export> { params ->
        val team = call.authorizedTeam
        
        val format = try { 
            ExportFormat.valueOf(params.format.uppercase()) 
        } catch (e: Exception) { 
            ExportFormat.CSV 
        }
        
        val query = FeedbackQuery(
            team = team,
            app = params.app?.takeIf { it != FILTER_ALL },
            page = 0,
            size = ExportService.MAX_EXPORT_SIZE,
            medTekst = params.medTekst ?: false,
            stjerne = params.stjerne ?: false,
            tags = params.tags?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            fritekst = params.fritekst?.split(" ")?.filter { it.isNotBlank() } ?: emptyList(),
            from = params.from,
            to = params.to,
            feedbackId = params.feedbackId
        )
        
        val feedbacks = exportService.getFeedbackForExport(query)
        
        when (format) {
            ExportFormat.CSV -> {
                val csv = exportService.exportToCsv(feedbacks)
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName, 
                        "flexjar-export.csv"
                    ).toString()
                )
                call.respondText(csv, ContentType.Text.CSV)
            }
            ExportFormat.JSON -> {
                val json = exportService.exportToJson(feedbacks)
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName, 
                        "flexjar-export.json"
                    ).toString()
                )
                call.respondText(json, ContentType.Application.Json)
            }
            ExportFormat.EXCEL -> {
                val bytes = exportService.exportToExcel(feedbacks)
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName, 
                        "flexjar-export.xlsx"
                    ).toString()
                )
                call.respondBytes(bytes, ContentType.Application.Xlsx)
            }
        }
    }
}
