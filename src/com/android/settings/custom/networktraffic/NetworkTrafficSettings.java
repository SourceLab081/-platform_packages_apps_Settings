/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.custom.networktraffic;

import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.widget.SeekBarPreference;

public class NetworkTrafficSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "NetworkTrafficSettings";

    private static final int UNITS_KILOBITS = 0;
    private static final int UNITS_MEGABITS = 1;
    private static final int UNITS_KILOBYTES = 2;
    private static final int UNITS_MEGABYTES = 3;
    private static final int UNITS_AUTOBYTES = 4;

    private ListPreference mNetTrafficMode;
    private SwitchPreferenceCompat mNetTrafficAutohide;
    private ListPreference mNetTrafficUnits;
    private SeekBarPreference mNetTrafficNumberSize;
    private SwitchPreferenceCompat mNetTrafficShowUnits;
    private SeekBarPreference mNetTrafficUnitSize;
    private ListPreference mNetTrafficDisplayStyle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.network_traffic_settings);
        getActivity().setTitle(R.string.network_traffic_settings_title);

        final ContentResolver resolver = getActivity().getContentResolver();

        mNetTrafficMode = findPreference(Settings.Secure.NETWORK_TRAFFIC_MODE);
        mNetTrafficMode.setOnPreferenceChangeListener(this);
        int mode = Settings.Secure.getInt(resolver,
                Settings.Secure.NETWORK_TRAFFIC_MODE, 0);
        mNetTrafficMode.setValue(String.valueOf(mode));

        mNetTrafficAutohide = findPreference(Settings.Secure.NETWORK_TRAFFIC_AUTOHIDE);
        mNetTrafficAutohide.setChecked(Settings.Secure.getInt(resolver,
                Settings.Secure.NETWORK_TRAFFIC_AUTOHIDE, 0) == 1);
        mNetTrafficAutohide.setOnPreferenceChangeListener(this);

        mNetTrafficUnits = findPreference(Settings.Secure.NETWORK_TRAFFIC_UNITS);
        mNetTrafficUnits.setOnPreferenceChangeListener(this);
        int units = Settings.Secure.getInt(resolver,
                Settings.Secure.NETWORK_TRAFFIC_UNITS, UNITS_KILOBYTES);
        mNetTrafficUnits.setValue(String.valueOf(units));

        mNetTrafficNumberSize = findPreference(Settings.Secure.NETWORK_TRAFFIC_NUMBER_SIZE);
        mNetTrafficNumberSize.setProgress(Settings.Secure.getInt(resolver,
                Settings.Secure.NETWORK_TRAFFIC_NUMBER_SIZE, 100));
        mNetTrafficNumberSize.setOnPreferenceChangeListener(this);

        mNetTrafficShowUnits = findPreference(Settings.Secure.NETWORK_TRAFFIC_SHOW_UNITS);
        mNetTrafficShowUnits.setChecked(Settings.Secure.getInt(resolver,
                Settings.Secure.NETWORK_TRAFFIC_SHOW_UNITS, 1) == 1);
        mNetTrafficShowUnits.setOnPreferenceChangeListener(this);

        mNetTrafficUnitSize = findPreference(Settings.Secure.NETWORK_TRAFFIC_UNIT_SIZE);
        mNetTrafficUnitSize.setProgress(Settings.Secure.getInt(resolver,
                Settings.Secure.NETWORK_TRAFFIC_UNIT_SIZE, 100));
        mNetTrafficUnitSize.setOnPreferenceChangeListener(this);

        mNetTrafficDisplayStyle = findPreference(Settings.Secure.NETWORK_TRAFFIC_DISPLAY_STYLE);
        mNetTrafficDisplayStyle.setOnPreferenceChangeListener(this);
        int displayStyle = Settings.Secure.getInt(resolver,
                Settings.Secure.NETWORK_TRAFFIC_DISPLAY_STYLE, 0);
        mNetTrafficDisplayStyle.setValue(String.valueOf(displayStyle));

        updateEnabledStates(mode);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mNetTrafficMode) {
            int mode = Integer.parseInt((String) newValue);
            Settings.Secure.putInt(resolver,
                    Settings.Secure.NETWORK_TRAFFIC_MODE, mode);
            updateEnabledStates(mode);
        } else if (preference == mNetTrafficAutohide) {
            boolean enabled = (Boolean) newValue;
            Settings.Secure.putInt(resolver,
                    Settings.Secure.NETWORK_TRAFFIC_AUTOHIDE, enabled ? 1 : 0);
        } else if (preference == mNetTrafficUnits) {
            int units = Integer.parseInt((String) newValue);
            Settings.Secure.putInt(resolver,
                    Settings.Secure.NETWORK_TRAFFIC_UNITS, units);
        } else if (preference == mNetTrafficNumberSize) {
            int numberSize = (Integer) newValue;
            Settings.Secure.putInt(resolver,
                    Settings.Secure.NETWORK_TRAFFIC_NUMBER_SIZE, numberSize);
        } else if (preference == mNetTrafficShowUnits) {
            boolean showUnits = (Boolean) newValue;
            Settings.Secure.putInt(resolver,
                    Settings.Secure.NETWORK_TRAFFIC_SHOW_UNITS, showUnits ? 1 : 0);
            updateUnitSliderVisibility(showUnits);
        } else if (preference == mNetTrafficUnitSize) {
            int unitSize = (Integer) newValue;
            Settings.Secure.putInt(resolver,
                    Settings.Secure.NETWORK_TRAFFIC_UNIT_SIZE, unitSize);
        } else if (preference == mNetTrafficDisplayStyle) {
            int displayStyle = Integer.parseInt((String) newValue);
            Settings.Secure.putInt(resolver,
                    Settings.Secure.NETWORK_TRAFFIC_DISPLAY_STYLE, displayStyle);
        }
        return true;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_SYSTEM_CATEGORY;
    }

    private void updateEnabledStates(int mode) {
        final boolean enabled = mode != 0;
        mNetTrafficAutohide.setEnabled(enabled);
        mNetTrafficUnits.setEnabled(enabled);
        mNetTrafficNumberSize.setEnabled(enabled);
        mNetTrafficShowUnits.setEnabled(enabled);
        mNetTrafficDisplayStyle.setEnabled(enabled);
        updateUnitSliderVisibility(enabled && mNetTrafficShowUnits.isChecked());
    }

    private void updateUnitSliderVisibility(boolean visible) {
        mNetTrafficUnitSize.setVisible(visible);
    }
}
