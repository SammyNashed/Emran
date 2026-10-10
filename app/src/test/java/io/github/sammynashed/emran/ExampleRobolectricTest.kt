package io.github.sammynashed.emran

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Emran", appName)
  }

  @Test
  fun `launch MainActivity test`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // Initialize WorkManager for testing to prevent workmanager related crashes during testing
    try {
      val config = Configuration.Builder().build()
      WorkManager.initialize(context, config)
    } catch (e: Exception) {
      // already initialized
    }

    val controller = Robolectric.buildActivity(MainActivity::class.java)
    controller.setup() // creates, starts, restores, resumes
    val activity = controller.get()
    assert(activity != null)
  }
}


