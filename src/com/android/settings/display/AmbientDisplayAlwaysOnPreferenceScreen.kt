/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.settings.display

import android.app.settings.SettingsEnums
import android.app.settings.SettingsEnums.ACTION_AMBIENT_DISPLAY_ALWAYS_ON
import android.content.Context
import android.hardware.display.AmbientDisplayConfiguration
import android.os.SystemProperties
import android.os.UserHandle
import android.os.UserManager
import androidx.fragment.app.Fragment
import com.android.internal.R.bool.config_dozeSupportsAodInactivityDetection
import com.android.internal.R.bool.config_dozeSupportsAodWallpaper
import com.android.settings.CatalystFragment
import com.android.settings.CatalystSettingsActivity
import com.android.settings.R
import com.android.settings.contract.KEY_AMBIENT_DISPLAY_ALWAYS_ON
import com.android.settings.core.PreferenceScreenMixin
import com.android.settings.display.AmbientDisplayAlwaysOnPreferenceScreenController.isAodSuppressedByBedtime
import com.android.settings.display.ambient.AmbientDisplayIllustration
import com.android.settings.display.ambient.AmbientDisplayMainSwitchPreference
import com.android.settings.display.ambient.AmbientDisplayStorage
import com.android.settings.display.ambient.AmbientDisplayTopIntroPreference
import com.android.settings.display.ambient.AmbientInactivityDetectionPreference
import com.android.settings.display.ambient.AmbientWallpaperPreference
import com.android.settings.metrics.PreferenceActionMetricsProvider
import com.android.settings.restriction.PreferenceRestrictionMixin
import com.android.settings.utils.makeLaunchIntent
import com.android.settingslib.PrimarySwitchPreferenceBinding
import com.android.settingslib.datastore.HandlerExecutor
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.datastore.KeyedObserver
import com.android.settingslib.datastore.SettingsSecureStore
import com.android.settingslib.metadata.BooleanValuePreference
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.preferencesapi.preconditions.PreconditionStability
import com.android.settingslib.metadata.PreferenceCategory as Category
import com.android.settingslib.metadata.PreferenceLifecycleContext
import com.android.settingslib.metadata.PreferenceLifecycleProvider
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.metadata.ProvidePreferenceScreen
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.metadata.preferenceHierarchy
import com.android.systemui.shared.Flags.aodInactivityDetection
import kotlinx.coroutines.CoroutineScope
import android.provider.Settings
import android.provider.Settings.Secure.DOZE_ALWAYS_ON
import android.provider.Settings.Secure.DOZE_ALWAYS_ON_AUTO_MODE
import android.provider.Settings.Secure.DOZE_ON_CHARGE
import android.provider.Settings.Secure.DOZE_PEEK
import android.provider.Settings.Secure.DOZE_PEEK_DURATION
import android.provider.Settings.Secure.DOZE_SHAKE_TO_SHOW
import android.provider.Settings.Secure.DOZE_SHAKE_TO_SHOW_DURATION
import android.provider.Settings.Secure.DOZE_SHAKE_INTENSITY
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.android.settingslib.datastore.SettingsSystemStore
import com.android.settingslib.datastore.KeyValueStoreDelegate
import com.android.settingslib.metadata.SwitchPreference
import com.android.settingslib.preference.PreferenceBinding
import com.android.settingslib.metadata.preferencesapi.PreferencesApiScreen.Companion.APP_FUNCTION_UNCATEGORIZED

// LINT.IfChange
/**
 * Contains the PrimarySwitchPreference for use on the Display setting page, and also the preference
 * subpage for additional related settings.
 */
