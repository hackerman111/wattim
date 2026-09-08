package io.ronesec.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import io.ronesec.android.data.repository.RonesecRepository

class RonesecApplication : Application() {

    lateinit var repository: RonesecRepository
        private set

    lateinit var appMetadataRepository: io.ronesec.android.data.repository.AppMetadataRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = RonesecRepository.getInstance(this)
        appMetadataRepository = io.ronesec.android.data.repository.AppMetadataRepository(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_PROTECTION,
                getString(R.string.channel_name_protection),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc_protection)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_PROTECTION = "protection_channel"

        lateinit var instance: RonesecApplication
            private set
    }
}
