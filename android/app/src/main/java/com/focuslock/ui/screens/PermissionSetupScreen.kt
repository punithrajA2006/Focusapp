package com.focuslock.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslock.permission.PermissionManager
import com.focuslock.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSetupScreen(
    onAllGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(PermissionManager.checkAllPermissions(context)) }

    // Re-check permissions whenever user returns to the screen
    LaunchedEffect(Unit) {
        permissions = PermissionManager.checkAllPermissions(context)
    }

    val refreshPermissions = {
        permissions = PermissionManager.checkAllPermissions(context)
        if (permissions.isReadyForSession) {
            onAllGranted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "FocusLock Setup",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Security & Enforcement Permissions",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSlateBg,
                    titleContentColor = TextPrimary
                ),
                actions = {
                    IconButton(onClick = refreshPermissions) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Permissions",
                            tint = ElectricBlue
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Card / Overall Status
            item {
                Spacer(modifier = Modifier.height(6.dp))
                OverallStatusCard(isReady = permissions.isReadyForSession)
            }

            // 1. Overlay Permission
            item {
                PermissionItemCard(
                    stepNumber = "1",
                    title = "Display Over Other Apps",
                    description = "Enables FocusLock to immediately draw the full-screen 'Back to Study' overlay over unwhitelisted apps.",
                    isGranted = permissions.overlayGranted,
                    icon = Icons.Default.Layers,
                    onGrantClick = {
                        try {
                            context.startActivity(PermissionManager.requestOverlayIntent(context))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open overlay settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 2. Accessibility Service
            item {
                PermissionItemCard(
                    stepNumber = "2",
                    title = "Accessibility Service",
                    description = "Detects foreground app switches in real time (under 50ms) and blocks Settings, uninstall, and distraction apps.",
                    isGranted = permissions.accessibilityGranted,
                    icon = Icons.Default.AccessibilityNew,
                    onGrantClick = {
                        try {
                            context.startActivity(PermissionManager.requestAccessibilityIntent())
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open accessibility settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 3. Usage Access
            item {
                PermissionItemCard(
                    stepNumber = "3",
                    title = "Usage Access",
                    description = "Acts as a secondary detection engine for active tasks, providing fallback verification if accessibility events stutter.",
                    isGranted = permissions.usageStatsGranted,
                    icon = Icons.Default.QueryStats,
                    onGrantClick = {
                        try {
                            context.startActivity(PermissionManager.requestUsageStatsIntent())
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open usage settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 4. Device Administrator
            item {
                PermissionItemCard(
                    stepNumber = "4",
                    title = "Device Administrator",
                    description = "Prevents FocusLock from being uninstalled, force-stopped, or having its data wiped while a session is running.",
                    isGranted = permissions.deviceAdminGranted,
                    icon = Icons.Default.Security,
                    onGrantClick = {
                        try {
                            context.startActivity(PermissionManager.requestDeviceAdminIntent(context))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open device admin prompt", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 5. Battery Optimization Exemption
            item {
                PermissionItemCard(
                    stepNumber = "5",
                    title = "Ignore Battery Optimizations",
                    description = "Prevents Android OS Doze mode from killing the foreground service or monotonic timer during long study blocks.",
                    isGranted = permissions.batteryOptimizationsIgnored,
                    icon = Icons.Default.BatteryChargingFull,
                    onGrantClick = {
                        try {
                            context.startActivity(PermissionManager.requestIgnoreBatteryOptimizationsIntent(context))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open battery settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 6. Device Owner (Advanced Hardening)
            item {
                DeviceOwnerCard(
                    isDeviceOwner = permissions.deviceOwnerGranted,
                    onCopyCommand = { cmd ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ADB Command", cmd))
                        Toast.makeText(context, "ADB command copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAllGranted,
                    enabled = permissions.isReadyForSession,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue,
                        disabledContainerColor = SurfaceCardBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (permissions.isReadyForSession) "Continue to FocusLock" else "Grant Required Permissions Above",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (permissions.isReadyForSession) Color.Black else TextMuted
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun OverallStatusCard(isReady: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isReady) AccentEmerald else BorderHighlight, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isReady) AccentEmerald.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isReady) AccentEmerald else WarningAmber,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isReady) "Enforcement Core Ready" else "Setup Required",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isReady) AccentEmerald else WarningAmber
                    )
                )
                Text(
                    text = if (isReady)
                        "All primary lock permissions granted. Sessions will be strictly tamper-proof."
                    else
                        "Complete the permissions below so distractions cannot bypass the lock.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    stepNumber: String,
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    onGrantClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isGranted) AccentEmerald.copy(alpha = 0.4f) else BorderHighlight,
                RoundedCornerShape(14.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isGranted) AccentEmerald.copy(alpha = 0.12f) else ElectricBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) AccentEmerald else ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$stepNumber. $title",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                    Text(
                        text = if (isGranted) "Active & Enforced" else "Action Required",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isGranted) AccentEmerald else WarningAmber,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Granted",
                        tint = AccentEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Button(
                        onClick = onGrantClick,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Enable",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, lineHeight = 18.sp)
            )
        }
    }
}

@Composable
private fun DeviceOwnerCard(
    isDeviceOwner: Boolean,
    onCopyCommand: (String) -> Unit
) {
    val adbCmd = "adb shell dpm set-device-owner com.focuslock/.receiver.FocusLockDeviceAdminReceiver"

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isDeviceOwner) AccentEmerald.copy(alpha = 0.5f) else BorderHighlight,
                RoundedCornerShape(14.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDeviceOwner) AccentEmerald.copy(alpha = 0.12f) else Color(0xFF8B5CF6).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = if (isDeviceOwner) AccentEmerald else Color(0xFFA78BFA),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Device Owner (Ultimate Hardening)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                    Text(
                        text = if (isDeviceOwner) "Device Owner Active" else "Optional via ADB (Fedora Terminal)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isDeviceOwner) AccentEmerald else Color(0xFFA78BFA),
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Grants OS-level lockdown: disables safe mode boot, prevents factory reset, and blocks uninstallation without phone root. Note: Android requires NO accounts (Google, etc.) logged into the phone when provisioning.",
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, lineHeight = 18.sp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Command Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF070A11))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = adbCmd,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = ElectricBlue,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = { onCopyCommand(adbCmd) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Command",
                            modifier = Modifier.size(16.dp),
                            tint = ElectricBlue
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copy ADB Shell Command",
                            style = MaterialTheme.typography.labelSmall.copy(color = ElectricBlue)
                        )
                    }
                }
            }
        }
    }
}
