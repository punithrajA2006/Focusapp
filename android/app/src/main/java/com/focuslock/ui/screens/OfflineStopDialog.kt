package com.focuslock.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.focuslock.crypto.HmacSigner
import com.focuslock.ui.theme.*

@Composable
fun OfflineStopDialog(
    secretHex: String,
    onStopSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var tokenInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCardBg),
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderHighlight, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(44.dp).background(ElectricBlue.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = ElectricBlue)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Offline Laptop Stop Token",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )

                Text(
                    text = "Paste or scan the Base64 HMAC token generated on your Fedora laptop (focuslock token):",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = {
                        tokenInput = it.trim()
                        errorMessage = null
                    },
                    label = { Text("Base64 Signed Token") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(10.dp)
                )

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = it, color = DangerCrimson, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = TextMuted)
                    }

                    Button(
                        onClick = {
                            if (tokenInput.isBlank()) {
                                errorMessage = "Please enter token"
                                return@Button
                            }
                            val verified = HmacSigner.verifyOfflineToken(secretHex, tokenInput)
                            if (verified) {
                                Toast.makeText(context, "Signature verified. Session unlocked.", Toast.LENGTH_SHORT).show()
                                onStopSuccess()
                            } else {
                                errorMessage = "Invalid cryptographic signature or expired token."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerCrimson),
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Unlock", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
