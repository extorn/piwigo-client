package delit.libs.ui.view.list;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;

public class CustomExpandableListView extends ExpandableListView {

    private ArrayList<Integer> expandedGroupsForDrawing;

    public CustomExpandableListView(Context context) {
        super(context);
    }

    public CustomExpandableListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomExpandableListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public CustomExpandableListView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void setAdapter(ExpandableListAdapter adapter) {
        super.setAdapter(adapter);
        if(adapter != null) {
            if(expandedGroupsForDrawing == null) {
                expandedGroupsForDrawing = new ArrayList<>(adapter.getGroupCount());
            } else {
                expandedGroupsForDrawing.ensureCapacity(adapter.getGroupCount());
                expandedGroupsForDrawing.clear();
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if(getMinimumHeight() <= 0 && getExpandableListAdapter() != null) {
            //FIXME track expansion of groups in real time!
            expandedGroupsForDrawing.clear();
            for(int i = 0; i < getExpandableListAdapter().getGroupCount(); i++) {
                if(isGroupExpanded(i)) {
                    expandedGroupsForDrawing.add(i);
                }
            }
            for(int i : expandedGroupsForDrawing) {
                collapseGroup(i);
            }
            int heightMeasureSpecOverride = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE / 2, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, heightMeasureSpecOverride);
            int measuredHeight = getMeasuredHeight();
            setMinimumHeight(measuredHeight);
            for(int i : expandedGroupsForDrawing) {
                expandGroup(i, false);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
