package app.orchard.tv.ui.policy

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Auto update is a device-level setting: it must be reachable with child mode off and after a
 * handoff hides the remote and password rows. These cases pin the rail order the d-pad follows.
 */
class PolicyParentRailTest {
    @Test
    fun autoUpdateFollowsTheSwitchWhenChildModeIsOff() {
        assertEquals(
            listOf(ParentFocusTarget.Switch, ParentFocusTarget.AutoUpdate),
            parentRailTargets(childOn = false, remoteVisible = false, passwordVisible = false),
        )
    }

    @Test
    fun autoUpdateStaysBelowThePasswordRow() {
        assertEquals(
            listOf(ParentFocusTarget.Switch, ParentFocusTarget.Password, ParentFocusTarget.AutoUpdate),
            parentRailTargets(childOn = true, remoteVisible = false, passwordVisible = true),
        )
    }

    @Test
    fun autoUpdateStaysBelowRemoteManagementAndPassword() {
        assertEquals(
            listOf(
                ParentFocusTarget.Switch,
                ParentFocusTarget.Remote,
                ParentFocusTarget.Password,
                ParentFocusTarget.AutoUpdate,
            ),
            parentRailTargets(childOn = true, remoteVisible = true, passwordVisible = true),
        )
    }

    @Test
    fun autoUpdateSurvivesAHandoffThatHidesTheOtherRows() {
        assertEquals(
            listOf(ParentFocusTarget.Switch, ParentFocusTarget.AutoUpdate),
            parentRailTargets(childOn = true, remoteVisible = false, passwordVisible = false),
        )
    }
}
