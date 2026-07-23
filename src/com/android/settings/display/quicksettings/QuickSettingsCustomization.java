/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.display.quicksettings;

import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.Settings;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.custom.preference.SecureSettingListPreference;
import com.android.settings.custom.preference.SystemSettingListPreference;
import com.android.settings.custom.preference.SystemSettingSeekBarPreference;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public class QuickSettingsCustomization extends SettingsPreferenceFragment implements Preference.OnPreferenceChangeListener {

    public static final String TAG = "QuickSettingsCustomization";

    private static final String KEY_SHOW_BRIGHTNESS_SLIDER = "qs_show_brightness_slider";
    private static final String KEY_BRIGHTNESS_SLIDER_POSITION = "qs_brightness_slider_position";
    private static final String KEY_BRIGHTNESS_SLIDER_SHAPE = "qs_brightness_slider_shape";
    private static final String KEY_BRIGHTNESS_SLIDER_SHAPE_CORNER_RADIUS = "qs_brightness_slider_shape_corner_radius";
    private static final String KEY_TILE_SHAPE = "qs_tile_shape";
    private static final String KEY_TILE_SHAPE_CORNER_RADIUS = "qs_tile_shape_corner_radius";

    private SecureSettingListPreference mShowBrightnessSlider;
    private SecureSettingListPreference mBrightnessSliderPosition;
    private SystemSettingListPreference mBrightnessSliderShape;
    private SystemSettingSeekBarPreference mBrightnessSliderCornerRadius;
    private SystemSettingListPreference mTileShape;
    private SystemSettingSeekBarPreference mTileCornerRadius;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.qs_customization_settings);

        ContentResolver resolver = getActivity().getContentResolver();

        mShowBrightnessSlider = findPreference(KEY_SHOW_BRIGHTNESS_SLIDER);
        mBrightnessSliderPosition = findPreference(KEY_BRIGHTNESS_SLIDER_POSITION);
        mBrightnessSliderShape = findPreference(KEY_BRIGHTNESS_SLIDER_SHAPE);
        mBrightnessSliderCornerRadius = findPreference(KEY_BRIGHTNESS_SLIDER_SHAPE_CORNER_RADIUS);

        if (mShowBrightnessSlider != null) {
            mShowBrightnessSlider.setOnPreferenceChangeListener(this);
            int showValue = Settings.Secure.getInt(resolver, KEY_SHOW_BRIGHTNESS_SLIDER, 1);
            updateBrightnessSliderPreferences(showValue);
        }

        if (mBrightnessSliderShape != null) {
            mBrightnessSliderShape.setOnPreferenceChangeListener(this);
        }

        mTileShape = findPreference(KEY_TILE_SHAPE);
        mTileCornerRadius = findPreference(KEY_TILE_SHAPE_CORNER_RADIUS);
        if (mTileShape != null && mTileCornerRadius != null) {
            mTileShape.setOnPreferenceChangeListener(this);
            int value = Settings.System.getInt(resolver, KEY_TILE_SHAPE, 0);
            mTileCornerRadius.setVisible(value >= 3);
        }
    }

    private void updateBrightnessSliderPreferences(int showValue) {
        boolean isVisible = showValue != 0;
        if (mBrightnessSliderPosition != null) {
            mBrightnessSliderPosition.setVisible(isVisible);
        }
        if (mBrightnessSliderShape != null) {
            mBrightnessSliderShape.setVisible(isVisible);
        }
        if (mBrightnessSliderCornerRadius != null) {
            if (!isVisible) {
                mBrightnessSliderCornerRadius.setVisible(false);
            } else {
                ContentResolver resolver = getActivity().getContentResolver();
                int shapeValue = Settings.System.getInt(resolver, KEY_BRIGHTNESS_SLIDER_SHAPE, 0);
                mBrightnessSliderCornerRadius.setVisible(shapeValue >= 3);
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mShowBrightnessSlider) {
            int showValue = Integer.parseInt((String) newValue);
            updateBrightnessSliderPreferences(showValue);
            return true;
        } else if (preference == mBrightnessSliderShape) {
            int value = Integer.parseInt((String) newValue);
            if (mBrightnessSliderCornerRadius != null) {
                mBrightnessSliderCornerRadius.setVisible(value >= 3);
            }
            return true;
        } else if (preference == mTileShape) {
            int value = Integer.parseInt((String) newValue);
            if (mTileCornerRadius != null) {
                mTileCornerRadius.setVisible(value >= 3);
            }
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DISPLAY;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.qs_customization_settings);
}
