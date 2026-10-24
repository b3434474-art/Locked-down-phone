package com.cousinlockdown

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class LockdownTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "Cousin Lock Down"
            state = if (LockdownService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
    override fun onClick() {
        super.onClick()
        if (LockdownService.running) {
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivityAndCollapse(launch)
            }
        } else {
            val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(intent)
        }
    }
}
