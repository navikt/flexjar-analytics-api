package no.nav.flexjar.routes

import io.ktor.resources.*
import kotlinx.serialization.Serializable

@Resource("/api/v1/intern")
class ApiV1Intern {
    
    @Resource("feedback")
    @Serializable
    class Feedback(
        val parent: ApiV1Intern = ApiV1Intern(),
        val team: String? = null,
        val app: String? = null,
        val page: Int? = null,
        val size: Int? = null,
        val medTekst: Boolean? = null,
        val stjerne: Boolean? = null,
        val tags: String? = null,
        val fritekst: String? = null,
        val from: String? = null,
        val to: String? = null,
        val feedbackId: String? = null,
        val deviceType: String? = null
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
        class MetadataKeys(val parent: Feedback, val feedbackId: String, val team: String)
    }

    @Resource("stats")
    @Serializable
    class Stats(
        val parent: ApiV1Intern = ApiV1Intern(),
        val team: String? = null,
        val app: String? = null,
        val from: String? = null,
        val to: String? = null,
        val feedbackId: String? = null,
        val deviceType: String? = null
    ) {
        @Resource("ratings")
        @Serializable
        class Ratings(val parent: Stats)

        @Resource("timeline")
        @Serializable
        class Timeline(val parent: Stats)

        @Resource("top-tasks")
        @Serializable
        class TopTasks(val parent: Stats)
    }

    @Resource("export")
    @Serializable
    class Export(
        val parent: ApiV1Intern = ApiV1Intern(),
        val format: String = "CSV",
        val team: String? = null,
        val app: String? = null,
        val medTekst: Boolean? = null,
        val stjerne: Boolean? = null,
        val tags: String? = null,
        val fritekst: String? = null,
        val from: String? = null,
        val to: String? = null,
        val feedbackId: String? = null
    )
}
