package delit.piwigoclient.ui.preferences;

import delit.piwigoclient.ui.common.FragmentUIHelper;

public class GalleryPreferenceFragment<F extends BaseGalleryPreferenceFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends BaseGalleryPreferenceFragment<F,FUIH> {
    public GalleryPreferenceFragment() {
    }

    public GalleryPreferenceFragment(int pagerIndex) {
        super(pagerIndex);
    }

    @Override
    protected void buildPreferencesViewAndInitialise(String rootKey) {
        super.buildPreferencesViewAndInitialise(rootKey);
        // perform any custom code for paid only preferences
    }
}
