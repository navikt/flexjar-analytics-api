package no.nav.flexjar.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.flexjar.domain.AnswerValue
import no.nav.flexjar.domain.FeedbackDto
import no.nav.flexjar.domain.FeedbackQuery
import no.nav.flexjar.domain.FieldType
import no.nav.flexjar.repository.FeedbackRepository
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream

/**
 * Service layer for exporting feedback data.
 * Supports CSV, JSON, and Excel formats.
 */
class ExportService(
    private val feedbackRepository: FeedbackRepository = FeedbackRepository()
) {
    private val json = Json { prettyPrint = true }

    /**
     * Get feedback data for export based on query.
     * Returns up to MAX_EXPORT_SIZE records.
     */
    fun getFeedbackForExport(query: FeedbackQuery): List<FeedbackDto> {
        val exportQuery = query.copy(size = MAX_EXPORT_SIZE)
        val (content, _, _) = feedbackRepository.findPaginated(exportQuery)
        return content
    }

    /**
     * Export feedback list to CSV format.
     */
    fun exportToCsv(feedbacks: List<FeedbackDto>): String {
        return buildString {
            // Header
            appendLine("id,submittedAt,app,surveyId,rating,feedback,sensitiveDataRedacted")
            
            // Data rows
            feedbacks.forEach { feedback ->
                val rating = feedback.answers.firstOrNull { it.fieldType == FieldType.RATING }?.let {
                    (it.value as? AnswerValue.Rating)?.rating?.toString() ?: ""
                } ?: ""
                val feedbackText = feedback.answers.firstOrNull { it.fieldType == FieldType.TEXT }?.let {
                    (it.value as? AnswerValue.Text)?.text?.escapeCsv() ?: ""
                } ?: ""
                
                appendLine("${feedback.id},${feedback.submittedAt},${feedback.app ?: ""},${feedback.surveyId},${rating},${feedbackText},${feedback.sensitiveDataRedacted}")
            }
        }
    }

    /**
     * Export feedback list to JSON format.
     */
    fun exportToJson(feedbacks: List<FeedbackDto>): String {
        return json.encodeToString(feedbacks)
    }

    /**
     * Export feedback list to Excel (XLSX) format.
     * Returns the Excel file as a byte array.
     */
    fun exportToExcel(feedbacks: List<FeedbackDto>): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Feedback")
        
        // Header row
        val headerRow = sheet.createRow(0)
        val headers = listOf("ID", "Tidspunkt", "App", "Survey", "Vurdering", "Tilbakemelding", "Sensitivt data fjernet")
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).setCellValue(header)
        }
        
        // Data rows
        feedbacks.forEachIndexed { rowIndex, feedback ->
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
        
        return outputStream.toByteArray()
    }

    companion object {
        /** Maximum number of records to export */
        const val MAX_EXPORT_SIZE = 10000
    }
}

/**
 * Helper extension to escape CSV values properly.
 */
private fun String.escapeCsv(): String {
    return if (contains(",") || contains("\"") || contains("\n")) {
        "\"${replace("\"", "\"\"")}\""
    } else {
        this
    }
}
