package app.orchard.tv.policy

import android.util.Log
import com.planyourtv.policy.RuleUsageEvent
import com.planyourtv.policy.RuleUsageEventType
import com.planyourtv.policy.RuleUsageQueue
import com.planyourtv.policy.RuleUsageSink
import com.planyourtv.policy.RuleUsageStore
import com.planyourtv.policy.TvStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Rule usage outbox for the remote rule guard.
 *
 * The TV remains the execution authority. Reporting is best-effort statistics: the queue is
 * persisted before the network call, a failed batch is retried, and the local countdown never
 * waits for the report. The sink is attached only while remote rule mode owns the guard, so an
 * offline/local-only TV never accumulates cloud usage.
 */
object RuleUsageReporter {
    private const val TAG = "RuleUsageReporter"
    private const val RETRY_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reporting = AtomicBoolean(false)
    private var job: Job? = null
    private var deviceNo = ""
    private var queue = RuleUsageQueue.empty()
    private var queueLoaded = false

    private val sink = RuleUsageSink { event ->
        enqueue(event)
    }

    /**
     * Starts the outbox and attaches it to the rule engine. Idempotent.
     *
     * The engine callback takes the engine lock and then this object's lock, so [setUsageSink]
     * must run outside this object's monitor to keep the lock order one-way.
     */
    fun start(deviceNo: String) {
        synchronized(this) {
            if (!TvStore.isReady) return
            if (deviceNo.isNotBlank()) this.deviceNo = deviceNo
            if (!queueLoaded) {
                queue = RuleUsageStore.load()
                queueLoaded = true
            }
            if (job == null) {
                job = scope.launch {
                    while (isActive) {
                        delay(RETRY_MS)
                        flushOnce()
                    }
                }
            }
        }
        LocalPolicyRuntime.setUsageSink(sink)
        scope.launch { flushOnce() }
    }

    @Synchronized
    fun updateDevice(deviceNo: String) {
        if (deviceNo.isNotBlank()) this.deviceNo = deviceNo
    }

    /** Detaches from the engine; queued events stay persisted until the next rule session. */
    fun stop() {
        LocalPolicyRuntime.setUsageSink(null)
        synchronized(this) {
            job?.cancel()
            job = null
        }
    }

    /** Explicit reset when the device is unbound; never called by a normal rule end. */
    @Synchronized
    fun clear() {
        queue = RuleUsageQueue.empty()
        queueLoaded = true
        RuleUsageStore.clear()
    }

    private fun enqueue(event: RuleUsageEvent) {
        val shouldFlush = synchronized(this) {
            queue.enqueue(event)
            RuleUsageStore.save(queue)
            event.type == RuleUsageEventType.END
        }
        if (shouldFlush) scope.launch { flushOnce() }
    }

    private suspend fun flushOnce() {
        if (!reporting.compareAndSet(false, true)) return
        try {
            val (target, events) = synchronized(this) {
                deviceNo to queue.snapshot()
            }
            if (target.isBlank() || events.isEmpty()) return
            PolicyCloud.reportRuleUsage(target, events)
                .onSuccess {
                    synchronized(this) {
                        queue.acknowledge(events)
                        RuleUsageStore.save(queue)
                    }
                }
                .onFailure { Log.w(TAG, "rule usage report failed; will retry", it) }
        } finally {
            reporting.set(false)
        }
    }
}
