package com.alice.quotarabbit

import com.alice.quotarabbit.BuildConfig
import com.alice.quotarabbit.R
import com.alice.quotarabbit.notification.DeviceCodeLoginCopyCodeReceiver
import com.alice.quotarabbit.ui.navigation.QuotaRabbitRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPackageIdentityTest {
    @Test
    fun generatedNamespaceIsAlice() {
        assertEquals("com.alice.quotarabbit", R::class.java.packageName)
    }

    @Test
    fun debugApplicationIdMatchesQuotaRabbit() {
        assertEquals("com.alice.quotarabbit.debug", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun appSpecificIntentKeysUseQuotaRabbitPrefix() {
        assertEquals(
            "com.alice.quotarabbit.action.COPY_DEVICE_CODE",
            DeviceCodeLoginCopyCodeReceiver.ACTION_COPY_DEVICE_CODE,
        )
        assertEquals(
            "com.alice.quotarabbit.extra.DEVICE_CODE_ATTEMPT_ID",
            DeviceCodeLoginCopyCodeReceiver.EXTRA_ATTEMPT_ID,
        )
        assertEquals(
            "com.alice.quotarabbit.extra.LAUNCH_DESTINATION",
            QuotaRabbitRoute.EXTRA_LAUNCH_DESTINATION,
        )
    }
}
