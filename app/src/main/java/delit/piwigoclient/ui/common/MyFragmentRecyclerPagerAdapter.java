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

package delit.piwigoclient.ui.common;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.PagerAdapter;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/**
 * Implementation of {@link PagerAdapter} that
 * uses a {@link Fragment} to manage each page. This class also handles
 * saving and restoring of fragment's state.
 *
 * This implementation is very similar to {@link FragmentPagerAdapter} except it caches and reuses the
 * fragments like a recycler view does. The advantage is better memory management.
 *
 * The number of fragments whose state is maintained when the parent view is destroyed can be controlled
 * to prevent transaction size problems. By default this is limited to 3 fragments, though it can be increased.
 *
 * Note that state for all fragments is kept while the page is still visible, it is only when it is not that the
 * state is trimmed.
 */
public abstract class MyFragmentRecyclerPagerAdapter extends PagerAdapter {
    private static final String TAG = "FrgmntStatePagerAdapter";
    private static final boolean DEBUG = false;

    private FragmentManager mFragmentManager;
    private FragmentTransaction mCurTransaction = null;

    private ArrayList<Fragment.SavedState> pageState;
    private final Map<Class,Queue<Fragment>> availableFragmentPool = new HashMap<>();
    private final Map<Integer,Fragment> activeFragments = new HashMap<>(3);
    private int maxFragmentsToSaveInState = 3;
    private int visibleItemIdx = -1;
    private Fragment mCurrentPrimaryItem;

    public MyFragmentRecyclerPagerAdapter(FragmentManager fm) {
        mFragmentManager = fm;
    }

    public void setFragmentManager(FragmentManager fragmentManager) {
        this.mFragmentManager = fragmentManager;
    }

    public void setMaxFragmentsToSaveInState(int maxFragmentsToSaveInState) {
        this.maxFragmentsToSaveInState = maxFragmentsToSaveInState;
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

    public void returnFragmentToPool(Fragment f, int position) {

        activeFragments.remove(position);

        if(availableFragmentPool.size() == 0) {
            // not using pooling
            return;
        }
        availableFragmentPool.get(f.getClass()).add(f);
    }

    public Fragment getNextAvailableFragmentFromPool(Class<? extends Fragment> fragmentType) {
        if(fragmentType == null) {
            // not using fragment pooling
            return null;
        }
        Queue<Fragment> fragmentPool = availableFragmentPool.get(fragmentType);
        if(fragmentPool == null) {
            fragmentPool = new ArrayDeque<>(3);
            availableFragmentPool.put(fragmentType, fragmentPool);
            return null;
        }
        if(fragmentPool.size() == 0) {
            return null;
        }
        Fragment f = fragmentPool.poll();
        if(!f.isAdded()) {
            return f;
        }
        // still being removed - cannot add to page again yet
        Fragment f2 = fragmentPool.poll();
        fragmentPool.add(f);
        return f2;
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

        // check if fragment is already active. If so, do nothing
        Fragment f = activeFragments.get(position);
        if(f != null) {
            return f;
        }

        f = getNextAvailableFragmentFromPool(fragmentTypeNeeded);

        if(f == null) {
            f = createNewItem(fragmentTypeNeeded, position);
        } else {
            bindDataToFragment(f, position);
        }

        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }

        if (pageState != null && pageState.size() > position) {
            Fragment.SavedState fss = pageState.get(position);
            if (fss != null) {
                f.setInitialSavedState(fss);
            }
        }


        f.setMenuVisibility(false);
        f.setUserVisibleHint(false);

        activeFragments.put(position, f);

        mCurTransaction.add(container.getId(), f);

        return f;
    }

    protected void bindDataToFragment(Fragment f, int position) {
        throw new UnsupportedOperationException("If fragment pooling is enabled, this method is used to initialise a fragment on first view");
    }

