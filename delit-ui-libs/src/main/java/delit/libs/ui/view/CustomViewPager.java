package delit.libs.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
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
        //TODO NOTE I've just tried moving this code to onFinishInflate
        /*if(getAdapter() == null) {
            setAdapter(new InlineViewPagerAdapter(this));
        }*/
    }

    @Override
    protected void onFinishInflate() {
        if(getChildCount() > 1) {
            InlineViewPagerAdapter pagerAdapter = new InlineViewPagerAdapter(this);
//            pagerAdapter.registerDataSetObserver(this);
            setAdapter(pagerAdapter);
        }
        super.onFinishInflate();

    }

    @Override
    public void setAdapter(@Nullable PagerAdapter adapter) {
        super.setAdapter(adapter);
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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if(getLayoutParams().height == LayoutParams.WRAP_CONTENT && getMeasuredHeight() != 0) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getMinimumDesiredHeight(), MeasureSpec.EXACTLY));
        } else if(MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY && getMeasuredHeight() == 0
        || MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY && getMeasuredWidth() == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    public int getMinimumDesiredHeight() {
        int height = 0;
        if(getChildCount() > 0 && getChildAt(0) instanceof TabLayout) {
            height += getChildAt(0).getMeasuredHeight();
        }
        InlineViewPagerAdapter viewPagerAdapter = (InlineViewPagerAdapter)getAdapter();
        if(viewPagerAdapter != null) {
            height  += viewPagerAdapter.getLargestDesiredChildHeight();
        }
        return height;
    }

}