@ProvidePreferenceScreen(AmbientDisplayAlwaysOnPreferenceScreen.KEY)
open class AmbientDisplayAlwaysOnPreferenceScreen(context: Context) :
    PreferenceScreenMixin,
    BooleanValuePreference,
    PrimarySwitchPreferenceBinding,
    PreferenceActionMetricsProvider,
    PreferenceAvailabilityProvider,
    PreferenceRestrictionMixin,
    PreferenceLifecycleProvider,
    PreferenceSummaryProvider {
    override fun tags(context: Context) = arrayOf(APP_FUNCTION_UNCATEGORIZED, KEY_AMBIENT_DISPLAY_ALWAYS_ON)


    private val ambientWallpaperPreference = AmbientWallpaperPreference(context)
    private val alwaysOnDisplaySchedulePreference = AlwaysOnDisplaySchedulePreference(context)
    private val dozeOnChargePreference = DozeOnChargePreference(context)
    private val dozePeekPreference = DozePeekPreference(context)
    private val dozePeekDurationPreference = DozePeekDurationPreference(context)
    private val dozeShakePreference = DozeShakePreference(context)
    private val dozeShakeDurationPreference = DozeShakeDurationPreference(context)
    private val dozeShakeIntensityPreference = DozeShakeIntensityPreference(context)
    private val ambientShowSettingsPreference = AmbientShowSettingsPreference(context)
    private val ambientShowSettingsIconsPreference = AmbientShowSettingsIconsPreference(context)
    private lateinit var keyedObserver: KeyedObserver<String>

    override val title: Int
        get() = R.string.doze_always_on_title2

    override val key: String
        get() = KEY

    //TODO(b/462618020) Catalyst-purpose: replace default purpose with 2 line description
    override val purpose: Int
        get() = R.string.ambient_display_always_on_screen_purpose

    override val keywords: Int
        get() = R.string.keywords_always_show_time_info

    override val indexable
        get() = true

    override fun getMetricsCategory() = SettingsEnums.AMBIENT_DISPLAY_ALWAYS_ON

    override val highlightMenuKey: Int
        get() = R.string.menu_key_display

    override val preferenceActionMetrics: Int
        get() = ACTION_AMBIENT_DISPLAY_ALWAYS_ON



    override val restrictionKeys: Array<String>
        get() = arrayOf(UserManager.DISALLOW_AMBIENT_DISPLAY)

    override fun getEnabledDescription(): String = "This setting must not be restricted by a device administrator."

    override fun getEnabledStability() = PreconditionStability.UNSTABLE

    override fun isEnabled(context: Context) = super<PreferenceRestrictionMixin>.isEnabled(context)

    override val availabilityDescription =
        "The device must support ambient display always on, and the user must not have display inversion enabled."

    override fun getAvailabilityStability() = PreconditionStability.UNSTABLE

    override fun isAvailable(context: Context): Boolean {
        return !SystemProperties.getBoolean(PROP_AWARE_AVAILABLE, false) &&
            AmbientDisplayConfiguration(context).alwaysOnAvailableForUser(UserHandle.myUserId())
    }

    override fun getSummary(context: Context): CharSequence? =
        context.getText(
            if (isAodSuppressedByBedtime(context)) {
                R.string.aware_summary_when_bedtime_on
            } else if (context.isAmbientWallpaperOptionsAvailable) {
                if (ambientWallpaperPreference.isChecked()) {
                    R.string.doze_always_on_summary_with_wallpaper
                } else {
                    R.string.doze_always_on_summary_without_wallpaper
                }
            } else {
                R.string.doze_always_on_summary_short
            }
        )

    override fun onCreate(context: PreferenceLifecycleContext) {
        if (isEntryPoint(context)) {
            keyedObserver = KeyedObserver { _, _ -> context.notifyPreferenceChange(KEY) }
            ambientWallpaperPreference
                .storage(context)
                .addObserver(AmbientWallpaperPreference.KEY, keyedObserver, HandlerExecutor.main)
        }
    }

    override fun onDestroy(context: PreferenceLifecycleContext) {
        if (isEntryPoint(context)) {
            ambientWallpaperPreference
                .storage(context)
                .removeObserver(AmbientWallpaperPreference.KEY, keyedObserver)
        }
    }

    override fun fragmentClass(): Class<out Fragment>? = AmbientPreferenceFragment::class.java

    override fun hasCompleteHierarchy() = true

    override fun getLaunchIntent(context: Context, metadata: PreferenceMetadata?) =
        makeLaunchIntent(context, AmbientDisplayAlwaysOnActivity::class.java, metadata?.key)

    override fun getPreferenceHierarchy(context: Context, coroutineScope: CoroutineScope) =
        preferenceHierarchy(context) {
            +AmbientDisplayTopIntroPreference()
            +AmbientDisplayIllustration(context)
            +AmbientDisplayMainSwitchPreference()
            if (context.isAmbientInactivityDetectionAvailable) {
                +AmbientInactivityDetectionPreference(context)
            }
            +Category(
                "aod_peek_group",
                R.string.aod_category_peek,
                R.string.aod_category_peek
            ) += {
                +dozePeekPreference
                +dozePeekDurationPreference
            }
            +Category("aod_shake_group", R.string.aod_category_shake, R.string.aod_category_shake) += {
                +dozeShakePreference
                +dozeShakeDurationPreference
                +dozeShakeIntensityPreference
            }
            +Category(
                "aod_schedule_behavior_group",
                R.string.aod_category_schedule_behavior_purpose,
                R.string.aod_category_schedule_behavior
            ) += {
                +alwaysOnDisplaySchedulePreference
                +dozeOnChargePreference
            }
            +Category(
                "aod_customization_group",
                R.string.aod_category_customization_purpose,
                R.string.aod_category_customization
            ) += {
                if (context.isAmbientWallpaperOptionsAvailable) {
                    +ambientWallpaperPreference
                }
                +ambientShowSettingsPreference
                +ambientShowSettingsIconsPreference
            }
        }

    override fun storage(context: Context): KeyValueStore = AmbientDisplayStorage(context)

    override fun getReadPermissions(context: Context) = SettingsSecureStore.getReadPermissions()

    override fun getWritePermissions(context: Context) = SettingsSecureStore.getWritePermissions()

    override fun getReadPermit(context: Context, callingPid: Int, callingUid: Int) =
        ReadWritePermit.ALLOW

    override fun getWritePermit(context: Context, callingPid: Int, callingUid: Int) =
        ReadWritePermit.ALLOW

    override val supportsWrite = true
    override val sensitivityLevel
        get() = SensitivityLevel.NO_SENSITIVITY

    companion object {
        const val KEY = "ambient_display_always_on_screen"
        const val PROP_AWARE_AVAILABLE = "ro.vendor.aware_available"

        private val Context.isAmbientWallpaperOptionsAvailable: Boolean
            get() = resources.getBoolean(config_dozeSupportsAodWallpaper)

        private val Context.isAmbientInactivityDetectionAvailable: Boolean
            get() =
                aodInactivityDetection() &&
                    resources.getBoolean(config_dozeSupportsAodInactivityDetection)
    }
}

