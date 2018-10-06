package delit.piwigoclient.ui.common;

import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import delit.piwigoclient.R;

public class InlineViewPagerAdapter extends PagerAdapter {

    private List<CharSequence> pagesTitle;
    private List<View> pagesContent;

    public InlineViewPagerAdapter(ViewPager viewPager) {
        if(viewPager.getChildCount() % 2 != 0) {
            throw new RuntimeException("There must be an even number of child components to the view pager - title, content, title, content, etc.\nThe titles must be text views (from which text is extracted)");
        }
        int pages = viewPager.getChildCount() / 2;
        pagesTitle = new ArrayList<>(pages);
        pagesContent = new ArrayList<>(pages);
        for(int i = 0; i < viewPager.getChildCount(); i+=2) {
            pagesTitle.add(((TextView)viewPager.getChildAt(i)).getText());
            pagesContent.add(viewPager.getChildAt(i+1));
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
        return pagesTitle.get(position);
    }

    @Override
    public int getItemPosition(@NonNull Object object) {
        return pagesContent.indexOf(object);
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        return pagesContent.get(position);
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        container.removeView(pagesContent.get(position));
        pagesContent.remove(position);
        pagesTitle.remove(position);
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

    public int getLargestDesiredChildHeight() {
        int maxHeight = 0;
        for(View v : pagesContent) {
            v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            maxHeight = Math.max(maxHeight, v.getMeasuredHeight());
        }
        return maxHeight;
    }
}
