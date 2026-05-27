package com.Badnng.moe.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.service.quicksettings.TileService
import com.Badnng.moe.R
import com.Badnng.moe.activity.PermissionActivity
import rikka.shizuku.Shizuku

class CaptureTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.icon = Icon.createWithResource(this, R.drawable.note_qs)
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val captureMode = prefs.getString("capture_mode", "media_projection")
        val useShizuku = captureMode == "shizuku" && isShizukuReady()
        val useRoot = captureMode == "root"

        val intent = Intent(this, PermissionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("use_shizuku", useShizuku)
            putExtra("use_root", useRoot)
        }

        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
    }

    private fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }
}
