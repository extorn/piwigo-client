/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import delit.libs.ui.view.recycler.MyFragmentRecyclerPagerAdapter;
import delit.libs.ui.view.recycler.SimpleFragmentPagerAdapter;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.AppLockedEvent;

/**
 */
public class CommonPreferencesFragment extends MyFragment<CommonPreferencesFragment> {

    static final String LOG_TAG = "PreferencesFragment";

    public CommonPreferencesFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDetach() {
        EventBus.getDefault().unregister(this);
        super.onDetach();
    }

    /**
     * Inflates the {@link View} which will be displayed by this {@link Fragment}, from the app's
     * resources.
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        View view = inflater.inflate(R.layout.fragment_preferences_view_all, container, false);

        AdView adView = view.findViewById(R.id.prefs_adView);
        if (AdsManager.getInstance().shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        // Get the ViewPager and set it's PagerAdapter so that it can display items
        /*
      A {@link ViewPager} which will be used in conjunction with the {@link CustomMaterialTabLayout} above.
     */
        ViewPager mViewPager = view.findViewById(R.id.viewpager);
        mViewPager.setAdapter(buildPagerAdapter(getChildFragmentManager()));

        // Give the CustomMaterialTabLayout the ViewPager, this must be done AFTER the ViewPager has had
        // it's PagerAdapter set.
        /*
      A custom {@link ViewPager} title strip which looks much like Tabs present in Android v4.0 and
      above, but is designed to give continuous feedback to the user when scrolling.
     */
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (PiwigoSessionDetails.isLoggedIn(ConnectionPreferences.getActiveProfile()) && isAppInReadOnlyMode()) {
            // immediately leave this screen.
            getParentFragmentManager().popBackStack();
        }
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.preferences_overall_heading);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onAppLockedEvent(AppLockedEvent event) {
        if (isVisible()) {
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    protected List<String> getTabTitles() {
        ArrayList<String> tabTitles = new ArrayList<>();
        tabTitles.add(getString(R.string.preference_page_connection));
        tabTitles.add(getString(R.string.preference_page_gallery));
        tabTitles.add(getString(R.string.preference_page_app));
        tabTitles.add(getString(R.string.preference_page_other));
        tabTitles.add(getString(R.string.preference_page_upload));
        return tabTitles;
    }

    protected List<Class<? extends MyPreferenceFragment>> getTabFragmentClasses() {
        ArrayList<Class<? extends MyPreferenceFragment>> tabClasses = new ArrayList<>();
        tabClasses.add(ConnectionPreferenceFragment.class);
        tabClasses.add(GalleryPreferenceFragment.class);
        tabClasses.add(AppPreferenceFragment.class);
        tabClasses.add(OtherPreferenceFragment.class);
        tabClasses.add(UploadPreferenceFragment.class);
        return tabClasses;
    }

    protected MyFragmentRecyclerPagerAdapter buildPagerAdapter(FragmentManager childFragmentManager) {
        return new SimpleFragmentPagerAdapter(childFragmentManager, getTabTitles(), getTabFragmentClasses());
    }
}