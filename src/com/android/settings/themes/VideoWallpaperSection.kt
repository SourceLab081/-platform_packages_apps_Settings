/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.themes

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.settings.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoWallpaperSection(
    viewModel: VideoWallpaperViewModel = viewModel()
) {
    var showGallery by remember { mutableStateOf(false) }

    if (showGallery) {
        BackHandler { showGallery = false }
        VideoWallpaperGalleryView(
            videoViewModel = viewModel,
            onBackClick = { showGallery = false }
        )
    } else {
        VideoWallpaperMainContent(
            viewModel = viewModel,
            onOpenGallery = { showGallery = true }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoWallpaperMainContent(
    viewModel: VideoWallpaperViewModel,
    onOpenGallery: () -> Unit
) {
    val currentFile by viewModel.currentWallpaperFile.collectAsStateWithLifecycle()
    val savedFiles by viewModel.savedWallpapers.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val pauseOnLock by viewModel.pauseOnLock.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.handleVideoSelection(it) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (currentFile != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = viewModel::clearWallpaper,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.video_wallpaper_clear_title))
                    }

                    Button(
                        onClick = viewModel::enableVideoWallpaper,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.apply))
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(
                    title = stringResource(R.string.video_wallpaper_title),
                    subtitle = stringResource(R.string.video_wallpaper_info_summary),
                    trailing = {
                        IconButton(onClick = onOpenGallery) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.explore)
                            )
                        }
                    }
                )
            }

            item {
                VideoPreviewCard(
                    file = currentFile,
                    isProcessing = isProcessing,
                    onPickClick = {
                        videoPickerLauncher.launch(arrayOf("video/*", "image/gif", "image/webp"))
                    }
                )
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.explore),
                    subtitle = stringResource(R.string.discover_new_wallpapers),
                    actionLabel = stringResource(R.string.explore),
                    onAction = onOpenGallery
                )
            }

            if (savedFiles.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    SavedWallpapersGalleryRow(
                        files = savedFiles,
                        currentFile = currentFile,
                        onFileClick = viewModel::applySavedWallpaper
                    )
                }
            }

            item {
                PlaybackSettingsCard(
                    playbackSpeed = playbackSpeed,
                    pauseOnLock = pauseOnLock,
                    onSpeedChange = viewModel::setPlaybackSpeed,
                    onPauseChange = viewModel::setPauseOnLock
                )
            }

            item {
                InformationTipsCard()
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            if (onAction != null && actionLabel != null) {
                TextButton(onClick = onAction) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else if (trailing != null) {
                trailing()
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun VideoPreviewCard(
    file: File?,
    isProcessing: Boolean,
    onPickClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isProcessing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (file != null) {
                VideoPreview(file)
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.video_wallpaper_current_none),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.4f)
                            ),
                            startY = 600f
                        )
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Button(
                    onClick = onPickClick,
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (file != null) Color.Black.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.video_wallpaper_pick_title))
                }
            }
        }
    }
}

@Composable
private fun VideoPreview(file: File) {
    AndroidView(
        factory = { context ->
            VideoView(context).apply {
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                    start()
                }
            }
        },
        update = { view ->
            view.setVideoPath(file.absolutePath)
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun rememberVideoThumbnail(file: File): Bitmap? {
    return produceState<Bitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) {
            MediaUtils.getVideoThumbnail(file)
        }
    }.value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedWallpapersGalleryRow(
    files: List<File>,
    currentFile: File?,
    onFileClick: (File) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(end = 16.dp)
    ) {
        items(files.size) { index ->
            val file = files[index]
            val isSelected = currentFile?.absolutePath == file.absolutePath
            val thumbnail = rememberVideoThumbnail(file)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(120.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp, 200.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { onFileClick(file) }
                        .then(
                            if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.VideoFile,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = file.name.substringBeforeLast("."),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun PlaybackSettingsCard(
    playbackSpeed: String,
    pauseOnLock: Boolean,
    onSpeedChange: (String) -> Unit,
    onPauseChange: (Boolean) -> Unit
) {
    val entries = stringArrayResource(R.array.playback_speed_entries)
    val values = stringArrayResource(R.array.playback_speed_values)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.video_wallpaper_playback),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
            )

            var showSpeedDialog by remember { mutableStateOf(false) }

            ListItem(
                headlineContent = { Text(stringResource(R.string.video_wallpaper_speed)) },
                supportingContent = {
                    val index = values.indexOf(playbackSpeed)
                    Text(if (index >= 0) entries[index] else playbackSpeed)
                },
                leadingContent = { Icon(Icons.Default.Speed, contentDescription = null) },
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { showSpeedDialog = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            if (showSpeedDialog) {
                AlertDialog(
                    onDismissRequest = { showSpeedDialog = false },
                    title = { Text(stringResource(R.string.video_wallpaper_speed)) },
                    text = {
                        Column {
                            values.forEachIndexed { index, value ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSpeedChange(value)
                                            showSpeedDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = value == playbackSpeed, onClick = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(entries[index])
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSpeedDialog = false }) {
                            Text(stringResource(R.string.close))
                        }
                    }
                )
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.video_wallpaper_pause_lock)) },
                supportingContent = { Text(stringResource(R.string.video_wallpaper_pause_lock_summary)) },
                leadingContent = { Icon(Icons.Default.BatteryChargingFull, contentDescription = null) },
                trailingContent = {
                    Switch(checked = pauseOnLock, onCheckedChange = onPauseChange)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

@Composable
private fun InformationTipsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.video_wallpaper_info_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = stringResource(R.string.video_wallpaper_info_summary),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFFBC02D),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.video_wallpaper_tips_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = stringResource(R.string.video_wallpaper_tips_summary),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoWallpaperGalleryView(
    videoViewModel: VideoWallpaperViewModel,
    onBackClick: () -> Unit
) {
    val savedFiles by videoViewModel.savedWallpapers.collectAsStateWithLifecycle()
    val currentFile by videoViewModel.currentWallpaperFile.collectAsStateWithLifecycle()

    var fileToDelete by remember { mutableStateOf<File?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.explore)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (savedFiles.isEmpty()) {
                EmptyGalleryContent()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(savedFiles.size) { index ->
                        val file = savedFiles[index]
                        val isSelected = currentFile?.absolutePath == file.absolutePath
                        GalleryItem(
                            file = file,
                            isSelected = isSelected,
                            onItemClick = { videoViewModel.applySavedWallpaper(file) },
                            onItemLongClick = { fileToDelete = file }
                        )
                    }
                }
            }
        }
    }

    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete Wallpaper?") },
            text = { Text("Are you sure you want to delete this video from your saved wallpapers?") },
            confirmButton = {
                Button(
                    onClick = {
                        fileToDelete?.let { videoViewModel.deleteSavedWallpaper(it) }
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun GalleryItem(
    file: File,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit
) {
    val thumbnail = rememberVideoThumbnail(file)

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onItemClick() },
                        onLongPress = { onItemLongClick() }
                    )
                }
                .then(
                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.VideoFile,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = file.name.substringBeforeLast("."),
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun EmptyGalleryContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.VideoFile,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No saved videos found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Your processed wallpapers will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
