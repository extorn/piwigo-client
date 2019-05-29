package delit.piwigoclient.ui.preferences;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.list.recycler.MyFragmentRecyclerPagerAdapter;

public class PreferencesFragment extends CommonPreferencesFragment {

    protected MyFragmentRecyclerPagerAdapter buildPagerAdapter(FragmentManager childFragmentManager) {
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
        protected Fragment createNewItem(Class<? extends Fragment> fragmentTypeNeeded, int position) {
            switch (position) {
                case 4:
                    return AutoUploadJobsPreferenceFragment.newInstance();
                default:
                    return super.createNewItem(fragmentTypeNeeded, position);
            }
        }

        @Override
        public int getCount() {
            return super.getCount() + 1;
        }
    }
}
