package app.orchard.tv.policy

import android.app.Application
import android.os.PowerManager
import com.planyourtv.policy.HostedFruitFacts
import com.planyourtv.policy.LocalPolicyStore
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalRuleSets
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.TvStore
import com.planyourtv.policy.UnlockRouteReason
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The hosted fruit projection is a HOSTED cache like any other: it drives the cloud rule guard's
 * wheel, is scoped to the cloud member, and never leaks into the LOCAL ledger after a switch or
 * an unbind.
 */
@RunWith(RobolectricTestRunner::class)
class HostedFruitRuntimeTest {
    private val pin = "246810"
    private val videoPackage = "com.example.video"
    private val memberId = 42L
    private lateinit var app: Application

    private fun state() = LocalPolicyRuntime.state.value

    private fun rules(fruit: Boolean) = LocalRules(
        dayGroups = LocalRules.fromLegacyWeekdayWeekend(
            weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekdayLimitMinutes = 2,
            weekendLimitMinutes = 2,
        ),
        sessionCapMinutes = 5,
        restMinutes = 0,
        allowedPackages = setOf(videoPackage),
        fruitExchangeEnabled = fruit,
        fruitExchangeDailyLimitCount = 0,
    )

    /**
     * Cloud rule snapshots deliberately do not carry the fruit switch; the orchard's device
     * context is the only hosted authority. The LOCAL partition keeps its own switch so the
     * switch-back test proves the two accounts never mix.
     */
    private fun localRules() = rules(fruit = true)

    private fun cloudRules() = rules(fruit = false)

    private fun facts(
        member: Long = memberId,
        route: Boolean = true,
        rate: Int = 2,
        available: Long = 1234L,
        maxFruits: Int = 5,
        dailyRemaining: Int = 3,
    ) = HostedFruitFacts(
        memberId = member,
        routeEnabled = route,
        exchangeRateMinutesPerFruit = rate,
        availableFruitHundredths = available,
        maxFruits = maxFruits,
        dailyRemaining = dailyRemaining,
        fetchedAtEpochMs = System.currentTimeMillis(),
    )

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        LocalPolicyRuntime.initialize(app)
        assertNotNull(LocalPolicyRuntime.completeSetup(pin, localRules()))
        assertTrue(
            LocalPolicyRuntime.enterHostedMode(LocalRuleSets.from(cloudRules()), 1L, memberId),
        )
        LocalPolicyRuntime.activate()
    }

    @After
    fun tearDown() {
        LocalPolicyRuntime.debugResetForTest()
    }

    @Test
    fun theHostedContextDrivesTheWheelWhileTheCloudSnapshotHasNoFruitSwitch() {
        assertTrue(LocalPolicyRuntime.setHostedFruitFacts(facts()))

        assertEquals(1234L, state().fruitAvailableFruitHundredths)
        assertEquals(2, state().fruitExchangeRateMinutesPerFruit)
        val fruit = requireNotNull(state().unlockRoutePlan).fruit
        assertEquals(UnlockRouteReason.READY, fruit.reason)
        assertTrue(fruit.visible)
        assertTrue(fruit.available)
        // The backend's maxFruit is the ceiling: 5 fruits x 2 min. The LOCAL 5-minute session
        // cap and 2-minute daily quota must not shrink it.
        assertEquals(5 * 2 * 60L, fruit.grantSeconds)
        assertEquals(3, fruit.remaining)
    }

    @Test
    fun aDifferentMembersCachedAccountIsRejected() {
        assertTrue(LocalPolicyRuntime.setHostedFruitFacts(facts()))

        assertFalse(LocalPolicyRuntime.setHostedFruitFacts(facts(member = 7L)))
        assertEquals(2, state().fruitExchangeRateMinutesPerFruit)
        assertEquals(1234L, state().fruitAvailableFruitHundredths)
    }

    @Test
    fun aDisabledCloudRouteHidesTheCardEvenWithABalance() {
        assertTrue(
            LocalPolicyRuntime.setHostedFruitFacts(
                facts(route = false, available = 5000L, maxFruits = 0),
            ),
        )

        val fruit = requireNotNull(state().unlockRoutePlan).fruit
        assertFalse(fruit.visible)
        assertEquals(UnlockRouteReason.SWITCH_OFF, fruit.reason)
    }

    @Test
    fun switchingBackToLocalNeverShowsTheHostedAccount() {
        assertTrue(LocalPolicyRuntime.setHostedFruitFacts(facts()))
        // The production LOCAL switch reloads the local partition; the hosted template in
        // memory must not survive it.
        LocalPolicyRuntime.setSource(PolicySource.LOCAL)
        LocalPolicyRuntime.reloadActiveSubject()

        assertEquals(0L, state().fruitAvailableFruitHundredths)
        assertEquals(0, state().fruitExchangeRateMinutesPerFruit)
        // The LOCAL account is absent, not a copy of the cloud balance.
        assertEquals(
            UnlockRouteReason.ACCOUNT_DISABLED,
            requireNotNull(state().unlockRoutePlan).fruit.reason,
        )
    }

    @Test
    fun unbindDropsTheCachedAccountWithoutTouchingLocalState() {
        assertTrue(LocalPolicyRuntime.setHostedFruitFacts(facts()))
        assertEquals(2, LocalPolicyRuntime.currentHostedFruitFacts()?.exchangeRateMinutesPerFruit)

        LocalPolicyRuntime.clearHostedExecution()

        assertNull(LocalPolicyRuntime.currentHostedFruitFacts())
        assertNull(LocalPolicyStore.loadHostedFruitFacts("member:$memberId"))

        LocalPolicyRuntime.setSource(PolicySource.LOCAL)
        assertEquals(0L, state().fruitAvailableFruitHundredths)
        assertEquals(0, state().fruitExchangeRateMinutesPerFruit)
    }
}