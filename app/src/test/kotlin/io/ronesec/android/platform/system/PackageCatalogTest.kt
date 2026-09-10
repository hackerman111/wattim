package io.ronesec.android.platform.system

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PackageCatalogTest {

    @Test
    fun getLaunchableAppsExcludesWattimAndExistingTargetsAndPlatformSurfaces() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager
        val shadowPm = shadowOf(pm)

        // Install a user app with launcher activity
        val sampleAppResolve = createResolveInfo("com.example.sample", "Sample App", false)
        shadowPm.addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            sampleAppResolve
        )

        // Install a preinstalled/OEM launchable app
        val oemAppResolve = createResolveInfo("com.oem.camera", "OEM Camera", true)
        shadowPm.addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            oemAppResolve
        )

        // Install SystemUI launchable (should be excluded)
        val systemUiResolve = createResolveInfo("com.android.systemui", "System UI", true)
        shadowPm.addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            systemUiResolve
        )

        // Install Wattim's own app launcher activity (should be excluded)
        val ownAppResolve = createResolveInfo(context.packageName, "wattim", false)
        shadowPm.addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            ownAppResolve
        )

        val catalog = AndroidPackageCatalog(context, StandardTestDispatcher(testScheduler))

        // Query with "com.example.sample" excluded as an existing target
        val excluded = setOf("com.example.sample")
        val apps = catalog.getLaunchableApps(excluded)

        // Only OEM Camera should remain
        assertEquals(1, apps.size)
        assertEquals("com.oem.camera", apps[0].packageName)
        assertEquals("OEM Camera", apps[0].label)
        assertTrue(apps[0].isSystem)

        // Query with empty excluded set: both sample app and OEM app should be present, but not SystemUI or Wattim
        val allApps = catalog.getLaunchableApps(emptySet())
        val packageNames = allApps.map { it.packageName }.toSet()

        assertTrue(packageNames.contains("com.example.sample"))
        assertTrue(packageNames.contains("com.oem.camera"))
        assertFalse(packageNames.contains("com.android.systemui"))
        assertFalse(packageNames.contains(context.packageName))
    }

    private fun createResolveInfo(packageName: String, label: String, isSystem: Boolean): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                this.name = "$packageName.MainActivity"
                this.applicationInfo = ApplicationInfo().apply {
                    this.packageName = packageName
                    this.flags = if (isSystem) ApplicationInfo.FLAG_SYSTEM else 0
                }
            }
            this.nonLocalizedLabel = label
        }
    }
}
