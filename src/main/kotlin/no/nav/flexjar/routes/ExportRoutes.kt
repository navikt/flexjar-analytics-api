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
        
        // Parse tags (accept repeated params + comma-separated entries)
        val tags = params.tag
            ?.flatMap { it.split(",") }
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        // Parse segment params (format: "key:value")
        val segments = params.segment
            ?.mapNotNull { segmentStr ->
                val parts = segmentStr.split(":", limit = 2)
                if (parts.size == 2) Pair(parts[0], parts[1]) else null
            }
            ?: emptyList()

        val query = FeedbackQuery(
            team = team,
            app = params.app?.takeIf { it != FILTER_ALL },
            page = 0,
            size = ExportService.MAX_EXPORT_SIZE,
            hasText = params.hasText ?: false,
            lowRating = params.lowRating ?: false,
            tags = tags,
            query = params.query?.takeIf { it.isNotBlank() },
            fromDate = params.fromDate,
            toDate = params.toDate,
            surveyId = params.surveyId,
            deviceType = params.deviceType?.takeIf { it.isNotBlank() },
            segments = segments
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
