package com.me.chat.ai.up.admin

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {

    companion object {
        const val CHANNEL_DOWNLOAD = "model_download"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOAD,
                getString(R.string.channel_download_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_download_desc)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(downloadChannel)
        }
    }
}
