package delit.piwigoclient.ui.common;

import android.content.Context;
import androidx.annotation.RequiresApi;
import android.util.AttributeSet;
import android.widget.TabHost;

public class CustomTabHost extends TabHost {

    private int tabCount;
    private int maxChildHeight;

    public CustomTabHost(Context context) {
        super(context);
    }

    public CustomTabHost(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @RequiresApi(21)
    public CustomTabHost(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(21)
    public CustomTabHost(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void addTab(TabSpec tabSpec) {
        super.addTab(tabSpec);
//        int currentTab = getCurrentTab();
//        if(currentTab >= 0) {
//            int currentHeight = getTabContentView().getHeight();
//            setCurrentTab(tabCount);
//            View view = getCurrentView();
//            view.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
//            getTabContentView().getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
//            view.invalidate();
//            view.requestLayout();
//            getTabContentView().invalidate();
//            view.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
//            getTabContentView().measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
//            int newHeight = getTabContentView().getMeasuredHeight();
//            getTabContentView().setMinimumHeight(Math.max(currentHeight, newHeight));
//
//            int thisTabHeight = view.getMeasuredHeight();
////            maxChildHeight = Math.max(maxChildHeight, thisTabHeight);
//            view.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
//            setCurrentTab(currentTab);
//        }
        tabCount++;
    }

    @Override
    public void invalidate() {
        maxChildHeight = 0;
        super.invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if(maxChildHeight == 0) {
            int currentTab = getCurrentTab();
            for (int i = 0; i < tabCount; i++) {
                setCurrentTab(i);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                maxChildHeight = Math.max(maxChildHeight, getMeasuredHeight());
            }
            getLayoutParams().height = maxChildHeight;
            setCurrentTab(currentTab);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public int getMinimumHeight() {
        return Math.max(super.getMinimumHeight(), maxChildHeight);
    }

    @Override
    public void clearAllTabs() {
        super.clearAllTabs();
        tabCount=0;
    }
}
