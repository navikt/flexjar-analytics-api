package no.nav.flexjar.domain

/**
 * Canonical answer field IDs used by the widget presets.
 *
 * If a team builds a completely custom Top Tasks survey with different IDs, the analytics endpoints
 * will not be able to reliably infer which answer represents the task/blocker without extra metadata.
 */
object TopTasksFieldIds {
    /** Task selection field. */
    val task: Set<String> = setOf("task")

    /** Blocker text field. */
    val blocker: Set<String> = setOf("blocker")
}
