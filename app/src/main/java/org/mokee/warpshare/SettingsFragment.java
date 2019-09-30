/*
 * Copyright (C) 2019 The MoKee Open Source Project
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

package org.mokee.warpshare;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

@SuppressWarnings("SwitchStatementWithTooFewBranches")
public class SettingsFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private ConfigManager mConfigManager;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings);
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        mConfigManager = new ConfigManager(getContext());

        final SwitchPreference discoverablePref = findPreference(ConfigManager.KEY_DISCOVERABLE);
        if (discoverablePref != null) {
            discoverablePref.setSummary(mConfigManager.isDiscoverable()
                    ? R.string.settings_discoverable_on
                    : R.string.settings_discoverable_off);
        }

        final EditTextPreference namePref = findPreference(ConfigManager.KEY_NAME);
        if (namePref != null) {
            namePref.setText(mConfigManager.getNameWithoutDefault());
            namePref.setSummary(mConfigManager.getName());
            namePref.setOnBindEditTextListener(editText ->
                    editText.setHint(mConfigManager.getDefaultName()));
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case ConfigManager.KEY_DISCOVERABLE:
                final SwitchPreference discoverablePref = findPreference(ConfigManager.KEY_DISCOVERABLE);
                if (discoverablePref != null) {
                    discoverablePref.setSummary(mConfigManager.isDiscoverable()
                            ? R.string.settings_discoverable_on
                            : R.string.settings_discoverable_off);
                }
                ReceiverService.updateDiscoverability(getContext());
                break;
            case ConfigManager.KEY_NAME:
                final EditTextPreference namePref = findPreference(ConfigManager.KEY_NAME);
                if (namePref != null) {
                    namePref.setSummary(mConfigManager.getName());
                }
                break;
        }
    }

}
