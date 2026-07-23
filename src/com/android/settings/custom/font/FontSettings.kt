/*
 * SPDX-FileCopyrightText: 2026 ASCP OS
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.android.settings.custom.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.SystemProperties
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.settings.SettingsEnums
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.android.internal.util.crdroid.ThemeUtils
import com.android.internal.util.custom.CustomUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FontPackage(
    val packageName: String,
    val label: String,
    val typeface: Typeface?,
    val isCustom: Boolean = false
)

class FontSettings : SettingsPreferenceFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().title = getString(R.string.font_settings_title)
    }

    override fun getMetricsCategory(): Int = SettingsEnums.SETTINGS_SYSTEM_CATEGORY

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // No preferences XML since we use Compose
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            SettingsTheme {
                FontSectionContent()
            }
        }
    }
}

private const val FONT_CATEGORY = "android.theme.customization.font"
private const val CUSTOM_PKG_KEY = "__custom_font__"

@Composable
fun FontSectionContent() {
    val context = LocalContext.current
    val themeUtils = remember { ThemeUtils(context) }
    val installer = remember { ExternalFontInstaller(context) }
    val coroutineScope = rememberCoroutineScope()
    
    var allFonts by remember { mutableStateOf(listOf<FontPackage>()) }
    var currentFontPackage by remember { mutableStateOf("android") }
    var selectedFont by remember { mutableStateOf("android") }
    var searchQuery by remember { mutableStateOf("") }
    
    var hasCustomFont by remember { mutableStateOf(false) }
    var customFontName by remember { mutableStateOf("") }
    
    var showRebootDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var previewTypeface by remember { mutableStateOf<Typeface?>(null) }

    val loadFonts = suspend {
        val customName = Settings.Secure.getString(context.contentResolver, "custom_font_name") ?: ""
        hasCustomFont = customName.isNotEmpty()
        customFontName = customName

        val activeOverlay = themeUtils.getOverlayInfos(FONT_CATEGORY)
            .firstOrNull { it.isEnabled }?.packageName ?: "android"
        
        currentFontPackage = if (hasCustomFont) CUSTOM_PKG_KEY else activeOverlay
        selectedFont = currentFontPackage

        val packages = mutableListOf<String>()
        packages.add("android") // Add default explicitly
        packages.addAll(themeUtils.getOverlayPackagesForCategory(FONT_CATEGORY, "android"))

        val loadedFonts = packages.distinct().map { pkg ->
            val label = if (pkg == "android") {
                context.getString(R.string.overlay_option_device_default)
            } else {
                getLabel(context, pkg)
            }
            val typeface = getTypeface(context, pkg)
            FontPackage(pkg, label, typeface)
        }.sortedBy { it.label.lowercase() }.toMutableList()

        if (hasCustomFont) {
            loadedFonts.add(0, FontPackage(CUSTOM_PKG_KEY, customFontName, Typeface.create(ExternalFontInstaller.DEFAULT_FONT_FAMILY, Typeface.NORMAL), true))
        }
        
        allFonts = loadedFonts
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            loadFonts()
        }
    }

    val fontPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            coroutineScope.launch {
                val typeface = installer.loadTypefaceFromUri(it)
                if (typeface == null) {
                    Toast.makeText(context, R.string.toast_invalid_font_file, Toast.LENGTH_SHORT).show()
                } else {
                    previewUri = it
                    previewTypeface = typeface
                }
            }
        }
    }

    val filteredFonts = allFonts.filter {
        if (searchQuery.isBlank()) return@filter true
        it.label.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.reset))
                }

                Button(
                    onClick = {
                        applyFont(context, themeUtils, installer, selectedFont, currentFontPackage) {
                            showRebootDialog = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.apply))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            val currentPreviewTypeface = allFonts.find { it.packageName == selectedFont }?.typeface
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val fontFamily = currentPreviewTypeface?.let { FontFamily(it) } ?: FontFamily.Default
                    
                    Text(
                        text = "Ag",
                        fontSize = 48.sp,
                        fontFamily = fontFamily,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Text(
                        text = stringResource(R.string.font_preview_text),
                        fontSize = 20.sp,
                        fontFamily = fontFamily,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.search)) },
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredFonts) { fontPackage ->
                    val isSelected = fontPackage.packageName == selectedFont
                    val fontFamily = fontPackage.typeface?.let { FontFamily(it) } ?: FontFamily.Default

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFont = fontPackage.packageName },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.FontDownload,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fontPackage.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = fontFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isSelected && currentFontPackage == selectedFont) {
                                    Text(
                                        text = stringResource(R.string.applied),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                // Add Custom Font Button
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fontPickerLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream")) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.add_custom_font),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (previewUri != null && previewTypeface != null) {
        AlertDialog(
            onDismissRequest = { 
                previewUri = null
                previewTypeface = null 
            },
            title = { Text(stringResource(R.string.font_preview_dialog_title)) },
            text = {
                Text(
                    text = stringResource(R.string.font_preview_text),
                    fontSize = 24.sp,
                    fontFamily = FontFamily(previewTypeface!!),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    val uri = previewUri!!
                    previewUri = null
                    previewTypeface = null
                    coroutineScope.launch {
                        val postScriptName = installer.installFontFromUri(uri)
                        if (postScriptName != null) {
                            Settings.Secure.putString(
                                context.contentResolver, "custom_font_name", postScriptName
                            )
                            showRebootDialog = true
                        }
                    }
                }) {
                    Text(stringResource(R.string.add_font))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    previewUri = null
                    previewTypeface = null 
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showRebootDialog) {
        AlertDialog(
            onDismissRequest = { showRebootDialog = false },
            title = { Text(stringResource(R.string.reboot_required_title)) },
            text = { Text(stringResource(R.string.reboot_required_custom_font_title)) },
            confirmButton = {
                Button(onClick = {
                    showRebootDialog = false
                    ExternalFontInstaller.rebootDevice()
                }) {
                    Text(stringResource(R.string.reboot_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootDialog = false }) {
                    Text(stringResource(R.string.later))
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_default_title)) },
            text = { Text(stringResource(R.string.reset_default_message)) },
            confirmButton = {
                Button(onClick = {
                    showResetDialog = false
                    coroutineScope.launch(Dispatchers.IO) {
                        Settings.Secure.putString(context.contentResolver, "custom_font_name", "")
                        installer.resetFontUpdates()
                        val current = ThemeUtils(context).getOverlayInfos(FONT_CATEGORY)
                            .firstOrNull { it.isEnabled }?.packageName ?: "android"
                        ThemeUtils(context).setOverlayEnabled(FONT_CATEGORY, current, current)
                        
                        withContext(Dispatchers.Main) {
                            showRebootDialog = true
                        }
                    }
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

private fun applyFont(
    context: Context, 
    themeUtils: ThemeUtils, 
    installer: ExternalFontInstaller,
    pkgName: String, 
    currentPkg: String,
    onRequiresReboot: () -> Unit
) {
    if (pkgName == currentPkg) return

    if (pkgName == CUSTOM_PKG_KEY) {
        Toast.makeText(context, R.string.reboot_required_title, Toast.LENGTH_SHORT).show()
        onRequiresReboot()
        return
    }

    val oldPkg = if (currentPkg == CUSTOM_PKG_KEY) {
        installer.resetFontUpdates()
        Settings.Secure.putString(context.contentResolver, "custom_font_name", "")
        themeUtils.getOverlayInfos(FONT_CATEGORY).firstOrNull { it.isEnabled }?.packageName ?: "android"
    } else {
        currentPkg
    }

    try {
        themeUtils.setOverlayEnabled(FONT_CATEGORY, oldPkg, oldPkg)
        themeUtils.setOverlayEnabled(FONT_CATEGORY, pkgName, "android")

        val fontName = if (pkgName == "android") "" else pkgName.substringAfterLast(".")
        SystemProperties.set("persist.sys.font", fontName)
        Toast.makeText(context, R.string.systemui_restart_font_message, Toast.LENGTH_SHORT).show()
        
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed({ 
            try {
                CustomUtils.restartSystemUI()
            } catch (e: Exception) {
                Log.e("FontSection", "Failed to restart SystemUI", e)
            }
        }, 1250)

    } catch (e: Exception) {
        Log.e("FontSection", "Failed to apply font", e)
    }
}

private fun getLabel(context: Context, pkgName: String): String {
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(pkgName, 0)
        info.loadLabel(pm).toString()
    } catch (e: Exception) {
        pkgName.substringAfterLast(".")
    }
}

private fun getTypeface(context: Context, pkgName: String): Typeface? {
    return try {
        if (pkgName == "android") return Typeface.DEFAULT
        val pm = context.packageManager
        val resources = pm.getResourcesForApplication(pkgName)
        val resId = resources.getIdentifier("config_bodyFontFamily", "string", pkgName)
        if (resId != 0) {
            val fontFamilyName = resources.getString(resId)
            Typeface.create(fontFamilyName, Typeface.NORMAL)
        } else {
            Typeface.DEFAULT
        }
    } catch (e: Exception) {
        null
    }
}
