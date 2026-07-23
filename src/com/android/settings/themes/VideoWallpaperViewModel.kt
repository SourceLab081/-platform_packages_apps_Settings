/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.themes

import android.app.Application
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideoWallpaperViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "VideoWallpaperViewModel"
    private val PREFS_NAME = "video_wallpaper_prefs"

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentWallpaperFile = MutableStateFlow<File?>(null)
    val currentWallpaperFile: StateFlow<File?> = _currentWallpaperFile

    private val _playbackSpeed = MutableStateFlow(prefs.getString("playback_speed", "1.0") ?: "1.0")
    val playbackSpeed: StateFlow<String> = _playbackSpeed

    private val _pauseOnLock = MutableStateFlow(prefs.getBoolean("pause_on_lock", true))
    val pauseOnLock: StateFlow<Boolean> = _pauseOnLock

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _savedWallpapers = MutableStateFlow<List<File>>(emptyList())
    val savedWallpapers: StateFlow<List<File>> = _savedWallpapers

    init {
        refreshWallpaperInfo()
    }

    private fun notifyWallpaperUpdated() {
        Log.d(TAG, "Sending wallpaper update broadcast")
        getApplication<Application>().sendBroadcast(Intent(VideoWallpaperService.ACTION_VIDEO_WALLPAPER_UPDATED))
    }

    fun refreshWallpaperInfo() {
        _currentWallpaperFile.value = MediaUtils.getCurrentWallpaperFile(getApplication())
        _savedWallpapers.value = MediaUtils.getSavedWallpaperFiles(getApplication())
    }

    fun handleVideoSelection(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            val savedPath = withContext(Dispatchers.IO) {
                MediaUtils.saveMediaToWallpaperStorage(getApplication(), uri)
            }
            _isProcessing.value = false

            if (savedPath != null) {
                refreshWallpaperInfo()
                notifyWallpaperUpdated()
                enableVideoWallpaper()
            }
        }
    }

    fun applySavedWallpaper(file: File) {
        viewModelScope.launch {
            _isProcessing.value = true
            val activePath = withContext(Dispatchers.IO) {
                MediaUtils.activateSavedWallpaper(getApplication(), file)
            }
            if (activePath != null) {
                refreshWallpaperInfo()
                notifyWallpaperUpdated()
                enableVideoWallpaper()
            }
            _isProcessing.value = false
        }
    }

    fun setPlaybackSpeed(speed: String) {
        prefs.edit().putString("playback_speed", speed).apply()
        _playbackSpeed.value = speed
        notifyWallpaperUpdated()
    }

    fun setPauseOnLock(pause: Boolean) {
        prefs.edit().putBoolean("pause_on_lock", pause).apply()
        _pauseOnLock.value = pause
        notifyWallpaperUpdated()
    }

    fun clearWallpaper() {
        if (MediaUtils.deleteCurrentWallpaper(getApplication())) {
            refreshWallpaperInfo()
            notifyWallpaperUpdated()
            MediaUtils.applyDefaultWallpaper(getApplication())
        }
    }

    fun deleteSavedWallpaper(file: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (file.exists()) {
                    val wasActive = currentWallpaperFile.value?.absolutePath == file.absolutePath
                    if (file.delete()) {
                        if (wasActive) {
                            withContext(Dispatchers.Main) {
                                clearWallpaper()
                            }
                        }
                    }
                }
            }
            refreshWallpaperInfo()
        }
    }

    fun enableVideoWallpaper() {
        val context = getApplication<Application>()
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(context, VideoWallpaperService::class.java)
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling video wallpaper", e)
        }
    }
}
