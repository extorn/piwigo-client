package delit.piwigoclient.ui.preferences;

import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.FragmentUIHelper;

public class PreferencesFragment<F extends PreferencesFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends CommonPreferencesFragment<F,FUIH> {

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
