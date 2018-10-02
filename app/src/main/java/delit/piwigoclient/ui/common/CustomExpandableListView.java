package delit.piwigoclient.ui.common;

import android.content.Context;
import android.icu.util.Measure;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.widget.ExpandableListView;

import java.util.ArrayList;
import java.util.List;

public class CustomExpandableListView extends ExpandableListView {

    public CustomExpandableListView(Context context) {
        super(context);
    }

    public CustomExpandableListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomExpandableListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(21)
    public CustomExpandableListView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if(getMinimumHeight() <= 0 && getExpandableListAdapter() != null) {
            List<Integer> expandedGroups = new ArrayList<>(getExpandableListAdapter().getGroupCount());
            for(int i = 0; i < getExpandableListAdapter().getGroupCount(); i++) {
                if(isGroupExpanded(i)) {
                    expandedGroups.add(i);
                }
            }
            for(int i : expandedGroups) {
                collapseGroup(i);
            }
            int heightMeasureSpecOverride = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE / 2, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, heightMeasureSpecOverride);
            int measuredHeight = getMeasuredHeight();
            setMinimumHeight(measuredHeight);
            for(int i : expandedGroups) {
                expandGroup(i, false);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
