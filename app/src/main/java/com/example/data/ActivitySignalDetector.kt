package com.example.data

import android.content.Intent

internal object ActivitySignalClassifier {
    fun reasonForBroadcast(action: String?): String? = when (action) {
        Intent.ACTION_USER_PRESENT -> "휴대전화 잠금 해제 감지"
        else -> null
    }
}

/** A screen wake alone is not evidence; require USER_PRESENT or an observed lock transition. */
internal class UserPresenceDetector {
    private var lockedObservedAtMs: Long? = null
    private var lastDetectionMs = Long.MIN_VALUE

    fun reset(initiallyLocked: Boolean = false, nowMs: Long = 0L) {
        lockedObservedAtMs = nowMs.takeIf { initiallyLocked }
        lastDetectionMs = Long.MIN_VALUE
    }

    fun record(interactive: Boolean, locked: Boolean, explicitUserPresent: Boolean, nowMs: Long): Boolean {
        if (locked) lockedObservedAtMs = nowMs
        if (!interactive || locked) return false
        // A resumed service must not turn an hours-old unlock into activity at resume time.
        val recentLock = lockedObservedAtMs?.let { nowMs - it in 0L..30_000L } == true
        lockedObservedAtMs = null
        if (!explicitUserPresent && !recentLock) return false
        if (lastDetectionMs != Long.MIN_VALUE && nowMs >= lastDetectionMs &&
            nowMs - lastDetectionMs < 2_000L
        ) return false
        lastDetectionMs = nowMs
        return true
    }
}

/** Cumulative steps need a fresh baseline; historic steps must never reset the timer. */
internal class RepeatedStepCounterDetector {
    private var baselineCount: Long? = null
    private var baselineMs = 0L
    private var lastCount = 0L
    private var lastSampleMs = 0L
    private var lastDetectionMs = Long.MIN_VALUE

    fun record(value: Float, nowMs: Long): Boolean {
        if (!value.isFinite() || value < 0f || value % 1f != 0f) return false
        val count = value.toLong()
        if (baselineCount == null || nowMs < lastSampleMs || count < lastCount) {
            if (nowMs < lastSampleMs) lastDetectionMs = Long.MIN_VALUE
            rebase(count, nowMs)
            return false
        }
        if (nowMs == lastSampleMs || count == lastCount) return false
        lastCount = count
        lastSampleMs = nowMs
        val span = nowMs - baselineMs
        if (span > 30_000L ||
            (lastDetectionMs != Long.MIN_VALUE && nowMs - lastDetectionMs < 60_000L)
        ) {
            rebase(count, nowMs)
            return false
        }
        if (span < 2_000L || count - checkNotNull(baselineCount) < 3L) return false
        lastDetectionMs = nowMs
        rebase(count, nowMs)
        return true
    }

    fun reset() {
        baselineCount = null
        lastCount = 0L
        lastSampleMs = 0L
        baselineMs = 0L
        lastDetectionMs = Long.MIN_VALUE
    }

    private fun rebase(count: Long, nowMs: Long) {
        baselineCount = count
        lastCount = count
        baselineMs = nowMs
        lastSampleMs = nowMs
    }
}

internal class RepeatedMotionDetector(
    private val requiredEvents: Int = 4,
    private val minimumSpanMs: Long = 3_000L,
    private val windowMs: Long = 10_000L,
    private val cooldownMs: Long = 60_000L
) {
    private val eventTimes = ArrayDeque<Long>()
    private var lastDetectionMs = Long.MIN_VALUE

    fun record(nowMs: Long): Boolean {
        if ((eventTimes.isNotEmpty() && nowMs < eventTimes.last()) ||
            (lastDetectionMs != Long.MIN_VALUE && nowMs < lastDetectionMs)
        ) reset()
        if (lastDetectionMs != Long.MIN_VALUE && nowMs - lastDetectionMs < cooldownMs) return false

        while (eventTimes.isNotEmpty() && nowMs - eventTimes.first() > windowMs) {
            eventTimes.removeFirst()
        }
        eventTimes.addLast(nowMs)

        val repeated = eventTimes.size >= requiredEvents &&
            nowMs - eventTimes.first() >= minimumSpanMs
        if (repeated) {
            lastDetectionMs = nowMs
            eventTimes.clear()
        }
        return repeated
    }

    fun reset() {
        eventTimes.clear()
        lastDetectionMs = Long.MIN_VALUE
    }
}
