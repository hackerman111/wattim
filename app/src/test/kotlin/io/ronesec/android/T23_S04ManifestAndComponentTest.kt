package io.ronesec.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T23_S04ManifestAndComponentTest {

    @Test
    fun manifestDeclaresAllRequiredCapabilitiesAndEntrypoints_F20_F22_F23() {
        val manifestFile = findFile("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())

        val content = manifestFile.readText()

        // Required permissions (F20)
        assertTrue("Manifest must declare FOREGROUND_SERVICE", content.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue("Manifest must declare FOREGROUND_SERVICE_SPECIAL_USE", content.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"))
        assertTrue("Manifest must declare SYSTEM_ALERT_WINDOW", content.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        assertTrue("Manifest must declare RECEIVE_BOOT_COMPLETED", content.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
        assertTrue("Manifest must declare REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", content.contains("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
        assertTrue("Manifest must declare POST_NOTIFICATIONS", content.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue("Manifest must declare QUERY_ALL_PACKAGES", content.contains("android.permission.QUERY_ALL_PACKAGES"))

        // FGS declaration (F22)
        assertTrue("Manifest must declare FocusForegroundService", content.contains("android:name=\".platform.system.FocusForegroundService\""))
        assertTrue("FocusForegroundService must declare specialUse", content.contains("android:foregroundServiceType=\"specialUse\""))
        assertTrue("FocusForegroundService must declare PROPERTY_SPECIAL_USE_FGS_SUBTYPE property", content.contains("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"))

        // BootReceiver declaration (F23)
        assertTrue("Manifest must declare BootReceiver", content.contains("android:name=\".platform.system.BootReceiver\""))
        assertTrue("BootReceiver must listen for BOOT_COMPLETED", content.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue("BootReceiver must listen for MY_PACKAGE_REPLACED", content.contains("android.intent.action.MY_PACKAGE_REPLACED"))

        // AccessibilityService declaration (F20)
        assertTrue("Manifest must declare AppMonitorService", content.contains("android:name=\".platform.accessibility.AppMonitorService\""))
        assertTrue("AppMonitorService must require BIND_ACCESSIBILITY_SERVICE", content.contains("android:permission=\"android.permission.BIND_ACCESSIBILITY_SERVICE\""))
        assertTrue("AppMonitorService must reference accessibility_service_config", content.contains("@xml/accessibility_service_config"))

        // Activities declaration
        assertTrue("Manifest must declare MainActivity", content.contains("android:name=\".ui.MainActivity\""))
        assertTrue("Manifest must declare InterventionActivity", content.contains("android:name=\".ui.intervention.InterventionActivity\""))

        // Offline & privacy invariants (F02, I10)
        assertFalse("Manifest must not request INTERNET", content.contains("android.permission.INTERNET"))
        assertTrue("Manifest must disable cloud backup", content.contains("android:allowBackup=\"false\""))
    }

    @Test
    fun accessibilityConfigXmlGuaranteesZeroScreenContentReading_I10_F25() {
        val configFile = findFile("src/main/res/xml/accessibility_service_config.xml")
        assertTrue("accessibility_service_config.xml must exist", configFile.exists())

        val content = configFile.readText()
        assertTrue("Must declare canRetrieveWindowContent=\"false\" (I10)", content.contains("android:canRetrieveWindowContent=\"false\""))
        assertTrue("Must declare notificationTimeout=\"25\" baseline (F25)", content.contains("android:notificationTimeout=\"25\""))
    }

    private fun findFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromApp = File("app", relativePath)
        if (fromApp.exists()) return fromApp
        val fromRoot = File("../app", relativePath)
        if (fromRoot.exists()) return fromRoot
        return direct
    }
}
