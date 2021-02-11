package delit.libs.ui.view.recycler;

import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import delit.libs.core.util.Logging;
import delit.libs.util.CollectionUtils;
import delit.libs.util.Utils;

public class SimpleFragmentPagerAdapter<T extends Fragment & MyFragmentRecyclerPagerAdapter.PagerItemView> extends MyFragmentRecyclerPagerAdapter<T, ViewPager> {

    private static final String TAG = "SimpleFragmentPagerAdapter";
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
            Logging.log(Log.DEBUG, TAG, "showing tab: " + getPageTitle(position) + "(" + Utils.getId(managedFragment) + ")");
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
            Logging.log(Log.ERROR, TAG, tabClass.getName());
            Logging.recordException(e);
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            Logging.log(Log.ERROR, TAG, tabClass.getName());
            Logging.recordException(e);
            throw new RuntimeException(e);
        } catch (java.lang.InstantiationException e) {
            Logging.log(Log.ERROR, TAG, tabClass.getName());
            Logging.recordException(e);
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            Logging.log(Log.ERROR, TAG, tabClass.getName());
            Logging.recordException(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getCount() {
        return tabClasses.size();
    }
}
