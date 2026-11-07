package com.planyourtv.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyAccessPolicyTest {
    @Test fun screenModeIsTheCurrentAllAllowedProduct() {
        assertFalse(ScreenAccessPolicy.enabled)
        assertTrue(ScreenAccessPolicy.isAllowed("com.example.anything"))
    }

    @Test fun appModeAllowsOnlyTheBaseAndRedeemedSets() {
        val policy = AppAccessPolicy(
            baseAllowlist = setOf("com.android.settings"),
            redeemedAllowlist = setOf("com.example.video"),
        )
        assertTrue(policy.enabled)
        assertTrue(policy.isAllowed("com.android.settings"))
        assertTrue(policy.isAllowed("com.example.video"))
        assertFalse(policy.isAllowed("com.example.game"))
    }

    @Test fun allValidAppMeansEveryLaunchableApp() {
        val policy = AppAccessPolicy(redeemedAllowlist = setOf(ALL_VALID_APP))
        assertTrue(policy.enabled)
        assertTrue(policy.isAllValid)
        assertTrue(policy.isAllowed("com.example.anything"))
        assertTrue(policy.isAllowed("com.example.video"))
    }
}
