package com.planyourtv.policy

import com.planyourtv.policy.quiz.RemoteQuizConfig

/**
 * A member of the bound garden family, as listed by `/devices/current/config`.
 *
 * <p>Since 2026-11-21 the TV desktop identity is the garden family member, not a locally
 * created one: the backend resolves `device_list.active_member_id` (or derives the first
 * allowed member read-only) and ships the roster alongside the grant.
 */
data class CloudMember(
    val memberId: Long,
    val name: String = "",
    val avatarUrl: String = "",
    val isChild: Boolean? = null,
)

/**
 * One `/devices/current/config` response. The redemption grant, the V6 policy node, and the
 * quiz config are independent: any of them may be absent while the others remain valid.
 *
 * <p>The cloud member fields are pure identity: they survive a grant ending, because the
 * desktop still has to show who is allowed to use it and let the family switch members.
 * Older backends omit them entirely; those stay at the defaults and the desktop keeps its
 * local behaviour.
 */
data class PolicySyncSnapshot(
    val grant: PolicyConfig?,
    val remotePolicy: RemotePolicy?,
    val quiz: RemoteQuizConfig? = null,
    val activeMemberId: Long? = null,
    val activeMemberName: String = "",
    val activeMemberIcon: String = "",
    val members: List<CloudMember> = emptyList(),
)

/**
 * Network port owned by the app module. Keeping HTTP out of :policy lets the state machine be
 * tested without Android and keeps Aerial's BuildConfig out of the product module.
 */
interface PolicySyncHost {
    suspend fun fetchConfig(deviceNo: String): Result<PolicySyncSnapshot>

    suspend fun reportUsage(
        deviceNo: String,
        recordId: Int,
        durationSeconds: Long,
        timeout: Boolean,
    ): Result<Unit>
}
