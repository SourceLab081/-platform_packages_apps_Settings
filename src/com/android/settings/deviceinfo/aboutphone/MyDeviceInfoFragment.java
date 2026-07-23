/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.deviceinfo.aboutphone;

import static androidx.core.content.ContextCompat.getMainExecutor;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.UserManager;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceScreen;
import android.widget.TextView;
import android.os.SystemProperties;
import android.os.Build;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.provider.Settings;
import android.app.usage.StorageStatsManager;
import android.os.storage.StorageManager;

import android.text.TextUtils;
import java.util.Locale;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.deviceinfo.BluetoothAddressPreferenceController;
import com.android.settings.deviceinfo.BuildNumberPreferenceController;
import com.android.settings.deviceinfo.DeviceNamePreferenceController;
import com.android.settings.deviceinfo.FccEquipmentIdPreferenceController;
import com.android.settings.deviceinfo.FeedbackPreferenceController;
import com.android.settings.deviceinfo.IpAddressPreferenceController;
import com.android.settings.deviceinfo.ManualPreferenceController;
import com.android.settings.deviceinfo.RegulatoryInfoPreferenceController;
import com.android.settings.deviceinfo.SafetyInfoPreferenceController;
import com.android.settings.deviceinfo.UptimePreferenceController;
import com.android.settings.deviceinfo.WifiMacAddressPreferenceController;
import com.android.settings.deviceinfo.imei.ImeiInfoPreferenceController;
import com.android.settings.deviceinfo.simstatus.EidStatus;
import com.android.settings.deviceinfo.simstatus.SimEidPreferenceController;
import com.android.settings.deviceinfo.simstatus.SimStatusPreferenceController;
import com.android.settings.deviceinfo.simstatus.SlotSimStatus;
import com.android.settings.flags.Flags;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.EntityHeaderController;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.LayoutPreference;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

