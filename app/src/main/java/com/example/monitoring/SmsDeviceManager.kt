package com.example.monitoring

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.data.MonitoringStore

data class SmsLine(
    val subscriptionId: Int,
    val slotIndex: Int,
    val label: String
)

enum class SmsSetupIssue {
    CHECKING,
    PHONE_PERMISSION_REQUIRED,
    UNSUPPORTED_DEVICE,
    NO_ACTIVE_SIM,
    SIM_SELECTION_REQUIRED,
    SIM_CHANGED,
    SIM_STATUS_UNAVAILABLE
}

sealed interface SmsSetupState {
    data class Ready(val line: SmsLine) : SmsSetupState
    data class Blocked(
        val issue: SmsSetupIssue,
        val lines: List<SmsLine> = emptyList()
    ) : SmsSetupState
}

internal object SmsSetupResolver {
    fun resolve(
        deviceSmsCapable: Boolean,
        phonePermissionGranted: Boolean,
        activeLines: List<SmsLine>,
        selectedSubscriptionId: Int
    ): SmsSetupState {
        if (!deviceSmsCapable) return SmsSetupState.Blocked(SmsSetupIssue.UNSUPPORTED_DEVICE)
        if (!phonePermissionGranted) {
            return SmsSetupState.Blocked(SmsSetupIssue.PHONE_PERMISSION_REQUIRED)
        }
        if (activeLines.isEmpty()) return SmsSetupState.Blocked(SmsSetupIssue.NO_ACTIVE_SIM)

        activeLines.firstOrNull { it.subscriptionId == selectedSubscriptionId }?.let {
            return SmsSetupState.Ready(it)
        }
        if (selectedSubscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return SmsSetupState.Blocked(SmsSetupIssue.SIM_CHANGED, activeLines)
        }
        if (activeLines.size == 1) return SmsSetupState.Ready(activeLines.single())
        return SmsSetupState.Blocked(SmsSetupIssue.SIM_SELECTION_REQUIRED, activeLines)
    }
}

class SmsDeviceManager(
    private val context: Context,
    private val monitoringStore: MonitoringStore = MonitoringStore(context)
) {
    fun inspect(): SmsSetupState {
        if (!hasSmsCapability()) return SmsSetupState.Blocked(SmsSetupIssue.UNSUPPORTED_DEVICE)
        val hasPermission = hasPhoneStatePermission()
        if (!hasPermission) return SmsSetupState.Blocked(SmsSetupIssue.PHONE_PERMISSION_REQUIRED)

        val lines = try {
            loadActiveLines()
        } catch (error: SecurityException) {
            return SmsSetupState.Blocked(SmsSetupIssue.PHONE_PERMISSION_REQUIRED)
        } catch (error: UnsupportedOperationException) {
            return SmsSetupState.Blocked(SmsSetupIssue.SIM_STATUS_UNAVAILABLE)
        }
        val state = SmsSetupResolver.resolve(
            deviceSmsCapable = true,
            phonePermissionGranted = true,
            activeLines = lines,
            selectedSubscriptionId = monitoringStore.smsSubscriptionId
        )
        if (state is SmsSetupState.Ready &&
            monitoringStore.smsSubscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID
        ) {
            monitoringStore.smsSubscriptionId = state.line.subscriptionId
        }
        return state
    }

    fun managerFor(subscriptionId: Int): SmsManager {
        require(subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            "A valid SMS subscription is required"
        }
        val manager = context.getSystemService(SmsManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.createForSubscriptionId(subscriptionId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }
    }

    private fun hasSmsCapability(): Boolean {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)) {
            return false
        }
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        return if (Build.VERSION.SDK_INT >= 35) {
            telephonyManager.isDeviceSmsCapable
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.isSmsCapable
        }
    }

    private fun hasPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    private fun loadActiveLines(): List<SmsLine> {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("READ_PHONE_STATE is required to inspect active SIMs")
        }
        val manager = context.getSystemService(SubscriptionManager::class.java)
        return manager.activeSubscriptionInfoList.orEmpty()
            .filter { info ->
                Build.VERSION.SDK_INT < 35 ||
                    info.serviceCapabilities.contains(SubscriptionManager.SERVICE_CAPABILITY_SMS)
            }
            .map { info ->
                val name = info.displayName?.toString()?.trim().orEmpty()
                    .ifEmpty { info.carrierName?.toString()?.trim().orEmpty() }
                    .ifEmpty { "SIM ${info.simSlotIndex + 1}" }
                SmsLine(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    label = "$name · SIM ${info.simSlotIndex + 1}"
                )
            }
    }
}

fun SmsSetupState.userMessage(): String = when (this) {
    is SmsSetupState.Ready -> "문자 회선: ${line.label}"
    is SmsSetupState.Blocked -> when (issue) {
        SmsSetupIssue.CHECKING -> "문자 발송 환경을 확인하고 있습니다."
        SmsSetupIssue.PHONE_PERMISSION_REQUIRED -> "활성 SIM을 확인하려면 전화 상태 권한이 필요합니다."
        SmsSetupIssue.UNSUPPORTED_DEVICE -> "이 기기는 이동통신사 SMS 발송을 지원하지 않습니다."
        SmsSetupIssue.NO_ACTIVE_SIM -> "활성화된 SMS 가능 SIM이 없습니다."
        SmsSetupIssue.SIM_SELECTION_REQUIRED -> "긴급 문자에 사용할 SIM을 선택해 주세요."
        SmsSetupIssue.SIM_CHANGED -> "SIM이 변경되었습니다. 문자 회선을 다시 확인해 주세요."
        SmsSetupIssue.SIM_STATUS_UNAVAILABLE -> "SIM 상태를 확인할 수 없습니다."
    }
}
