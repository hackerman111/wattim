package io.ronesec.android.platform.system

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PackageAppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean = false
)

interface PackageCatalog {
    suspend fun getLaunchableApps(excludedPackages: Set<String> = emptySet()): List<PackageAppEntry>
}

class AndroidPackageCatalog(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PackageCatalog {

    override suspend fun getLaunchableApps(excludedPackages: Set<String>): List<PackageAppEntry> {
        return withContext(ioDispatcher) {
            val pm = context.packageManager
            val ownPackage = context.packageName

            // Exclude platform surfaces: SystemUI, active/installed IMEs, current default launcher
            val platformExclusions = mutableSetOf(
                ownPackage,
                "com.android.systemui"
            )

            // Resolve default home launcher packages
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val homeResolutions = pm.queryIntentActivities(homeIntent, 0)
            for (res in homeResolutions) {
                res.activityInfo?.packageName?.let { platformExclusions.add(it) }
            }

            // Resolve input method packages
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.inputMethodList?.forEach { imi ->
                platformExclusions.add(imi.packageName)
            }

            // Query all launchable activities exposing a normal launcher entry (F46)
            // Includes OEM/preinstalled launchable apps, excludes Wattim itself and platform surfaces
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val launchableActivities = pm.queryIntentActivities(launcherIntent, 0)

            val seenPackages = mutableSetOf<String>()
            val result = mutableListOf<PackageAppEntry>()

            for (resolveInfo in launchableActivities) {
                val pkgName = resolveInfo.activityInfo?.packageName ?: continue
                if (pkgName in platformExclusions || pkgName in excludedPackages || pkgName in seenPackages) {
                    continue
                }
                seenPackages.add(pkgName)

                val label = resolveInfo.loadLabel(pm)?.toString()?.trim() ?: pkgName
                val isSystem = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                result.add(
                    PackageAppEntry(
                        packageName = pkgName,
                        label = if (label.isNotBlank()) label else pkgName,
                        isSystem = isSystem
                    )
                )
            }

            result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }
    }
}
