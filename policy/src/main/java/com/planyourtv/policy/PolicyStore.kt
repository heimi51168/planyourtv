package com.planyourtv.policy

/**
 * Storage contract for the future policy engine. No implementation is shipped
 * in the skeleton: persistence, payload validation and versioning are product
 * decisions that come with the policy contract.
 */
interface PolicyStore {
    fun snapshot(): PolicySnapshot?

    /** Returns false when the snapshot is older than the stored version. */
    fun apply(snapshot: PolicySnapshot): Boolean
}
