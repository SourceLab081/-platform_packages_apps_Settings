/*
 * SPDX-FileCopyrightText: 2024-2026 Lunaris AOSP
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.themes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import java.io.File
import java.io.IOException

class VideoWallpaperService : WallpaperService() {
    companion object {
        private const val TAG = "VideoWallpaperService"
        private const val PREFS_NAME = "video_wallpaper_prefs"
        const val ACTION_VIDEO_WALLPAPER_UPDATED = "com.android.settings.themes.ACTION_VIDEO_WALLPAPER_UPDATED"
    }

    override fun onCreateEngine(): Engine {
        return VideoEngine()
    }

    private inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null
        private var isVisible = false
        private var isPrepared = false
        private var prefs: SharedPreferences? = null
        private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
        private var handler: Handler? = null
        private var currentVideoPath: String? = null

        private var currentPlaybackSpeed = 1.0f
        private var pauseOnLock = true

        private var checkFileRunnable: Runnable? = null
        private var reloadRunnable: Runnable? = null
        private var activeHolder: SurfaceHolder? = null
        private var updateReceiver: BroadcastReceiver? = null
        private var userUnlockedReceiver: BroadcastReceiver? = null
        private var retryCount = 0

        private fun initPrefs(holder: SurfaceHolder) {
            try {
                if (prefs == null) {
                    prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                }
                loadSettings()
                if (prefsListener == null) {
                    prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                        Log.d(TAG, "Preference changed: $key")
                        if ("current_wallpaper_path" == key) {
                            val newPath = sharedPreferences.getString(key, null)
                            if (newPath != null && newPath != currentVideoPath) {
                                Log.d(TAG, "New video detected, reloading...")
                                currentVideoPath = newPath
                                retryCount = 0
                                handler?.post {
                                    releaseMediaPlayer()
                                    activeHolder?.let { createMediaPlayer(it) } ?: createMediaPlayer(holder)
                                }
                            }
                        } else {
                            loadSettings()
                            if (mediaPlayer != null && isPrepared) {
                                applyPlaybackSpeed()
                            }
                        }
                    }
                    prefs?.registerOnSharedPreferenceChangeListener(prefsListener)
                }
            } catch (e: Exception) {
                Log.w(TAG, "CE storage locked, preferences not available. Waiting for unlock.")
                prefs = null
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)

            handler = Handler(Looper.getMainLooper())
            initPrefs(surfaceHolder)

            val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
            userUnlockedReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    Log.d(TAG, "User unlocked, checking wallpaper initialization")
                    if (prefs == null) {
                        initPrefs(surfaceHolder)
                    }
                    retryCount = 0
                    handler?.post {
                        activeHolder?.let { createMediaPlayer(it) } ?: createMediaPlayer(surfaceHolder)
                    }
                }
            }
            try {
                registerReceiver(userUnlockedReceiver, filter)

                val updateFilter = IntentFilter(ACTION_VIDEO_WALLPAPER_UPDATED)
                updateReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        Log.d(TAG, "Update broadcast received, triggering reload")
                        triggerReload()
                    }
                }
                registerReceiver(updateReceiver, updateFilter)
            } catch (e: Exception) {
                Log.e(TAG, "Error registering receivers", e)
            }

            setTouchEventsEnabled(false)

            checkFileRunnable = Runnable {
                activeHolder?.let {
                    if (mediaPlayer == null) {
                        createMediaPlayer(it)
                    }
                }
            }

            reloadRunnable = Runnable {
                Log.d(TAG, "Executing delayed reload")
                loadSettings()
                activeHolder?.let {
                    releaseMediaPlayer()
                    createMediaPlayer(it)
                }
            }
        }

        private fun triggerReload() {
            reloadRunnable?.let {
                handler?.removeCallbacks(it)
                handler?.postDelayed(it, 500)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible

            if (visible) {
                if (mediaPlayer != null && isPrepared && mediaPlayer?.isPlaying == false) {
                    mediaPlayer?.start()
                    Log.d(TAG, "MediaPlayer started (visible)")
                }
            } else {
                if (pauseOnLock && mediaPlayer != null && mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                    Log.d(TAG, "MediaPlayer paused (not visible)")
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            activeHolder = holder
            retryCount = 0
            createMediaPlayer(holder)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            activeHolder = holder
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            activeHolder = null
            releaseMediaPlayer()
        }

        override fun onDestroy() {
            super.onDestroy()

            userUnlockedReceiver?.let {
                try {
                    unregisterReceiver(it)
                } catch (e: Exception) {}
                userUnlockedReceiver = null
            }

            updateReceiver?.let {
                try {
                    unregisterReceiver(it)
                } catch (e: Exception) {}
                updateReceiver = null
            }

            prefsListener?.let {
                prefs?.unregisterOnSharedPreferenceChangeListener(it)
                prefsListener = null
            }

            handler?.let {
                it.removeCallbacksAndMessages(null)
                handler = null
            }

            releaseMediaPlayer()
            prefs = null

            Log.d(TAG, "Engine destroyed, all resources cleaned")
        }

        private fun loadSettings() {
            val p = prefs ?: return

            pauseOnLock = p.getBoolean("pause_on_lock", true)
            currentVideoPath = p.getString("current_wallpaper_path", null)

            val speedStr = p.getString("playback_speed", "1.0")
            try {
                currentPlaybackSpeed = speedStr?.toFloat() ?: 1.0f
                currentPlaybackSpeed = currentPlaybackSpeed.coerceIn(0.25f, 2.0f)
            } catch (e: NumberFormatException) {
                currentPlaybackSpeed = 1.0f
            }

            Log.d(
                TAG,
                "Settings loaded - Speed: ${currentPlaybackSpeed}x, PauseOnLock: $pauseOnLock"
            )
        }

        private fun createMediaPlayer(holder: SurfaceHolder) {
            try {
                val wallpaperFile = MediaUtils.getCurrentWallpaperFile(this@VideoWallpaperService)

                if (wallpaperFile == null || !wallpaperFile.exists() || !wallpaperFile.canRead()) {
                    val path = wallpaperFile?.absolutePath ?: "unknown"
                    Log.w(TAG, "Wallpaper file unavailable ($path).")

                    releaseMediaPlayer()

                    if (wallpaperFile != null && retryCount < 10) {
                        Log.d(TAG, "Retrying in 2s... ($retryCount)")
                        retryCount++
                        checkFileRunnable?.let { handler?.postDelayed(it, 2000) }
                    } else {
                        drawErrorMessage(
                            holder,
                            if (wallpaperFile == null) "No wallpaper set\nPick a video in Settings" else "Wallpaper file not found"
                        )
                    }
                    return
                }

                checkFileRunnable?.let { handler?.removeCallbacks(it) }
                retryCount = 0

                Log.d(TAG, "Loading wallpaper: ${wallpaperFile.absolutePath}")
                currentVideoPath = wallpaperFile.absolutePath

                mediaPlayer = MediaPlayer().apply {
                    setSurface(holder.surface)
                    setDataSource(wallpaperFile.absolutePath)
                    isLooping = true
                    setVolume(0f, 0f)

                    setOnPreparedListener { mp ->
                        isPrepared = true
                        Log.d(TAG, "MediaPlayer prepared")

                        applyPlaybackSpeed()

                        if (isVisible) {
                            mp.start()
                            Log.d(TAG, "MediaPlayer started")
                        }
                    }

                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        val errorMsg = getErrorMessage(what)
                        releaseMediaPlayer()
                        drawErrorMessage(holder, errorMsg)
                        true
                    }

                    setOnInfoListener { _, what, _ ->
                        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                            Log.d(TAG, "Video rendering started")
                        }
                        false
                    }

                    setOnCompletionListener { mp ->
                        Log.w(TAG, "Video completed unexpectedly")
                        if (isVisible) {
                            mp.start()
                        }
                    }

                    prepareAsync()
                }

            } catch (e: IOException) {
                Log.e(TAG, "Error creating MediaPlayer: ${e.message}", e)
                releaseMediaPlayer()
                drawErrorMessage(holder, "Failed to load video")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error: ${e.message}", e)
                releaseMediaPlayer()
                drawErrorMessage(holder, "Unexpected error")
            }
        }

        private fun applyPlaybackSpeed() {
            val mp = mediaPlayer
            if (mp == null || !isPrepared) return

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val params = mp.playbackParams
                    params.speed = currentPlaybackSpeed
                    mp.playbackParams = params
                    Log.d(TAG, "Playback speed applied: ${currentPlaybackSpeed}x")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting playback speed: ${e.message}")
            }
        }

        private fun getErrorMessage(errorCode: Int): String {
            return when (errorCode) {
                MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "Media server died"
                MediaPlayer.MEDIA_ERROR_UNKNOWN -> "Unknown error"
                else -> "Playback error ($errorCode)"
            }
        }

        private fun drawErrorMessage(holder: SurfaceHolder, message: String) {
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(-0xe3e4e1)

                    val paint = Paint().apply {
                        isAntiAlias = true
                        color = -0x4cd9e2
                        textSize = 48f
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText(
                        "⚠",
                        canvas.width / 2f,
                        canvas.height / 2f - 100,
                        paint
                    )

                    paint.apply {
                        color = -0x191e1b
                        textSize = 28f
                    }
                    val lines = message.split("\n").toTypedArray()
                    var y = canvas.height / 2f

                    for (line in lines) {
                        canvas.drawText(line, canvas.width / 2f, y, paint)
                        y += 36f
                    }

                    paint.apply {
                        textSize = 18f
                        color = -0x6c7067
                    }
                    canvas.drawText(
                        "Open Settings → Themes → Video Wallpaper",
                        canvas.width / 2f,
                        canvas.height - 100f,
                        paint
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing error message: ${e.message}")
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error unlocking canvas: ${e.message}")
                    }
                }
            }
        }

        private fun releaseMediaPlayer() {
            mediaPlayer?.let {
                try {
                    if (it.isPlaying) {
                        it.stop()
                    }
                    it.reset()
                    it.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing MediaPlayer: ${e.message}")
                } finally {
                    mediaPlayer = null
                    isPrepared = false
                    Log.d(TAG, "MediaPlayer released")
                }
            }
        }
    }
}
