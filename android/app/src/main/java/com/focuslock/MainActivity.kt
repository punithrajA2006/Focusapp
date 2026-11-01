package com.focuslock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.focuslock.permission.PermissionManager
import com.focuslock.ui.screens.HomeScreen
import com.focuslock.ui.screens.PermissionSetupScreen
import com.focuslock.ui.theme.DarkSlateBg
import com.focuslock.ui.theme.FocusLockTheme
import kotlinx.coroutines.launch

enum class AppNavScreen {
    SETUP_PERMISSIONS,
    HOME,
    PAIRING_QR
}

class MainActivity : ComponentActivity() {

    private lateinit var app: FocusLockApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as FocusLockApplication

        setContent {
            FocusLockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkSlateBg
                ) {
                    var currentScreen by remember {
                        val ready = PermissionManager.checkAllPermissions(this@MainActivity).isReadyForSession
                        mutableStateOf(if (ready) AppNavScreen.HOME else AppNavScreen.SETUP_PERMISSIONS)
                    }

                    var isEnforcementReady by remember {
                        mutableStateOf(PermissionManager.checkAllPermissions(this@MainActivity).isReadyForSession)
                    }

                    val pairingConfig by app.repository.pairingConfigFlow.collectAsState()

                    // Re-evaluate permissions when resuming
                    DisposableEffect(Unit) {
                        isEnforcementReady = PermissionManager.checkAllPermissions(this@MainActivity).isReadyForSession
                        onDispose { }
                    }

                    when (currentScreen) {
                        AppNavScreen.SETUP_PERMISSIONS -> {
                            PermissionSetupScreen(
                                onAllGranted = {
                                    isEnforcementReady = true
                                    currentScreen = AppNavScreen.HOME
                                }
                            )
                        }
                        AppNavScreen.HOME -> {
                            HomeScreen(
                                isEnforcementReady = isEnforcementReady,
                                pairingConfig = pairingConfig,
                                onStartSession = { duration, allowed ->
                                    lifecycleScope.launch {
                                        app.repository.startSession(duration, allowed)
                                        com.focuslock.service.SessionEnforcementService.start(this@MainActivity)
                                    }
                                },
                                onOpenPermissions = {
                                    currentScreen = AppNavScreen.SETUP_PERMISSIONS
                                },
                                onOpenPairing = {
                                    // In Phase 4, launch QR scanner
                                }
                            )
                        }
                        AppNavScreen.PAIRING_QR -> {
                            // Implemented in Phase 4
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure UI refreshes when returning from system Settings menus
    }
}