// LINT.ThenChange(AmbientDisplayAlwaysOnPreferenceScreenController.java)

class AmbientDisplayAlwaysOnActivity :
    CatalystSettingsActivity(
        AmbientDisplayAlwaysOnPreferenceScreen.KEY,
        AmbientPreferenceFragment::class.java,
    )

class AmbientPreferenceFragment : CatalystFragment() {
    override fun getPreferenceScreenBindingKey(context: Context): String {
        return AmbientDisplayAlwaysOnPreferenceScreen.KEY
    }
}

class AlwaysOnDisplaySchedulePreference(context: Context) :
    PreferenceMetadata,
    PreferenceBinding,
    PreferenceSummaryProvider,
    PreferenceAvailabilityProvider {

    private val config = AmbientDisplayConfiguration(context)

    override val key: String
        get() = KEY

    override val purpose: Int
        get() = R.string.always_on_display_schedule_title

    override val title: Int
        get() = R.string.always_on_display_schedule_title

    override val availabilityDescription: String
        get() = "Device must support always-on display"

    override fun getAvailabilityStability() = PreconditionStability.UNSTABLE

    override fun isAvailable(context: Context): Boolean {
        return config.alwaysOnAvailableForUser(UserHandle.myUserId()) &&
            !SystemProperties.getBoolean(PROP_AWARE_AVAILABLE, false)
    }

    override fun getSummary(context: Context): CharSequence? {
        val mode = Settings.Secure.getIntForUser(context.contentResolver,
            Settings.Secure.DOZE_ALWAYS_ON_AUTO_MODE, 0, UserHandle.USER_CURRENT)
        return when (mode) {
            AODSchedulePreferenceController.MODE_NIGHT -> context.getString(R.string.night_display_auto_mode_twilight)
            AODSchedulePreferenceController.MODE_TIME -> context.getString(R.string.night_display_auto_mode_custom)
            AODSchedulePreferenceController.MODE_MIXED_SUNSET -> context.getString(R.string.always_on_display_schedule_mixed_sunset)
            AODSchedulePreferenceController.MODE_MIXED_SUNRISE -> context.getString(R.string.always_on_display_schedule_mixed_sunrise)
            else -> context.getString(R.string.string_disabled)
        }
    }

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
        preference.fragment = "com.android.settings.display.AODSchedule"
    }

    companion object {
        const val KEY = "always_on_display_schedule"
        private const val PROP_AWARE_AVAILABLE = "ro.vendor.aware_available"
    }
}

