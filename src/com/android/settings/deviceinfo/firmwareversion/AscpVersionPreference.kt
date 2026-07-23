/*
 * Copyright (C) 2026 ASCP OS Project
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

package com.android.settings.deviceinfo.firmwareversion

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemProperties
import androidx.preference.Preference
import com.android.settings.R
import com.android.settings.utils.getLocale
import com.android.settingslib.DeviceInfoUtils
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.metadata.preferencesapi.preconditions.PreconditionStability
import com.android.settingslib.preference.PreferenceBinding

class AscpVersionPreference :
    PreferenceMetadata,
    PreferenceAvailabilityProvider,
    PreferenceSummaryProvider,
    PreferenceBinding {

    override val purpose: Int
        get() = R.string.ascp_version

    override val availabilityDescription: String
        get() = "Always available"

    override fun getAvailabilityStability(): PreconditionStability =
        PreconditionStability.STABLE_UNTIL_APK_UPDATE

    private val KEY_ASCP_RELEASE_VERSION = "ro.ascp.release.version"
    private val KEY_ASCP_VERSION = "ro.ascp.version"

    private var currentVersion: String? = null

    override val key: String
        get() = "ascp_version"

    override val title: Int
        get() = R.string.ascp_version

    override fun intent(context: Context): Intent? {
        val url = SystemProperties.get("ro.ascp.maintainer.link", "https://github.com/ASCP-staging")
        return Intent(Intent.ACTION_VIEW).setData(Uri.parse(url))
    }

    override fun isAvailable(context: Context) = context.getVersion().isNotEmpty()

    override fun getSummary(context: Context) = context.getVersion()

    private fun Context.getVersion(): String {
        val baseVersion = SystemProperties.get("ro.ascp.version.base", "6.0")
        val buildType = SystemProperties.get("ro.ascp.build.type", "official")
        val finalBase = if (baseVersion.isNotEmpty()) baseVersion else "6.0"
        val finalType = if (buildType.isNotEmpty()) buildType.lowercase() else "official"
        return "v$finalBase | $finalType"
    }

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
        preference.isCopyingEnabled = true
    }
}
