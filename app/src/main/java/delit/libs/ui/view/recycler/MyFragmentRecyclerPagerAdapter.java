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

package delit.libs.ui.view.recycler;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.crashlytics.android.Crashlytics;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import delit.libs.ui.util.BundleUtils;
import delit.libs.util.CollectionUtils;
import delit.piwigoclient.BuildConfig;

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
public abstract class MyFragmentRecyclerPagerAdapter<T extends Fragment & MyFragmentRecyclerPagerAdapter.PagerItemFragment, S extends ViewPager> extends PagerAdapter {
    private static final String TAG = "FrgmntStatePagerAdapter";
    private static final boolean DEBUG = false;
    private final Map<Integer, T> activeFragments = new HashMap<>(3);
    private FragmentManager mFragmentManager;
    private FragmentTransaction mCurTransaction = null;
    private Map<Integer, Fragment.SavedState> pageState;
    private int maxFragmentsToSaveInState = 3;
    private int visibleItemIdx = -1;
    private Fragment mCurrentPrimaryItem;
    private int lastPosition;
    private S container;
    private ViewPager.OnPageChangeListener pageListener = new CustomPageChangeListener(this);

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

    public Class<? extends T> getFragmentType(int position) {
        return null;
    }

    public Collection<T> getActiveFragments() {
        return activeFragments.values();
    }

    public T getActiveFragment(int position) {
        return activeFragments.get(position);
    }

    public void onPageSelected(int position) {
        T managedFragment = getActiveFragment(position);
        if (managedFragment != null) {
            // if this item still exists (not been deleted by user)
            managedFragment.onPageSelected();
        }
    }

