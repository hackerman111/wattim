package io.ronesec.android.platform.accessibility

import android.content.pm.PackageManager
import android.content.Intent

/** HOME handlers come from Android, including OEM and user-installed launchers. */
internal fun PackageManager.homePackages(): Set<String> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .mapNotNull { it.activityInfo?.packageName }.toSet()
}
