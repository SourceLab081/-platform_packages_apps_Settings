/*
 * Copyright (C) 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.display;

import android.app.settings.SettingsEnums;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public class ColorModeAdvancedSettings extends DashboardFragment {

    private static final String TAG = "ColorModeAdvancedSettings";

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.color_mode_advanced_settings;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.COLOR_MODE_SETTINGS;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.color_mode_advanced_settings);
}
