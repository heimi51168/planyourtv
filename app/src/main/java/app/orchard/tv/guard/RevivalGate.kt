package app.orchard.tv.guard

/**
 * The app the viewer opened from PlanYourTV, as written by [MainActivity.onPause] once
 * PlanYourTV really left the front. Kept in GuardSetup's `system` preferences, not MMKV:
 * this marker is read by the guard and the boot receiver before, or without, the policy store.
 */
internal data class LastLeftForApp(
    val packageName: String,
    val activityName: String?,
    val elapsedAt: Long,
    val sameBoot: Boolean,
)

/** Only an automatic revival is gated. Boots, wakes, Home and policy keep their own paths. */
internal enum class RevivalOrigin { AUTO, OTHER }

/** What a front signal says, not just "the recorded app or not". */
internal enum class FrontKind {
    AERIAL,
    RECORDED_APP,
    OTHER_APP,
    STOCK_HOME,
    SYSTEM_SURFACE,
    NOTHING,
    UNKNOWN,
}

internal enum class RevivalAction {
    /** Bring PlanYourTV forward now. */
    SHOW_AERIAL,

    /** The app in front has a claim to stay; do not take Home. */
    LEAVE_FRONT,

    /** No new decision: keep the existing boot/wake/exceptions path. */
    CONTINUE,
}

internal data class PolicyGateState(
    val loaded: Boolean,
    val mayExist: Boolean,
    val lockScreen: Boolean,
    val active: Boolean,
    val blocksFront: Boolean,
)

/**
 * Decides what an automatic revival does with the app in front.
 *
 * This is deliberately a pure function: the guard's existing boot, wake, stock-launcher visit,
 * `revivedOften` and unknown-front exceptions continue to own [RevivalAction.CONTINUE].
 * Without a marker nothing but an active policy changes the old behaviour; the marker is what
 * proves the viewer left PlanYourTV for an app of their own.
 */
internal object RevivalGate {
    /**
     * The listener may start Aerial before the guard binds when the stock launcher was just seen.
     * With a recorded app that is only safe when usage stats name a different app or the stock
     * launcher; the recorded app itself, a system surface above it and an unreadable front all
     * wait for the guard, which then decides with a window signal.
     */
    fun earlyStartAllowed(
        marker: LastLeftForApp?,
        front: String?,
        ownPackage: String,
        stockHome: Boolean,
        launchableApp: Boolean,
    ): Boolean {
        if (marker == null || !marker.sameBoot) return true
        if (front == null || front == ownPackage || front == marker.packageName) return false
        return stockHome || launchableApp
    }

    fun decide(
        origin: RevivalOrigin,
        front: FrontKind,
        marker: LastLeftForApp?,
        policy: PolicyGateState,
        stockVisitActive: Boolean,
    ): RevivalAction {
        if (origin != RevivalOrigin.AUTO) return RevivalAction.CONTINUE

        // Parental control is never allowed to be silenced by a revival. A policy that is
        // configured but not restored yet must fail closed rather than look like NONE.
        if (policy.lockScreen || policy.blocksFront) return RevivalAction.SHOW_AERIAL
        if (!policy.loaded && policy.mayExist) return RevivalAction.SHOW_AERIAL

        val recorded = marker?.takeIf { it.sameBoot }
        // Without evidence of an app the viewer opened, an active policy takes an unknown front.
        // With the marker, a delayed or missing usage stat is still the viewer's app.
        if (policy.active && front == FrontKind.UNKNOWN && recorded == null) return RevivalAction.SHOW_AERIAL

        // The stock launcher opened from its own tile stays until that visit ends.
        if (stockVisitActive) return RevivalAction.LEAVE_FRONT
        return when (front) {
            FrontKind.AERIAL, FrontKind.STOCK_HOME, FrontKind.NOTHING -> RevivalAction.CONTINUE
            FrontKind.RECORDED_APP -> RevivalAction.LEAVE_FRONT
            // Permission screens, choosers, input overlays and other windows without a launcher
            // entry belong to the app underneath; they are not proof that the viewer left it.
            // Without the marker there is no app underneath to protect: keep the old handling.
            FrontKind.SYSTEM_SURFACE -> if (recorded != null) RevivalAction.LEAVE_FRONT else RevivalAction.CONTINUE
            // A normal app that is not the recorded one: on LeTV this is the app opened from
            // eUI after the recorded app was left behind, so PlanYourTV takes Home back. Without
            // a marker there is no such evidence; keep the old "leave the app alone" behaviour.
            FrontKind.OTHER_APP -> if (recorded != null) RevivalAction.SHOW_AERIAL else RevivalAction.CONTINUE
            // With a same-boot marker and no stock home since, silence is safer than pulling the
            // viewer out of an app the window/usage-stats sources cannot name. Otherwise the
            // existing #63/#1430/#497 handling still applies.
            FrontKind.UNKNOWN -> if (recorded != null) RevivalAction.LEAVE_FRONT else RevivalAction.CONTINUE
        }
    }
}
