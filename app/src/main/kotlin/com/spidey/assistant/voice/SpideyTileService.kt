package com.spidey.assistant.voice

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Quick Settings tile that shows SPIDEY listening state and lets the user
 * start or stop the WakeWordService with a single tap — even from the lock screen
 * or without opening the app.
 *
 * Register in AndroidManifest.xml under <service> with
 *   android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"
 *
 * Tile label: "SPIDEY"
 * Active (green): service is running and listening
 * Inactive (grey): service is stopped — tap to start
 */
@RequiresApi(Build.VERSION_CODES.N)
class SpideyTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onStartListening() {
        super.onStartListening()
        // Sync tile state whenever the panel opens
        WakeWordService.isServiceRunning
            .onEach { running -> updateTile(running) }
            .launchIn(scope)
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val running = WakeWordService.isServiceRunning.value
        Log.d(TAG, "Tile tapped – service running=$running")
        if (running) {
            WakeWordService.stop(applicationContext)
            updateTile(false)
        } else {
            // startForegroundService IS allowed from a TileService onClick on all API levels
            WakeWordService.start(applicationContext)
            updateTile(true)
        }
    }

    private fun updateTile(running: Boolean) {
        val tile = qsTile ?: return
        tile.label = "SPIDEY"
        tile.contentDescription = if (running) "SPIDEY is listening" else "SPIDEY is stopped – tap to start"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (running) "Listening" else "Tap to start"
        }
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, android.R.drawable.ic_btn_speak_now)
        tile.updateTile()
    }

    companion object {
        private const val TAG = "SpideyTileService"
    }
}

