package org.mokee.warpshare;

import android.os.Bundle;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.mokee.warpshare.airdrop.AirDropManager;

public class SettingsFragment extends PreferenceFragmentCompat {

    private AirDropManager mAirDropManager;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings);

        mAirDropManager = new AirDropManager(getContext());

        final EditTextPreference namePref = findPreference("name");
        if (namePref != null) {
            namePref.setText(mAirDropManager.getConfig().getNameWithoutDefault());
            namePref.setSummary(mAirDropManager.getConfig().getName());
            namePref.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
                @Override
                public void onBindEditText(@NonNull EditText editText) {
                    editText.setHint(mAirDropManager.getConfig().getDefaultName());
                }
            });
            namePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final String name = (String) newValue;
                    mAirDropManager.getConfig().setName(name);
                    preference.setSummary(mAirDropManager.getConfig().getName());
                    return true;
                }
            });
        }
    }

}
