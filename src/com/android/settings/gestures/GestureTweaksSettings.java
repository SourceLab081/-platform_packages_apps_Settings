/*
 * Copyright (C) 2020 abcduwhatever
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.android.settings.gestures;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.custom.preference.SystemSettingSwitchPreference;
import com.android.settings.search.BaseSearchIndexProvider;
import android.app.settings.SettingsEnums;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable
public class GestureTweaksSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_EXTENDED = "back_swipe_extended";
    private static final String KEY_LEFT = "left_swipe_actions";
    private static final String KEY_RIGHT = "right_swipe_actions";
    private static final String KEY_LEFT_APP = "left_swipe_app_action";
    private static final String KEY_RIGHT_APP = "right_swipe_app_action";
    private static final String KEY_LEFT_VERTICAL = "left_vertical_swipe_actions";
    private static final String KEY_RIGHT_VERTICAL = "right_vertical_swipe_actions";
    private static final String KEY_LEFT_VERTICAL_APP = "left_vertical_swipe_app_action";
    private static final String KEY_RIGHT_VERTICAL_APP = "right_vertical_swipe_app_action";
    private static final int ACTION_APP = 4;

    private ListPreference mLeftSwipeActions;
    private ListPreference mRightSwipeActions;
    private Preference mLeftSwipeAppSelection;
    private Preference mRightSwipeAppSelection;
    private ListPreference mLeftVerticalSwipeActions;
    private ListPreference mRightVerticalSwipeActions;
    private Preference mLeftVerticalSwipeAppSelection;
    private Preference mRightVerticalSwipeAppSelection;
    private SystemSettingSwitchPreference mExtendedSwipe;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.gesture_nav_tweaks);

        mExtendedSwipe = findPreference(KEY_EXTENDED);
        mLeftSwipeActions = findPreference(KEY_LEFT);
        mRightSwipeActions = findPreference(KEY_RIGHT);
        mLeftSwipeAppSelection = findPreference(KEY_LEFT_APP);
        mRightSwipeAppSelection = findPreference(KEY_RIGHT_APP);
        mLeftVerticalSwipeActions = findPreference(KEY_LEFT_VERTICAL);
        mRightVerticalSwipeActions = findPreference(KEY_RIGHT_VERTICAL);
        mLeftVerticalSwipeAppSelection = findPreference(KEY_LEFT_VERTICAL_APP);
        mRightVerticalSwipeAppSelection = findPreference(KEY_RIGHT_VERTICAL_APP);

        mExtendedSwipe.setOnPreferenceChangeListener(this);
        mLeftSwipeActions.setOnPreferenceChangeListener(this);
        mRightSwipeActions.setOnPreferenceChangeListener(this);
        mLeftVerticalSwipeActions.setOnPreferenceChangeListener(this);
        mRightVerticalSwipeActions.setOnPreferenceChangeListener(this);
        reloadPreferences();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mExtendedSwipe) {
            updateExtendedSwipe((Boolean) newValue);
            return true;
        }

        int action = Integer.parseInt((String) newValue);
        if (preference == mLeftSwipeActions) {
            putAction(Settings.System.LEFT_LONG_BACK_SWIPE_ACTION, action);
            updateActionPreference(mLeftSwipeActions, mLeftSwipeAppSelection, action, true);
            return true;
        } else if (preference == mRightSwipeActions) {
            putAction(Settings.System.RIGHT_LONG_BACK_SWIPE_ACTION, action);
            updateActionPreference(mRightSwipeActions, mRightSwipeAppSelection, action, true);
            return true;
        } else if (preference == mLeftVerticalSwipeActions) {
            putAction(Settings.System.LEFT_VERTICAL_BACK_SWIPE_ACTION, action);
            updateActionPreference(mLeftVerticalSwipeActions, mLeftVerticalSwipeAppSelection,
                    action, mExtendedSwipe.isChecked());
            return true;
        } else if (preference == mRightVerticalSwipeActions) {
            putAction(Settings.System.RIGHT_VERTICAL_BACK_SWIPE_ACTION, action);
            updateActionPreference(mRightVerticalSwipeActions, mRightVerticalSwipeAppSelection,
                    action, mExtendedSwipe.isChecked());
            return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadPreferences();
    }

    private void reloadPreferences() {
        ContentResolver resolver = getContentResolver();
        boolean extendedSwipe = Settings.System.getIntForUser(resolver,
                Settings.System.BACK_SWIPE_EXTENDED, 0, UserHandle.USER_CURRENT) != 0;
        mExtendedSwipe.setChecked(extendedSwipe);
        mLeftVerticalSwipeActions.setEnabled(extendedSwipe);
        mRightVerticalSwipeActions.setEnabled(extendedSwipe);

        updateStoredAction(mLeftSwipeActions, mLeftSwipeAppSelection,
                Settings.System.LEFT_LONG_BACK_SWIPE_ACTION, true);
        updateStoredAction(mRightSwipeActions, mRightSwipeAppSelection,
                Settings.System.RIGHT_LONG_BACK_SWIPE_ACTION, true);
        updateStoredAction(mLeftVerticalSwipeActions, mLeftVerticalSwipeAppSelection,
                Settings.System.LEFT_VERTICAL_BACK_SWIPE_ACTION, extendedSwipe);
        updateStoredAction(mRightVerticalSwipeActions, mRightVerticalSwipeAppSelection,
                Settings.System.RIGHT_VERTICAL_BACK_SWIPE_ACTION, extendedSwipe);
        updateCustomAppSummaries();
    }

    private void updateStoredAction(ListPreference preference, Preference appPreference,
            String setting, boolean appPreferenceAllowed) {
        int action = Settings.System.getIntForUser(getContentResolver(), setting, 0,
                UserHandle.USER_CURRENT);
        updateActionPreference(preference, appPreference, action, appPreferenceAllowed);
    }

    private void updateActionPreference(ListPreference preference, Preference appPreference,
            int action, boolean appPreferenceAllowed) {
        preference.setValue(Integer.toString(action));
        preference.setSummary(preference.getEntry());
        appPreference.setVisible(appPreferenceAllowed && action == ACTION_APP);
        updateCustomAppSummaries();
    }

    private void updateExtendedSwipe(boolean enabled) {
        mExtendedSwipe.setChecked(enabled);
        mLeftVerticalSwipeActions.setEnabled(enabled);
        mRightVerticalSwipeActions.setEnabled(enabled);
        mLeftVerticalSwipeAppSelection.setVisible(
                enabled && ACTION_APP == Integer.parseInt(mLeftVerticalSwipeActions.getValue()));
        mRightVerticalSwipeAppSelection.setVisible(
                enabled && ACTION_APP == Integer.parseInt(mRightVerticalSwipeActions.getValue()));
    }

    private void putAction(String setting, int action) {
        Settings.System.putIntForUser(getContentResolver(), setting, action,
                UserHandle.USER_CURRENT);
    }

    private void updateCustomAppSummaries() {
        setAppSummary(mLeftSwipeAppSelection, Settings.System.LEFT_LONG_BACK_SWIPE_APP_FR_ACTION);
        setAppSummary(mRightSwipeAppSelection, Settings.System.RIGHT_LONG_BACK_SWIPE_APP_FR_ACTION);
        setAppSummary(mLeftVerticalSwipeAppSelection,
                Settings.System.LEFT_VERTICAL_BACK_SWIPE_APP_FR_ACTION);
        setAppSummary(mRightVerticalSwipeAppSelection,
                Settings.System.RIGHT_VERTICAL_BACK_SWIPE_APP_FR_ACTION);
    }

    private void setAppSummary(Preference preference, String setting) {
        String summary = Settings.System.getStringForUser(getContentResolver(), setting,
                UserHandle.USER_CURRENT);
        preference.setSummary(summary == null || summary.isEmpty()
                ? getString(R.string.swipe_app_select_summary) : summary);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PAGE_UNKNOWN;
    }

    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result = new ArrayList<>();
                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.gesture_nav_tweaks;
                    result.add(sir);
                    return result;
                }
            };
}
