/************************************************************
 * SettingsActivity for Request Music (tentative title)     *
 * Settings view for the Request Music app                  *
 * Allows entry of server IP address                        *
 * 															*
 * by Lawrence Bouzane (inexpensive on github)				*
 ************************************************************/

/**
 * Provides the classes necessary to create an Android client to communicate with the DJ Music Manager.
 */
package com.example.lawrence.requestmusic;


import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

/**
 * A SettingActivity that starts the settings view for the app to allow entry of a server IP address.
 */
public class SettingsActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    /**
     * Creates the Settings view.
     * @param savedInstanceState The saved instance state bundle.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //noinspection deprecation
        addPreferencesFromResource(R.xml.pref_general);

        //noinspection deprecation
        bindPreferenceSummaryToValue(findPreference(getString(R.string.server_address_key)));
    }

    /**
     * Sets the initial views of the preferences in the Activity.
     * @param preference The preference to update.
     */
    private void bindPreferenceSummaryToValue(Preference preference) {
        preference.setOnPreferenceChangeListener(this);

        onPreferenceChange(preference, PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(), ""));
    }


    /**
     * Changes the SharedPreferences when a preference value is changed
     * @param preference The changed preference,
     * @param value The new value.
     * @return true,
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        String stringValue = value.toString();

        if (preference instanceof ListPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list (since they have separate labels/values).
            ListPreference listPreference = (ListPreference) preference;
            int prefIndex = listPreference.findIndexOfValue(stringValue);
            if (prefIndex >= 0) {
                preference.setSummary(listPreference.getEntries()[prefIndex]);
            }
        } else {
            // For other preferences, set the summary to the value's simple string representation.
            preference.setSummary(stringValue);
        }
        return true;
    }
}