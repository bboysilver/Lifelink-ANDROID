package com.example.monitoring

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.data.LifeLinkRepository
import com.example.data.MonitoringStore
import com.example.data.SafetyIncidentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal data class SafetySmsBatch(
    val statuses: List<SmsDispatchStatus>,
    val queuedAny: Boolean
)

internal class SafetyMessageDispatcher(
    private val context: Context,
    private val store: MonitoringStore,
    private val repository: LifeLinkRepository,
    private val incidents: SafetyIncidentRepository,
    private val reportBlocked: suspend (String, String, String) -> Unit
) {
    suspend fun queue(
        type: SafetySmsEventType,
        occurredAtMs: Long,
        message: String,
        batteryPercent: Int?,
        canCreateIncident: () -> Boolean = { true }
    ): SafetySmsBatch? {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            reportBlocked(
                "문자 권한이 없어 보호자 문자를 보낼 수 없습니다.",
                "문자 권한이 필요합니다",
                "앱 설정에서 문자 권한을 허용해 주세요."
            )
            return null
        }

        val incidentId = "${type.wireName}:$occurredAtMs"
        val snapshot = incidents.get(incidentId) ?: run {
            val contacts = repository.allContacts.first().take(3)
            if (contacts.isEmpty()) {
                reportBlocked(
                    "등록된 긴급 연락처가 없어 문자를 보낼 수 없습니다.",
                    "긴급 연락처가 없습니다",
                    "앱을 열어 긴급 연락처를 등록해 주세요."
                )
                return null
            }
            val smsSetup = SmsDeviceManager(context, store).inspect()
            if (smsSetup !is SmsSetupState.Ready) {
                reportBlocked(
                    smsSetup.userMessage(),
                    "문자 발송 환경 확인 필요",
                    smsSetup.userMessage()
                )
                return null
            }
            // A user may stop or reset monitoring while the contact query is suspended.
            if (!canCreateIncident()) return null
            incidents.getOrCreate(
                incidentId = incidentId,
                type = type.wireName,
                occurredAtMs = occurredAtMs,
                deviceAlias = store.deviceAlias,
                message = message,
                batteryPercent = batteryPercent,
                subscriptionId = smsSetup.line.subscriptionId,
                contacts = contacts
            )
        }

        if (snapshot.recipients.isEmpty()) {
            reportBlocked(
                "사고 수신자 기록이 없어 문자를 보낼 수 없습니다.",
                "긴급 연락처 기록 오류",
                "앱을 열어 긴급 연락처를 다시 확인해 주세요."
            )
            return null
        }

        val sender = EmergencySmsSender(context)
        var queuedAny = false
        snapshot.recipients.forEach { recipient ->
            try {
                if (
                    sender.queue(
                        eventId = recipient.eventId,
                        contact = recipient.asContact(),
                        message = snapshot.incident.message,
                        subscriptionId = snapshot.incident.subscriptionId
                    ) == SmsQueueResult.QUEUED
                ) {
                    queuedAny = true
                    repository.insertLog(
                        "SMS_QUEUED",
                        "${recipient.name} 보호자 문자 발송 결과를 기다리고 있습니다."
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                repository.insertLog(
                    "SMS_FAILED",
                    "${recipient.name} 보호자 문자 발송 요청에 실패했습니다.",
                    error.message ?: "알 수 없는 오류"
                )
            }
            incidents.recordStatus(recipient.eventId, sender.status(recipient.eventId))
        }
        val statuses = snapshot.recipients.map { sender.status(it.eventId) }
        if (statuses.all { it.isResolved }) incidents.completeAndRedact(incidentId)
        return SafetySmsBatch(statuses = statuses, queuedAny = queuedAny)
    }
}
