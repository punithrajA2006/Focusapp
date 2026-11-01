package com.focuslock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslock.data.model.AppWhitelistItem
import com.focuslock.data.model.PairingConfig
import com.focuslock.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isEnforcementReady: Boolean,
    pairingConfig: PairingConfig,
    onStartSession: (durationMinutes: Int, allowedPackages: Set<String>) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDurationMinutes by remember { mutableIntStateOf(60) }
    var selectedApps by remember {
        mutableStateOf(
            listOf(
                AppWhitelistItem("com.focuslock", "FocusLock (Core)", isEssential = true, isEnabled = true, category = "Core"),
                AppWhitelistItem("com.android.dialer", "Phone Dialer (Safety)", isEssential = true, isEnabled = true, category = "Emergency & Calls"),
                AppWhitelistItem("com.google.android.apps.docs.editors.docs", "Google Docs / Notes", isEssential = false, isEnabled = true, category = "Study Tools"),
                AppWhitelistItem("com.adobe.reader", "PDF Reader", isEssential = false, isEnabled = true, category = "Study Tools"),
                AppWhitelistItem("com.google.android.calculator", "Calculator", isEssential = false, isEnabled = true, category = "Study Tools"),
                AppWhitelistItem("com.google.android.youtube", "YouTube (Blocked during study)", isEssential = false, isEnabled = false, category = "Distraction")
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ElectricBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "FocusLock",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSlateBg,
                    titleContentColor = TextPrimary
                ),
                actions = {
                    IconButton(onClick = onOpenPairing) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Pair Laptop",
                            tint = if (pairingConfig.isPaired) AccentEmerald else TextMuted
                        )
                    }
                    IconButton(onClick = onOpenPermissions) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Permissions",
                            tint = if (isEnforcementReady) AccentEmerald else WarningAmber
                        )
                    }
                }
            )
        },
        containerColor = DarkSlateBg
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Pairing & Enforcement Status Banner
            item {
                Spacer(modifier = Modifier.height(4.dp))
                StatusHeaderCard(
                    isEnforcementReady = isEnforcementReady,
                    isPaired = pairingConfig.isPaired,
                    laptopHost = pairingConfig.laptopHost,
                    onOpenPermissions = onOpenPermissions,
                    onOpenPairing = onOpenPairing
                )
            }

            // Duration Selector
            item {
                DurationSelectorCard(
                    selectedMinutes = selectedDurationMinutes,
                    onSelect = { selectedDurationMinutes = it }
                )
            }

            // Allowed Apps Whitelist
            item {
                Text(
                    text = "Allowed Apps Whitelist",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )
                Text(
                    text = "Phone and dialer are unconditionally whitelisted for safety. Non-whitelisted apps are blocked instantly.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
            }

            items(selectedApps) { appItem ->
                AppWhitelistRow(
                    item = appItem,
                    onToggle = { isChecked ->
                        if (!appItem.isEssential) {
                            selectedApps = selectedApps.map {
                                if (it.packageName == appItem.packageName) it.copy(isEnabled = isChecked) else it
                            }
                        }
                    }
                )
            }

            // Start Session CTA
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val allowed = selectedApps.filter { it.isEnabled }.map { it.packageName }.toSet()
                        onStartSession(selectedDurationMinutes, allowed)
                    },
                    enabled = isEnforcementReady,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerCrimson,
                        disabledContainerColor = SurfaceCardBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isEnforcementReady) Color.White else TextMuted
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isEnforcementReady) "Engage Strict Lock (${selectedDurationMinutes}m)" else "Grant Permissions to Unlock",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isEnforcementReady) Color.White else TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Note: Once started, this phone cannot cancel or shorten the timer. Only timer expiration or laptop CLI stop command can exit.",
                    style = MaterialTheme.typography.bodySmall.copy(color = DangerCrimson.copy(alpha = 0.8f), fontSize = 11.sp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StatusHeaderCard(
    isEnforcementReady: Boolean,
    isPaired: Boolean,
    laptopHost: String,
    onOpenPermissions: () -> Unit,
    onOpenPairing: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardBg),
        modifier = Modifier.fillMaxWidth().border(1.dp, BorderHighlight, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isEnforcementReady) AccentEmerald else WarningAmber)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnforcementReady) "Lockdown Core Ready" else "Permissions Needed",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isPaired) "Paired with Fedora ($laptopHost)" else "Laptop Not Paired (QR code required)",
                    style = MaterialTheme.typography.bodySmall.copy(color = if (isPaired) AccentEmerald else TextMuted, fontSize = 11.sp)
                )
            }

            Row {
                if (!isEnforcementReady) {
                    TextButton(onClick = onOpenPermissions) {
                        Text(text = "Fix", color = WarningAmber, fontWeight = FontWeight.Bold)
                    }
                }
                if (!isPaired) {
                    TextButton(onClick = onOpenPairing) {
                        Text(text = "Pair", color = ElectricBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationSelectorCard(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit
) {
    val presets = listOf(25, 45, 60, 90, 120)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardBg),
        modifier = Modifier.fillMaxWidth().border(1.dp, BorderHighlight, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Session Duration",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
            )
            Text(
                text = "Monotonic hardware timer guarantees no early exit on phone.",
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { minutes ->
                    val isSelected = minutes == selectedMinutes
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) ElectricBlue else Color(0xFF0F172A))
                            .border(1.dp, if (isSelected) ElectricBlue else BorderHighlight, RoundedCornerShape(10.dp))
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${minutes}m",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else TextPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppWhitelistRow(
    item: AppWhitelistItem,
    onToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardBg),
        modifier = Modifier.fillMaxWidth().border(1.dp, BorderHighlight, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.appName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    )
                    if (item.isEssential) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentEmerald.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "LIFELINE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentEmerald
                                )
                            )
                        }
                    }
                }
                Text(
                    text = item.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                )
            }

            Switch(
                checked = item.isEnabled,
                onCheckedChange = onToggle,
                enabled = !item.isEssential,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = ElectricBlue,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Color(0xFF0F172A)
                )
            )
        }
    }
}
