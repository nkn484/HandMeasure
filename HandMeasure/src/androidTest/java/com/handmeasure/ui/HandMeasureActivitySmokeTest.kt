package com.handmeasure.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.HandMeasureConfig
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class HandMeasureActivitySmokeTest {
    @Test
    fun launchWithReplayPath_reachesResultScreen() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val replayDir = File(context.cacheDir, "smoke_replay").apply { mkdirs() }
        CaptureStep.entries.forEach { step ->
            val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(0xFF666666.toInt())
            FileOutputStream(File(replayDir, "${step.name.lowercase()}.jpg")).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()
        }

        val intent =
            Intent(context, HandMeasureActivity::class.java).putExtra(
                HandMeasureActivity.EXTRA_CONFIG,
                HandMeasureConfig(debugReplayInputPath = replayDir.absolutePath),
            )

        ActivityScenario.launch<HandMeasureActivity>(intent).use {
            Thread.sleep(1500)
            onView(withText("Measurement result")).check(matches(isDisplayed()))
        }
    }
}
