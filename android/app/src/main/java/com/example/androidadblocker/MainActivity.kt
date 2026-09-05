package com.example.androidadblocker

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.androidadblocker.service.AdBlockerVpnService
import com.example.androidadblocker.theme.AndroidAdBlockerTheme
import com.example.androidadblocker.ui.main.MainScreen

class MainActivity : ComponentActivity() {

    private var pendingUpstreamDns: String = "1.1.1.1"

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService(pendingUpstreamDns)
        } else {
            Toast.makeText(this, "VPN permission is required for local DNS ad blocking", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AndroidAdBlockerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onToggleVpn = { start, upstreamDns ->
                            if (start) {
                                requestVpnAndStart(upstreamDns)
                            } else {
                                stopVpnService()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun requestVpnAndStart(upstreamDns: String) {
        pendingUpstreamDns = upstreamDns
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            startVpnService(upstreamDns)
        }
    }

    private fun startVpnService(upstreamDns: String) {
        val intent = Intent(this, AdBlockerVpnService::class.java).apply {
            action = AdBlockerVpnService.ACTION_START
            putExtra(AdBlockerVpnService.EXTRA_UPSTREAM_DNS, upstreamDns)
        }
        startService(intent)
    }

    private fun stopVpnService() {
        val intent = Intent(this, AdBlockerVpnService::class.java).apply {
            action = AdBlockerVpnService.ACTION_STOP
        }
        startService(intent)
    }
}
