/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.display;

import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.List;

@SearchIndexable
public class MediaArtSettings extends DashboardFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "MediaArtSettings";
    private static final String KEY_MEDIA_ART_FILTER = "ls_media_art_filter";
    private static final String KEY_PIXEL_SIZE = "ls_media_art_pixel_size";
    private static final int FILTER_PIXELATION = 7;

    private ListPreference mMediaArtFilter;
    private Preference mPixelSize;

    private final ContentObserver mMediaFilterObserver =
            new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    updatePixelSizeVisibility();
                }
            };

    public static void reset(Context context) {
        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.LS_MEDIA_ART_ENABLED, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.LS_MEDIA_ART_FILTER, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.LS_MEDIA_ART_PIXEL_SIZE, 20, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.LS_MEDIA_ART_FADE_LEVEL, 40, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.LS_MEDIA_ART_BLUR_LEVEL, 90, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.LS_MEDIA_ART_AOD_ENABLED, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.LS_MEDIA_ART_AOD_DIM_LEVEL, 35, UserHandle.USER_CURRENT);
    }

    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMediaArtFilter = findPreference(KEY_MEDIA_ART_FILTER);
        mPixelSize = findPreference(KEY_PIXEL_SIZE);

        if (mMediaArtFilter != null) {
            mMediaArtFilter.setOnPreferenceChangeListener(this);
        }

        updatePixelSizeVisibility();

        final Context context = getContext();
        if (context != null) {
            context.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.LS_MEDIA_ART_FILTER),
                    false,
                    mMediaFilterObserver,
                    UserHandle.USER_CURRENT);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePixelSizeVisibility();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        final Context context = getContext();
        if (context != null) {
            context.getContentResolver().unregisterContentObserver(mMediaFilterObserver);
        }
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.media_art_settings;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_LOCK_SCREEN_PREFERENCES;
    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
        if (preference == mMediaArtFilter) {
            updatePixelSizeVisibility(Integer.parseInt((String) newValue));
        }
        return true;
    }

    private void updatePixelSizeVisibility() {
        final Context context = getContext();
        if (context == null) {
            return;
        }

        final ContentResolver resolver = context.getContentResolver();
        final int currentFilter = Settings.System.getIntForUser(
                resolver, Settings.System.LS_MEDIA_ART_FILTER, 0, UserHandle.USER_CURRENT);
        updatePixelSizeVisibility(currentFilter);
    }

    private void updatePixelSizeVisibility(int filterValue) {
        if (mPixelSize == null) {
            return;
        }
        mPixelSize.setVisible(filterValue == FILTER_PIXELATION);
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.media_art_settings) {
                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    final List<String> keys = super.getNonIndexableKeys(context);
                    final int currentFilter = Settings.System.getIntForUser(
                            context.getContentResolver(),
                            Settings.System.LS_MEDIA_ART_FILTER,
                            0,
                            UserHandle.USER_CURRENT);
                    if (currentFilter != FILTER_PIXELATION) {
                        keys.add(KEY_PIXEL_SIZE);
                    }
                    return keys;
                }
            };
}