    protected abstract Fragment createNewItem(Class<? extends Fragment> fragmentTypeNeeded, int position);

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {

        Fragment fragment = getActiveFragment(position);
        if(fragment == null) {
            //nothing to do here, fragment no longer managed.
            return;
        }

        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }

        if (DEBUG) Log.v(TAG, "Removing item #" + position + ": f=" + object
                + " v=" + fragment.getView());

        recordPageState(fragment, position);

        returnFragmentToPool(fragment, position);

        mCurTransaction.remove(fragment);
    }

    @Override
    public void notifyDataSetChanged() {
        if(pageState == null) {
            pageState = new ArrayList<>(getCount());
        } else {
            pageState.ensureCapacity(getCount());
        }
        super.notifyDataSetChanged();
    }

    protected void recordPageState(Fragment fragment, int position) {
        if(pageState == null) {
            pageState = new ArrayList<>(getCount());
        }
        while (pageState.size() <= position) {
            pageState.add(null);
        }

        if(fragment.isAdded() && position >= 0) {
            pageState.set(position, mFragmentManager.saveFragmentInstanceState(fragment));
        } else {
            pageState.set(position, null);
        }
    }

    @Override
    public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        Fragment fragment = (Fragment)object;
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
            mCurTransaction.commitNowAllowingStateLoss();
            mCurTransaction = null;
        }
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return ((Fragment)object).getView() == view;
    }

    private int getMinIdxFragmentStateToKeep() {
        if(maxFragmentsToSaveInState == 0) {
            return Integer.MAX_VALUE;
        }
        return (int)Math.round(visibleItemIdx - Math.floor(((double)maxFragmentsToSaveInState-1) / 2));
    }

    private int getMaxIdxFragmentStateToKeep() {
        if(maxFragmentsToSaveInState == 0) {
            return Integer.MIN_VALUE;
        }
        return getMinIdxFragmentStateToKeep() + maxFragmentsToSaveInState;
    }

    @Override
    public Parcelable saveState() {

        int minIdxToKeep = getMinIdxFragmentStateToKeep();
        int maxIdxToKeep = getMaxIdxFragmentStateToKeep();

        Bundle state = new Bundle();

        int windowStart = visibleItemIdx - 1;
        for(Map.Entry<Integer, Fragment> activeFragmentEntry : activeFragments.entrySet()) {
            recordPageState(activeFragmentEntry.getValue(), activeFragmentEntry.getKey());
        }

        Fragment.SavedState[] fss;
        // Save the state of those pages not currently active
        if (pageState.size() > 0) {
            fss = new Fragment.SavedState[pageState.size()];
            fss = pageState.toArray(fss);

            // remove the state that has been recorded for fragments we don't want to keep
            for (int i = 0; i < fss.length; i++) {
                if (i < minIdxToKeep || i > maxIdxToKeep) {
                    fss[i] = null;
                }
            }
            state.putParcelableArray("pagesState", fss);
        }
        state.putInt("visibleItemIndex", visibleItemIdx);
        return state;
    }

    @Override
    public void restoreState(Parcelable state, ClassLoader loader) {
        if (state != null) {
            Bundle bundle = (Bundle)state;
            bundle.setClassLoader(loader);
            Parcelable[] fss = bundle.getParcelableArray("pagesState");

            if(pageState == null) {
                pageState = new ArrayList<>(fss != null ? fss.length : 0);
            } else {
                pageState.clear();
            }
            if (fss != null) {
                for (Parcelable fs : fss) {
                    pageState.add((Fragment.SavedState) fs);
                }
            }
            visibleItemIdx = bundle.getInt("visibleItemIndex");
        }
    }

    public void destroy() {
        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }

        for(Fragment cachedFragment : activeFragments.values()) {
            if(cachedFragment.isAdded()) {
                mCurTransaction.remove(cachedFragment);
            }
        }
        mCurTransaction.commitAllowingStateLoss();
        activeFragments.clear();
        // flush any cached state.
        pageState.clear();
    }
}
