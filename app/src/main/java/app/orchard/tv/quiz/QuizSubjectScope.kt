package app.orchard.tv.quiz

import app.orchard.tv.policy.FamilyHub

/**
 * Namespaces the quiz preferences that belong to one child.
 *
 * The default member keeps the historical key names. That is what makes an upgraded,
 * single-child TV read its existing quiz document, session and answer prefs without a
 * migration, and it keeps the downgrade mirror readable by an older APK. Any later member
 * gets a suffix derived from its stable local `userId`, so its QCD, session, answer levels
 * and AI generation prefs cannot overwrite another child's.
 *
 * Device-level quiz state deliberately stays outside this object: the AI provider key,
 * web/editor consent and tokens, speech choices, seed-bank versions and the install id are
 * properties of the TV or the parent, not of the child currently watching.
 */
object QuizSubjectScope {
    private const val SUFFIX = "#"

    /** The key for the active member, resolved from the same roster the runtime executes. */
    fun key(base: String): String {
        val family = FamilyHub.state.value
        return keyFor(family, family.activeUserId, base)
    }

    /**
     * The key for [userId]. Used while provisioning a new child before the active pointer
     * moves, so it never depends on which member is currently watching.
     */
    fun keyFor(family: FamilyHub.UiState, userId: String, base: String): String =
        if (!family.ready || userId.isBlank() || userId == family.defaultUserId) {
            base
        } else {
            base + SUFFIX + userId
        }

    /**
     * Creates the target member's first local quiz document without moving the active
     * pointer. A [sourceUserId] clones that child's document (level structure and quiz
     * settings only — the QCD carries no progress); null writes a clean default. Progress
     * keys (session, wrong answers, generated prefs) are not part of the document and stay
     * absent for the new child.
     */
    fun provisionLocalDocument(targetUserId: String, sourceUserId: String? = null): Boolean =
        QuizConfigDocumentStore.provisionLocalDocument(targetUserId, sourceUserId)
}
