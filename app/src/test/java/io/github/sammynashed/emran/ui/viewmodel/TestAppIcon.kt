package io.github.sammynashed.emran.ui.viewmodel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import android.content.ComponentName

@RunWith(RobolectricTestRunner::class)
class TestAppIcon {
    @Test
    fun testUpdate() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pm = app.packageManager
        val packageName = app.packageName
        println("Package Name: $packageName")
        val cmp = ComponentName(packageName, "io.github.sammynashed.emran.MainActivityAliasExpressive")
        try {
            pm.getComponentEnabledSetting(cmp)
            println("SUCCESS")
        } catch(e: Exception) {
            println("FAILED: ${e.message}")
        }
    }
}
