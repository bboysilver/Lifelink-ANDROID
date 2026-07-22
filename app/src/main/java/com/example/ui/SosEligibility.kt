package com.example.ui

internal object SosEligibility {
    fun canStart(
        smsReady: Boolean,
        hasEmergencyContacts: Boolean,
        smsPermissionGranted: Boolean
    ): Boolean = smsReady && hasEmergencyContacts && smsPermissionGranted
}