package app.orchard.tv.ui.policy

import com.planyourtv.policy.PolicyMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The top-right parent-center code follows the phone surface that can actually manage the TV:
 * the local web page only while LOCAL and unbound, the mini program once cloud-bound. Anything
 * else hides the hint instead of advertising a surface that would only show its blocked state.
 */
class ParentCenterQrRoutingTest {
    private fun target(
        loaded: Boolean = true,
        mode: PolicyMode = PolicyMode.LOCAL,
        bound: Boolean = false,
        cloudEnabled: Boolean = true,
        webEnabled: Boolean = true,
    ) = parentQrTarget(loaded, mode, bound, cloudEnabled, webEnabled)

    @Test
    fun nothingIsShownBeforeTheModeIsLoaded() {
        assertEquals(ParentQrTarget.HIDDEN, target(loaded = false))
        assertEquals(
            ParentQrTarget.HIDDEN,
            target(loaded = false, mode = PolicyMode.CLOUD, bound = true),
        )
    }

    @Test
    fun localUnboundShowsTheWebPage() {
        assertEquals(ParentQrTarget.LOCAL_WEB, target(mode = PolicyMode.LOCAL))
        assertEquals(
            ParentQrTarget.LOCAL_WEB,
            target(mode = PolicyMode.LOCAL, cloudEnabled = false),
        )
    }

    @Test
    fun bindingSwitchesTheCodeEvenBeforeTheCloudModeReturns() {
        assertEquals(
            ParentQrTarget.MINI_PROGRAM,
            target(mode = PolicyMode.CLOUD, bound = true),
        )
        assertEquals(
            ParentQrTarget.MINI_PROGRAM,
            target(mode = PolicyMode.LOCAL, bound = true),
        )
    }

    @Test
    fun cloudWithoutABindingAndChildModeOffShowNothing() {
        assertEquals(ParentQrTarget.HIDDEN, target(mode = PolicyMode.CLOUD))
        assertEquals(ParentQrTarget.HIDDEN, target(mode = PolicyMode.NONE))
    }

    @Test
    fun aDisabledWebSurfaceHidesTheLocalCode() {
        assertEquals(
            ParentQrTarget.HIDDEN,
            target(mode = PolicyMode.LOCAL, webEnabled = false),
        )
    }

    @Test
    fun aCommunityBuildNeverAdvertisesTheMiniProgram() {
        assertEquals(
            ParentQrTarget.HIDDEN,
            target(mode = PolicyMode.CLOUD, bound = true, cloudEnabled = false),
        )
        // A stale bound flag is ignored, like ParentWebAccess.webAllowed() does.
        assertEquals(
            ParentQrTarget.LOCAL_WEB,
            target(mode = PolicyMode.LOCAL, bound = true, cloudEnabled = false),
        )
    }
}