    public void onPageDeselected(int position) {
        T managedFragment = getActiveFragment(position);
        if (managedFragment != null) {
            // if this slideshow item still exists (not been deleted by user)
            managedFragment.onPageDeselected();
        }
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {

        Class<? extends T> fragmentTypeNeeded = getFragmentType(position);

        if (activeFragments.size() == 0) {
            List<Fragment> fragments = mFragmentManager.getFragments();
            // if fragments is not empty then the page was very probably rotated
            for (Fragment f : fragments) {
                if (f instanceof PagerItemFragment) {
                    T pif = (T) f; // safe since f is already a fragment.
                    int pagerIndex = pif.getPagerIndex();
                    if (pagerIndex < 0) {
                        Crashlytics.log(Log.WARN, TAG, "Warning pager fragment found in fragment manager with index of " + pagerIndex + " while looking for fragment as position " + position);
                    }
                    if (pagerIndex >= 0) {
                        Fragment removed = activeFragments.put(pagerIndex, pif);
                        if (removed != null) {
                            throw new RuntimeException("Two fragments share the same pager index: " + pagerIndex);
                        }
                    } else {
                        Crashlytics.log(Log.WARN, TAG, "Warning pager fragment found in fragment manager with index of " + pagerIndex + " while looking for fragment as position " + position + ". Ignored.");
                    }
                }
            }
        }

        // check if fragment is already active. If so, do nothing
        T f = activeFragments.get(position);

        if (f == null) {
            f = createNewItem(fragmentTypeNeeded, position);
            if (f == null) {
                Log.e(TAG, "Fragment must implement PagerItemFragment");
            }

            addFragmentToTransaction(container, f, position);
        }

        if (position == ((ViewPager) container).getCurrentItem()) {
            if (lastPosition >= 0 && lastPosition != position) {
                onPageDeselected(lastPosition);
            }
            lastPosition = position;
        }

        return f;
    }

    protected void addFragmentToTransaction(ViewGroup container, T f, int position) {
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

    protected abstract T createNewItem(Class<? extends T> fragmentTypeNeeded, int position);


    protected T instantiateItem(Class<? extends T> fragmentTypeNeeded) {
        try {
            return fragmentTypeNeeded.newInstance();
        } catch (IllegalAccessException e) {
            Crashlytics.logException(e);
            throw new RuntimeException(e);
        } catch (java.lang.InstantiationException e) {
            Crashlytics.logException(e);
            throw new RuntimeException(e);
        }
    }

    public final void onDeleteItem(ViewGroup container, int position) {

        T fragment = getActiveFragment(position);
        fragment.onPageDeselected();
        onItemDeleted(fragment);

        destroyItem(container, position, fragment);
        // remove this item
        pageState.remove(position);

        // now shift the remaining active fragments by one position.
        int activeAdapterPosition = position;
        T f;
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

    protected void onItemDeleted(T fragment) {
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {

        T fragment = getActiveFragment(position);
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
        boolean trimmed = false;
        while(pageState.size() > maxFragmentsToSaveInState && iter.hasNext()) {
            int idx = iter.next();
            if(idx < minIdxToKeep || idx > maxIdxToKeep) {
                iter.remove();
                trimmed = true;
            }
        }
        if(BuildConfig.DEBUG) {
            if (trimmed) {
                Log.d(TAG, String.format("Page State Trimmed to those pages centered on %1$d, between %2$d - %3$d (%4$d items)", visibleItemIdx, minIdxToKeep, maxIdxToKeep, pageState.size()));
            } else {
                Log.d(TAG, String.format("Page State Not Trimmed contains %1$d items with max of %2$d items", pageState.size(), maxFragmentsToSaveInState));
            }
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
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, String.format("Building page state for fragment at position : %1$d", position));
                }
                pageState.put(position, mFragmentManager.saveFragmentInstanceState(fragment));
            } else {
                if(BuildConfig.DEBUG) {
                    Log.d(TAG, String.format("Removing page state for removed fragment : %1$d", position));
                }
                pageState.remove(position);
            }
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
            setPrimaryItemOnce(container, position, (T) fragment);
        }
    }

    public void setPrimaryItemOnce(@NonNull ViewGroup container, int position, @NonNull T primaryFragment) {

        T activeFragment = primaryFragment;
        if (activeFragment == null) {
            activeFragment = (T) instantiateItem(container, position);
        }
        activeFragment.onPageSelected();
    }

    @Override
    public void finishUpdate(@NonNull ViewGroup container) {
        try {
            commitFragmentTransaction();
        } catch (IllegalStateException e) {
            Crashlytics.log(Log.ERROR, TAG, "Unable to commit fragment transaction at this time - transaction will likely be lost.");
        }
    }

    private void commitFragmentTransaction() {
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

        commitFragmentTransaction();

        int minIdxToKeep = getMinIdxFragmentStateToKeep();
        int maxIdxToKeep = getMaxIdxFragmentStateToKeep();

        Bundle state = new Bundle();

        for (Map.Entry<Integer, T> activeFragmentEntry : activeFragments.entrySet()) {
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
            BundleUtils.logSize("Complete Slideshow (Adapter)", state);
        }

        return state;
    }

    @Override
    public void restoreState(Parcelable state, ClassLoader loader) {
        if (state != null) {
            Bundle bundle = (Bundle) state;
            bundle.setClassLoader(loader);

            pageState = BundleUtils.readMap(bundle, "pagesState", new HashMap<Integer, Fragment.SavedState>(), getClass().getClassLoader());

            visibleItemIdx = bundle.getInt("visibleItemIndex");
        }
    }

    public void destroy() {
        activeFragments.clear();
        // flush any cached state.
        pageState.clear();
    }

    public void onDataAppended(int firstPositionAddedAt, int itemsAddedCount) {
        throw new UnsupportedOperationException("please implement this if it is needed");
    }

    public interface PagerItemFragment {
        void onPageSelected();

        void onPageDeselected();

        int getPagerIndex();
    }

    public S getContainer() {
        return container;
    }

    public void setContainer(S container) {
        this.container = container;
        container.addOnPageChangeListener(pageListener);
    }

    private static class CustomPageChangeListener<T extends MyFragmentRecyclerPagerAdapter<?, ?>> implements ViewPager.OnPageChangeListener {

        private T parentAdapter;
        private ViewPager.OnPageChangeListener wrapped;
        private int lastPage = -1;

        public CustomPageChangeListener(T parentAdapter) {
            this.parentAdapter = parentAdapter;
        }

        public void setWrapped(ViewPager.OnPageChangeListener wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public final void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            if (lastPage < 0) {
                lastPage = position;
            }

            if (wrapped != null) {
                wrapped.onPageScrolled(position, positionOffset, positionOffsetPixels);
            }
        }

        @Override
        public final void onPageSelected(int position) {
            if (lastPage >= 0) {
                parentAdapter.onPageDeselected(lastPage);
            }
            parentAdapter.onPageSelected(position);
            lastPage = position;

            if (wrapped != null) {
                wrapped.onPageSelected(position);
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            if (wrapped != null) {
                wrapped.onPageScrollStateChanged(state);
            }
        }
    }


}
