package io.ronesec.android.data.repository

import android.content.Context
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

class AppMetadataRepository(
    private val context: Context
) {
    private val labelCache = ConcurrentHashMap<String, String>()

    fun getAppLabel(packageName: String): String {
        return labelCache.computeIfAbsent(packageName) { pkg ->
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                pkg.substringAfterLast('.')
            } catch (_: Exception) {
                pkg.substringAfterLast('.')
            }
        }
    }

    fun invalidate(packageName: String) {
        labelCache.remove(packageName)
    }

    fun clear() {
        labelCache.clear()
    }
}
