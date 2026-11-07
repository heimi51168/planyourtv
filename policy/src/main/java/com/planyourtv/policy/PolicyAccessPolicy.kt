package com.planyourtv.policy

/** The phone app's "all apps" sentinel; it is not a package name. */
const val ALL_VALID_APP = "all_valid_app"

/**
 * The access layer sits beside the session layer. It only decides which apps are usable while
 * the session is ACTIVE; it never starts, pauses or charges the session.
 */
interface PolicyAccessPolicy {
    /** True when the per-app policy must be consulted by HomeGuard. */
    val enabled: Boolean

    fun isAllowed(packageName: String): Boolean
}

/** Screen-level product: ACTIVE means the whole TV is usable. */
object ScreenAccessPolicy : PolicyAccessPolicy {
    override val enabled: Boolean = false

    override fun isAllowed(packageName: String): Boolean = true
}

/**
 * App-level future mode. The phone app's model is a device-wide quota plus a base allowlist and
 * the redeemed allowlist; per-app quotas are deliberately not represented here.
 */
class AppAccessPolicy(
    private val baseAllowlist: Set<String> = emptySet(),
    private val redeemedAllowlist: Set<String> = emptySet(),
) : PolicyAccessPolicy {
    private val allowAll = ALL_VALID_APP in redeemedAllowlist

    override val enabled: Boolean = true

    override fun isAllowed(packageName: String): Boolean =
        allowAll || packageName in baseAllowlist || packageName in redeemedAllowlist

    /** True when the grant means "all launchable apps", not a specific list. */
    val isAllValid: Boolean
        get() = allowAll
}
