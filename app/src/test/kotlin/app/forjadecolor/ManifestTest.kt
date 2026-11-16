package app.forjadecolor

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManifestTest {
    @Test
    fun `the app never asks for network access`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toList()
        assertFalse("android.permission.INTERNET" in requested, "requested: $requested")
        assertFalse("android.permission.ACCESS_NETWORK_STATE" in requested, "requested: $requested")
    }

    @Test
    fun `the app runs under ForjaApp`() {
        kotlin.test.assertIs<ForjaApp>(ApplicationProvider.getApplicationContext<Application>())
    }
}
