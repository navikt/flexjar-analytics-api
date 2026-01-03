package no.nav.flexjar.repository

import no.nav.flexjar.domain.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Repository for Discovery survey data access.
 * Handles database queries for discovery feedback data.
 */
class DiscoveryStatsRepository {

    /**
     * Fetch all discovery survey feedback for the given query parameters.
     * Returns FeedbackDto list for further processing by the service layer.
     */
    fun getDiscoveryFeedback(query: StatsQuery): List<FeedbackDto> {
        return transaction {
            val dbQuery = FeedbackTable.selectAll()
            dbQuery.andWhere { FeedbackTable.team eq query.team }
            dbQuery.andWhere { 
                JsonExtract(FeedbackTable.feedbackJson, listOf("surveyType")) eq "discovery" 
            }
            
            query.feedbackId?.let { fid ->
                dbQuery.andWhere { JsonExtract(FeedbackTable.feedbackJson, listOf("feedbackId")) eq fid }
            }
            query.from?.let { from ->
                dbQuery.andWhere { FeedbackTable.opprettet greaterEq Instant.parse(from) }
            }
            query.to?.let { to ->
                dbQuery.andWhere { FeedbackTable.opprettet lessEq Instant.parse(to) }
            }
            query.deviceType?.let { device ->
                dbQuery.andWhere { JsonExtract(FeedbackTable.feedbackJson, listOf("context", "deviceType")) eq device }
            }
            
            dbQuery
                .orderBy(FeedbackTable.opprettet to SortOrder.DESC)
                .map { it.toDto() }
        }
    }
}
