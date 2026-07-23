/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.display;

import android.app.settings.SettingsEnums;
import android.graphics.Color;
import android.content.Context;
import android.content.ContentResolver;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.display.pulse.ColorPickerDialogFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public class PulseSettings extends DashboardFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "PulseSettings";
    private static final String KEY_PULSE_COLOR = "pulse_color";
    private static final String KEY_PULSE_CUSTOM_COLOR = "pulse_custom_color";
    private static final String COLOR_MODE_CUSTOM = "custom";

    private Preference mPulseColor;
    private Preference mPulseCustomColor;

    public static void reset(Context context) {
        final ContentResolver resolver = context.getContentResolver();
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.LOCKSCREEN_PULSE_ENABLED, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.PULSE_SHOW_ON_AMBIENT, 1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.PULSE_BAR_COUNT, 32, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.PULSE_ROUNDED_BARS, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putStringForUser(resolver,
                Settings.Secure.PULSE_COLOR, "lavalamp", UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.PULSE_CUSTOM_COLOR, Color.WHITE, UserHandle.USER_CURRENT);
        Settings.Secure.putStringForUser(resolver,
                Settings.Secure.PULSE_RENDERER, "solid", UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.PULSE_HEIGHT_MULTIPLIER, 100, UserHandle.USER_CURRENT);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPulseColor = findPreference(KEY_PULSE_COLOR);
        mPulseCustomColor = findPreference(KEY_PULSE_CUSTOM_COLOR);
        if (mPulseColor != null) {
            mPulseColor.setOnPreferenceChangeListener(this);
        }
        if (mPulseCustomColor != null) {
            mPulseCustomColor.setOnPreferenceClickListener(preference -> {
                showCustomColorDialog();
                return true;
            });
        }

        updatePreferenceVisibility(getCurrentColorMode());
        updateCustomColorSummary();
    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
        if (preference == mPulseColor) {
            updatePreferenceVisibility((String) newValue);
        }
        return true;
    }

    private void updatePreferenceVisibility(String colorValue) {
        if (mPulseCustomColor == null) {
            return;
        }
        mPulseCustomColor.setVisible(COLOR_MODE_CUSTOM.equals(colorValue));
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferenceVisibility(getCurrentColorMode());
        updateCustomColorSummary();
    }

    private void showCustomColorDialog() {
        if (getActivity() == null) {
            return;
        }
        final int currentColor = Settings.Secure.getIntForUser(
                getContentResolver(),
                Settings.Secure.PULSE_CUSTOM_COLOR,
                Color.WHITE,
                UserHandle.USER_CURRENT);
        final String colorHex = String.format("%06X", 0xFFFFFF & currentColor);
        final ColorPickerDialogFragment dialog = ColorPickerDialogFragment.newInstance(colorHex);
        dialog.setOnColorSelectedListener(colorArgb -> {
            Settings.Secure.putIntForUser(
                    getContentResolver(),
                    Settings.Secure.PULSE_CUSTOM_COLOR,
                    colorArgb,
                    UserHandle.USER_CURRENT);
            updateCustomColorSummary();
        });
        dialog.show(getParentFragmentManager(), ColorPickerDialogFragment.TAG);
    }

    private void updateCustomColorSummary() {
        if (mPulseCustomColor == null) {
            return;
        }
        final int currentColor = Settings.Secure.getIntForUser(
                getContentResolver(),
                Settings.Secure.PULSE_CUSTOM_COLOR,
                Color.WHITE,
                UserHandle.USER_CURRENT);
        mPulseCustomColor.setSummary(String.format("#%06X", 0xFFFFFF & currentColor));
    }

    private String getCurrentColorMode() {
        return Settings.Secure.getStringForUser(
                getContentResolver(),
                Settings.Secure.PULSE_COLOR,
                UserHandle.USER_CURRENT);
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.pulse_settings;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_LOCK_SCREEN_PREFERENCES;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.pulse_settings) {
                @Override
                public java.util.List<String> getNonIndexableKeys(Context context) {
                    final java.util.List<String> keys = super.getNonIndexableKeys(context);
                    final String colorMode = Settings.Secure.getStringForUser(
                            context.getContentResolver(),
                            Settings.Secure.PULSE_COLOR,
                            UserHandle.USER_CURRENT);
                    if (!COLOR_MODE_CUSTOM.equals(colorMode)) {
                        keys.add(KEY_PULSE_CUSTOM_COLOR);
                    }
                    return keys;
                }
            };
}
