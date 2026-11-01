package com.focuslock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslock.ui.theme.*

/** Paste the JSON from `python cli.py pair` (or the laptop dashboard) to link this phone. */
@Composable
fun PairingScreen(
    isPaired: Boolean,
    onSave: (String) -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().background(AppBackdrop).statusBarsPadding().navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Pair your laptop", fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = TextPrimary)
        Text(
            "On the laptop run  python cli.py pair  (or open the dashboard), copy the JSON, and paste it here. " +
                "Keep server.py running while you study.",
            color = TextMuted, fontSize = 13.sp
        )
        if (isPaired) Text("Already paired. Pasting new JSON replaces it.", color = AccentEmerald, fontSize = 12.sp)

        OutlinedTextField(
            value = text,
            onValueChange = { text = it; message = null },
            placeholder = { Text("{\"v\":2,\"secret\":\"...\",\"laptop_host\":\"192.168.x.x\",\"laptop_port\":5001}", color = TextMuted, fontSize = 12.sp) },
            minLines = 6,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricBlue, unfocusedBorderColor = BorderHighlight,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )
        message?.let { Text(it, color = if (success) AccentEmerald else DangerCrimson, fontSize = 13.sp) }

        Button(
            onClick = {
                success = onSave(text)
                message = if (success) "Paired. You can go back." else "That is not valid pairing JSON, or a session is running."
            },
            enabled = text.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Save pairing", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back", color = TextMuted) }
    }
}
