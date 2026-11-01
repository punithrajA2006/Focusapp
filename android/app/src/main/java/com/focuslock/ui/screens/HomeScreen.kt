package com.focuslock.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.focuslock.data.model.FocusStats
import com.focuslock.data.model.PairingConfig
import com.focuslock.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledApp(val pkg: String, val label: String, val icon: ImageBitmap?)

/** Study apps that get pre-selected if they are installed. Edit this list to taste. */
private val SUGGESTED_STUDY_PACKAGES = setOf(
    "com.google.android.calculator", "com.sec.android.app.popupcalculator",
    "com.google.android.apps.docs.editors.docs", "com.google.android.apps.docs",
    "com.google.android.keep", "com.adobe.reader", "com.ichi2.anki",
    "md.obsidian", "notion.id", "com.termux", "com.google.android.apps.classroom"
)

private fun loadLaunchableApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .mapNotNull { info ->
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName) return@mapNotNull null
            val icon = runCatching { info.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            InstalledApp(pkg, info.loadLabel(pm).toString(), icon)
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

private fun formatMinutes(min: Int): String =
    if (min >= 60) "${min / 60}h" + (if (min % 60 != 0) " ${min % 60}m" else "") else "$min min"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isEnforcementReady: Boolean,
    pairingConfig: PairingConfig,
    stats: FocusStats,
    onStartSession: (durationMinutes: Int, allowedPackages: Set<String>) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var minutes by remember { mutableIntStateOf(60) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirming by remember { mutableStateOf(false) }
    var seeded by remember { mutableStateOf(false) }

    val apps by produceState(initialValue = emptyList<InstalledApp>()) {
        value = withContext(Dispatchers.Default) { loadLaunchableApps(context) }
    }
    LaunchedEffect(apps) {
        if (!seeded && apps.isNotEmpty()) {
            selected = apps.map { it.pkg }.filter { it in SUGGESTED_STUDY_PACKAGES }.toSet()
            seeded = true
        }
    }
    val visible = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.background(AppBackdrop),
        bottomBar = {
            Surface(color = DarkSlateBg.copy(alpha = 0.96f), tonalElevation = 0.dp) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Button(
                        onClick = { confirming = true },
                        enabled = isEnforcementReady,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerCrimson,
                            disabledContainerColor = SurfaceRaised
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = if (isEnforcementReady) Color.White else TextMuted)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (isEnforcementReady) "Lock in for ${formatMinutes(minutes)}" else "Grant permissions to start",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (isEnforcementReady) Color.White else TextMuted
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "No cancel button. It ends when the timer does, or when you stop it from your laptop.",
                        fontSize = 11.sp, color = TextMuted
                    )
                }
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner).statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Header(isEnforcementReady, pairingConfig.isPaired, onOpenPermissions, onOpenPairing) }
            item { StatsRow(stats) }
            item { DurationCard(minutes) { minutes = it } }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Apps you can use", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                    Text(
                        "Everything else is blocked. Phone calls always work. ${selected.size} selected.",
                        fontSize = 12.sp, color = TextMuted
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search your apps", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = BorderHighlight,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (apps.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator(color = ElectricBlue) } }
            }
            items(visible, key = { it.pkg }) { app ->
                AppRow(app, app.pkg in selected) { on ->
                    selected = if (on) selected + app.pkg else selected - app.pkg
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = SurfaceCardBg,
            title = { Text("Start ${formatMinutes(minutes)} lock?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${selected.size} apps stay available. You will not be able to cancel from this phone.",
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = { confirming = false; onStartSession(minutes, selected) },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerCrimson)
                ) { Text("Lock it", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Not yet", color = TextMuted) } }
        )
    }
}

@Composable
private fun Header(ready: Boolean, paired: Boolean, onPermissions: () -> Unit, onPairing: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeroGradient).padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("FocusLock", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Color.White)
                    Text("Study mode, built around you", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(
                    icon = { Icon(Icons.Default.Security, null, tint = if (ready) AccentEmerald else WarningAmber, modifier = Modifier.size(14.dp)) },
                    text = if (ready) "Lock ready" else "Fix permissions",
                    onClick = onPermissions
                )
                StatusChip(
                    icon = { Icon(Icons.Default.Link, null, tint = if (paired) AccentEmerald else WarningAmber, modifier = Modifier.size(14.dp)) },
                    text = if (paired) "Laptop paired" else "Pair laptop",
                    onClick = onPairing
                )
            }
        }
    }
}

@Composable
private fun StatusChip(icon: @Composable () -> Unit, text: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.28f)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun StatsRow(stats: FocusStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Hours", "%.1f".format(stats.totalMinutes / 60.0), ElectricBlue, Modifier.weight(1f))
        StatTile("Sessions", stats.completedSessions.toString(), AccentViolet, Modifier.weight(1f))
        StatTile("Streak", "${stats.streakDays}d", AccentEmerald, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(SurfaceCardBg)
            .border(1.dp, BorderHighlight, RoundedCornerShape(18.dp)).padding(14.dp)
    ) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = accent)
        Text(label, fontSize = 12.sp, color = TextMuted)
    }
}

@Composable
private fun DurationCard(minutes: Int, onChange: (Int) -> Unit) {
    val presets = listOf(25, 45, 60, 90, 120)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SurfaceCardBg)
            .border(1.dp, BorderHighlight, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("How long?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            FilledTonalIconButton(
                onClick = { onChange((minutes - 5).coerceAtLeast(10)) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = SurfaceRaised, contentColor = TextPrimary)
            ) { Icon(Icons.Default.Remove, contentDescription = "Less time") }
            Text(formatMinutes(minutes), fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, color = TextPrimary)
            FilledTonalIconButton(
                onClick = { onChange((minutes + 5).coerceAtMost(240)) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = SurfaceRaised, contentColor = TextPrimary)
            ) { Icon(Icons.Default.Add, contentDescription = "More time") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                val on = preset == minutes
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (on) ElectricBlue else SurfaceRaised)
                        .clickable { onChange(preset) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${preset}m", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (on) Color.Black else TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (checked) SurfaceRaised else SurfaceCardBg)
            .border(1.dp, if (checked) ElectricBlue.copy(alpha = 0.5f) else BorderHighlight, RoundedCornerShape(16.dp))
            .clickable { onToggle(!checked) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
        } else {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(BorderHighlight))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
            Text(app.pkg, fontSize = 10.sp, color = TextMuted, maxLines = 1)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black, checkedTrackColor = ElectricBlue,
                uncheckedThumbColor = TextMuted, uncheckedTrackColor = DarkSlateBg
            )
        )
    }
}
