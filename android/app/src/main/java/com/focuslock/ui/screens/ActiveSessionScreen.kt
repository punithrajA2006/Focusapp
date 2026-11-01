package com.focuslock.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslock.data.model.ActiveSession
import com.focuslock.ui.theme.*
import kotlinx.coroutines.delay

private fun clock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
fun ActiveSessionScreen(
    session: ActiveSession,
    laptopPaired: Boolean,
    laptopOnline: Boolean,
    verifyOfflineToken: (String) -> Boolean,
    onOpenStudyYouTube: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latest by rememberUpdatedState(session)
    var remainingMs by remember { mutableLongStateOf(session.remainingTimeMs(SystemClock.elapsedRealtime())) }
    var showTokenDialog by remember { mutableStateOf(false) }

    LaunchedEffect(session.sessionId) {
        while (true) {
            remainingMs = latest.remainingTimeMs(SystemClock.elapsedRealtime())
            delay(500)
        }
    }

    val progress by animateFloatAsState(
        targetValue = (1f - remainingMs.toFloat() / session.expectedDurationMs.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "ring"
    )

    Column(
        modifier.fillMaxSize().background(AppBackdrop).statusBarsPadding().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Locked in", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElectricBlue, letterSpacing = 3.sp)
        Spacer(Modifier.height(20.dp))

        Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 18.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = BorderHighlight, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    brush = Brush.sweepGradient(listOf(ElectricBlue, AccentViolet, ElectricBlue)),
                    startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(clock(remainingMs), fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, color = TextPrimary)
                Text("remaining", fontSize = 13.sp, color = TextMuted)
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (!laptopPaired) "Laptop not paired" else if (laptopOnline) "Laptop linked" else "Laptop offline",
                if (laptopOnline) AccentEmerald else WarningAmber)
            Pill("Apps locked", AccentEmerald)
        }

        Spacer(Modifier.height(18.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceCardBg)
                .border(1.dp, BorderHighlight, RoundedCornerShape(20.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("This session ends when the timer does, or when you stop it from your laptop.", color = TextPrimary, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Session code", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(session.sessionCode.uppercase(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = ElectricBlue, fontSize = 18.sp)
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onOpenStudyYouTube,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.Black),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Icon(Icons.Default.PlayCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Study YouTube", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = { showTokenDialog = true }) {
            Icon(Icons.Default.Key, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Wi-Fi down? Enter laptop token", color = TextMuted, fontSize = 12.sp)
        }
    }

    if (showTokenDialog) {
        OfflineStopDialog(
            verifyToken = verifyOfflineToken,
            onStopSuccess = { showTokenDialog = false },
            onDismiss = { showTokenDialog = false }
        )
    }
}

@Composable
private fun Pill(text: String, dot: Color) {
    Row(
        Modifier.clip(CircleShape).background(SurfaceCardBg).border(1.dp, BorderHighlight, CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}
