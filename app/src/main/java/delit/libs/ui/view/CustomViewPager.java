package delit.libs.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewParent;

import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

/**
 * Created by gareth on 06/06/17.
 */

public class CustomViewPager extends ViewPager {

    public CustomViewPager(Context context) {
        super(context);
    }

    public CustomViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if(getAdapter() == null) {
            setAdapter(new InlineViewPagerAdapter(this));
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return isEnabled() && super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return isEnabled() && super.onTouchEvent(ev);
    }

    public int getMinimumDesiredHeight() {
        int height = 0;
        if(getChildCount() > 0 && getChildAt(0) instanceof TabLayout) {
            height += getChildAt(0).getHeight();
        }
        InlineViewPagerAdapter viewPagerAdapter = (InlineViewPagerAdapter)getAdapter();
        if(viewPagerAdapter != null) {
            height  += viewPagerAdapter.getLargestDesiredChildHeight();
        }
        return height;
    }
}
