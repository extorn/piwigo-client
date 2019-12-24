package delit.libs.ui.view.recycler;

import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import com.crashlytics.android.Crashlytics;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import delit.libs.util.CollectionUtils;

public class SimpleFragmentPagerAdapter<T extends Fragment & MyFragmentRecyclerPagerAdapter.PagerItemFragment> extends MyFragmentRecyclerPagerAdapter<T, ViewPager> {

    private final static String TAG = "SimpleFragmentPagerAdapter";
    private final ArrayList<String> tabTitles;
    private final ArrayList<Class<? extends T>> tabClasses;

    public SimpleFragmentPagerAdapter(FragmentManager fm, List<String> tabTitles, List<Class<? extends T>> tabClasses) {
        super(fm);
        this.tabTitles = new ArrayList<>(tabTitles);
        this.tabClasses = new ArrayList<>(tabClasses);
        if (tabTitles.size() != tabClasses.size()) {
            throw new IllegalArgumentException("Tab titles and tab classes arrays must be same length");
        }
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return tabTitles.get(position);
    }

    @Override
    public void onPageSelected(int position) {
        T managedFragment = getActiveFragment(position);
        if (managedFragment != null) {
            // if this item still exists (not been deleted by user)
            Crashlytics.log(Log.DEBUG, TAG, "showing tab: " + getPageTitle(position) + "(" + managedFragment.getClass().getName() + ")");
        }
        super.onPageSelected(position);
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        try {
            return super.instantiateItem(container, position);
        } catch (RuntimeException e) {
            throw new RuntimeException("Error in pager with tab titles (" + CollectionUtils.toCsvList(tabTitles) + ")", e);
        }
    }

    @Override
    protected T createNewItem(Class<? extends T> fragmentTypeNeeded, int position) {
        Class<? extends T> tabClass = tabClasses.get(position);
        try {
            Constructor<? extends T> constructor = tabClass.getConstructor(int.class);
            return constructor.newInstance(position);

        } catch (NoSuchMethodException e) {
            Crashlytics.log(Log.ERROR, TAG, "Unable to find pager item constructor for fragment class : " + tabClass == null ? null : tabClass.getName());
            Crashlytics.logException(e);
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            Crashlytics.log(Log.ERROR, TAG, "Unable to find pager item constructor for fragment class : " + tabClass == null ? null : tabClass.getName());
            Crashlytics.logException(e);
            throw new RuntimeException(e);
        } catch (java.lang.InstantiationException e) {
            Crashlytics.log(Log.ERROR, TAG, "Unable to find pager item constructor for fragment class : " + tabClass == null ? null : tabClass.getName());
            Crashlytics.logException(e);
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            Crashlytics.log(Log.ERROR, TAG, "Unable to find pager item constructor for fragment class : " + tabClass == null ? null : tabClass.getName());
            Crashlytics.logException(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getCount() {
        return tabClasses.size();
    }
}
