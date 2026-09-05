package com.example.androidadblocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.androidadblocker.MainActivity
import com.example.androidadblocker.R
import com.example.androidadblocker.dns.DnsPacket
import com.example.androidadblocker.dns.DnsResolver
import com.example.androidadblocker.filter.DomainFilter
import com.example.androidadblocker.model.BlockerStats
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class AdBlockerVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null

    companion object {
        const val TAG = "AdBlockerVpnService"
        const val CHANNEL_ID = "adblocker_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.androidadblocker.START"
        const val ACTION_STOP = "com.example.androidadblocker.STOP"
        const val EXTRA_UPSTREAM_DNS = "extra_upstream_dns"

        val domainFilter = DomainFilter()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        loadDefaultRules()
    }

    private fun loadDefaultRules() {
        try {
            resources.openRawResource(R.raw.default_blocklist).use {
                domainFilter.loadBlocklist(it)
            }
            resources.openRawResource(R.raw.default_whitelist).use {
                domainFilter.loadWhitelist(it)
            }
            Log.d(TAG, "Loaded rules: ${domainFilter.getBlockedDomains().size} blocked domains.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load default rules", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        val upstream = intent?.getStringExtra(EXTRA_UPSTREAM_DNS) ?: "1.1.1.1"
        startVpn(upstream)
        return START_STICKY
    }

    private fun startVpn(upstreamDns: String) {
        if (isRunning.get()) return

        startForeground(NOTIFICATION_ID, createNotification("Protecting DNS queries..."))

        val builder = Builder()
            .setSession("AdBlocker")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("10.0.0.2")
            .addRoute("10.0.0.2", 32)
            .setBlocking(true)

        try {
            vpnInterface = builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN interface", e)
            stopSelf()
            return
        }

        if (vpnInterface == null) {
            Log.e(TAG, "VPN interface was null")
            stopSelf()
            return
        }

        isRunning.set(true)
        BlockerStats.setRunning(true)

        val resolver = DnsResolver(
            upstreamHost = upstreamDns,
            socketProtector = { socket -> protect(socket) }
        )

        workerThread = Thread({ runVpnLoop(vpnInterface!!, resolver) }, "AdBlocker-TUN-Worker").apply {
            start()
        }
    }

    private fun runVpnLoop(pfd: ParcelFileDescriptor, resolver: DnsResolver) {
        val inputStream = FileInputStream(pfd.fileDescriptor)
        val outputStream = FileOutputStream(pfd.fileDescriptor)
        val packetBuffer = ByteArray(4096)

        try {
            while (isRunning.get()) {
                val length = inputStream.read(packetBuffer)
                if (length <= 0) continue

                val query = DnsPacket.parseQuery(packetBuffer, length) ?: continue
                val isBlocked = domainFilter.isBlocked(query.qName)
                BlockerStats.record(query.qName, isBlocked)

                if (isBlocked) {
                    val nxResponse = DnsPacket.buildNxDomainResponse(query)
                    outputStream.write(nxResponse)
                    outputStream.flush()
                } else {
                    val upstreamResponse = resolver.resolve(query.dnsPayload)
                    if (upstreamResponse != null) {
                        val forwarded = DnsPacket.buildForwardedResponse(query, upstreamResponse)
                        outputStream.write(forwarded)
                        outputStream.flush()
                    } else {
                        // Upstream timeout: return NXDOMAIN as fallback
                        val fallback = DnsPacket.buildNxDomainResponse(query)
                        outputStream.write(fallback)
                        outputStream.flush()
                    }
                }
            }
        } catch (e: IOException) {
            if (isRunning.get()) {
                Log.e(TAG, "Error in VPN loop", e)
            }
        } finally {
            try { inputStream.close() } catch (_: Exception) {}
            try { outputStream.close() } catch (_: Exception) {}
        }
    }

    private fun stopVpn() {
        isRunning.set(false)
        BlockerStats.setRunning(false)
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        workerThread?.interrupt()
        workerThread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ad Blocker Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ad blocker protection status"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AdBlockerVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Ad Blocker Active")
            .setContentText(statusText)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}
