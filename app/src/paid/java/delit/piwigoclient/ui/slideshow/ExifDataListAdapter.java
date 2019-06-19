package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import delit.piwigoclient.R;

public class ExifDataListAdapter extends SimpleExpandableListAdapter {

    private final List<? extends List<? extends Map<String, ?>>> childData;
    private final int[] childTo;
    private final String[] childFrom;

    public ExifDataListAdapter(Context context, List<? extends Map<String, ?>> groupData, String[] groupFrom, int[] groupTo, List<? extends List<? extends Map<String, ?>>> childData, String[] childFrom, int[] childTo) {
        super(context, groupData, R.layout.exif_list_group_layout, groupFrom, groupTo, childData, R.layout.exif_list_children_layout, childFrom, childTo);
        this.childData = childData;
        this.childFrom = childFrom;
        this.childTo = childTo;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return Math.min(1,super.getChildrenCount(groupPosition));
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                             View convertView, ViewGroup parent) {
        View v;
        if (convertView == null) {
            v = newChildView(isLastChild, parent);
        } else {
            v = convertView;
        }
        v.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        parent.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        List<? extends Map<String, ?>> children = childData.get(groupPosition);
        bindChildDataToView(parent.getContext(), (GridLayout) v.findViewById(R.id.childGrid), children);
        return v;
    }

    private void bindChildDataToView(Context context, GridLayout v, List<? extends Map<String, ?>> childItems) {
        v.removeAllViews();
        v.setColumnCount(childFrom.length);
        String[] children = flatten(childItems);
        for(int i = 0; i < children.length; i++) {
            View childItemView = inflateChildItemView(context, i, v);
            ((TextView) childItemView).setText(children[i]);
            v.addView(childItemView);
        }
    }

    private View inflateChildItemView(Context context, int position, ViewGroup parent) {
        switch(position % 2) {
            case 0:
                return LayoutInflater.from(context).inflate(R.layout.exif_list_child_heading_layout, parent, false);
            case 1:
                return LayoutInflater.from(context).inflate(R.layout.exif_list_child_value_layout, parent, false);
            default:
                throw new RuntimeException("Will never occur");
        }
    }

    private String[] flatten(List<? extends Map<String, ?>> childItems) {
        List<String> flatList = new ArrayList<>(childItems.size() * childFrom.length);
        for(Map<String, ?> childDetails : childItems) {
            for (int i = 0; i < childFrom.length; i++) {
                String value = (String) childDetails.get(childFrom[i]);
                flatList.add(value);
            }
        }
        return flatList.toArray(new String[0]);
    }

    public static ExifDataListAdapter newAdapter(Context c, Metadata metadata) {

        final String NAME = "NAME";
        final String VALUE = "VALUE";

        List<Map<String, String>> groupData = new ArrayList<>();
        List<List<Map<String, String>>> childData = new ArrayList<>();

        if(metadata != null) {

            for (Directory directory : metadata.getDirectories()) {

                List<Map<String, String>> children = new ArrayList<>();

                for (Tag tag : directory.getTags()) {
                    Map<String, String> curChildMap = new HashMap();
                    curChildMap.put(NAME, tag.getTagName());
                    curChildMap.put(VALUE, tag.getDescription());
                    children.add(curChildMap);
                }
                if (directory.hasErrors()) {
                    for (String error : directory.getErrors()) {
                        Map<String, String> curChildMap = new HashMap();
                        curChildMap.put(NAME, "ERROR");
                        curChildMap.put(VALUE, error);
                        children.add(curChildMap);
                    }
                }
                if(children.size() > 0) {
                    Map<String, String> curGroupMap = new HashMap<>();
                    groupData.add(curGroupMap);
                    curGroupMap.put(NAME, directory.getName());
                    childData.add(children);
                }
            }
        } else {
            Map<String, String> curChildMap = new HashMap();
            curChildMap.put(NAME, "Exif Data");
            curChildMap.put(VALUE, c.getString(R.string.picture_resource_exif_data_unavailable));

            List<Map<String, String>> children = new ArrayList<>();
            children.add(curChildMap);
            childData.add(children);
        }

        // define arrays for displaying data in Expandable list view
        String groupFrom[] = {NAME/*, VALUE*/};
        int groupTo[] = {R.id.lblListItemHeader/*, R.id.descListItemHeader*/};
        String childFrom[] = {NAME, VALUE};
        int childTo[] = {-1, -1};

        // Set up the adapter
        return new ExifDataListAdapter(c, groupData, groupFrom, groupTo, childData, childFrom, childTo);
    }
}
