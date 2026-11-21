package app.orchard.tv.quiz

import android.util.Log
import app.orchard.tv.policy.FamilyHub
import com.planyourtv.policy.MemberStatus
import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizPackageSource

/**
 * One-time V41 cleanup for members other than the default child.
 *
 * Before V41 every manual-import tag was global, so an existing second child's
 * QCD may still contain level packages that pull from those tags. V41 gives the
 * old tags and their questions to the default member; the sibling gets neither,
 * so leaving those packages behind would show permanently empty levels.
 * Course and wrong-review levels are kept.
 *
 * The marker is only written after every member was handled. A storage failure
 * is retried on the next boot instead of freezing a half-migrated roster.
 */
object QuizOwnershipMigration {
    private const val TAG = "QuizOwnershipMigration"
    private const val KEY_DONE = "quiz.ownership.migration.v1"

    fun reconcile(): Boolean {
        if (!TvStore.isReady) return false
        if (TvStore.getString(KEY_DONE) == "1") return true
        val family = FamilyHub.state.value
        if (!family.ready) return false
        var ok = true
        family.members
            .filter { it.status == MemberStatus.ACTIVE && it.userId != family.defaultUserId }
            .forEach { member ->
                when (val loaded = QuizConfigDocumentStore.localDocumentFor(member.userId)) {
                    is QuizDocLoadResult.Ok -> {
                        val extension = QuizLevelPackageStore.read(loaded.document)
                        if (extension is QuizLevelPackageStore.Extension.Present) {
                            val kept = extension.packages.filterNot { pkg ->
                                pkg.sources.any { it is QuizPackageSource.Collection }
                            }
                            if (kept.size != extension.packages.size) {
                                val saved = QuizConfigDocumentStore.updateLevelPackagesFor(
                                    member.userId,
                                    kept,
                                )
                                if (saved !is QuizDocSaveResult.Ok) {
                                    Log.w(TAG, "cannot strip private tag levels for a member")
                                    ok = false
                                }
                            }
                        }
                    }
                    // No document yet: provisioning writes a clean one later.
                    is QuizDocLoadResult.Unreadable -> Unit
                    QuizDocLoadResult.Storage -> ok = false
                }
            }
        if (ok) TvStore.putStringDurable(KEY_DONE, "1")
        return ok
    }
}
