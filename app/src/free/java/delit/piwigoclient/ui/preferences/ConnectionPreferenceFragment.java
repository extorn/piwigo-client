package delit.piwigoclient.ui.preferences;

import delit.piwigoclient.ui.common.FragmentUIHelper;

/**
 * Created by gareth on 12/05/17.
 */

public class ConnectionPreferenceFragment<F extends ConnectionPreferenceFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends BaseConnectionPreferenceFragment<F,FUIH> {

    public ConnectionPreferenceFragment(){}

    public ConnectionPreferenceFragment(int pagerIndex) {
        super(pagerIndex);
    }
}