class DozeOnChargeStore(private val context: Context) : KeyValueStoreDelegate {
    override val keyValueStoreDelegate: KeyValueStore
        get() = SettingsSecureStore.get(context)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getValue(key: String, valueType: Class<T>): T? {
        val intVal = keyValueStoreDelegate.getInt(key) ?: 0
        return (intVal == 1) as T
    }

    override fun <T : Any> setValue(key: String, valueType: Class<T>, value: T?) {
        if (value == null) {
            keyValueStoreDelegate.setInt(key, null)
        } else if (value is Boolean) {
            keyValueStoreDelegate.setInt(key, if (value) 1 else 0)
        }
    }
}

class DozeOnChargePreference(context: Context) :
    SwitchPreference(
        KEY,
        R.string.doze_on_charge_title,
        R.string.doze_on_charge_summary,
    ),
    PreferenceAvailabilityProvider {

    private val dataStore = DozeOnChargeStore(context)
    private val dozeAlwaysOnDataStore = AmbientDisplayStorage(context)
    private val config = AmbientDisplayConfiguration(context)

    override fun isEnabled(context: Context): Boolean {
        val aodEnabled = dozeAlwaysOnDataStore.getBoolean(DOZE_ALWAYS_ON) == true
        return !aodEnabled
    }

    override val availabilityDescription: String
        get() = "Device must support always-on display"

    override fun getAvailabilityStability() = PreconditionStability.UNSTABLE

    override fun isAvailable(context: Context): Boolean {
        return config.alwaysOnAvailableForUser(UserHandle.USER_CURRENT)
    }

    override fun storage(context: Context) = dataStore

    companion object {
        const val KEY = Settings.Secure.DOZE_ON_CHARGE
    }
}

class AmbientShowSettingsPreference(context: Context) :
    PreferenceMetadata,
    PreferenceBinding,
    PreferenceSummaryProvider {

    override val key: String
        get() = KEY

    override val purpose: Int
        get() = R.string.ambient_bottom_title

    override val title: Int
        get() = R.string.ambient_bottom_title

    override fun getSummary(context: Context): CharSequence? {
        val value = Settings.System.getInt(context.contentResolver, KEY, 1)
        val entries = context.resources.getStringArray(R.array.ambient_bottom_entries)
        val values = context.resources.getStringArray(R.array.ambient_bottom_values)
        val index = values.indexOf(value.toString())
        return if (index != -1) entries[index] else null
    }

    override fun createWidget(context: Context): Preference {
        return ListPreference(context).apply {
            setEntries(R.array.ambient_bottom_entries)
            setEntryValues(R.array.ambient_bottom_values)
            val currentValue = Settings.System.getInt(context.contentResolver, KEY, 1).toString()
            value = currentValue
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                val strValue = newValue as String
                Settings.System.putInt(context.contentResolver, KEY, strValue.toInt())
                true
            }
        }
    }

    companion object {
        const val KEY = "ambient_show_settings"
    }
}

class AmbientShowSettingsIconsPreference(context: Context) :
    SwitchPreference(
        KEY,
        R.string.display_icon_title,
        R.string.display_icon_summary,
    ) {

    private val dataStore = SettingsSystemStore.get(context).apply { setDefaultValue(KEY, false) }

    override fun storage(context: Context) = dataStore

    companion object {
        const val KEY = "ambient_show_settings_icons"
    }
}

class DozePeekStore(private val context: Context) : KeyValueStoreDelegate {
    override val keyValueStoreDelegate: KeyValueStore
        get() = SettingsSecureStore.get(context)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getValue(key: String, valueType: Class<T>): T? {
        val intVal = keyValueStoreDelegate.getInt(key) ?: 0
        return (intVal == 1) as T
    }

    override fun <T : Any> setValue(key: String, valueType: Class<T>, value: T?) {
        if (value == null) {
            keyValueStoreDelegate.setInt(key, null)
        } else if (value is Boolean) {
            keyValueStoreDelegate.setInt(key, if (value) 1 else 0)
        }
    }
}

