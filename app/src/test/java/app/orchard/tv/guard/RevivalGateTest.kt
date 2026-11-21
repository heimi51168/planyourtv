package app.orchard.tv.guard

import org.junit.Assert.assertEquals
import org.junit.Test

class RevivalGateTest {
    private val noPolicy = PolicyGateState(
        loaded = true,
        mayExist = false,
        lockScreen = false,
        active = false,
        blocksFront = false,
    )

    private val marker = LastLeftForApp(
        packageName = "com.example.a",
        activityName = "com.example.a.MainActivity",
        elapsedAt = 1_000L,
        sameBoot = true,
    )

    private fun decide(
        front: FrontKind,
        marker: LastLeftForApp? = this.marker,
        policy: PolicyGateState = noPolicy,
        stockVisitActive: Boolean = false,
        origin: RevivalOrigin = RevivalOrigin.AUTO,
    ) = RevivalGate.decide(origin, front, marker, policy, stockVisitActive)

    @Test
    fun recordedAppStays() {
        assertEquals(RevivalAction.LEAVE_FRONT, decide(FrontKind.RECORDED_APP))
    }

    @Test
    fun otherAppWithMarkerIsPushed() {
        assertEquals(RevivalAction.SHOW_AERIAL, decide(FrontKind.OTHER_APP))
    }

    @Test
    fun otherAppWithoutMarkerKeepsExistingBehaviour() {
        assertEquals(RevivalAction.CONTINUE, decide(FrontKind.OTHER_APP, marker = null))
    }

    @Test
    fun systemSurfaceAboveRecordedAppStays() {
        assertEquals(RevivalAction.LEAVE_FRONT, decide(FrontKind.SYSTEM_SURFACE))
    }

    @Test
    fun systemSurfaceWithoutMarkerKeepsExistingBehaviour() {
        assertEquals(RevivalAction.CONTINUE, decide(FrontKind.SYSTEM_SURFACE, marker = null))
    }

    @Test
    fun unknownFrontWithMarkerStays() {
        assertEquals(RevivalAction.LEAVE_FRONT, decide(FrontKind.UNKNOWN))
    }

    @Test
    fun unknownFrontWithoutMarkerKeepsExistingBehaviour() {
        assertEquals(RevivalAction.CONTINUE, decide(FrontKind.UNKNOWN, marker = null))
    }

    @Test
    fun stockHomeIsTakenOver() {
        assertEquals(RevivalAction.CONTINUE, decide(FrontKind.STOCK_HOME))
    }

    @Test
    fun nothingInFrontIsTakenOver() {
        assertEquals(RevivalAction.CONTINUE, decide(FrontKind.NOTHING))
    }

    @Test
    fun aerialInFrontDoesNothing() {
        assertEquals(RevivalAction.CONTINUE, decide(FrontKind.AERIAL))
    }

    @Test
    fun lockScreenWinsOverRecordedApp() {
        assertEquals(
            RevivalAction.SHOW_AERIAL,
            decide(FrontKind.RECORDED_APP, policy = noPolicy.copy(lockScreen = true)),
        )
    }

    @Test
    fun policyBlockedAppIsReplacedByAerial() {
        assertEquals(
            RevivalAction.SHOW_AERIAL,
            decide(FrontKind.OTHER_APP, policy = noPolicy.copy(active = true, blocksFront = true)),
        )
    }

    @Test
    fun policyBlockWinsOverAnUnreadableFront() {
        assertEquals(
            RevivalAction.SHOW_AERIAL,
            decide(FrontKind.UNKNOWN, policy = noPolicy.copy(active = true, blocksFront = true)),
        )
    }

    @Test
    fun policyBlockWinsOverTheStockLauncherVisit() {
        assertEquals(
            RevivalAction.SHOW_AERIAL,
            decide(
                FrontKind.STOCK_HOME,
                policy = noPolicy.copy(active = true, blocksFront = true),
                stockVisitActive = true,
            ),
        )
    }

