package com.example.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafetySmsEventTest {
    @Test
    fun parsesEmergencySosAndDailyEvents() {
        assertEquals(
            SafetySmsEvent(SafetySmsEventType.EMERGENCY, 1_000L, 7),
            SafetySmsEvent.parse("emergency:1000:7")
        )
        assertEquals(
            SafetySmsEvent(SafetySmsEventType.SOS, 2_000L, 8),
            SafetySmsEvent.parse("sos:2000:8")
        )
        assertEquals(
            SafetySmsEvent(SafetySmsEventType.DAILY, 3_000L, 9),
            SafetySmsEvent.parse("daily:3000:9")
        )
    }

    @Test
    fun rejectsTestAndMalformedEvents() {
        assertNull(SafetySmsEvent.parse("test:1000:7"))
        assertNull(SafetySmsEvent.parse("emergency:bad:7"))
        assertNull(SafetySmsEvent.parse("sos:1000:0"))
    }
}
