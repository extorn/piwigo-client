/*
 * Copyright (C) 2011 The Android Open Source Project
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

package delit.piwigoclient.ui.common.list.recycler;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.ui.common.util.BundleUtils;
import delit.piwigoclient.util.CollectionUtils;
import delit.piwigoclient.util.SetUtils;

/**
 * Implementation of {@link PagerAdapter} that
 * uses a {@link Fragment} to manage each page. This class also handles
 * saving and restoring of fragment's state.
 * <p>
 * This implementation is very similar to {@link FragmentPagerAdapter} except it caches and reuses the
 * fragments like a recycler view does. The advantage is better memory management.
 * <p>
 * The number of fragments whose state is maintained when the parent view is destroyed can be controlled
 * to prevent transaction size problems. By default this is limited to 3 fragments, though it can be increased.
 * <p>
 * Note that state for all fragments is kept while the page is still visible, it is only when it is not that the
 * state is trimmed.
 */
public abstract class MyFragmentRecyclerPagerAdapter extends PagerAdapter {
    private static final String TAG = "FrgmntStatePagerAdapter";
    private static final boolean DEBUG = false;
    private final Map<Integer, Fragment> activeFragments = new HashMap<>(3);
    private FragmentManager mFragmentManager;
    private FragmentTransaction mCurTransaction = null;
    private Map<Integer, Fragment.SavedState> pageState;
    private int maxFragmentsToSaveInState = 3;
    private int visibleItemIdx = -1;
    private Fragment mCurrentPrimaryItem;

    public MyFragmentRecyclerPagerAdapter(FragmentManager fm) {
        mFragmentManager = fm;
        pageState = new HashMap<>(maxFragmentsToSaveInState);
    }

    public void setMaxFragmentsToSaveInState(int maxFragmentsToSaveInState) {
        this.maxFragmentsToSaveInState = maxFragmentsToSaveInState;
    }

    public FragmentManager getFragmentManager() {
        return mFragmentManager;
    }

    @Override
    public void startUpdate(@NonNull ViewGroup container) {
        if (container.getId() == View.NO_ID) {
            throw new IllegalStateException("ViewPager with adapter " + this
                    + " requires a view id");
        }
    }

    public Class<? extends Fragment> getFragmentType(int position) {
        return null;
    }

    public Collection<Fragment> getActiveFragments() {
        return activeFragments.values();
    }

    public Fragment getActiveFragment(int position) {
        return activeFragments.get(position);
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {

        Class<? extends Fragment> fragmentTypeNeeded = getFragmentType(position);

        if (activeFragments.size() == 0) {
            List<Fragment> fragments = mFragmentManager.getFragments();
            // if fragments is not empty then the page was very probably rotated
            for (Fragment f : fragments) {
                activeFragments.put(((PagerItemFragment) f).getPagerIndex(), f);
            }
        }

        // check if fragment is already active. If so, do nothing
        Fragment f = activeFragments.get(position);
        if (f != null) {
            return f;
        }

        f = createNewItem(fragmentTypeNeeded, position);

        addFragmentToTransaction(container, f, position);

        return f;
    }

    protected void addFragmentToTransaction(ViewGroup container, Fragment f, int position) {
        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }

        if (pageState != null) {
            Fragment.SavedState fss = pageState.get(position);
            if (fss != null) {
                f.setInitialSavedState(fss);
            }
        }


        f.setMenuVisibility(false);
        f.setUserVisibleHint(false);

        activeFragments.put(position, f);
        mCurTransaction.add(container.getId(), f);
    }

    protected abstract Fragment createNewItem(Class<? extends Fragment> fragmentTypeNeeded, int position);

    public void onDeleteItem(ViewGroup container, int position) {

        destroyItem(container, position, getActiveFragment(position));
        // remove this item
        pageState.remove(position);

        // now shift the remaining active fragments by one position.
        int activeAdapterPosition = position;
        Fragment f;
        do {
            f = activeFragments.remove(activeAdapterPosition + 1);
            if(f != null) {
                activeFragments.put(activeAdapterPosition, f);
                // shift any page state too
                Fragment.SavedState thisFragmentPageState = pageState.remove(activeAdapterPosition + 1);
                if(thisFragmentPageState != null) {
                    pageState.put(activeAdapterPosition, thisFragmentPageState);
                }
            }
            activeAdapterPosition++;
        } while(f != null);

        if(getCount() > position && activeFragments.get(position) == null) {
            // reinstantiate this item (the old item has been deleted now so this gets the fresh one)
            instantiateItem(container, position);
        }

        clearPageState();
        notifyDataSetChanged();
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {

        Fragment fragment = getActiveFragment(position);
        if (fragment == null) {
            //nothing to do here, fragment no longer managed.
            return;
        }

        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }

        if (DEBUG) Log.v(TAG, "Removing item #" + position + ": f=" + object
                + " v=" + fragment.getView());

        recordPageState(fragment, position);
        tidyPageState();

        activeFragments.remove(position);

        mCurTransaction.remove(fragment);
    }

