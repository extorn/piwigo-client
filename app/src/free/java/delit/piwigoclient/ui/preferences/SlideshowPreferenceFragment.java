package delit.piwigoclient.ui.preferences;

import delit.piwigoclient.ui.common.FragmentUIHelper;

public class SlideshowPreferenceFragment<F extends BaseSlideshowPreferenceFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends BaseSlideshowPreferenceFragment<F,FUIH> {
    public SlideshowPreferenceFragment() {
    }

    public SlideshowPreferenceFragment(int pagerIndex) {
        super(pagerIndex);
    }

    @Override
    protected void buildPreferencesViewAndInitialise(String rootKey) {
        super.buildPreferencesViewAndInitialise(rootKey);
        // perform any custom code for paid only preferences
    }
}
