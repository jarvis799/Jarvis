package com.jarvis.assistant.automation.command

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

object NativeCallControl {
    fun route(context: Context, text: String): String? {
        val reject = Regex("(?i)^(?:(?:please|the)\\s+)?(?:(?:reject|decline|end|hang up|cut|kete dao)\\s+(?:(?:the|incoming)\\s+)?call|call\\s+(?:reject|decline|cut|kete dao)(?:\\s+koro)?)$").matches(text)
        val answer = Regex("(?i)^(?:(?:please|the)\\s+)?(?:(?:answer|pick\\s*up|pickup|receive|dhoro)\\s+(?:(?:the|incoming)\\s+)?call|call\\s+(?:answer|pick\\s*up|pickup|receive|dhoro)(?:\\s+koro)?)$").matches(text)
        if (!reject && !answer) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            return "Call control permission off. Android app permissions e Phone/Answer calls allow korun. Kono call change hoyni."
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return "Call state permission off. Android app permissions e Phone access allow korun."
        }
        val state = context.getSystemService(TelephonyManager::class.java).callState
        if (answer && state != TelephonyManager.CALL_STATE_RINGING) return "Ekhon incoming SIM call ring korche na. WhatsApp call er jonno ei control noy."
        if (reject && state == TelephonyManager.CALL_STATE_IDLE) return "Ekhon kono SIM call active nei."
        val telecom = context.getSystemService(TelecomManager::class.java)
        return try {
            if (reject) {
                if (Build.VERSION.SDK_INT < 28) return "Call rejection requires Android 9 or newer on this device."
                @Suppress("DEPRECATION") val ended = telecom.endCall()
                if (ended) "SIM call ended." else "Android call end request accept koreni; call end confirm hoyni."
            } else {
                @Suppress("DEPRECATION") telecom.acceptRingingCall()
                "Incoming SIM call answer request Android e pathano hoyeche."
            }
        } catch (_: SecurityException) {
            "Android ei app ke call control korte dey ni. Phone permissions/default dialer restrictions check korun; call change confirm hoyni."
        }
    }
}