package no.nav.flexjar.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.flexjar.TestDatabase
import no.nav.flexjar.config.DatabaseHolder
import no.nav.flexjar.domain.StatsQuery
import no.nav.flexjar.insertTestFeedback

class FeedbackStatsRepositoryTest : FunSpec({
    val repository = FeedbackStatsRepository()

    beforeSpec {
        DatabaseHolder.initializeForTesting(TestDatabase.dataSource)
        TestDatabase.initialize()
    }

    beforeTest {
        TestDatabase.clearAllData()
    }

    context("getStats") {
        test("returns correct statistics") {
            insertTestFeedback(team = "flex", svar = 4)
            insertTestFeedback(team = "flex", svar = 5)
            insertTestFeedback(team = "flex", svar = 5)
            
            val stats = repository.getStats(StatsQuery(team = "flex"))
            
            stats.totalCount shouldBe 3L
        }
    }
})
