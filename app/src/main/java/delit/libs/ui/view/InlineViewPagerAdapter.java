package delit.libs.ui.view;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

public class InlineViewPagerAdapter extends PagerAdapter {
    private List<CharSequence> pagesTitles;
    private List<View> pagesContent;
    private int currentMaxChildHeight = 0;

    public InlineViewPagerAdapter(ViewPager viewPager) {
        initialiseFromView(viewPager);
        // force re-draw to get the child added.
        viewPager.invalidate();
    }

    private void initialiseFromView(ViewPager viewPager) {
        int childCount = viewPager.getChildCount();
        int offset = 0;
        if(childCount == 0) {
            throw new IllegalStateException("No children views found in ViewPager to make tabs and tab contents from");
        }
        if(childCount > 0 && viewPager.getChildAt(0) instanceof TabLayout) {
            offset = 1;
        }
        if((childCount - offset) % 2 != 0) {
            throw new RuntimeException("There must be an even number of child components to the view pager - title, content, title, content, etc.\nThe titles must be text views (from which text is extracted)");
        }
        int pages = (childCount - 1) / 2;
        pagesTitles = new ArrayList<>(pages);
        pagesContent = new ArrayList<>(pages);
        for(int i = offset; i < viewPager.getChildCount(); i++) {
            View child = viewPager.getChildAt(i);
            pagesTitles.add(((TextView)child).getText());
            viewPager.removeView(child);
            child = viewPager.getChildAt(i);
            child.setVisibility(View.GONE);
            pagesContent.add(child);
        }
    }

    @Override
    public int getCount() {
        return pagesContent.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return pagesTitles.get(position);
    }

    @Override
    public int getItemPosition(@NonNull Object object) {
        return pagesContent.indexOf(object);
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        View child = pagesContent.get(position);
        child.setVisibility(View.VISIBLE);
        return child;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        pagesContent.get(position).setVisibility(View.GONE);
    }

    @Nullable
    public <T extends View> T findViewById(int page, @IdRes int id) {
        return pagesContent.get(page).findViewById(id);
    }

    @Nullable
    public <T extends View> T findViewById(@IdRes int id) {
        for(View v : pagesContent) {
            View wantedView = v.findViewById(id);
            if(wantedView != null) {
                return (T)wantedView;
            }
        }
        return null;
    }

    @Override
    public void notifyDataSetChanged() {
        currentMaxChildHeight = 0;
        super.notifyDataSetChanged();
    }

    public int getLargestDesiredChildHeight() {
        if(currentMaxChildHeight == 0) {
            int maxHeight = 0;
            for(View v : pagesContent) {
                if(v.getLayoutParams().height != ViewGroup.LayoutParams.MATCH_PARENT) {
                    v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                    maxHeight = Math.max(maxHeight, v.getMeasuredHeight());
                }
            }
            currentMaxChildHeight = maxHeight;
        }
        return currentMaxChildHeight;
    }
}
