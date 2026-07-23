/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.custom.spoofing

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PlayIntegrityFix : SettingsPreferenceFragment() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var activeConfigData: Map<String, String> = emptyMap()

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val content = requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(StandardCharsets.UTF_8)
                    } ?: ""
                    val normalized = normalizePifPayload(content)
                    Settings.Secure.putString(
                        requireContext().contentResolver,
                        PIF_CONFIG_KEY,
                        normalized
                    )
                    killPackage(VENDING_PACKAGE)
                    toast(getString(R.string.pif_imported_as, PIF_CONFIG_NAME))
                    refreshStatus()
                } catch (e: Exception) {
                    toast(getString(R.string.pif_failed, e.message ?: ""))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.play_integrity_fix)

        findPreference<Preference>("pif_fetch_beta")?.setOnPreferenceClickListener {
            fetchRandomCanaryFingerprint()
            true
        }

        findPreference<Preference>("pif_import_config")?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            importLauncher.launch(intent)
            true
        }

        findPreference<Preference>("pif_delete_config")?.setOnPreferenceClickListener {
            showDeleteDialog()
            true
        }

        findPreference<SwitchPreferenceCompat>("spoof_pif_photos")?.setOnPreferenceChangeListener { _, newValue ->
            killPackage(PHOTOS_PACKAGE)
            killPackage(VENDING_PACKAGE)
            true
        }

        findPreference<SwitchPreferenceCompat>("pif_spoof_props")?.setOnPreferenceChangeListener { _, _ ->
            killPackage(VENDING_PACKAGE)
            true
        }

        findPreference<SwitchPreferenceCompat>("pif_spoof_provider")?.setOnPreferenceChangeListener { _, _ ->
            killPackage(VENDING_PACKAGE)
            true
        }

        findPreference<SwitchPreferenceCompat>("pif_spoof_signature")?.setOnPreferenceChangeListener { _, _ ->
            killPackage(VENDING_PACKAGE)
            true
        }

        findPreference<SwitchPreferenceCompat>("pif_spoof_vending_build")?.setOnPreferenceChangeListener { _, _ ->
            killPackage(VENDING_PACKAGE)
            true
        }

        refreshStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun refreshStatus() {
        val content = Settings.Secure.getStringForUser(requireContext().contentResolver,
                        PIF_CONFIG_KEY, UserHandle.USER_CURRENT)
        activeConfigData = if (!content.isNullOrEmpty()) readConfigData(content) else emptyMap()
        val exists = activeConfigData.isNotEmpty()

        val activePref = findPreference<Preference>("pif_active_config")
        if (exists) {
            val model = activeConfigData["MODEL"] ?: ""
            val fingerprint = activeConfigData["FINGERPRINT"] ?: ""
            activePref?.title = PIF_CONFIG_NAME
            activePref?.summary = if (model.isNotEmpty()) {
                "MODEL: $model" + if (fingerprint.isNotEmpty()) "\nFINGERPRINT: $fingerprint" else ""
            } else {
                getString(R.string.pif_config_loaded)
            }
        } else {
            activePref?.title = getString(R.string.pif_active_config)
            activePref?.summary = getString(R.string.pif_no_config)
        }

        findPreference<Preference>("pif_delete_config")?.isEnabled = exists

        val targetsStr = Settings.Secure.getStringForUser(requireContext().contentResolver,
                        "spoof_pif_targets", UserHandle.USER_CURRENT)
        val targetCount = if (targetsStr.isNullOrEmpty()) 0
            else targetsStr.lines().count { it.isNotBlank() }
        findPreference<Preference>("pif_manage_targets")?.summary =
            if (targetCount == 0) getString(R.string.pif_manage_targets_summary)
            else getString(R.string.pif_target_apps_count, targetCount)

        populateConfigDetails(activeConfigData)
    }

    private fun populateConfigDetails(data: Map<String, String>) {
        val category = findPreference<PreferenceCategory>("pif_config_details_category") ?: return
        category.removeAll()

        if (data.isEmpty()) return

        val displayOrder = listOf(
            "MODEL", "MANUFACTURER", "BRAND", "PRODUCT", "DEVICE",
            "FINGERPRINT", "SECURITY_PATCH", "VERSION.SECURITY_PATCH", "ID", "RELEASE", "VERSION.RELEASE",
            "VERSION.INCREMENTAL", "DEVICE_INITIAL_SDK_INT", "VERSION.DEVICE_INITIAL_SDK_INT"
        )

        for (key in displayOrder) {
            val value = data[key] ?: continue
            category.addPreference(Preference(requireContext()).apply {
                this.title = key
                this.summary = value
                isSelectable = false
            })
        }

        data.keys.filter { it !in displayOrder && !it.startsWith("spoof") && it != "DEBUG" && it != "verboseLogs" }
            .forEach { key ->
                category.addPreference(Preference(requireContext()).apply {
                    this.title = key
                    this.summary = data[key]
                    isSelectable = false
                })
            }
    }

    private fun showDeleteDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.pif_delete_title, PIF_CONFIG_NAME))
            .setMessage(R.string.pif_delete_message)
            .setPositiveButton(R.string.pif_delete) { _, _ ->
                try {
                    Settings.Secure.putString(
                        requireContext().contentResolver,
                        PIF_CONFIG_KEY,
                        null
                    )
                    toast(getString(R.string.pif_deleted, PIF_CONFIG_NAME))
                    refreshStatus()
                } catch (e: Exception) {
                    toast(getString(R.string.pif_failed, e.message ?: ""))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun fetchRandomCanaryFingerprint() {
        val fetchPref = findPreference<Preference>("pif_fetch_beta") ?: return
        fetchPref.summary = getString(R.string.pif_fetching)
        fetchPref.isEnabled = false

        scope.launch {
            try {
                val pifJson = withContext(Dispatchers.IO) {
                    val (devices, apiKey) = fetchAvailableCanaryDevicesAndApiKey()
                    if (devices.isEmpty()) {
                        throw Exception("No devices found")
                    }
                    if (apiKey.isNullOrEmpty()) {
                        throw Exception("Failed to extract Flash Tool API key")
                    }
                    val device = devices.random()
                    
                    val buildsUrl = "$FLASH_API?product=${device.product}&key=$apiKey"
                    val buildsConn = URL(buildsUrl).openConnection().apply {
                        setRequestProperty("Referer", FLASH_URL)
                        connectTimeout = 15000
                        readTimeout = 15000
                    }
                    val stationJson = buildsConn.getInputStream().use {
                        it.readBytes().toString(StandardCharsets.UTF_8)
                    }
                    
                    var buildId: String? = null
                    var buildInc: String? = null
                    var canaryId: String? = null

                    try {
                        val root = JSONObject(stationJson)
                        val buildsArray = root.optJSONArray("flashstationBuild")
                        if (buildsArray != null && buildsArray.length() > 0) {
                            for (i in buildsArray.length() - 1 downTo 0) {
                                val b = buildsArray.optJSONObject(i) ?: continue
                                val meta = b.optJSONObject("previewMetadata")
                                val rc = b.optString("releaseCandidateName")
                                val bid = b.optString("buildId")
                                if (rc.isNotEmpty() && bid.isNotEmpty()) {
                                    buildId = rc
                                    buildInc = bid
                                    if (meta != null) {
                                        canaryId = meta.optString("id").takeIf { it.contains("canary-") }
                                    }
                                    break
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "JSON parsing of station builds failed, trying regex", e)
                    }

                    if (buildId == null || buildInc == null) {
                        val rcMatcher = Regex(""""releaseCandidateName"\s*:\s*"([^"]+)"""").findAll(stationJson)
                        val biMatcher = Regex(""""buildId"\s*:\s*"([^"]+)"""").findAll(stationJson)
                        val rcList = rcMatcher.map { it.groupValues[1] }.toList()
                        val biList = biMatcher.map { it.groupValues[1] }.toList()
                        if (rcList.isNotEmpty() && biList.isNotEmpty()) {
                            buildId = rcList.last()
                            buildInc = biList.last()
                        }
                    }
                    
                    if (buildId == null || buildInc == null) {
                        throw Exception("Failed to parse build info for ${device.model}")
                    }
                    
                    val fingerprint = "google/${device.product}/${device.device}:CANARY/$buildId/$buildInc:user/release-keys"
                    
                    if (canaryId == null) {
                        val canaryMatcher = Regex("""\{[^}]*"canary"\s*:\s*true[^}]*\}""", RegexOption.IGNORE_CASE)
                        var lastCanaryObject: String? = null
                        canaryMatcher.findAll(stationJson).forEach { match ->
                            lastCanaryObject = match.value
                        }
                        if (lastCanaryObject != null) {
                            val idMatcher = Regex(""""id"\s*:\s*"canary-([^"]+)"""").find(lastCanaryObject!!)
                            if (idMatcher != null) {
                                canaryId = idMatcher.groupValues[1]
                            }
                        }
                    }
                    
                    var canaryMonth: String? = null
                    if (canaryId != null) {
                        val m = Regex("""(?:canary-)?(\d{4})(\d{2})""").find(canaryId!!)
                        if (m != null) {
                            canaryMonth = "${m.groupValues[1]}-${m.groupValues[2]}"
                        }
                    }
                    
                    var securityPatch: String? = null
                    if (canaryMonth != null) {
                        try {
                            val secHtml = URL(PIXEL_BULLETIN_URL).readText(StandardCharsets.UTF_8)
                            val sp = Regex("""<td>(${Regex.escape(canaryMonth!!)}-\d{2})</td>""", RegexOption.IGNORE_CASE)
                            securityPatch = sp.find(secHtml)?.groupValues?.get(1)
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not resolve security patch from bulletin", e)
                        }
                        if (securityPatch == null) {
                            securityPatch = "$canaryMonth-05"
                        }
                    }
                    
                    JSONObject().apply {
                        put("MANUFACTURER", "Google")
                        put("MODEL", device.model)
                        put("FINGERPRINT", fingerprint)
                        put("BRAND", "google")
                        put("PRODUCT", device.product)
                        put("DEVICE", device.device)
                        put("VERSION.RELEASE", "16")
                        put("ID", buildId)
                        put("VERSION.INCREMENTAL", buildInc)
                        put("TYPE", "user")
                        put("TAGS", "release-keys")
                        if (securityPatch != null) {
                            put("VERSION.SECURITY_PATCH", securityPatch)
                        }
                        put("VERSION.DEVICE_INITIAL_SDK_INT", "32")
                    }
                }
                
                Settings.Secure.putString(
                    requireContext().contentResolver,
                    PIF_CONFIG_KEY,
                    pifJson.toString(2)
                )
                killPackage(VENDING_PACKAGE)
                toast(getString(R.string.pif_fetched_model, pifJson.getString("MODEL")))
                refreshStatus()
            } catch (e: Exception) {
                toast(getString(R.string.pif_failed, e.message ?: ""))
            } finally {
                fetchPref.summary = getString(R.string.pif_fetch_pixel_beta_summary)
                fetchPref.isEnabled = true
            }
        }
    }

    private fun killPackage(pkg: String) {
        try {
            val am = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.forceStopPackage(pkg)
        } catch (_: Exception) {}
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.CUSTOM

    companion object {
        private const val TAG = "PlayIntegrityFix"
        private const val PIF_CONFIG_KEY = "spoof_pif_config"
        private const val PIF_CONFIG_NAME = "pif.json"
        private const val GOOGLE_URL = "https://developer.android.com"
        private const val FLASH_URL = "https://flash.android.com"
        private const val FLASH_API = "https://content-flashstation-pa.googleapis.com/v1/builds"
        private const val PIXEL_BULLETIN_URL = "https://source.android.com/docs/security/bulletin/pixel"
        private const val VENDING_PACKAGE = "com.android.vending"
        private const val PHOTOS_PACKAGE = "com.google.android.apps.photos"

        private val DEVICE_MODEL_MAP = mapOf(
            "oriole" to "Pixel 6",
            "raven" to "Pixel 6 Pro",
            "bluejay" to "Pixel 6a",
            "panther" to "Pixel 7",
            "cheetah" to "Pixel 7 Pro",
            "lynx" to "Pixel 7a",
            "shiba" to "Pixel 8",
            "tangorpro" to "Pixel Tablet",
            "felix" to "Pixel Fold",
            "husky" to "Pixel 8 Pro",
            "akita" to "Pixel 8a",
            "tokay" to "Pixel 9",
            "caiman" to "Pixel 9 Pro",
            "komodo" to "Pixel 9 Pro XL",
            "comet" to "Pixel 9 Pro Fold",
            "tegu" to "Pixel 9a",
            "frankel" to "Pixel 10",
            "blazer" to "Pixel 10 Pro",
            "mustang" to "Pixel 10 Pro XL",
            "rango" to "Pixel 10 Pro Fold",
            "stallion" to "Pixel 10a",
        )

        /**
         * Reads the config from a JSON string (stored in Settings.Secure).
         * Also handles legacy prop-format strings in case an old value is present.
         */
        private fun readConfigData(content: String): Map<String, String> {
            return try {
                val result = mutableMapOf<String, String>()
                val trimmed = content.trim()
                if (trimmed.startsWith("{")) {
                    val json = JSONObject(trimmed)
                    json.keys().forEach { key -> result[key] = json.optString(key, "") }
                } else {
                    trimmed.lines().forEach { line ->
                        val l = line.trim()
                        if (l.isNotEmpty() && !l.startsWith("#") && !l.startsWith("//")) {
                            val eq = l.indexOf('=')
                            if (eq > 0) result[l.substring(0, eq).trim()] = l.substring(eq + 1).trim()
                        }
                    }
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read config", e)
                emptyMap()
            }
        }

        /**
         * Normalises an imported PIF payload (JSON or prop-format) to a JSON string
         * suitable for storage in Settings.Secure.
         */
        private fun normalizePifPayload(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return "{}"
            if (trimmed.startsWith("{")) return trimmed
            val json = JSONObject()
            trimmed.lines().forEach { line ->
                val stripped = line.trim()
                if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("//")) return@forEach
                val eq = stripped.indexOf('=')
                if (eq > 0) {
                    val key = stripped.substring(0, eq).trim()
                    val value = stripped.substring(eq + 1).trim().substringBefore('#').trim()
                    if (key.isNotEmpty()) json.put(key, value)
                }
            }
            return json.toString(2)
        }

        data class PifDevice(
            val product: String,
            val device: String,
            val model: String,
            val otaUrl: String,
        )

        private fun fetchAvailableCanaryDevicesAndApiKey(): Pair<List<PifDevice>, String?> {
            try {
                val versionsHtml = URL("$GOOGLE_URL/about/versions").readText(StandardCharsets.UTF_8)
                val links = Regex("""https://developer\.android\.com/about/versions/\d+""")
                    .findAll(versionsHtml).map { it.value }.toList()
                if (links.isEmpty()) {
                    throw Exception("Failed to get latest build URL")
                }
                val latestBuildUrl = links.sorted().last()

                val latestHtml = URL(latestBuildUrl).readText(StandardCharsets.UTF_8)
                val factoryUrlMatch = Regex("""href="([^"]*download[^"]*)"""").find(latestHtml)
                    ?: throw Exception("Could not find factory image download link")
                val factoryUrl = factoryUrlMatch.groupValues[1]
                val fullFactoryUrl = if (factoryUrl.startsWith("http")) factoryUrl else "$GOOGLE_URL$factoryUrl"

                val factoryHtml = URL(fullFactoryUrl).readText(StandardCharsets.UTF_8)

                val devices = mutableListOf<PifDevice>()
                val rowPattern = Regex("""<tr\s+id="([^"]+)"[^>]*>\s*<td[^>]*>([^<]+)</td>""", RegexOption.IGNORE_CASE)
                rowPattern.findAll(factoryHtml).forEach { match ->
                    val device = match.groupValues[1].trim()
                    val model = match.groupValues[2].trim()
                    devices.add(
                        PifDevice(
                            product = "${device}_beta",
                            device = device,
                            model = model,
                            otaUrl = ""
                        )
                    )
                }

                if (devices.isEmpty()) {
                    throw Exception("No beta devices found")
                }

                val flashHtml = URL(FLASH_URL).readText(StandardCharsets.UTF_8)
                var apiKey = Regex("""data-client-config\s*=\s*"(?:[^,]*?,){2}\s*&quot;([^&]+)&quot;""", RegexOption.IGNORE_CASE)
                    .find(flashHtml)?.groupValues?.get(1)
                    ?.replace("&quot;", "\"")
                    ?.replace("&amp;", "&")
                    ?.replace("&lt;", "<")
                    ?.replace("&gt;", ">")

                if (apiKey.isNullOrEmpty()) {
                    apiKey = Regex("""AIza[0-9A-Za-z_-]{35}""").find(flashHtml)?.value
                }

                return devices to apiKey
            } catch (e: Exception) {
                Log.e(TAG, "Canary device/apikey fetch failed", e)
                throw e
            }
        }
    }
}
