#!/usr/bin/env bash
set -euo pipefail

PKG="io.ronesec.android"
SERVICE="$PKG/io.ronesec.android.platform.accessibility.AppMonitorService"
MEDIA_SERVICE="$PKG/io.ronesec.android.platform.audio.WattimNotificationListenerService"
APK="$(cd "$(dirname "$0")" && pwd)/app/build/outputs/apk/release/app-release.apk"

cd "$(dirname "$0")"
./gradlew :app:assembleRelease
adb install -r -g "$APK"
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

# SYSTEM_ALERT_WINDOW is a special AppOp and is not covered by install -g.
if ! adb shell appops set --uid "$PKG" android:system_alert_window allow; then
    adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
fi

overlay_mode="$(adb shell appops get "$PKG" android:system_alert_window 2>/dev/null | tr -d '\r')"
if [[ "$overlay_mode" != *"allow"* ]]; then
    echo "Прошивка не разрешила оверлей через ADB. Включите переключатель Wattim на открывшемся экране."
    adb shell am start \
        -a android.settings.action.MANAGE_OVERLAY_PERMISSION \
        -d "package:$PKG" >/dev/null
    exit 1
fi

adb shell dumpsys deviceidle whitelist +"$PKG"

current="$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')"
if [[ "$current" != *"$SERVICE"* ]]; then
    [[ -z "$current" || "$current" == "null" ]] && current="$SERVICE" || current="$current:$SERVICE"
    adb shell settings put secure enabled_accessibility_services "$current"
fi
adb shell settings put secure accessibility_enabled 1

media_listeners="$(adb shell settings get secure enabled_notification_listeners | tr -d '\r')"
if [[ "$media_listeners" != *"$MEDIA_SERVICE"* ]]; then
    [[ -z "$media_listeners" || "$media_listeners" == "null" ]] && \
        media_listeners="$MEDIA_SERVICE" || media_listeners="$media_listeners:$MEDIA_SERVICE"
    adb shell settings put secure enabled_notification_listeners "$media_listeners"
fi

adb shell am force-stop "$PKG"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null

echo "Wattim установлен и запущен."
