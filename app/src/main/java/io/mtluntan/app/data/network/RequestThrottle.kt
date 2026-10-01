package io.mtluntan.app.data.network

import java.util.ArrayDeque
import java.util.Deque

/**
 * Global request throttle with a foreground lane.
 *
 * User-initiated navigation (opening a thread, opening a profile) must never
 * wait: those requests are marked foreground and only accounted. Background
 * traffic (badge polling, batch scans, pre-fetch) is limited by a token bucket
 * so it cannot starve or pile up and trigger the site's anti-hotlink / WAF
 * "too many requests" behaviour.
 */
object RequestThrottle {

    /** Burst capacity: allowed burst requests */
    private const val BURST_CAPACITY = 20
    /** Token refill interval: ~85 requests/minute sustained */
    private const val REFILL_INTERVAL_MS = 700L
    /** Sliding window length */
    private const val WINDOW_MS = 60_000L
    /** Hard cap per window (only constrains the background lane) */
    private const val MAX_PER_WINDOW = 90
    /** Longest a background request may be deferred */
    private const val MAX_WAIT_MS = 1_200L

    private val window: Deque<Long> = ArrayDeque()
    private val lock = Object()

    private var tokens: Double = BURST_CAPACITY.toDouble()
    private var lastRefillAt: Long = System.currentTimeMillis()

    private var throttledCount = 0L
    private var throttledTotalMs = 0L

    /**
     * Acquire a send permit. Returns the number of ms actually waited
     * (0 means not throttled). Foreground (user navigation) requests never
     * wait; background requests are constrained by the token bucket.
     */
    fun acquire(foreground: Boolean = false): Long {
        if (foreground) {
            synchronized(lock) {
                refill()
                trimWindow()
                tokens = (tokens - 1.0).coerceAtLeast(0.0)
                window.addLast(System.currentTimeMillis())
            }
            return 0L
        }

        var waited = 0L
        while (true) {
            var sleep: Long
            synchronized(lock) {
                val now = System.currentTimeMillis()
                refill()
                trimWindow()

                val hasToken = tokens >= 1.0
                val windowOk = window.size < MAX_PER_WINDOW

                if ((hasToken && windowOk) || waited >= MAX_WAIT_MS) {
                    tokens = (tokens - 1.0).coerceAtLeast(0.0)
                    window.addLast(now)
                    if (waited > 0) {
                        throttledCount++
                        throttledTotalMs += waited
                    }
                    return waited
                }

                val tokenWait = if (hasToken) 0L
                else Math.ceil((1.0 - tokens) * REFILL_INTERVAL_MS).toLong()
                var windowWait = 0L
                if (!windowOk) {
                    val oldest = window.peekFirst()
                    if (oldest != null) windowWait = WINDOW_MS - (now - oldest) + 10L
                }
                sleep = Math.max(1L, Math.max(tokenWait, windowWait))
                sleep = Math.min(sleep, MAX_WAIT_MS - waited)
            }
            try {
                Thread.sleep(sleep)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return waited
            }
            waited += sleep
        }
    }

    fun stats(): String = synchronized(lock) {
        if (throttledCount > 0) "后台累计排队 $throttledCount 次 / ${throttledTotalMs}ms" else ""
    }

    private fun refill() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRefillAt
        if (elapsed > 0) {
            tokens = Math.min(BURST_CAPACITY.toDouble(), tokens + elapsed / REFILL_INTERVAL_MS.toDouble())
            lastRefillAt = now
        }
    }

    private fun trimWindow() {
        val now = System.currentTimeMillis()
        while (!window.isEmpty()) {
            val oldest = window.peekFirst() ?: break
            if (now - oldest < WINDOW_MS) break
            window.pollFirst()
        }
    }
}
