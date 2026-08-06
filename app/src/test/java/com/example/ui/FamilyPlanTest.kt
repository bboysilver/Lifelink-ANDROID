package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyPlanTest {
    @Test
    fun annualPlanIsEquivalentToTenMonthlyPayments() {
        assertEquals(
            LifeLinkFamilyPlan.MONTHLY_PRICE_WON * 10,
            LifeLinkFamilyPlan.ANNUAL_PRICE_WON
        )
    }

    @Test
    fun familyPlanContainsOnlyRecurringRemoteSafetyValue() {
        assertTrue(LifeLinkFamilyPlan.benefits.any { it.contains("연결 끊김") })
        assertTrue(LifeLinkFamilyPlan.benefits.any { it.contains("보호자") })
    }
}
