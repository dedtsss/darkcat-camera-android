package com.raulshma.lenscast

import android.Manifest
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensCastNavigationSmokeTest {
    private lateinit var device: UiDevice

    private fun waitForObject(selector: BySelector): UiObject2 {
        val uiObject = device.wait(Until.findObject(selector), UI_TIMEOUT_MS)
        assertNotNull("Timed out waiting for $selector", uiObject)
        return uiObject!!
    }

    private fun clickWhenReady(selector: BySelector) {
        waitForObject(selector).click()
    }

    @Before
    fun launch() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        device.executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.CAMERA}")
        device.executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}")
        device.pressHome()
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.desc("Gallery")), LAUNCH_TIMEOUT_MS))
    }

    @Test
    fun opensCameraCaptureSettingsAndGallery() {
        clickWhenReady(By.desc("More options"))
        clickWhenReady(By.text("Capture tools"))
        assertTrue(device.wait(Until.hasObject(By.text("Capture")), UI_TIMEOUT_MS))
        device.pressBack()
        waitForObject(By.desc("Gallery"))

        clickWhenReady(By.desc("More options"))
        clickWhenReady(By.text("Settings"))
        assertTrue(device.wait(Until.hasObject(By.text("Camera Settings")), UI_TIMEOUT_MS))
        device.pressBack()
        waitForObject(By.desc("Gallery"))

        clickWhenReady(By.desc("Gallery"))
        assertTrue(device.wait(Until.hasObject(By.text("Gallery")), UI_TIMEOUT_MS))
    }

    private companion object {
        const val LAUNCH_TIMEOUT_MS = 15_000L
        const val UI_TIMEOUT_MS = 5_000L
    }
}