class DozePeekPreference(context: Context) :
    SwitchPreference(
        KEY,
        R.string.doze_peek_title,
        R.string.doze_peek_summary,
    ),
    PreferenceAvailabilityProvider {

    private val dataStore = DozePeekStore(context)
    private val config = AmbientDisplayConfiguration(context)

    override val availabilityDescription: String
        get() = "Device must support always-on display"

    override fun getAvailabilityStability() = PreconditionStability.UNSTABLE

    override fun isAvailable(context: Context): Boolean {
        return config.alwaysOnAvailableForUser(UserHandle.USER_CURRENT)
    }

    override fun storage(context: Context) = dataStore

    companion object {
        const val KEY = DOZE_PEEK
    }
}

class DozePeekDurationPreference(context: Context) :
    PreferenceMetadata,
    PreferenceBinding,
    PreferenceSummaryProvider,
    PreferenceAvailabilityProvider {

    private val dozePeekStore = DozePeekStore(context)
    private val config = AmbientDisplayConfiguration(context)

    override val key: String
        get() = KEY

    override val purpose: Int
        get() = R.string.doze_peek_duration_title

    override val title: Int
        get() = R.string.doze_peek_duration_title

    override fun dependencies(context: Context) = arrayOf(DOZE_PEEK)

    override fun isEnabled(context: Context): Boolean {
        return dozePeekStore.getValue(DOZE_PEEK, Boolean::class.java) == true
    }

    override val availabilityDescription: String
        get() = "Device must support always-on display"

    override fun getAvailabilityStability() = PreconditionStability.UNSTABLE

    override fun isAvailable(context: Context): Boolean {
        return config.alwaysOnAvailableForUser(UserHandle.USER_CURRENT)
    }

    override fun getSummary(context: Context): CharSequence? {
        val value = Settings.Secure.getIntForUser(context.contentResolver, KEY, 5, UserHandle.USER_CURRENT)
        val entries = context.resources.getStringArray(R.array.doze_peek_duration_entries)
        val values = context.resources.getStringArray(R.array.doze_peek_duration_values)
        val index = values.indexOf(value.toString())
        return if (index != -1) entries[index] else null
    }

    override fun createWidget(context: Context): Preference {
        return ListPreference(context).apply {
            setEntries(R.array.doze_peek_duration_entries)
            setEntryValues(R.array.doze_peek_duration_values)
            val currentValue = Settings.Secure.getIntForUser(context.contentResolver, KEY, 5, UserHandle.USER_CURRENT).toString()
            value = currentValue
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                val strValue = newValue as String
                Settings.Secure.putIntForUser(context.contentResolver, KEY, strValue.toInt(), UserHandle.USER_CURRENT)
                true
            }
        }
    }

    companion object {
        const val KEY = DOZE_PEEK_DURATION
    }
}

class DozeShakeStore(private val context: Context) : KeyValueStoreDelegate {
    override val keyValueStoreDelegate: KeyValueStore
        get() = SettingsSecureStore.get(context)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getValue(key: String, valueType: Class<T>): T? {
        val intVal = keyValueStoreDelegate.getInt(key) ?: 0
        return (intVal == 1) as T
    }

    override fun <T : Any> setValue(key: String, valueType: Class<T>, value: T?) {
        if (value == null) {
            keyValueStoreDelegate.setInt(key, null)
        } else if (value is Boolean) {
            keyValueStoreDelegate.setInt(key, if (value) 1 else 0)
        }
    }
}

class DozeShakePreference(context: Context) :
    SwitchPreference(
        KEY,
        R.string.doze_shake_title,
        R.string.doze_shake_summary,
    ),
    PreferenceAvailabilityProvider {

    private val dataStore = DozeShakeStore(context)
    private val config = AmbientDisplayConfiguration(context)

    override val availabilityDescription: String
        get() = "Device must support always-on display"

    override fun getAvailabilityStability() = PreconditionStability.UNSTABLE

    override fun isAvailable(context: Context): Boolean {
        return config.alwaysOnAvailableForUser(UserHandle.USER_CURRENT)
    }

    override fun storage(context: Context) = dataStore

    companion object {
        const val KEY = DOZE_SHAKE_TO_SHOW
    }
}

