package com.example.data

import android.content.Intent

internal object ActivitySignalClassifier {
    fun reasonForBroadcast(action: String?): String? = when (action) {
        Intent.ACTION_USER_PRESENT -> "휴대전화 잠금 해제 감지"
        else -> null
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
        if (eventTimes.isNotEmpty() && nowMs < eventTimes.last()) eventTimes.clear()
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
}
