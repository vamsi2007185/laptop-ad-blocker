package com.example.androidadblocker.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidadblocker.model.BlockerStats
import com.example.androidadblocker.model.QueryLog
import com.example.androidadblocker.service.AdBlockerVpnService
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onToggleVpn: (Boolean, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val statsState by BlockerStats.state.collectAsState()
    var selectedUpstream by remember { mutableStateOf("1.1.1.1") }
    var showAddRuleDialog by remember { mutableStateOf(false) }

    val upstreamOptions = listOf(
        "1.1.1.1" to "Cloudflare (1.1.1.1)",
        "8.8.8.8" to "Google (8.8.8.8)",
        "9.9.9.9" to "Quad9 (9.9.9.9)",
        "94.140.14.14" to "AdGuard (94.140.14.14)"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Android Ad Blocker", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Status Card ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (statsState.isRunning) Color(0xFF1B5E20) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(
                                    if (statsState.isRunning) Color(0xFF4CAF50) else Color.Gray,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (statsState.isRunning) "🛡️" else "⏸️",
                                fontSize = 36.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (statsState.isRunning) "Ad Blocker Active" else "Ad Blocker Inactive",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (statsState.isRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = if (statsState.isRunning) "Filtering ads & trackers system-wide" else "Tap below to start protection",
                            fontSize = 14.sp,
                            color = if (statsState.isRunning) Color(0xFFC8E6C9) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { onToggleVpn(!statsState.isRunning, selectedUpstream) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (statsState.isRunning) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Text(
                                text = if (statsState.isRunning) "Stop Protection" else "Start Protection",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // --- Stats Row ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Blocked",
                        count = statsState.blocked,
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Allowed",
                        count = statsState.allowed,
                        color = Color(0xFF388E3C),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Total",
                        count = statsState.total,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // --- Upstream DNS Selector & Rules ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Upstream DNS Resolver",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            TextField(
                                value = upstreamOptions.firstOrNull { it.first == selectedUpstream }?.second ?: selectedUpstream,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                upstreamOptions.forEach { (ip, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            selectedUpstream = ip
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Custom Domain Rules",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Button(
                                onClick = { showAddRuleDialog = true },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("+ Add Rule")
                            }
                        }
                    }
                }
            }

            // --- Recent Queries Header ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Queries (${statsState.recentQueries.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    if (statsState.recentQueries.isNotEmpty()) {
                        TextButton(onClick = { BlockerStats.reset() }) {
                            Text("Clear")
                        }
                    }
                }
            }

            // --- Query Log List ---
            if (statsState.recentQueries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (statsState.isRunning) "Waiting for queries..." else "Protection stopped. No queries logged.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(statsState.recentQueries) { log ->
                    QueryLogItem(log = log)
                }
            }
        }
    }

    if (showAddRuleDialog) {
        AddRuleDialog(
            onDismiss = { showAddRuleDialog = false },
            onAdd = { domain, isBlock ->
                if (isBlock) {
                    AdBlockerVpnService.domainFilter.addBlockedDomain(domain)
                } else {
                    AdBlockerVpnService.domainFilter.addAllowedDomain(domain)
                }
                showAddRuleDialog = false
            }
        )
    }
}

@Composable
fun StatCard(title: String, count: Long, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = count.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun QueryLogItem(log: QueryLog) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (log.isBlocked) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.domain,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = Color.Black
                )
                Text(
                    text = timeFormat.format(Date(log.timestamp)),
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )
            }
            Surface(
                color = if (log.isBlocked) Color(0xFFD32F2F) else Color(0xFF388E3C),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = if (log.isBlocked) "BLOCKED" else "ALLOWED",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AddRuleDialog(onDismiss: () -> Unit, onAdd: (String, Boolean) -> Unit) {
    var domainText by remember { mutableStateOf("") }
    var isBlock by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Domain Rule") },
        text = {
            Column {
                OutlinedTextField(
                    value = domainText,
                    onValueChange = { domainText = it },
                    label = { Text("Domain (e.g., analytics.example.com)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isBlock, onClick = { isBlock = true })
                    Text("Block")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = !isBlock, onClick = { isBlock = false })
                    Text("Whitelist (Allow)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (domainText.isNotBlank()) {
                        onAdd(domainText.trim(), isBlock)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
