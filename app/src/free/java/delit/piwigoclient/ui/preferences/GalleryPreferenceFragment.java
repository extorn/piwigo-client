package delit.piwigoclient.ui.preferences;

public class GalleryPreferenceFragment extends BaseGalleryPreferenceFragment {
    public GalleryPreferenceFragment() {
    }

    public GalleryPreferenceFragment(int pagerIndex) {
        super(pagerIndex);
    }

    @Override
    protected void buildPreferencesViewAndInitialise(String rootKey) {
        super.buildPreferencesViewAndInitialise(rootKey);
        // perform any custom code for free only preferences
    }
}
