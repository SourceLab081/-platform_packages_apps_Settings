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

package com.android.settings.system

import android.app.settings.SettingsEnums
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.View.LAYOUT_DIRECTION_RTL
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.android.settings.R
import com.android.settings.Utils.isDeviceFoldable
import com.android.settings.dashboard.DashboardFragment
import com.android.settings.search.BaseSearchIndexProvider
import com.android.settings.support.actionbar.HelpResourceProvider
import com.android.settingslib.search.SearchIndexable
import com.android.settingslib.search.SearchIndexableRaw
import com.android.settingslib.widget.FooterPreference
import com.android.settingslib.widget.IllustrationPreference
import com.android.settingslib.widget.SelectorWithWidgetPreference

/**
 * The preference fragment for the Settings page controlling Notifications & Quick Settings panels,
 * allowing the user to switch between "Dual Shade" and "Single Shade" for Portrait and Landscape.
 */
@SearchIndexable
class ShadePanelsFragment : DashboardFragment(), HelpResourceProvider {

    private lateinit var portraitSeparate: SelectorWithWidgetPreference
    private lateinit var portraitCombined: SelectorWithWidgetPreference
    private lateinit var landscapeSeparate: SelectorWithWidgetPreference
    private lateinit var landscapeCombined: SelectorWithWidgetPreference
    private var illustrationPref: IllustrationPreference? = null

    override fun getPreferenceScreenResId(): Int = R.xml.shade_panels_settings

    override fun getLogTag(): String = TAG

    override fun getMetricsCategory(): Int = SettingsEnums.PAGE_VISIBLE

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        val context = requireContext()

        portraitSeparate = findPreference("shade_panels_portrait_separate")!!
        portraitCombined = findPreference("shade_panels_portrait_combined")!!
        landscapeSeparate = findPreference("shade_panels_landscape_separate")!!
        landscapeCombined = findPreference("shade_panels_landscape_combined")!!
        illustrationPref = findPreference("shade_panels_illustration")

        portraitSeparate.setOnClickListener {
            updatePortraitSelection(true)
        }
        portraitCombined.setOnClickListener {
            updatePortraitSelection(false)
        }
        landscapeSeparate.setOnClickListener {
            updateLandscapeSelection(true)
        }
        landscapeCombined.setOnClickListener {
            updateLandscapeSelection(false)
        }

        // Initialize checked states from Secure Settings
        val portraitEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.DUAL_SHADE,
            0 // Default: OFF (Combined)
        ) == 1
        val landscapeEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.DUAL_SHADE_LANDSCAPE,
            0 // Default: OFF (Combined)
        ) == 1

        updatePortraitSelectionCheckedState(portraitEnabled)
        updateLandscapeSelectionCheckedState(landscapeEnabled)

        updateIllustrationAndSeekBar()

        if (isDeviceFoldable(context)) {
            preferenceScreen.addPreference(
                FooterPreference(context).apply {
                    title = context.getText(R.string.shade_panels_foldables_footer_message)
                }
            )
        }
    }

    private fun updatePortraitSelection(separate: Boolean) {
        Settings.Secure.putInt(
            requireContext().contentResolver,
            Settings.Secure.DUAL_SHADE,
            if (separate) 1 else 0
        )
        updatePortraitSelectionCheckedState(separate)
        updateIllustrationAndSeekBar()
    }

    private fun updateLandscapeSelection(separate: Boolean) {
        Settings.Secure.putInt(
            requireContext().contentResolver,
            Settings.Secure.DUAL_SHADE_LANDSCAPE,
            if (separate) 1 else 0
        )
        updateLandscapeSelectionCheckedState(separate)
        updateIllustrationAndSeekBar()
    }

    private fun updatePortraitSelectionCheckedState(separate: Boolean) {
        portraitSeparate.isChecked = separate
        portraitCombined.isChecked = !separate
    }

    private fun updateLandscapeSelectionCheckedState(separate: Boolean) {
        landscapeSeparate.isChecked = separate
        landscapeCombined.isChecked = !separate
    }

    private fun updateIllustrationAndSeekBar() {
        val context = requireContext()
        val isSeparate = portraitSeparate.isChecked || landscapeSeparate.isChecked

        // Update illustration based on current selection
        val configuration = context.resources.configuration
        val isRtl = configuration.layoutDirection == LAYOUT_DIRECTION_RTL
        val lottieResId = if (isSeparate) {
            if (isRtl) R.raw.lottie_shade_panels_separate_rtl
            else R.raw.lottie_shade_panels_separate_ltr
        } else {
            if (isRtl) R.raw.lottie_shade_panels_combined_rtl
            else R.raw.lottie_shade_panels_combined_ltr
        }
        illustrationPref?.setLottieAnimationResId(lottieResId)

        // Show/hide split ratio SeekBarPreference dynamically
        val screen = preferenceScreen
        val seekBarKey = Settings.System.STATUS_BAR_SHADE_SPLIT_PERCENTAGE
        var seekBarPref = screen.findPreference<com.android.settings.widget.SeekBarPreference>(seekBarKey)

        if (isSeparate) {
            if (seekBarPref == null) {
                seekBarPref = com.android.settings.widget.SeekBarPreference(context).apply {
                    key = seekBarKey
                    title = context.getString(R.string.status_bar_shade_split_percentage_title)
                    max = 90
                    min = 10
                    setHapticFeedbackMode(com.android.settings.widget.SeekBarPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS)
                    val currentValue = Settings.System.getInt(
                        context.contentResolver,
                        Settings.System.STATUS_BAR_SHADE_SPLIT_PERCENTAGE,
                        50
                    )
                    progress = currentValue
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                        val percentage = newValue as Int
                        Settings.System.putInt(
                            context.contentResolver,
                            Settings.System.STATUS_BAR_SHADE_SPLIT_PERCENTAGE,
                            percentage
                        )
                        true
                    }
                }
                screen.addPreference(seekBarPref)
            }
        } else {
            seekBarPref?.let { screen.removePreference(it) }
        }
    }

    companion object {
        private const val TAG = "ShadePanelsFragment"

        // Expose as a static field for the @SearchIndexable annotation processor.
        @JvmField
        val SEARCH_INDEX_DATA_PROVIDER: BaseSearchIndexProvider =
            object : BaseSearchIndexProvider(R.xml.shade_panels_settings) {

                override fun isPageSearchEnabled(context: Context): Boolean =
                    ShadePanelsPreferenceController.isDualShadeAvailable(context)

                override fun getRawDataToIndex(context: Context, enabled: Boolean): List<SearchIndexableRaw> {
                    return listOf(
                        SearchIndexableRaw(context).apply {
                            title = context.getString(R.string.shade_panels_separate_title)
                            summaryOn = context.getString(R.string.shade_panels_separate_summary)
                            key = "shade_panels_portrait_separate"
                        },
                        SearchIndexableRaw(context).apply {
                            title = context.getString(R.string.shade_panels_combined_title)
                            summaryOn = context.getString(R.string.shade_panels_combined_summary)
                            key = "shade_panels_portrait_combined"
                        },
                    )
                }
            }
    }
}
