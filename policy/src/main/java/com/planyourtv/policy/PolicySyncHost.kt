package com.planyourtv.policy

import com.planyourtv.policy.quiz.RemoteQuizConfig

/**
 * One `/devices/current/config` response. The redemption grant, the V6 policy node, and the
 * quiz config are independent: any of them may be absent while the others remain valid.
 */
data class PolicySyncSnapshot(
    val grant: PolicyConfig?,
    val remotePolicy: RemotePolicy?,
    val quiz: RemoteQuizConfig? = null,
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
