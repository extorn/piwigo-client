package delit.piwigoclient.ui.preferences;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

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
        public Fragment getItem(int position) {
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
