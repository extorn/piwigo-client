package delit.piwigoclient.ui.preferences;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.events.ConnectionsPreferencesChangedEvent;

/**
 * Created by gareth on 12/05/17.
 */

public class ConnectionPreferenceFragment<F extends ConnectionPreferenceFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends BaseConnectionPreferenceFragment<F,FUIH> {

    public ConnectionPreferenceFragment() {
    }

    public ConnectionPreferenceFragment(int pagerIndex) {
        super(pagerIndex);
    }

    @Override
    protected void buildPreferencesViewAndInitialise(String rootKey) {
        super.buildPreferencesViewAndInitialise(rootKey);
        findPreference(R.string.preference_server_alter_cache_directives_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ConnectionsPreferencesChangedEvent event) {
        refreshSession(ConnectionPreferences.getActiveConnectionProfileKey(getPrefs(), requireContext()));
    }
}