package com.planyourtv.policy

/**
 * One-time privacy consent for uploading the TV's local policy to the cloud. The consent is
 * product-scoped and survives re-binding, so the parent sees the disclaimer once rather than
 * on every remote-management click.
 */
object PolicyConsentStore {
    fun cloudUploadAccepted(): Boolean =
        TvStore.getBoolean(PolicyKeys.CLOUD_UPLOAD_CONSENT, false)

    fun acceptCloudUpload() {
        TvStore.putBoolean(PolicyKeys.CLOUD_UPLOAD_CONSENT, true)
        TvStore.sync()
    }

    fun resetCloudUploadConsent() {
        TvStore.remove(PolicyKeys.CLOUD_UPLOAD_CONSENT)
        TvStore.sync()
    }
}
