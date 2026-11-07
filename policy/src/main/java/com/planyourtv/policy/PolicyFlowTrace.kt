package com.planyourtv.policy

import com.planyourtv.log.TvLog

/**
 * Domain tag for the hosted grant/session flow. The shared [TvLog] owns enablement, formatting
 * and production gating; this facade keeps call sites short and the logcat filter stable:
 *
 *   adb logcat -s PolicyFlow
 */
object PolicyFlowTrace {
    const val TAG = "PolicyFlow"

    /** Hot paths check this before formatting fields; production returns false immediately. */
    fun enabled(): Boolean = TvLog.isEnabled()

    fun event(stage: String, vararg fields: Pair<String, Any?>) {
        TvLog.event(TAG, stage, *fields)
    }
}
