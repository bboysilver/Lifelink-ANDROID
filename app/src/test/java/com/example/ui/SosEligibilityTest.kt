package com.example.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SosEligibilityTest {
    @Test
    fun monitoringStateIsNotRequiredWhenSmsAndContactsAreReady() {
        assertTrue(SosEligibility.canStart(true, true, true))
    }

    @Test
    fun missingContactBlocksSos() {
        assertFalse(SosEligibility.canStart(true, false, true))
    }

    @Test
    fun missingSmsPermissionBlocksSos() {
        assertFalse(SosEligibility.canStart(true, true, false))
    }

    @Test
    fun sensorErrorDoesNotMatterAfterSmsIsReady() {
        assertTrue(SosEligibility.canStart(true, true, true))
    }
}