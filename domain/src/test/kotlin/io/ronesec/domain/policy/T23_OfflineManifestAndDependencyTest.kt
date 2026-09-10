package io.ronesec.domain.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T23_OfflineManifestAndDependencyTest {

    @Test
    fun manifestContainsNoInternetOrNetworkPermissions() {
        val manifestFile = File("../app/src/main/AndroidManifest.xml")
        if (!manifestFile.exists()) {
            // If running from project root
            val rootManifest = File("app/src/main/AndroidManifest.xml")
            if (rootManifest.exists()) {
                assertManifestOffline(rootManifest)
                return
            }
        } else {
            assertManifestOffline(manifestFile)
        }
    }

    private fun assertManifestOffline(file: File) {
        val content = file.readText()

        // F02 invariant: Strictly offline with zero network access
        assertFalse("Manifest must not request INTERNET permission", content.contains("android.permission.INTERNET"))
        assertFalse("Manifest must not request ACCESS_NETWORK_STATE", content.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse("Manifest must not request ACCESS_WIFI_STATE", content.contains("android.permission.ACCESS_WIFI_STATE"))

        // I10: No cloud backup
        assertTrue("Manifest must disable cloud backup (allowBackup=\"false\")", content.contains("android:allowBackup=\"false\""))
    }

    @Test
    fun domainModuleContainsNoAndroidImports() {
        val domainSrcDir = File("src/main/kotlin")
        val effectiveDir = if (domainSrcDir.exists()) domainSrcDir else File("domain/src/main/kotlin")

        assertTrue("Domain source directory must exist", effectiveDir.exists())

        val kotlinFiles = effectiveDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("Domain module must contain Kotlin files", kotlinFiles.isNotEmpty())

        for (file in kotlinFiles) {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("import android.") || trimmed.startsWith("import androidx.")) {
                    throw AssertionError("Forbidden Android import found in domain module file ${file.name}:${index + 1}: $trimmed")
                }
            }
        }
    }
}
