package com.example.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSetupResolverTest {
    private val sim1 = SmsLine(subscriptionId = 11, slotIndex = 0, label = "SIM 1")
    private val sim2 = SmsLine(subscriptionId = 22, slotIndex = 1, label = "SIM 2")

    @Test
    fun smsUnsupportedDeviceIsBlocked() {
        val state = SmsSetupResolver.resolve(false, true, emptyList(), -1)

        assertEquals(SmsSetupIssue.UNSUPPORTED_DEVICE, (state as SmsSetupState.Blocked).issue)
    }

    @Test
    fun phonePermissionIsRequiredBeforeReadingSimState() {
        val state = SmsSetupResolver.resolve(true, false, emptyList(), -1)

        assertEquals(SmsSetupIssue.PHONE_PERMISSION_REQUIRED, (state as SmsSetupState.Blocked).issue)
    }

    @Test
    fun noActiveSimIsBlocked() {
        val state = SmsSetupResolver.resolve(true, true, emptyList(), -1)

        assertEquals(SmsSetupIssue.NO_ACTIVE_SIM, (state as SmsSetupState.Blocked).issue)
    }

    @Test
    fun singleActiveSimIsSelectedAutomatically() {
        val state = SmsSetupResolver.resolve(true, true, listOf(sim1), -1)

        assertEquals(sim1, (state as SmsSetupState.Ready).line)
    }

    @Test
    fun multipleSimsRequireAnExplicitSelection() {
        val state = SmsSetupResolver.resolve(true, true, listOf(sim1, sim2), -1)

        assertEquals(SmsSetupIssue.SIM_SELECTION_REQUIRED, (state as SmsSetupState.Blocked).issue)
        assertEquals(listOf(sim1, sim2), state.lines)
    }

    @Test
    fun replacementSimRequiresExplicitReconfirmationEvenWhenOnlyOneRemains() {
        val state = SmsSetupResolver.resolve(true, true, listOf(sim2), 11)

        assertEquals(SmsSetupIssue.SIM_CHANGED, (state as SmsSetupState.Blocked).issue)
        assertEquals(listOf(sim2), state.lines)
    }
    @Test
    fun savedActiveSimIsReady() {
        val state = SmsSetupResolver.resolve(true, true, listOf(sim1, sim2), 22)

        assertTrue(state is SmsSetupState.Ready)
        assertEquals(sim2, (state as SmsSetupState.Ready).line)
    }
}
