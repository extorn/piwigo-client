package delit.piwigoclient.ui.upload.status;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.gridlayout.widget.GridLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.ProcessErrors;

class UploadJobErrorsListAdapter extends SimpleExpandableListAdapter {

    public UploadJobErrorsListAdapter(Context context, List<? extends Map<String, String>> groupData, String[] groupFrom, int[] groupTo, List<? extends List<? extends Map<String, ChildData>>> childrenDataByGroup, String[] childFrom, int[] childTo) {
        super(context, groupData, R.layout.layout_expandable_list_header_simple, groupFrom, groupTo, childrenDataByGroup, R.layout.layout_list_expandable_list_child_grid, childFrom, childTo);
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                             View convertView, ViewGroup parent) {
        GridLayout childView;
        if (convertView == null) {
            ChildData childModel = getChildData(groupPosition, childPosition);
            childView = (GridLayout) newChildView(isLastChild, parent);
            bindChildDataToView(parent.getContext(), childView, childModel);
            childView.setTag(getChildId(groupPosition, childPosition));
        } else {
            childView = (GridLayout)convertView;
            if(Objects.equals(childView.getTag(),getChildId(groupPosition, childPosition))) {
                // replace the data
                ChildData childModel = getChildData(groupPosition, childPosition);
                bindChildDataToView(parent.getContext(), childView, childModel);
                childView.setTag(getChildId(groupPosition, childPosition));
            }
        }
        return childView;
    }

    private ChildData getChildData(int groupPosition, int childPosition) {
        HashMap<String, ChildData> data = (HashMap<String, ChildData>) getChild(groupPosition, childPosition);
        return data.get(CHILD_DATA);
    }

    private void bindChildDataToView(Context context, GridLayout v, ChildData groupChild) {
        v.removeAllViews();
        v.setColumnCount(1); // error title above detail
        for(Map<String,String> childrenDataItem : groupChild.dataItems) {
            for(Map.Entry<String,String> childDataVal : childrenDataItem.entrySet()) {
                TextView childItemView = inflateChildComponentView(context, childDataVal.getKey(), v);
                childItemView.setText(childDataVal.getValue());
                v.addView(childItemView);
            }
        }
    }

    /**
     * Build part of a child - either a heading or a value - there are many of these in a single child.
     * @param context
     * @param parent
     * @return
     */
    private TextView inflateChildComponentView(@NonNull Context context, @NonNull String childDataType, ViewGroup parent) {
        TextView childView;
        switch(childDataType) {
            case ChildData.HEADING:
                childView = (TextView) LayoutInflater.from(context).inflate(R.layout.layout_list_expandable_list_child_heading, parent, false);
                break;
            case ChildData.VALUE:
                childView = (TextView) LayoutInflater.from(context).inflate(R.layout.layout_list_expandable_list_child_value, parent, false);
                break;
            default:
                throw new RuntimeException("Will never occur");
        }
        return childView;
    }
    static final String CHILD_DATA = "CHILD_DATA";

    private static class ChildData {
        private static final String HEADING = "HEADING";
        private static final String VALUE = "VALUE";
        private final List<Map<String, String>> dataItems = new ArrayList<>();

        public ChildData() {}

        private void addDataItem(int idx, String heading, String value) {
            HashMap<String,String> childDataItem = new HashMap<>();
            childDataItem.put(HEADING, heading);
            childDataItem.put(VALUE, value);
            dataItems.add(idx, childDataItem);
        }

        private void addDataItem(String heading, String value) {
            addDataItem(dataItems.size(), heading, value);
        }
    }

    public static UploadJobErrorsListAdapter newAdapter(Context context, LinkedHashMap<String, ProcessErrors> errors) {
        SimpleDateFormat piwigoDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        final String GROUP_NAME = "NAME";

        List<Map<String, String>> groupData = new ArrayList<>();
        List<List<Map<String, ChildData>>> allGroupsChildren = new ArrayList<>();

        List<Map<String, ChildData>> groupChildren;
        if(errors != null && !errors.isEmpty()) {

            for (Map.Entry<String, ProcessErrors> errorAreaErrorsMap : errors.entrySet()) {
                groupChildren = new ArrayList<>();
                ProcessErrors areaErrors = errorAreaErrorsMap.getValue();
                ChildData childData = new ChildData();
                for (Map.Entry<Date,String> areaError : areaErrors.getEntrySet()) {
                    childData.addDataItem(0, piwigoDateFormat.format(areaError.getKey()), areaError.getValue());
                }
                Map<String, ChildData> listChild = new HashMap<>(1);
                listChild.put(CHILD_DATA, childData);
                // add the child of this group (only ever one - that contains many items potentially)
                groupChildren.add(listChild);

                // if there are errors in this area (theoretically this should always be true)
                if(!groupChildren.isEmpty()) {
                    Map<String, String> curGroupMap = new HashMap<>();
                    curGroupMap.put(GROUP_NAME, errorAreaErrorsMap.getKey());
                    // add this group
                    groupData.add(curGroupMap);
                    // add this list of children for the group to the overall children of all groups mapping list
                    allGroupsChildren.add(groupChildren);
                }
            }
        } else {
            // compile a list of group - child values so something is shown to indicate there are no errors

            Map<String, String> curGroupMap = new HashMap<>();
            curGroupMap.put(GROUP_NAME, context.getString(R.string.upload));
            // add this group
            groupData.add(curGroupMap);
            ChildData child = new ChildData();
            child.addDataItem(context.getString(R.string.no_errors_found_title), context.getString(R.string.no_errors_found_text));
            Map<String, ChildData> listChild = new HashMap<>(1);
            listChild.put(CHILD_DATA, child);
            groupChildren = new ArrayList<>();
            groupChildren.add(listChild);
            allGroupsChildren.add(groupChildren);
        }

        // define view id for displaying keyed data in the Expandable list view
        String[] groupFrom = {GROUP_NAME};
        int[] groupTo = {R.id.lblListItemHeader};
        String[] childFrom = {CHILD_DATA};
        int[] childTo = {View.NO_ID}; // because we have more than a single text value per child we do the mapping ourselves

        // Set up the adapter
        return new UploadJobErrorsListAdapter(context, groupData, groupFrom, groupTo, allGroupsChildren, childFrom, childTo);
    }
}
