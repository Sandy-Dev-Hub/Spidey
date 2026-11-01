package com.spidey.spikes

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.spidey.spikes.spike1.WakeWordSpikeScreen
import com.spidey.spikes.spike2.MicHandoffSpikeScreen
import com.spidey.spikes.spike3.BgLaunchSpikeScreen
import com.spidey.spikes.spike4.UnlockSpikeScreen
import com.spidey.spikes.spike5.CallSpikeScreen
import com.spidey.spikes.spike6.ScreenOffSpikeScreen
import com.spidey.spikes.util.SpikeLogger

import com.spidey.assistant.ui.home.HomeScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SpikeLogger.init(this)

        // Request notifications permission upfront (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpideyNavGraph()
                }
            }
        }
    }
}

@Composable
fun SpideyNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home")    {
            HomeScreen(
                onNavigateToSpikes = { nav.navigate("menu") },
                onNavigateToAliases = { nav.navigate("aliases") }
            )
        }
        composable("aliases") {
            com.spidey.assistant.ui.settings.AliasSettingsScreen(
                onNavigateBack = { nav.popBackStack() }
            )
        }
        composable("menu")    { SpikeMenuScreen(nav) }
        composable("spike1")  { WakeWordSpikeScreen() }
        composable("spike2")  { MicHandoffSpikeScreen() }
        composable("spike3")  { BgLaunchSpikeScreen() }
        composable("spike4")  { UnlockSpikeScreen() }
        composable("spike5")  { CallSpikeScreen() }
        composable("spike6")  { ScreenOffSpikeScreen() }
    }
}
