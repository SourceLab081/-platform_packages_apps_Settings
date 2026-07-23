/*
 * SPDX-FileCopyrightText: 2024-2025 Lunaris AOSP
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.themes

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.android.settings.R
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaUtils {
    private const val TAG = "MediaUtils"
    private const val BUFFER_SIZE = 8192
    private const val MAX_FILE_SIZE = 50 * 1024 * 1024L
    private const val PREFS_NAME = "video_wallpaper_prefs"
    private const val KEY_WALLPAPER_PATH = "current_wallpaper_path"

    private const val BASE_DIRECTORY = "/sdcard/ASCP/Wallpapers"
    private const val HISTORY_DIRECTORY = "$BASE_DIRECTORY/processed"
    private const val ACTIVE_WALLPAPER_NAME = "active_wallpaper.mp4"

    private val SUPPORTED_VIDEO_FORMATS = listOf("mp4")
    private val SUPPORTED_IMAGE_FORMATS = listOf("gif", "webp")

    fun saveMediaToWallpaperStorage(context: Context, mediaUri: Uri?): String? {
        return saveMediaToExternalStorage(context, mediaUri, "wallpaper")
    }

    fun saveMediaToExternalStorage(
        context: Context,
        mediaUri: Uri?,
        filePrefix: String
    ): String? {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            if (mediaUri == null) {
                Log.e(TAG, "Media URI is null")
                return null
            }
            inputStream = getInputStreamFromUri(context, mediaUri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to get input stream from URI")
                return null
            }
            val extension = getFileExtension(context, mediaUri)
            if (!isValidWallpaperFormat(extension)) {
                Log.e(TAG, "Unsupported file format: $extension")
                return null
            }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${filePrefix}_$timeStamp$extension"

            val histDir = File(HISTORY_DIRECTORY)
            if (!histDir.exists() && !histDir.mkdirs()) {
                Log.e(TAG, "Failed to create history directory: ${histDir.absolutePath}")
                return null
            }

            val savedFile = File(histDir, fileName)
            outputStream = FileOutputStream(savedFile)
            val bytesCopied = copyStreamWithLimit(inputStream, outputStream, MAX_FILE_SIZE)

            if (bytesCopied < 0) {
                Log.e(TAG, "File size exceeds maximum allowed size")
                savedFile.delete()
                return null
            }

            val activeFile = File(BASE_DIRECTORY, ACTIVE_WALLPAPER_NAME)
            if (copyFile(savedFile, activeFile)) {
                val absolutePath = activeFile.absolutePath
                saveWallpaperPath(context, absolutePath)
                Log.d(TAG, "Media saved and activated: $absolutePath ($bytesCopied bytes)")
                return absolutePath
            } else {
                Log.e(TAG, "Failed to copy to active location")
                return savedFile.absolutePath
            }

        } catch (e: FileNotFoundException) {
            Log.e(TAG, "File not found: ${e.message}")
            return null
        } catch (e: IOException) {
            Log.e(TAG, "IO error: ${e.message}")
            return null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory: ${e.message}")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            return null
        } finally {
            closeQuietly(inputStream)
            closeQuietly(outputStream)
        }
    }

    private fun saveWallpaperPath(context: Context, path: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_WALLPAPER_PATH, path).apply()
            Log.d(TAG, "Saved wallpaper path to SharedPreferences: $path")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving wallpaper path: ${e.message}")
        }
    }

    fun getWallpaperPath(context: Context): String? {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val path = prefs.getString(KEY_WALLPAPER_PATH, null)
            Log.d(TAG, "Retrieved wallpaper path: $path")
            return path
        } catch (e: Exception) {
            Log.e(TAG, "Error getting wallpaper path: ${e.message}")
            return null
        }
    }

    fun getCurrentWallpaperFile(context: Context): File? {
        val savedPath = getWallpaperPath(context)
        if (savedPath != null) {
            val file = File(savedPath)
            if (file.exists()) {
                Log.d(TAG, "Found wallpaper from SharedPreferences: $savedPath")
                return file
            }
        }

        val directory = File(BASE_DIRECTORY)
        if (!directory.exists()) {
            Log.d(TAG, "Wallpaper directory does not exist")
            return null
        }

        val files = directory.listFiles { _, name ->
            val lower = name.lowercase(Locale.ROOT)
            (lower.endsWith(".mp4") || lower.endsWith(".gif") || lower.endsWith(".webp"))
        }

        if (files != null && files.isNotEmpty()) {
            files.sortByDescending { it.lastModified() }
            Log.d(TAG, "Found wallpaper from directory scan: ${files[0].absolutePath}")
            saveWallpaperPath(context, files[0].absolutePath)
            return files[0]
        }

        Log.d(TAG, "No wallpaper file found")
        return null
    }

    fun getSavedWallpaperFiles(context: Context): List<File> {
        val directory = File(HISTORY_DIRECTORY)
        if (!directory.exists()) return emptyList()

        return directory.listFiles { _, name ->
            val lower = name.lowercase(Locale.ROOT)
            (lower.endsWith(".mp4") || lower.endsWith(".gif") || lower.endsWith(".webp"))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun copyFile(source: File, destination: File): Boolean {
        return try {
            source.inputStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying file from ${source.absolutePath} to ${destination.absolutePath}", e)
            false
        }
    }

    fun activateSavedWallpaper(context: Context, file: File): String? {
        val activeFile = File(BASE_DIRECTORY, ACTIVE_WALLPAPER_NAME)
        return if (copyFile(file, activeFile)) {
            val absolutePath = activeFile.absolutePath
            saveWallpaperPath(context, absolutePath)
            absolutePath
        } else {
            null
        }
    }

    fun applyDefaultWallpaper(context: Context) {
        try {
            val wm = WallpaperManager.getInstance(context)
            wm.setResource(R.drawable.ascp_default_wallpaper)
            Log.d(TAG, "Applied default static wallpaper")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying default wallpaper: ${e.message}")
        }
    }

    fun getVideoThumbnail(file: File): Bitmap? {
        if (!file.exists()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract thumbnail from ${file.absolutePath}", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }

    fun deleteCurrentWallpaper(context: Context): Boolean {
        val wallpaperFile = getCurrentWallpaperFile(context)
        if (wallpaperFile != null && wallpaperFile.exists()) {
            val deleted = wallpaperFile.delete()
            if (deleted) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().remove(KEY_WALLPAPER_PATH).apply()
                Log.d(TAG, "Deleted wallpaper: ${wallpaperFile.absolutePath}")
            }
            return deleted
        }
        return false
    }

    fun isVideoFormat(filePath: String?): Boolean {
        if (filePath == null) return false
        val extension = filePath.substring(filePath.lastIndexOf('.') + 1).lowercase(Locale.ROOT)
        return SUPPORTED_VIDEO_FORMATS.contains(extension)
    }

    fun isAnimatedImageFormat(filePath: String?): Boolean {
        if (filePath == null) return false
        val extension = filePath.substring(filePath.lastIndexOf('.') + 1).lowercase(Locale.ROOT)
        return SUPPORTED_IMAGE_FORMATS.contains(extension)
    }

    private fun isValidWallpaperFormat(extension: String?): Boolean {
        if (extension == null || extension.isEmpty()) return false
        val ext = extension.lowercase(Locale.ROOT).replace(".", "")
        return SUPPORTED_VIDEO_FORMATS.contains(ext) || SUPPORTED_IMAGE_FORMATS.contains(ext)
    }

    @Throws(IOException::class)
    private fun getInputStreamFromUri(context: Context, uri: Uri): InputStream? {
        return if (uri.toString().startsWith("content://com.google.android.apps.photos.contentprovider")) {
            val segments = uri.pathSegments
            if (segments.size > 2) {
                val mediaUriString = URLDecoder.decode(segments[2], StandardCharsets.UTF_8.name())
                val mediaUri = Uri.parse(mediaUriString)
                context.contentResolver.openInputStream(mediaUri)
            } else {
                throw FileNotFoundException("Failed to parse Google Photos content URI")
            }
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    @Throws(IOException::class)
    private fun copyStreamWithLimit(
        input: InputStream,
        output: FileOutputStream,
        maxSize: Long
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var totalBytes: Long = 0

        while (input.read(buffer).also { bytesRead = it } != -1) {
            totalBytes += bytesRead.toLong()
            if (totalBytes > maxSize) {
                return -1
            }
            output.write(buffer, 0, bytesRead)
        }
        output.flush()
        return totalBytes
    }

    private fun getFileExtension(context: Context, uri: Uri): String? {
        var extension: String? = null

        if ("content" == uri.scheme) {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType != null) {
                extension = getExtensionFromMimeType(mimeType)
            }
        }

        if (extension == null && uri.path != null) {
            extension = getExtensionFromPath(uri.path!!)
        }

        return extension ?: ".mp4"
    }

    private fun getExtensionFromMimeType(mimeType: String?): String? {
        if (mimeType == null) return null

        val lower = mimeType.lowercase(Locale.ROOT)
        if (lower.contains("mp4") || lower == "video/mp4") {
            return ".mp4"
        } else if (lower.contains("gif") || lower == "image/gif") {
            return ".gif"
        } else if (lower.contains("webp") || lower == "image/webp") {
            return ".webp"
        }

        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (extension != null) {
            return ".$extension"
        }

        return null
    }

    private fun getExtensionFromPath(path: String): String? {
        val lowerPath = path.lowercase(Locale.ROOT)
        return if (lowerPath.endsWith(".mp4")) {
            ".mp4"
        } else if (lowerPath.endsWith(".gif")) {
            ".gif"
        } else if (lowerPath.endsWith(".webp")) {
            ".webp"
        } else {
            null
        }
    }

    private fun closeQuietly(closeable: java.io.Closeable?) {
        if (closeable != null) {
            try {
                closeable.close()
            } catch (e: IOException) {
            }
        }
    }
}
