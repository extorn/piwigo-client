package delit.libs.ui.view.list;

import android.content.Context;
import android.database.DataSetObserver;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.widget.TextViewCompat;

import java.util.ArrayList;

import delit.libs.R;
import delit.libs.ui.util.DisplayUtils;

public class CustomExpandableListView extends ExpandableListView {

    private ArrayList<Integer> expandedGroupsForDrawing;

    public CustomExpandableListView(Context context) {
        super(context);
        addDemoData();
    }

    public CustomExpandableListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        addDemoData();
    }

    public CustomExpandableListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        addDemoData();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public CustomExpandableListView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    private void addDemoData() {
        if(!isInEditMode()) {
            return;
        }
        setAdapter(new DemoExpandableListAdapter(15, 3));
        expandGroup(2);
        expandGroup(8);
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
        int currentHeight = getHeight();
        int heightMeasureSpec_custom = MeasureSpec.makeMeasureSpec(
                Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec_custom);
        ViewGroup.LayoutParams params = getLayoutParams();
        int newHeight = getMeasuredHeight();
        params.height = newHeight;
//        if(currentHeight != getHeight()) {
//            getParent().requestLayout();
//        }
//
//        if(/*getMinimumHeight() <= 0 &&*/ getExpandableListAdapter() != null && !getExpandableListAdapter().isEmpty()) {
//            //FIXME track expansion of groups in real time!
//            expandedGroupsForDrawing.clear();
//            for(int i = 0; i < getExpandableListAdapter().getGroupCount(); i++) {
//                if(isGroupExpanded(i)) {
//                    expandedGroupsForDrawing.add(i);
//                }
//            }
//            for(int i : expandedGroupsForDrawing) {
//                collapseGroup(i);
//            }
////            int desiredHeightMeasureMode = MeasureSpec.getMode(heightMeasureSpec);
////            String modeText = DisplayUtils.getMeasureModeText(desiredHeightMeasureMode);
//
//            int heightMeasureSpecOverride = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 4, MeasureSpec.AT_MOST);
//            super.onMeasure(widthMeasureSpec, heightMeasureSpecOverride);
//            int measuredHeight = getMeasuredHeight();
//            setMinimumHeight(measuredHeight);
//            for(int i : expandedGroupsForDrawing) {
//                expandGroup(i, false);
//            }
//            //now set the height to allow for all the open ones.
//            super.onMeasure(widthMeasureSpec, heightMeasureSpecOverride);
//        } else {
//            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//        }

    }

    private class DemoExpandableListAdapter implements ExpandableListAdapter {
        int groups;
        int children;

        public DemoExpandableListAdapter(int groups, int children) {
            this.groups = groups;
            this.children = children;
        }

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {

        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {

        }

        @Override
        public int getGroupCount() {
            return groups;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return children;
        }

        @Override
        public Object getGroup(int groupPosition) {
            return null;
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return null;
        }

        @Override
        public long getGroupId(int groupPosition) {
            return 0;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return 0;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            TextView v =  new TextView(getContext());
            v.setText("Example Group " + groupPosition);
            v.setPaddingRelative(20, 20, 20, 20);
            v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextViewCompat.setTextAppearance(v, DisplayUtils.getStyle(getContext(), R.attr.textAppearanceSubtitle2));
            return v;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            TextView v =  new TextView(getContext());
            v.setText("Example Child " + groupPosition + ":" + childPosition);
            v.setPaddingRelative(40, 20, 20, 20);
            v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextViewCompat.setTextAppearance(v, DisplayUtils.getStyle(getContext(), R.attr.textAppearanceBody1));
            return v;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void onGroupExpanded(int groupPosition) {

        }

        @Override
        public void onGroupCollapsed(int groupPosition) {

        }

        @Override
        public long getCombinedChildId(long groupId, long childId) {
            return 0;
        }

        @Override
        public long getCombinedGroupId(long groupId) {
            return 0;
        }
    }
}
