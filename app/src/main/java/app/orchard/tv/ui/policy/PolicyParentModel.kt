package app.orchard.tv.ui.policy

/** Logical focus targets. Compose focus search is never used to move between them. */
internal sealed interface ParentFocusTarget {
    data object Switch : ParentFocusTarget
    data object Remote : ParentFocusTarget
    data object WeChat : ParentFocusTarget
    data object MigrationRetry : ParentFocusTarget
    data object WaitConfigRetry : ParentFocusTarget
    data object SummaryRead : ParentFocusTarget
    data class LocalRow(val key: String) : ParentFocusTarget
}

/**
 * The V29 rail keeps the child-mode switch and remote management; the WeChat alerts row is the
 * one local feature that stays on the TV rail, below remote management when it is visible.
 */
internal fun parentRailTargets(
    childOn: Boolean,
    remoteVisible: Boolean,
): List<ParentFocusTarget> = buildList {
    add(ParentFocusTarget.Switch)
    if (childOn && remoteVisible) add(ParentFocusTarget.Remote)
    // WeChat chat control is a child-mode entry that works without a backend.
    if (childOn) add(ParentFocusTarget.WeChat)
}

/** The right-hand pane. The rail no longer selects a guard mode. */
internal enum class ParentPaneKind {
    OFF,
    LOCAL,
    BIND,
    MIGRATION,
    WAIT_CONFIG,
    SUMMARY,
    PARENT_WEB_QR,
    WECHAT,
}

internal enum class ParentZone { RAIL, PANE }

/** Left always returns to the rail row that opened the pane. */
internal enum class ParentRailOrigin { SWITCH, REMOTE, WECHAT }
