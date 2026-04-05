package com.proxichat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.proxichat.app.data.preferences.UserPreferences
import com.proxichat.app.service.BluetoothChatService
import com.proxichat.app.ui.navigation.AppNavigation
import com.proxichat.app.ui.navigation.Routes
import com.proxichat.app.ui.theme.ProxiChatTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Determine start destination synchronously
        val isOnboardingComplete = runBlocking {
            userPreferences.isOnboardingComplete.first()
        }

        val startDestination = if (isOnboardingComplete) {
            Routes.DISCOVERY
        } else {
            Routes.ONBOARDING
        }

        // Start foreground service for maintaining Bluetooth connections
        if (isOnboardingComplete) {
            BluetoothChatService.start(this)
        }

        setContent {
            val darkModeSetting by userPreferences.darkMode.collectAsState(initial = "system")
            val isDarkTheme = when (darkModeSetting) {
                "on" -> true
                "off" -> false
                else -> isSystemInDarkTheme()
            }

            ProxiChatTheme(darkTheme = isDarkTheme) {
                AppNavigation(startDestination = startDestination)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            BluetoothChatService.stop(this)
        }
    }
}
