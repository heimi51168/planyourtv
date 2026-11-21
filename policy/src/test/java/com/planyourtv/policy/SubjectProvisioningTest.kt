package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectProvisioningTest {
    private val sourceVersion = "test"

    @Test
    fun `new member without a source gets usable manual defaults`() {
        val config = SubjectProvisioning.initialConfig(
            familyId = "fam_test",
            userId = "usr_new",
            source = null,
            sourceVersion = sourceVersion,
            now = 1L,
        )

        assertEquals(Activation.MANUAL, config.activation)
        assertTrue(config.ruleSets.isUsable)
        assertEquals(0, config.ruleSets.manual.restMinutes)
        assertEquals(SubjectSource.LOCAL_WEB, config.source)
        assertEquals(ControlOwner.LOCAL, config.controlOwner)
        assertEquals(1L, config.revision)
    }

    @Test
    fun `usable source rules are copied but the member starts in manual mode`() {
        val sourceRules = LocalRules.DEFAULTS.copy(
            dayGroups = LocalRules.DEFAULTS.dayGroups.map {
                it.copy(entertainmentDailyLimitMinutes = 90)
            },
            sessionLimitMinutes = 15,
            restMinutes = 5,
            allowedPackages = setOf("com.example.video"),
            studyPackages = setOf("com.example.study"),
        )
        val source = SubjectConfigDocument(
            familyId = "fam_test",
            userId = "usr_source",
            controlOwner = ControlOwner.LOCAL,
            source = SubjectSource.LOCAL_WEB,
            revision = 4L,
            activation = Activation.MANUAL,
            ruleSets = LocalRuleSets.from(sourceRules),
            sourceVersion = sourceVersion,
            updatedAt = 1L,
        )

        val config = SubjectProvisioning.initialConfig(
            familyId = "fam_test",
            userId = "usr_new",
            source = source,
            sourceVersion = sourceVersion,
            now = 2L,
        )

        // AUTO is a deprecated flow; a copied config must not restore its cursor.
        assertEquals(Activation.MANUAL, config.activation)
        assertEquals(source.ruleSets, config.ruleSets)
        assertEquals(1L, config.revision)
        assertTrue(config.ruleSets.isUsable)
    }

    @Test
    fun `unusable source falls back to usable defaults`() {
        val unusable = LocalRuleSets.from(LocalRules.DEFAULTS).copy(
            manual = LocalRuleSet(
                dayGroups = LocalRules.DEFAULTS.dayGroups.map {
                    it.copy(studyWindows = emptyList(), entertainmentWindows = emptyList())
                },
            ),
        )
        assertFalse(unusable.isUsable)
        val source = SubjectConfigDocument(
            familyId = "fam_test",
            userId = "usr_source",
            controlOwner = ControlOwner.LOCAL,
            source = SubjectSource.LOCAL_WEB,
            revision = 2L,
            activation = Activation.MANUAL,
            ruleSets = unusable,
            sourceVersion = sourceVersion,
            updatedAt = 1L,
        )

        val config = SubjectProvisioning.initialConfig(
            familyId = "fam_test",
            userId = "usr_new",
            source = source,
            sourceVersion = sourceVersion,
            now = 3L,
        )

        assertEquals(Activation.MANUAL, config.activation)
        assertTrue(config.ruleSets.isUsable)
        assertEquals(0, config.ruleSets.manual.restMinutes)
    }
}
