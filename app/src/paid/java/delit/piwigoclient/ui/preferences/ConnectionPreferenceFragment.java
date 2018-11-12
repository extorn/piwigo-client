package delit.piwigoclient.ui.preferences;

import delit.piwigoclient.R;

/**
 * Created by gareth on 12/05/17.
 */

public class ConnectionPreferenceFragment extends BaseConnectionPreferenceFragment {
    @Override
    protected void buildPreferencesViewAndInitialise(String rootKey) {
        super.buildPreferencesViewAndInitialise(rootKey);
        findPreference(R.string.preference_server_alter_cache_directives_key).setOnPreferenceChangeListener(sessionInvalidationPrefListener);
    }
}