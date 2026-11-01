package com.focuslock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.focuslock.crypto.HmacSigner
import com.focuslock.data.model.SessionStatus
import com.focuslock.permission.PermissionManager
import com.focuslock.service.SessionEnforcementService
import com.focuslock.ui.screens.ActiveSessionScreen
import com.focuslock.ui.screens.HomeScreen
import com.focuslock.ui.screens.PairingScreen
import com.focuslock.ui.screens.PermissionSetupScreen
import com.focuslock.ui.screens.YouTubeStudyScreen
import com.focuslock.ui.theme.DarkSlateBg
import com.focuslock.ui.theme.FocusLockTheme

enum class AppNavScreen {
    SETUP_PERMISSIONS,
    HOME,
    PAIRING_QR
}

class MainActivity : ComponentActivity() {

    private lateinit var app: FocusLockApplication
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as FocusLockApplication

        setContent {
            FocusLockTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkSlateBg) {
                    val repo = app.repository
                    val session by repo.activeSessionFlow.collectAsState()
                    val pairing by repo.pairingConfigFlow.collectAsState()
                    val stats by repo.statsFlow.collectAsState()
                    val laptopOnline by repo.laptopOnlineFlow.collectAsState()

                    // Re-checked every time the app returns from a system settings screen.
                    val ready = remember(resumeTick) {
                        PermissionManager.checkAllPermissions(this@MainActivity).isReadyForSession
                    }
                    var screen by remember { mutableStateOf(if (ready) AppNavScreen.HOME else AppNavScreen.SETUP_PERMISSIONS) }
                    var showYouTube by remember { mutableStateOf(false) }

                    val active = session?.takeIf { it.status == SessionStatus.ACTIVE }

                    when {
                        active != null && showYouTube -> YouTubeStudyScreen(onClose = { showYouTube = false })

                        active != null -> ActiveSessionScreen(
                            session = active,
                            laptopPaired = pairing.isPaired,
                            laptopOnline = laptopOnline,
                            verifyOfflineToken = { token ->
                                val verdict = HmacSigner.verifyOfflineToken(
                                    pairing.hmacSecretHex, token, active.sessionCode
                                ) { repo.isNonceUsed(it) }
                                if (verdict.accepted) {
                                    repo.rememberNonce(verdict.nonce)
                                    repo.endSession(SessionStatus.STOPPED_BY_LAPTOP)
                                }
                                verdict.accepted
                            },
                            onOpenStudyYouTube = { showYouTube = true }
                        )

                        else -> {
                            showYouTube = false
                            when (screen) {
                                AppNavScreen.SETUP_PERMISSIONS -> PermissionSetupScreen(
                                    onAllGranted = { screen = AppNavScreen.HOME }
                                )
                                AppNavScreen.HOME -> HomeScreen(
                                    isEnforcementReady = ready,
                                    pairingConfig = pairing,
                                    stats = stats,
                                    onStartSession = { minutes, allowed ->
                                        repo.startSession(minutes, allowed)
                                        SessionEnforcementService.start(this@MainActivity)
                                    },
                                    onOpenPermissions = { screen = AppNavScreen.SETUP_PERMISSIONS },
                                    onOpenPairing = { screen = AppNavScreen.PAIRING_QR }
                                )
                                AppNavScreen.PAIRING_QR -> PairingScreen(
                                    isPaired = pairing.isPaired,
                                    onSave = { repo.savePairingFromJson(it) },
                                    onBack = { screen = AppNavScreen.HOME }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        // If the process was restored mid-session, make sure enforcement is running again.
        val session = app.repository.activeSessionFlow.value
        if (session?.status == SessionStatus.ACTIVE) SessionEnforcementService.start(this)
    }
}
