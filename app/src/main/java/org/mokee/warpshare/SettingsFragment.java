package org.mokee.warpshare;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreference;

@SuppressWarnings("SwitchStatementWithTooFewBranches")
public class SettingsFragment extends PreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private ConfigManager mConfigManager;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings);
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        mConfigManager = new ConfigManager(getContext());

        final SwitchPreference discoverablePref = (SwitchPreference) findPreference(ConfigManager.KEY_DISCOVERABLE);
        if (discoverablePref != null) {
            discoverablePref.setSummary(mConfigManager.isDiscoverable()
                    ? R.string.settings_discoverable_on
                    : R.string.settings_discoverable_off);
        }

        final EditTextPreference namePref = (EditTextPreference) findPreference(ConfigManager.KEY_NAME);
        if (namePref != null) {
            namePref.setText(mConfigManager.getNameWithoutDefault());
            namePref.setSummary(mConfigManager.getName());
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case ConfigManager.KEY_DISCOVERABLE:
                final SwitchPreference discoverablePref = (SwitchPreference) findPreference(ConfigManager.KEY_DISCOVERABLE);
                if (discoverablePref != null) {
                    discoverablePref.setSummary(mConfigManager.isDiscoverable()
                            ? R.string.settings_discoverable_on
                            : R.string.settings_discoverable_off);
                }
                ReceiverService.updateDiscoverability(getActivity());
                break;
            case ConfigManager.KEY_NAME:
                final EditTextPreference namePref = (EditTextPreference) findPreference(ConfigManager.KEY_NAME);
                if (namePref != null) {
                    namePref.setSummary(mConfigManager.getName());
                }
                break;
        }
    }

}
