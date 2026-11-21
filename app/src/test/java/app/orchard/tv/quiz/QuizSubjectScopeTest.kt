package app.orchard.tv.quiz

import app.orchard.tv.policy.FamilyHub
import org.junit.Assert.assertEquals
import org.junit.Test

class QuizSubjectScopeTest {
    private fun family(activeUserId: String, defaultUserId: String) = FamilyHub.UiState(
        ready = true,
        familyId = "fam_test",
        defaultUserId = defaultUserId,
        activeUserId = activeUserId,
    )

    @Test
    fun defaultMemberKeepsTheLegacyKeyNames() {
        val state = family(activeUserId = "member_default", defaultUserId = "member_default")
        assertEquals("quiz.doc.local", QuizSubjectScope.keyFor(state, "member_default", "quiz.doc.local"))
        assertEquals("quiz.session", QuizSubjectScope.keyFor(state, "member_default", "quiz.session"))
    }

    @Test
    fun otherMembersGetTheirOwnSuffix() {
        val state = family(activeUserId = "member_default", defaultUserId = "member_default")
        assertEquals(
            "quiz.doc.local#member_b",
            QuizSubjectScope.keyFor(state, "member_b", "quiz.doc.local"),
        )
    }

    @Test
    fun provisioningKeyDoesNotDependOnTheActiveMember() {
        val state = family(activeUserId = "member_a", defaultUserId = "member_default")
        assertEquals(
            "quiz.doc.local#member_b",
            QuizSubjectScope.keyFor(state, "member_b", "quiz.doc.local"),
        )
        assertEquals(
            "quiz.doc.local#member_a",
            QuizSubjectScope.keyFor(state, "member_a", "quiz.doc.local"),
        )
    }

    @Test
    fun unreadyFamilyKeepsLegacyKeysAndCannotAddMembers() {
        val state = FamilyHub.UiState(ready = false, activeUserId = "member_a")
        assertEquals("quiz.session", QuizSubjectScope.keyFor(state, "member_a", "quiz.session"))
        assertEquals(false, state.canAddMember)
    }
}
