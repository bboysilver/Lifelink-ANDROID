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
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
        val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, 0)
        if (partIndex !in 0 until totalParts) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                recordResult(
                    context.applicationContext,
                    intent.action,
                    eventId,
                    contactName,
                    phoneSuffix,
                    partIndex,
                    totalParts,
                    resultCode
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun recordResult(
        context: Context,
        action: String?,
        eventId: String,
        contactName: String,
        phoneSuffix: String,
        partIndex: Int,
        totalParts: Int,
        resultCode: Int
    ) {
        val repository = LifeLinkRepository(AppDatabase.getDatabase(context))
        val preferences = context.getSharedPreferences(EmergencySmsSender.STATUS_FILE, Context.MODE_PRIVATE)
        val stage = if (action == ACTION_SMS_DELIVERED) "delivered" else "sent"
        val partKey = "$stage:$eventId:$partIndex"
        val completionKey = "$stage:complete:$eventId"
        var finalStatus: Boolean? = null

        synchronized(EmergencySmsSender.LOCK) {
            if (preferences.contains(partKey) || preferences.getBoolean(completionKey, false)) return
            val succeeded = resultCode == Activity.RESULT_OK
            preferences.edit().putBoolean(partKey, succeeded).commit()
            if (!succeeded) {
                preferences.edit().putBoolean(completionKey, true).commit()
                finalStatus = false
            } else {
                val allSucceeded = (0 until totalParts).all {
                    preferences.getBoolean("$stage:$eventId:$it", false)
                }
                if (allSucceeded) {
                    preferences.edit().putBoolean(completionKey, true).commit()
                    finalStatus = true
                }
            }
        }

        when (finalStatus) {
            true -> repository.insertLog(
                if (stage == "delivered") "SMS_DELIVERED" else "SMS_SENT",
                if (stage == "delivered") {
                    "$contactName 보호자에게 문자 전달이 확인되었습니다."
                } else {
                    "$contactName 보호자에게 문자 발송이 확인되었습니다."
                },
                "수신 번호: ****$phoneSuffix"
            )
            false -> repository.insertLog(
                "SMS_FAILED",
                "$contactName 보호자 문자 ${if (stage == "delivered") "전달" else "발송"}을 확인하지 못했습니다.",
                "수신 번호: ****$phoneSuffix, 오류: ${resultDescription(resultCode)}"
            )
            null -> Unit
        }
    }

    private fun resultDescription(code: Int): String = when (code) {
        Activity.RESULT_OK -> "성공"
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "일반 오류"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "통신 서비스 없음"
        SmsManager.RESULT_ERROR_NULL_PDU -> "문자 데이터 오류"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "통신 기능 꺼짐"
        else -> "코드 $code"
    }

    companion object {
        const val ACTION_SMS_SENT = "com.bboysilver.lifelink.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.bboysilver.lifelink.SMS_DELIVERED"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_CONTACT_NAME = "contact_name"
        const val EXTRA_PHONE_SUFFIX = "phone_suffix"
        const val EXTRA_PART_INDEX = "part_index"
        const val EXTRA_TOTAL_PARTS = "total_parts"
    }
}
