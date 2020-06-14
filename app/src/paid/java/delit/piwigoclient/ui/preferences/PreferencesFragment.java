package delit.piwigoclient.ui.preferences;

import java.util.List;

import delit.piwigoclient.R;

public class PreferencesFragment extends CommonPreferencesFragment {

    @Override
    protected List<String> getTabTitles() {
        List<String> tabTitles = super.getTabTitles();
        tabTitles.add(getString(R.string.preference_page_auto_upload_jobs));
        return tabTitles;
    }

    @Override
    protected List<Class<? extends MyPreferenceFragment>> getTabFragmentClasses() {
        List<Class<? extends MyPreferenceFragment>> tabClasses = super.getTabFragmentClasses();
        tabClasses.add(AutoUploadJobsPreferenceFragment.class);
        return tabClasses;
    }
}
