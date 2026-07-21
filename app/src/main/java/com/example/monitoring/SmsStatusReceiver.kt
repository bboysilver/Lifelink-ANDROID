package com.example.monitoring

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.example.data.AppDatabase
import com.example.data.LifeLinkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME) ?: "보호자"
        val phoneSuffix = intent.getStringExtra(EXTRA_PHONE_SUFFIX).orEmpty()
        val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 0)
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
        val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, 0)
        if (attempt <= 0 || partIndex !in 0 until totalParts) return

        // BroadcastReceiver 결과는 goAsync() 전에 캡처해야 비동기 처리 중에도 보존된다.
        val callbackResultCode = resultCode
        val callbackStage = when (intent.action) {
            ACTION_SMS_SENT -> SmsCallbackStage.SENT
            ACTION_SMS_DELIVERED -> SmsCallbackStage.DELIVERED
            else -> return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val outcome = SmsDispatchStore(context.applicationContext).recordCallback(
                    stage = callbackStage,
                    eventId = eventId,
                    attempt = attempt,
                    partIndex = partIndex,
                    totalParts = totalParts,
                    resultCode = callbackResultCode
                )
                logOutcome(
                    context = context.applicationContext,
                    outcome = outcome,
                    contactName = contactName,
                    phoneSuffix = phoneSuffix,
                    resultCode = callbackResultCode
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun logOutcome(
        context: Context,
        outcome: SmsCallbackOutcome,
        contactName: String,
        phoneSuffix: String,
        resultCode: Int
    ) {
        val repository = LifeLinkRepository(AppDatabase.getDatabase(context))
        val maskedPhone = "수신 번호: ****$phoneSuffix"
        when (outcome) {
            SmsCallbackOutcome.SENT -> repository.insertLog(
                "SMS_SENT",
                "$contactName 보호자에게 문자 발송이 확인되었습니다.",
                maskedPhone
            )
            SmsCallbackOutcome.DELIVERED -> repository.insertLog(
                "SMS_DELIVERED",
                "$contactName 보호자에게 문자 전달이 확인되었습니다.",
                maskedPhone
            )
            SmsCallbackOutcome.FAILED_RETRYABLE -> repository.insertLog(
                "SMS_FAILED",
                "$contactName 보호자 문자 발송에 실패해 5분 뒤 다시 시도합니다.",
                "$maskedPhone, 오류: ${resultDescription(resultCode)}"
            )
            SmsCallbackOutcome.FAILED_FINAL -> repository.insertLog(
                "SMS_FAILED",
                "$contactName 보호자 문자 발송이 3회 실패했습니다.",
                "$maskedPhone, 오류: ${resultDescription(resultCode)}"
            )
            SmsCallbackOutcome.DELIVERY_UNCONFIRMED -> repository.insertLog(
                "SMS_DELIVERY_UNCONFIRMED",
                "$contactName 보호자에게 보낸 문자의 전달 여부를 확인하지 못했습니다.",
                "$maskedPhone, 오류: ${resultDescription(resultCode)}"
            )
            SmsCallbackOutcome.PENDING,
            SmsCallbackOutcome.IGNORED -> Unit
        }
    }

    private fun resultDescription(code: Int): String = when (code) {
        Activity.RESULT_OK -> "성공"
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "일반 오류"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "통신 서비스 없음"
        SmsManager.RESULT_ERROR_NULL_PDU -> "문자 데이터 오류"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "통신 기능 꺼짐"
        SmsDispatchStore.RESULT_CALLBACK_TIMEOUT -> "발송 결과 시간 초과"
        EmergencySmsSender.RESULT_QUEUE_EXCEPTION -> "발송 요청 오류"
        else -> "코드 $code"
    }

    companion object {
        const val ACTION_SMS_SENT = "com.bboysilver.lifelink.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.bboysilver.lifelink.SMS_DELIVERED"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_CONTACT_NAME = "contact_name"
        const val EXTRA_PHONE_SUFFIX = "phone_suffix"
        const val EXTRA_ATTEMPT = "attempt"
        const val EXTRA_PART_INDEX = "part_index"
        const val EXTRA_TOTAL_PARTS = "total_parts"
    }
}
