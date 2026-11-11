package com.alice.quotarabbit.notification

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.alice.quotarabbit.R

class DeviceCodeLoginCopyCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COPY_DEVICE_CODE) return
        val attemptId = intent.getStringExtra(EXTRA_ATTEMPT_ID)
        val code = GlobalDeviceCodeLoginNotificationRegistry.codeForAttempt(attemptId) ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                context.getString(R.string.notification_auth_login_copy_code),
                code,
            ),
        )
        Toast.makeText(
            context,
            context.getString(R.string.notification_auth_login_code_copied),
            Toast.LENGTH_SHORT,
        ).show()
    }

    companion object {
        const val ACTION_COPY_DEVICE_CODE = "com.alice.quotarabbit.action.COPY_DEVICE_CODE"
        const val EXTRA_ATTEMPT_ID = "com.alice.quotarabbit.extra.DEVICE_CODE_ATTEMPT_ID"
    }
}
