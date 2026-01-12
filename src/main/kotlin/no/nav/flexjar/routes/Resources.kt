package no.nav.flexjar.routes

import io.ktor.resources.*
import kotlinx.serialization.Serializable

@Resource("/api/v1/intern")
class ApiV1Intern {
    
    @Resource("feedback")
    @Serializable
    class Feedback(
        val parent: ApiV1Intern = ApiV1Intern(),
        val app: String? = null,
        val page: Int? = null,
        val size: Int? = null,
        /** Filter for feedback with text responses */
        val hasText: Boolean? = null,
        /** Filter for low ratings (1-2) */
        val lowRating: Boolean? = null,
        /** Tags filter - repeated params like tag=foo&tag=bar (also accepts comma-separated values per entry) */
        val tag: List<String>? = null,
        /** Full-text search query */
        val query: String? = null,
        /** Start date (YYYY-MM-DD, Europe/Oslo inclusive) */
        val fromDate: String? = null,
        /** End date (YYYY-MM-DD, Europe/Oslo inclusive) */
        val toDate: String? = null,
        /** Survey ID filter */
        val surveyId: String? = null,
        /** Device type filter: mobile, tablet, desktop */
        val deviceType: String? = null,
        /** Segment filter - repeated params like segment=key:value */
        val segment: List<String>? = null
    ) {
        @Resource("{id}")
        @Serializable
        class Id(val parent: Feedback, val id: String) {
            @Resource("tags")
            @Serializable
            class Tags(val parent: Id, val tag: String? = null)
        }

        @Resource("tags")
        @Serializable
        class AllTags(val parent: Feedback)

        @Resource("teams")
        @Serializable
        class Teams(val parent: Feedback)

        @Resource("metadata-keys")
        @Serializable
        class MetadataKeys(
            val parent: Feedback, 
            val surveyId: String,
            /** Max unique values per key. Keys with more values are filtered out. Default 10. */
            val maxCardinality: Int? = 10
        )
    }

    @Resource("stats")
    @Serializable
    class Stats(
        val parent: ApiV1Intern = ApiV1Intern(),
        val app: String? = null,
        /** Start date (YYYY-MM-DD, Europe/Oslo inclusive) */
        val fromDate: String? = null,
        /** End date (YYYY-MM-DD, Europe/Oslo inclusive) */
        val toDate: String? = null,
        /** Survey ID filter */
        val surveyId: String? = null,
        /** Device type filter */
        val deviceType: String? = null,
        /** Segment filter - repeated params like segment=key:value */
        val segment: List<String>? = null,
        /** Task filter for Top Tasks drill-down */
        val task: String? = null
    ) {
        @Resource("overview")
        @Serializable
        class Overview(val parent: Stats)

        @Resource("ratings")
        @Serializable
        class Ratings(val parent: Stats)

        @Resource("timeline")
        @Serializable
        class Timeline(val parent: Stats)

        @Resource("top-tasks")
        @Serializable
        class TopTasks(val parent: Stats)

        @Resource("survey-types")
        @Serializable
        class SurveyTypes(val parent: Stats)

        @Resource("blockers")
        @Serializable
        class Blockers(val parent: Stats)

        @Resource("discovery")
        @Serializable
        class Discovery(val parent: Stats)

        @Resource("task-priority")
        @Serializable
        class TaskPriority(val parent: Stats)
    }

    @Resource("themes")
    @Serializable
    class Themes(
        val parent: ApiV1Intern = ApiV1Intern(),
        /** Optional theme context filter (GENERAL_FEEDBACK | BLOCKER) */
        val context: String? = null
    ) {
        @Resource("{id}")
        @Serializable
        class Id(val parent: Themes, val id: String)
    }

    @Resource("surveys")
    @Serializable
    class Surveys(val parent: ApiV1Intern = ApiV1Intern()) {
        @Resource("{surveyId}")
        @Serializable
        class Id(
            val parent: Surveys = Surveys(),
            val surveyId: String,
        ) {
            @Resource("context-tags")
            @Serializable
            class ContextTags(
                val parent: Id,
                /** Max unique values per key. Keys with more values are filtered out. Default 10. */
                val maxCardinality: Int? = 10,
                /** Task filter for Top Tasks drill-down (matches option label) */
                val task: String? = null,
                /** Segment filter - repeated params like segment=key:value */
                val segment: List<String>? = null,
                /** Start date (YYYY-MM-DD, Europe/Oslo inclusive) */
                val fromDate: String? = null,
                /** End date (YYYY-MM-DD, Europe/Oslo inclusive) */
                val toDate: String? = null,
                /** Device type filter: mobile, tablet, desktop */
                val deviceType: String? = null,
                /** Filter for feedback with text responses */
                val hasText: Boolean? = null,
                /** Filter for low ratings (1-2) */
                val lowRating: Boolean? = null,
            )
        }
    }

    @Resource("filters")
    @Serializable
    class Filters(val parent: ApiV1Intern = ApiV1Intern()) {
        @Resource("bootstrap")
        @Serializable
        class Bootstrap(val parent: Filters = Filters())
    }

    @Resource("export")
    @Serializable
    class Export(
        val parent: ApiV1Intern = ApiV1Intern(),
        val format: String = "CSV",
        val app: String? = null,
        val hasText: Boolean? = null,
        val lowRating: Boolean? = null,
        val tag: List<String>? = null,
        val query: String? = null,
        val fromDate: String? = null,
        val toDate: String? = null,
        val surveyId: String? = null,
        val deviceType: String? = null,
        val segment: List<String>? = null
    )
}
