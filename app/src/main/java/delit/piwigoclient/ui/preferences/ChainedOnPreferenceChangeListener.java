package delit.piwigoclient.ui.preferences;

import android.preference.Preference;

/**
 * Created by gareth on 25/01/18.
 */

public class ChainedOnPreferenceChangeListener implements Preference.OnPreferenceChangeListener {

    final Preference.OnPreferenceChangeListener chainedListener;

    public ChainedOnPreferenceChangeListener(Preference.OnPreferenceChangeListener chainedListener) {
        this.chainedListener = chainedListener;
    }

    public boolean onBeforeChainedPreferenceListenerCalled(Preference preference, Object newValue) {
        return true;
    }

    public boolean onAfterChainedPreferenceListenerCalled(Preference preference, Object newValue) {
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean retVal = onBeforeChainedPreferenceListenerCalled(preference, newValue);
        retVal = retVal && chainedListener.onPreferenceChange(preference, newValue);
        retVal = retVal && onAfterChainedPreferenceListenerCalled(preference, newValue);
        return retVal;
    }
}
