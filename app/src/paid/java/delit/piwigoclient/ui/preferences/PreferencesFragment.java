package delit.piwigoclient.ui.preferences;

import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v7.preference.PreferenceFragmentCompat;

import delit.piwigoclient.R;

public class PreferencesFragment extends CommonPreferencesFragment {

    protected FragmentPagerAdapter buildPagerAdapter(FragmentManager childFragmentManager) {
        return new PaidPreferencesPagerAdapter(childFragmentManager);
    }

    protected class PaidPreferencesPagerAdapter extends CommonPreferencesPagerAdapter {

        public PaidPreferencesPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 4:
                    return getString(R.string.preference_page_auto_upload_jobs);
                default:
                    return super.getPageTitle(position);
            }
        }

        @Override
        public PreferenceFragmentCompat getItem(int position) {
            switch (position) {
                case 4:
                    return AutoUploadJobsPreferenceFragment.newInstance();
                default:
                    return super.getItem(position);
            }
        }

        @Override
        public int getCount() {
            return super.getCount() + 1;
        }
    }
}
