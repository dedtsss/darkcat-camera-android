package com.raulshma.lenscast

import android.Manifest
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensCastNavigationSmokeTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    private lateinit var device: UiDevice

    @Before
    fun launch() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        device.pressHome()
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.desc("Gallery")), 15_000))
    }

    @Test
    fun opensCameraCaptureSettingsAndGallery() {
        device.findObject(By.desc("More options")).click()
        device.findObject(By.text("Capture tools")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Capture")), 5_000))
        device.pressBack()

        device.findObject(By.desc("More options")).click()
        device.findObject(By.text("Settings")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Camera Settings")), 5_000))
        device.pressBack()

        device.findObject(By.desc("Gallery")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Gallery")), 5_000))
    }
}