class DozeShakeDurationPreference(context: Context) :
    PreferenceMetadata,
    PreferenceBinding,
    PreferenceSummaryProvider,
    PreferenceAvailabilityProvider {

    private val dozeShakeStore = DozeShakeStore(context)
    private val config = AmbientDisplayConfiguration(context)

    override val key: String
        get() = KEY

    override val purpose: Int
        get() = R.string.doze_shake_duration_title

    override val title: Int
        get() = R.string.doze_shake_duration_title

    override fun dependencies(context: Context) = arrayOf(DOZE_SHAKE_TO_SHOW)

    override fun isEnabled(context: Context): Boolean {
        return dozeShakeStore.getValue(DOZE_SHAKE_TO_SHOW, Boolean::class.java) == true
    }

    override val availabilityDescription: String
        get() = "Device must support always-on display"

    override fun getAvailabilityStability() = PreconditionStability.UNSTABLE

    override fun isAvailable(context: Context): Boolean {
        return config.alwaysOnAvailableForUser(UserHandle.USER_CURRENT)
    }

    override fun getSummary(context: Context): CharSequence? {
        val value = Settings.Secure.getIntForUser(context.contentResolver, KEY, 5, UserHandle.USER_CURRENT)
        val entries = context.resources.getStringArray(R.array.doze_shake_duration_entries)
        val values = context.resources.getStringArray(R.array.doze_shake_duration_values)
        val index = values.indexOf(value.toString())
        return if (index != -1) entries[index] else null
    }

    override fun createWidget(context: Context): Preference {
        return ListPreference(context).apply {
            setEntries(R.array.doze_shake_duration_entries)
            setEntryValues(R.array.doze_shake_duration_values)
            val currentValue = Settings.Secure.getIntForUser(context.contentResolver, KEY, 5, UserHandle.USER_CURRENT).toString()
            value = currentValue
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                val strValue = newValue as String
                Settings.Secure.putIntForUser(context.contentResolver, KEY, strValue.toInt(), UserHandle.USER_CURRENT)
                true
            }
        }
    }

    companion object {
        const val KEY = DOZE_SHAKE_TO_SHOW_DURATION
    }
}

class DozeShakeIntensityPreference(context: Context) :
    PreferenceMetadata,
    PreferenceBinding,
    PreferenceSummaryProvider,
    PreferenceAvailabilityProvider {

    private val dozeShakeStore = DozeShakeStore(context)
    private val config = AmbientDisplayConfiguration(context)

    override val key: String
        get() = KEY

    override val purpose: Int
        get() = R.string.doze_shake_intensity_title

    override val title: Int
        get() = R.string.doze_shake_intensity_title

    override fun dependencies(context: Context) = arrayOf(DOZE_SHAKE_TO_SHOW)

    override fun isEnabled(context: Context): Boolean {
        return dozeShakeStore.getValue(DOZE_SHAKE_TO_SHOW, Boolean::class.java) == true
    }

    override val availabilityDescription: String
        get() = "Device must support always-on display"

    override fun getAvailabilityStability() = PreconditionStability.UNSTABLE

    override fun isAvailable(context: Context): Boolean {
        return config.alwaysOnAvailableForUser(UserHandle.USER_CURRENT)
    }

    override fun getSummary(context: Context): CharSequence? {
        val value = Settings.Secure.getIntForUser(context.contentResolver, KEY, 1, UserHandle.USER_CURRENT)
        val entries = context.resources.getStringArray(R.array.doze_shake_intensity_entries)
        val values = context.resources.getStringArray(R.array.doze_shake_intensity_values)
        val index = values.indexOf(value.toString())
        return if (index != -1) entries[index] else null
    }

    override fun createWidget(context: Context): Preference {
        return ListPreference(context).apply {
            setEntries(R.array.doze_shake_intensity_entries)
            setEntryValues(R.array.doze_shake_intensity_values)
            val currentValue = Settings.Secure.getIntForUser(context.contentResolver, KEY, 1, UserHandle.USER_CURRENT).toString()
            value = currentValue
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                val strValue = newValue as String
                Settings.Secure.putIntForUser(context.contentResolver, KEY, strValue.toInt(), UserHandle.USER_CURRENT)
                true
            }
        }
    }

    companion object {
        const val KEY = DOZE_SHAKE_INTENSITY
    }
}
