package app.orchard.tv.policy.web

import android.content.Context

/**
 * Build-time switch for the V27 web parent center.
 *
 * While false the whole surface is invisible and inert: `/quiz` keeps its old LOCAL-only
 * behaviour, `/api/parent/…` answers 404, the page shows no entry, and the TV shows no
 * "configure on web" row. This is deliberately a constant, not a runtime setting; flip it only
 * after release-APK verification on a real TV.
 */
object ParentWebCenter {
    const val ENABLED = true

    fun install(context: Context) {
        if (!ENABLED) return
        ParentWebAccess.install(context.applicationContext)
        ParentCenterActions.install(context.applicationContext)
    }

    fun stop() {
        if (!ENABLED) return
        ParentWebAccess.stop()
        ParentCenterActions.stop()
        ParentEditorLease.invalidate("stop")
    }
}
