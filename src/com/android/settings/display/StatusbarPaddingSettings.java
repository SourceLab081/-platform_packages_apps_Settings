/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.display;

import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.widget.SeekBarPreference;

public class StatusbarPaddingSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private SeekBarPreference mPaddingStart;
    private SeekBarPreference mPaddingTop;
    private SeekBarPreference mPaddingEnd;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.statusbar_padding_settings);
        getActivity().setTitle(R.string.statusbar_padding_title);

        final ContentResolver resolver = getActivity().getContentResolver();

        mPaddingStart = findPreference(Settings.System.STATUSBAR_EXTRA_PADDING_START);
        mPaddingStart.setProgress(Settings.System.getInt(resolver,
                Settings.System.STATUSBAR_EXTRA_PADDING_START, 0));
        mPaddingStart.setOnPreferenceChangeListener(this);

        mPaddingTop = findPreference(Settings.System.STATUSBAR_EXTRA_PADDING_TOP);
        mPaddingTop.setProgress(Settings.System.getInt(resolver,
                Settings.System.STATUSBAR_EXTRA_PADDING_TOP, 0));
        mPaddingTop.setOnPreferenceChangeListener(this);

        mPaddingEnd = findPreference(Settings.System.STATUSBAR_EXTRA_PADDING_END);
        mPaddingEnd.setProgress(Settings.System.getInt(resolver,
                Settings.System.STATUSBAR_EXTRA_PADDING_END, 0));
        mPaddingEnd.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final ContentResolver resolver = getActivity().getContentResolver();
        int value = (Integer) newValue;
        if (preference == mPaddingStart) {
            Settings.System.putInt(resolver,
                    Settings.System.STATUSBAR_EXTRA_PADDING_START, value);
        } else if (preference == mPaddingTop) {
            Settings.System.putInt(resolver,
                    Settings.System.STATUSBAR_EXTRA_PADDING_TOP, value);
        } else if (preference == mPaddingEnd) {
            Settings.System.putInt(resolver,
                    Settings.System.STATUSBAR_EXTRA_PADDING_END, value);
        }
        return true;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_SYSTEM_CATEGORY;
    }
}