// LINT.IfChange
@SearchIndexable
public class MyDeviceInfoFragment extends DashboardFragment
        implements DeviceNamePreferenceController.DeviceNamePreferenceHost {

    private static final String LOG_TAG = "MyDeviceInfoFragment";
    private static final String KEY_EID_INFO = "eid_info";
    private static final String KEY_MY_DEVICE_INFO_HEADER = "my_device_info_header";

    private BuildNumberPreferenceController mBuildNumberPreferenceController;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DEVICEINFO;
    }

    @Override
    public int getHelpResource() {
        return R.string.help_uri_about;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        use(DeviceNamePreferenceController.class).setHost(this /* parent */);
        mBuildNumberPreferenceController = use(BuildNumberPreferenceController.class);
        mBuildNumberPreferenceController.setHost(this /* parent */);
    }

    @Override
    protected @NonNull Set<String> getPreferenceKeysInHierarchy() {
        Set<String> keys = super.getPreferenceKeysInHierarchy();
        // add async preference key manually
        keys.add(KEY_EID_INFO);
        return keys;
    }

    @Override
    protected void onPreferenceScreenCreatedFromResource(
            @NonNull PreferenceScreen preferenceScreen) {
        if (isCatalystEnabled()) {
            // remove the preference created from resource to avoid duplicated key
            preferenceScreen.removePreferenceRecursively(KEY_EID_INFO);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        initCustomUI();
    }

    private void initCustomUI() {
        LayoutPreference headerPref = findPreference("my_device_info_custom_header");
        if (headerPref != null) {
            TextView romVersion = headerPref.findViewById(R.id.rom_version);
            TextView androidVersion = headerPref.findViewById(R.id.android_version);
            TextView deviceNameText = headerPref.findViewById(R.id.device_name_text);
            TextView buildStatus = headerPref.findViewById(R.id.build_status);
            
            if (romVersion != null) romVersion.setText("ASCP v" + SystemProperties.get("ro.ascp.version.base", "6.1"));
            if (androidVersion != null) androidVersion.setText("Android " + Build.VERSION.RELEASE);
            if (deviceNameText != null) deviceNameText.setText(Settings.Global.getString(getContext().getContentResolver(), Settings.Global.DEVICE_NAME));
            if (buildStatus != null) {
                String buildType = SystemProperties.get("ro.ascp.build.type", "OFFICIAL");
                if (TextUtils.isEmpty(buildType)) {
                    buildType = "OFFICIAL";
                }
                buildStatus.setText(buildType.toUpperCase(Locale.ENGLISH));
            }
        }

        LayoutPreference hwPref = findPreference("my_device_info_hardware_cards");
        if (hwPref != null) {
            TextView processorName = hwPref.findViewById(R.id.processor_name);
            TextView memoryInfo = hwPref.findViewById(R.id.memory_info);
            TextView batteryInfo = hwPref.findViewById(R.id.battery_info);

            if (processorName != null) processorName.setText(SystemProperties.get("ro.soc.model", "Qualcomm Snapdragon 870 5G"));
            
            if (memoryInfo != null) {
                long usedStorageInGb = getUsedStorageSizeGb();
                long totalStorageInGb = getRoundedStorageSizeGb();
                memoryInfo.setText(usedStorageInGb + "GB / " + totalStorageInGb + "GB");
            }

            if (batteryInfo != null) {
                Object powerProfile = null;
                double batteryCapacity = 0;
                try {
                    final String POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile";
                    Class<?> powerProfileClass = Class.forName(POWER_PROFILE_CLASS);
                    powerProfile = powerProfileClass.getConstructor(Context.class).newInstance(getContext());
                    batteryCapacity = (Double) powerProfileClass.getMethod("getBatteryCapacity").invoke(powerProfile);
                } catch (Exception e) {
                    // Ignore
                }
                if (batteryCapacity > 0) {
                    batteryInfo.setText((int)batteryCapacity + "mAh");
                } else {
                    batteryInfo.setText("4500mAh");
                }
            }
        }
    }

    /**
     * Returns the device's actual used internal storage in GB, computed the
     * same way the Storage settings screen does (total - free), so it
     * matches what's shown there (e.g. "75GB" used of 256GB).
     */
    private long getUsedStorageSizeGb() {
        try {
            StorageStatsManager storageStatsManager =
                    (StorageStatsManager) getContext()
                            .getSystemService(Context.STORAGE_STATS_SERVICE);
            long totalBytes = storageStatsManager.getTotalBytes(StorageManager.UUID_DEFAULT);
            long freeBytes = storageStatsManager.getFreeBytes(StorageManager.UUID_DEFAULT);
            long usedBytes = totalBytes - freeBytes;
            return Math.round(usedBytes / (1024.0 * 1024.0 * 1024.0));
        } catch (Exception e) {
            return 0; // fallback if StorageStatsManager fails
        }
    }

    /**
     * Returns the device's total internal storage, rounded up to the nearest
     * commonly marketed capacity (e.g. 128GB, 256GB, 512GB).
     *
     * Raw reported bytes are always a bit less than the advertised capacity
     * (decimal vs binary GB, plus space reserved for system partitions), so
     * we round up to the nearest standard size instead of showing the raw
     * value directly.
     */
    private long getRoundedStorageSizeGb() {
        final long[] standardSizesGb = {16, 32, 64, 128, 256, 512, 1024, 2048};
        try {
            StorageStatsManager storageStatsManager =
                    (StorageStatsManager) getContext()
                            .getSystemService(Context.STORAGE_STATS_SERVICE);
            long totalBytes = storageStatsManager.getTotalBytes(StorageManager.UUID_DEFAULT);
            double totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0);

            for (long size : standardSizesGb) {
                if (totalGb <= size) {
                    return size;
                }
            }
            return standardSizesGb[standardSizesGb.length - 1];
        } catch (Exception e) {
            return 128; // fallback if StorageStatsManager fails
        }
    }

    @Override
    protected String getLogTag() {
        return LOG_TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.my_device_info;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, this /* fragment */, getSettingsLifecycle());
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(
            Context context, MyDeviceInfoFragment fragment, Lifecycle lifecycle) {
        // disable catalyst for settings search (i.e. fragment is null)
        boolean isCatalystEnabled = Flags.catalystMyDeviceInfoPrefScreen() && fragment != null;
        final List<AbstractPreferenceController> controllers = new ArrayList<>();

        final Executor executor = (fragment == null) ? getMainExecutor(context) :
                Executors.newSingleThreadExecutor();
        androidx.lifecycle.Lifecycle lifecycleObject = (fragment == null) ? null :
                fragment.getLifecycle();
        final SlotSimStatus slotSimStatus = new SlotSimStatus(context, executor, lifecycleObject);

        controllers.add(new IpAddressPreferenceController(context, lifecycle));
        controllers.add(new WifiMacAddressPreferenceController(context, lifecycle));
        controllers.add(new BluetoothAddressPreferenceController(context, lifecycle));
        controllers.add(new RegulatoryInfoPreferenceController(context));
        controllers.add(new SafetyInfoPreferenceController(context));
        controllers.add(new ManualPreferenceController(context));
        controllers.add(new FeedbackPreferenceController(fragment, context));
        controllers.add(new FccEquipmentIdPreferenceController(context));
        controllers.add(new UptimePreferenceController(context, lifecycle));

        Consumer<String> imeiInfoList = imeiKey -> {
            if (Flags.catalystMyDeviceInfoPrefScreen()) {
                return;
            }
            ImeiInfoPreferenceController imeiRecord =
                    new ImeiInfoPreferenceController(context, imeiKey);
            imeiRecord.init(fragment, slotSimStatus);
            controllers.add(imeiRecord);
        };

        if (fragment != null) {
            imeiInfoList.accept(ImeiInfoPreferenceController.DEFAULT_KEY);
        }

        for (int slotIndex = 0; slotIndex < slotSimStatus.size(); slotIndex++) {
            SimStatusPreferenceController slotRecord =
                    new SimStatusPreferenceController(context,
                            slotSimStatus.getPreferenceKey(slotIndex));
            slotRecord.init(fragment, slotSimStatus);
            controllers.add(slotRecord);

            if (fragment != null) {
                imeiInfoList.accept(ImeiInfoPreferenceController.DEFAULT_KEY + (1 + slotIndex));
            }
        }

        if (!isCatalystEnabled) {
            EidStatus eidStatus = new EidStatus(slotSimStatus, context, executor);
            SimEidPreferenceController simEid = new SimEidPreferenceController(context,
                    KEY_EID_INFO);
            simEid.init(slotSimStatus, eidStatus);
            controllers.add(simEid);
        }

        if (executor instanceof ExecutorService) {
            ((ExecutorService) executor).shutdown();
        }
        return controllers;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mBuildNumberPreferenceController.onActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void initHeader() {
        // TODO: Migrate into its own controller.
        final LayoutPreference headerPreference =
                getPreferenceScreen().findPreference(KEY_MY_DEVICE_INFO_HEADER);
        final boolean shouldDisplayHeader = getContext().getResources().getBoolean(
                R.bool.config_show_device_header_in_device_info);
        headerPreference.setVisible(shouldDisplayHeader);
        if (!shouldDisplayHeader) {
            return;
        }
        final View headerView = headerPreference.findViewById(R.id.entity_header);
        final Activity context = getActivity();
        final Bundle bundle = getArguments();
        final EntityHeaderController controller = EntityHeaderController
                .newInstance(context, this, headerView)
                .setButtonActions(EntityHeaderController.ActionType.ACTION_NONE,
                        EntityHeaderController.ActionType.ACTION_NONE);

        // TODO: There may be an avatar setting action we can use here.
        final int iconId = bundle != null ? bundle.getInt("icon_id", 0) : 0;
        if (iconId == 0) {
            final UserManager userManager = (UserManager) getActivity().getSystemService(
                    Context.USER_SERVICE);
            final UserInfo info = Utils.getExistingUser(userManager,
                    android.os.Process.myUserHandle());
            controller.setLabel(info.name);
            controller.setIcon(
                    com.android.settingslib.Utils.getUserIcon(getActivity(), userManager, info));
        }

        controller.done(true /* rebindActions */);
    }

    @Override
    public void showDeviceNameWarningDialog(String deviceName) {
        DeviceNameWarningDialog.show(this);
    }

    public void onSetDeviceNameConfirm(boolean confirm) {
        final DeviceNamePreferenceController controller = use(
                DeviceNamePreferenceController.class);
        controller.updateDeviceName(confirm);
    }

    @Override
    public @Nullable String getPreferenceScreenBindingKey(@NonNull Context context) {
        return MyDeviceInfoScreen.KEY;
    }

    /**
     * For Search.
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.my_device_info) {

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(
                        Context context) {
                    return buildPreferenceControllers(context, null /* fragment */,
                            null /* lifecycle */);
                }
            };
}
// LINT.ThenChange(MyDeviceInfoScreen.kt, MyDeviceInfoApiFirstScreen.kt)