    @Test
    fun policyNotLoadedButMayExistFailsClosed() {
        assertEquals(
            RevivalAction.SHOW_AERIAL,
            decide(
                FrontKind.RECORDED_APP,
                policy = noPolicy.copy(loaded = false, mayExist = true),
            ),
        )
    }

    @Test
    fun policyNotLoadedAndAbsentKeepsRecordedApp() {
        assertEquals(
            RevivalAction.LEAVE_FRONT,
            decide(
                FrontKind.RECORDED_APP,
                policy = noPolicy.copy(loaded = false, mayExist = false),
            ),
        )
    }

    @Test
    fun activePolicyTakesUnknownFrontWithoutMarker() {
        assertEquals(
            RevivalAction.SHOW_AERIAL,
            decide(
                FrontKind.UNKNOWN,
                marker = null,
                policy = noPolicy.copy(active = true),
            ),
        )
    }

    @Test
    fun activePolicyKeepsUnknownFrontForRecordedApp() {
        assertEquals(
            RevivalAction.LEAVE_FRONT,
            decide(FrontKind.UNKNOWN, policy = noPolicy.copy(active = true)),
        )
    }

    @Test
    fun stockLauncherVisitDoesNotStartAerial() {
        assertEquals(RevivalAction.LEAVE_FRONT, decide(FrontKind.OTHER_APP, stockVisitActive = true))
    }

    @Test
    fun nonAutomaticOriginIsNeverGated() {
        assertEquals(
            RevivalAction.CONTINUE,
            decide(FrontKind.OTHER_APP, origin = RevivalOrigin.OTHER),
        )
    }

    @Test
    fun earlyStartWithoutMarkerKeepsExistingBehaviour() {
        assertEquals(
            true,
            RevivalGate.earlyStartAllowed(null, null, "app.orchard.tv", stockHome = false, launchableApp = false),
        )
    }

    @Test
    fun earlyStartWaitsForTheRecordedApp() {
        assertEquals(
            false,
            RevivalGate.earlyStartAllowed(marker, marker.packageName, "app.orchard.tv", stockHome = false, launchableApp = true),
        )
    }

    @Test
    fun earlyStartWaitsWithoutAFront() {
        assertEquals(
            false,
            RevivalGate.earlyStartAllowed(marker, null, "app.orchard.tv", stockHome = false, launchableApp = false),
        )
    }

    @Test
    fun earlyStartWaitsAboveASystemSurface() {
        assertEquals(
            false,
            RevivalGate.earlyStartAllowed(marker, "com.android.permissioncontroller", "app.orchard.tv", stockHome = false, launchableApp = false),
        )
    }

    @Test
    fun earlyStartTakesHomeBackFromAnotherApp() {
        assertEquals(
            true,
            RevivalGate.earlyStartAllowed(marker, "com.example.b", "app.orchard.tv", stockHome = false, launchableApp = true),
        )
    }

    @Test
    fun earlyStartTakesHomeBackFromTheStockLauncher() {
        assertEquals(
            true,
            RevivalGate.earlyStartAllowed(marker, "com.mitv.tvhome", "app.orchard.tv", stockHome = true, launchableApp = false),
        )
    }

    @Test
    fun earlyStartHasNoMarkerEvidenceFromAnotherBoot() {
        assertEquals(
            true,
            RevivalGate.earlyStartAllowed(marker.copy(sameBoot = false), marker.packageName, "app.orchard.tv", stockHome = false, launchableApp = true),
        )
    }

    @Test
    fun markerFromAnotherBootIsNoEvidence() {
        assertEquals(
            RevivalAction.CONTINUE,
            decide(FrontKind.OTHER_APP, marker = marker.copy(sameBoot = false)),
        )
        assertEquals(
            RevivalAction.CONTINUE,
            decide(FrontKind.UNKNOWN, marker = marker.copy(sameBoot = false)),
        )
    }
}