    protected void tidyPageState() {
        int minIdxToKeep = getMinIdxFragmentStateToKeep();
        int maxIdxToKeep = getMaxIdxFragmentStateToKeep();
        Iterator<Integer> iter = pageState.keySet().iterator();
        if(BuildConfig.DEBUG) {
            Log.d(TAG, String.format("Page State contents prior to trim : %1$s", CollectionUtils.toCsvList(pageState.keySet())));
        }
        while(pageState.size() > maxFragmentsToSaveInState && iter.hasNext()) {
            int idx = iter.next();
            if(idx < minIdxToKeep || idx > maxIdxToKeep) {
                iter.remove();
            }
        }
        if(BuildConfig.DEBUG) {
            Log.d(TAG, String.format("Page State Trimmed to those pages centered on %1$d, between %2$d - %3$d (%4$d items)", visibleItemIdx, minIdxToKeep, maxIdxToKeep, pageState.size()));
        }
    }

    protected void clearPageState() {
        pageState.clear();
    }

    @Override
    public void notifyDataSetChanged() {
        if(BuildConfig.DEBUG) {
            Log.d(TAG, "Page state cleared");
        }
        super.notifyDataSetChanged();
    }

    protected void recordPageState(Fragment fragment, int position) {
        if(position >= 0) {
            if (fragment.isAdded()) {
                pageState.put(position, mFragmentManager.saveFragmentInstanceState(fragment));
            } else {
                if(BuildConfig.DEBUG) {
                    Log.d(TAG, String.format("Removing page state for removed fragment : %1$d", position));
                }
                pageState.remove(position);
            }
        }

        if(BuildConfig.DEBUG) {
            Bundle b = new Bundle();
            BundleUtils.writeMap(b, "pagesState", pageState);
            BundleUtils.logSize("Slideshow items", b);
        }
    }

    @Override
    public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        Fragment fragment = (Fragment) object;
        if (fragment != mCurrentPrimaryItem) {
            if (mCurrentPrimaryItem != null) {
                mCurrentPrimaryItem.setMenuVisibility(false);
                mCurrentPrimaryItem.setUserVisibleHint(false);
            }
            fragment.setMenuVisibility(true);
            fragment.setUserVisibleHint(true);
            mCurrentPrimaryItem = fragment;
            visibleItemIdx = position;
        }
    }

    @Override
    public void finishUpdate(@NonNull ViewGroup container) {
        if (mCurTransaction != null) {
            mCurTransaction.commitNow();
            mCurTransaction = null;
        }
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return ((Fragment) object).getView() == view;
    }

    private int getMinIdxFragmentStateToKeep() {
        if (maxFragmentsToSaveInState == 0) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.round(visibleItemIdx - Math.floor(((double) maxFragmentsToSaveInState - 1) / 2));
    }

    private int getMaxIdxFragmentStateToKeep() {
        if (maxFragmentsToSaveInState == 0) {
            return Integer.MIN_VALUE;
        }
        return getMinIdxFragmentStateToKeep() + maxFragmentsToSaveInState;
    }

    @Override
    public Parcelable saveState() {

        int minIdxToKeep = getMinIdxFragmentStateToKeep();
        int maxIdxToKeep = getMaxIdxFragmentStateToKeep();

        Bundle state = new Bundle();

        for (Map.Entry<Integer, Fragment> activeFragmentEntry : activeFragments.entrySet()) {
            int key = activeFragmentEntry.getKey();
            if(pageState.size() < maxFragmentsToSaveInState || (key > minIdxToKeep && key < maxIdxToKeep)) {
                //TODO these fragments are active so this is probably doubling up state already stored.
                recordPageState(activeFragmentEntry.getValue(), activeFragmentEntry.getKey());
            }
        }

        // Save the state of those pages not currently active
        if (pageState.size() > 0) {
            tidyPageState();
            BundleUtils.writeMap(state, "pagesState", pageState);
        }

        state.putInt("visibleItemIndex", visibleItemIdx);

        if(BuildConfig.DEBUG) {
            BundleUtils.logSize("Slideshow", state);
        }

        return state;
    }

    @Override
    public void restoreState(Parcelable state, ClassLoader loader) {
        if (state != null) {
            Bundle bundle = (Bundle) state;
            bundle.setClassLoader(loader);

            pageState = BundleUtils.readMap(bundle, "pagesState", getClass().getClassLoader());
            if (pageState == null) {
                pageState = new HashMap<>();
            }

            visibleItemIdx = bundle.getInt("visibleItemIndex");
        }
    }

    public void destroy() {
        activeFragments.clear();
        // flush any cached state.
        pageState.clear();
    }

    public void onDataAppended(int itemsAddedCount) {
        throw new UnsupportedOperationException("please implement this if it is needed");
    }

    public interface PagerItemFragment {
        void onPageSelected();

        void onPageDeselected();

        int getPagerIndex();
    }
}
