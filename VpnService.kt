package com.example.vpnclient.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import timber.log.Timber
import java.io.File

class VpnService : android.net.VpnService() {

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "vpn_channel"
        const val ACTION_CONNECT = "com.example.vpnclient.CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpnclient.DISCONNECT"
        const val EXTRA_CONFIG = "config"
    }

    private var vpnThread: VpnThread? = null
    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Timber.d("VPN Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("VPN Service started")
        
        when (intent?.action) {
            ACTION_CONNECT -> {
                val config = intent.getSerializableExtra(EXTRA_CONFIG)
                if (config != null) {
                    startVpnConnection(config as com.example.vpnclient.model.VlessConfig)
                }
            }
            ACTION_DISCONNECT -> {
                stopVpnConnection()
            }
        }

        return START_STICKY
    }

    private fun startVpnConnection(config: com.example.vpnclient.model.VlessConfig) {
        try {
            Timber.d("Starting VPN connection to ${config.address}:${config.port}")

            // Создаем VPN interface
            val builder = Builder()
            builder.setSession("VLESS Client")
            builder.addAddress("10.8.0.1", 24)
            builder.addDnsServer("8.8.8.8")
            builder.addDnsServer("8.8.4.4")
            
            // Маршруты
            builder.addRoute("0.0.0.0", 0)
            builder.setMtu(1500)

            // Исключаем собственное приложение из VPN
            builder.addDisallowedApplication(packageName)

            val vpnInterface = builder.establish()
            if (vpnInterface != null) {
                vpnThread = VpnThread(this, vpnInterface, config)
                vpnThread?.start()
                
                showNotification("VPN подключен к ${config.name}")
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    createNotification("VPN подключен к ${config.name}"),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                Timber.e("Failed to establish VPN interface")
                stopSelf()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error starting VPN connection")
            stopSelf()
        }
    }

    private fun stopVpnConnection() {
        Timber.d("Stopping VPN connection")
        vpnThread?.interrupt()
        vpnThread?.join()
        vpnThread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Service",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(message: String) {
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }

    private fun createNotification(message: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VLESS VPN")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, com.example.vpnclient.MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpnConnection()
        Timber.d("VPN Service destroyed")
    }
}
