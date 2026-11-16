package app.orchard.tv.ui.policy

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * V29 collapses the TV rail to the child-mode switch and remote management. Local configuration
 * lives on the phone, so password, auto-update, quiz-setup and web-pair rows no longer exist.
 * WeChat alerts are the one local feature that keeps a rail row: they work without any backend,
 * so they stay whenever child mode is on, below remote management when it is visible.
 */
class PolicyParentRailTest {
    @Test
    fun childModeOffShowsOnlyTheSwitch() {
        assertEquals(
            listOf(ParentFocusTarget.Switch),
            parentRailTargets(childOn = false, remoteVisible = false),
        )
        assertEquals(
            listOf(ParentFocusTarget.Switch),
            parentRailTargets(childOn = false, remoteVisible = true),
        )
    }

    @Test
    fun childModeOnWithoutCloudShowsSwitchAndWeChat() {
        assertEquals(
            listOf(ParentFocusTarget.Switch, ParentFocusTarget.WeChat),
            parentRailTargets(childOn = true, remoteVisible = false),
        )
    }

    @Test
    fun remoteManagementFollowsTheSwitch() {
        assertEquals(
            listOf(ParentFocusTarget.Switch, ParentFocusTarget.Remote, ParentFocusTarget.WeChat),
            parentRailTargets(childOn = true, remoteVisible = true),
        )
    }
}
