package no.nav.flexjar.routes

import io.ktor.http.*
import io.ktor.server.resources.get
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.flexjar.domain.AnswerValue
import no.nav.flexjar.domain.ExportFormat
import no.nav.flexjar.domain.FeedbackDto
import no.nav.flexjar.domain.FeedbackQuery
import no.nav.flexjar.domain.FieldType
import no.nav.flexjar.domain.FILTER_ALL
import no.nav.flexjar.repository.FeedbackRepository
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream

private val defaultFeedbackRepository = FeedbackRepository()
private val json = Json { prettyPrint = true }

fun Route.exportRoutes(repository: FeedbackRepository = defaultFeedbackRepository) {
    // Export feedback data
    get<ApiV1Intern.Export> { params ->
        val format = try { ExportFormat.valueOf(params.format.uppercase()) } catch (e: Exception) { ExportFormat.CSV }
        
        val team = params.team ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing team parameter")
        
        val query = FeedbackQuery(
            team = team,
            app = params.app?.takeIf { it != FILTER_ALL },
            page = 0,
            size = 10000, // Max export size
            medTekst = params.medTekst ?: false,
            stjerne = params.stjerne ?: false,
            tags = params.tags?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            fritekst = params.fritekst?.split(" ")?.filter { it.isNotBlank() } ?: emptyList(),
            from = params.from,
            to = params.to,
            feedbackId = params.feedbackId
        )
        
        val (content, _, _) = repository.findPaginated(query)
        
        when (format) {
            ExportFormat.CSV -> exportCsv(call, content)
            ExportFormat.JSON -> exportJson(call, content)
            ExportFormat.EXCEL -> exportExcel(call, content)
        }
    }
}

private suspend fun exportCsv(call: io.ktor.server.application.ApplicationCall, data: List<FeedbackDto>) {
    val csv = buildString {
        // Header
        appendLine("id,submittedAt,app,surveyId,rating,feedback,sensitiveDataRedacted")
        
        // Data rows
        data.forEach { feedback ->
            val rating = feedback.answers.firstOrNull { it.fieldType == FieldType.RATING }?.let {
                (it.value as? AnswerValue.Rating)?.rating?.toString() ?: ""
            } ?: ""
            val feedbackText = feedback.answers.firstOrNull { it.fieldType == FieldType.TEXT }?.let {
                (it.value as? AnswerValue.Text)?.text?.escapeCsv() ?: ""
            } ?: ""
            
            appendLine("${feedback.id},${feedback.submittedAt},${feedback.app ?: ""},${feedback.surveyId},${rating},${feedbackText},${feedback.sensitiveDataRedacted}")
        }
    }
    
    call.response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "flexjar-export.csv").toString()
    )
    call.respondText(csv, ContentType.Text.CSV)
}

private suspend fun exportJson(call: io.ktor.server.application.ApplicationCall, data: List<FeedbackDto>) {
    call.response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "flexjar-export.json").toString()
    )
    call.respondText(json.encodeToString(data), ContentType.Application.Json)
}

private suspend fun exportExcel(call: io.ktor.server.application.ApplicationCall, data: List<FeedbackDto>) {
    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet("Feedback")
    
    // Header row
    val headerRow = sheet.createRow(0)
    val headers = listOf("ID", "Tidspunkt", "App", "Survey", "Vurdering", "Tilbakemelding", "Sensitivt data fjernet")
    headers.forEachIndexed { index, header ->
        headerRow.createCell(index).setCellValue(header)
    }
    
    // Data rows
    data.forEachIndexed { rowIndex, feedback ->
        val row = sheet.createRow(rowIndex + 1)
        row.createCell(0).setCellValue(feedback.id)
        row.createCell(1).setCellValue(feedback.submittedAt)
        row.createCell(2).setCellValue(feedback.app ?: "")
        row.createCell(3).setCellValue(feedback.surveyId)
        
        val rating = feedback.answers.firstOrNull { it.fieldType == FieldType.RATING }?.let {
            (it.value as? AnswerValue.Rating)?.rating?.toString() ?: ""
        } ?: ""
        row.createCell(4).setCellValue(rating)
        
        val feedbackText = feedback.answers.firstOrNull { it.fieldType == FieldType.TEXT }?.let {
            (it.value as? AnswerValue.Text)?.text ?: ""
        } ?: ""
        row.createCell(5).setCellValue(feedbackText)
        row.createCell(6).setCellValue(if (feedback.sensitiveDataRedacted) "Ja" else "Nei")
    }
    
    // Auto-size columns
    headers.indices.forEach { sheet.autoSizeColumn(it) }
    
    val outputStream = ByteArrayOutputStream()
    workbook.write(outputStream)
    workbook.close()
    
    call.response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "flexjar-export.xlsx").toString()
    )
    call.respondBytes(outputStream.toByteArray(), ContentType.Application.Xlsx)
}

private fun String.escapeCsv(): String {
    return if (contains(",") || contains("\"") || contains("\n")) {
        "\"${replace("\"", "\"\"")}\""
    } else {
        this
    }
}


