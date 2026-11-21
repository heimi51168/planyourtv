package com.planyourtv.policy

/**
 * Builds the first config document of a locally added member.
 *
 * A usable source document is copied so "add child from an existing member" keeps the same
 * time/app/study rules. Every other case writes first-use defaults instead of leaving the
 * member without a config: the TV would otherwise land in the first-run setup wizard, which
 * also owns the family PIN.
 *
 * The activation always starts as MANUAL. V40's multi-member flow only covers the parent-start
 * manual mode; copying an old AUTO cursor would resurrect a deprecated automatic timer.
 */
object SubjectProvisioning {
    fun initialConfig(
        familyId: String,
        userId: String,
        source: SubjectConfigDocument?,
        sourceVersion: String?,
        now: Long,
    ): SubjectConfigDocument {
        val copySource = source?.takeIf { it.ruleSets.isUsable }
        return SubjectConfigDocument(
            familyId = familyId,
            userId = userId,
            controlOwner = ControlOwner.LOCAL,
            source = SubjectSource.LOCAL_WEB,
            revision = 1L,
            activation = Activation.MANUAL,
            ruleSets = copySource?.ruleSets
                ?: LocalRuleSets.from(LocalRules.DEFAULTS),
            sourceVersion = sourceVersion,
            updatedAt = now,
        )
    }
}
