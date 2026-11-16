package app.orchard.tv.policy

import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import app.orchard.tv.BuildConfig
import com.planyourtv.policy.Activation
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicyModeStore
import com.planyourtv.policy.RejectReason
import com.planyourtv.policy.SessionStartResult
import com.planyourtv.policy.TvStore
import com.planyourtv.policy.PolicySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * V29 made local starts obey the daily quota — and its own comment promises quiz rewards are
 * "already capped by their own maxEarnedMinutes" instead. The reproduced bug: once the day's
 * quota is spent, completeReview's grant was refused with DAILY_LIMIT and a child who had just
 * answered 14/15 correctly got nothing. These cases pin the split: parent-chosen time obeys the
 * quota, quiz-earned time does not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizRewardDailyQuotaTest {

    private val pin = "246810"

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        LocalPolicyRuntime.initialize(app)
        assertNotNull(
            LocalPolicyRuntime.completeSetup(
                pin,
                LocalRules(
                    weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60 - 1)),
                    weekendWindows = listOf(LocalTimeWindow(0, 24 * 60 - 1)),
                    autoDailyLimitMinutes = 10,
                    sessionLimitMinutes = 20,
                    restMinutes = 0,
                    allowedPackages = setOf("com.example.video"),
                ),
            ),
        )
        LocalPolicyRuntime.setSource(PolicySource.LOCAL)
        LocalPolicyRuntime.activate()
        PolicyModeStore.save(PolicyMode.LOCAL)
        assertEquals(Activation.MANUAL, LocalPolicyRuntime.state.value.activation)
    }

    private fun snapshot() =
        "state(used=${LocalPolicyRuntime.state.value.dailyUsedSeconds}, " +
            "total=${LocalPolicyRuntime.state.value.totalSeconds})"

    /** Burns the day's quota deterministically through the ledger, not wall time. */
    private fun exhaustQuota() {
        LocalPolicyRuntime.debugRestoreUsage(10 * 60L)
    }

    @Test
    fun parentStartIsRefusedOnceTheDailyQuotaIsSpent() {
        exhaustQuota()
        val refused = LocalPolicyRuntime.startManualSession(5 * 60L)
        assertEquals(snapshot(), SessionStartResult.Rejected(RejectReason.DAILY_LIMIT), refused)
    }

    @Test
    fun quizRewardStartsOnItsOwnBudgetAfterTheQuotaIsSpent() {
        exhaustQuota()
        val reward = LocalPolicyRuntime.startQuizRewardSession(8 * 60L)
        assertEquals(snapshot(), SessionStartResult.Started, reward)
        assertEquals(snapshot(), 8 * 60L, LocalPolicyRuntime.state.value.totalSeconds)
    }

    @Test
    fun quizRewardKeepsEveryOtherAdmissionCheck() {
        LocalPolicyRuntime.startQuizRewardSession(8 * 60L)
        val busy = LocalPolicyRuntime.startQuizRewardSession(5 * 60L)
        assertEquals(SessionStartResult.Rejected(RejectReason.BUSY), busy)
    }
}
