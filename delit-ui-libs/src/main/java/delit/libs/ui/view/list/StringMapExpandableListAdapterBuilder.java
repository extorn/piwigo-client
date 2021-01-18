package delit.libs.ui.view.list;

import android.content.Context;
import android.widget.SimpleExpandableListAdapter;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import delit.libs.R;

public class StringMapExpandableListAdapterBuilder extends ExpandableListAdapterBuilder<Map<String,String>, String, String> {

    private Iterator<String> parentIter;

    public SimpleExpandableListAdapter build(Context context, Map<String, String> data) {
        return super.build(context, data, new StringViewBinding(), new StringViewBinding(R.layout.layout_list_item_simple_padded, android.R.id.text1));
    }

    @Override
    public boolean hasNextParent(Map<String, String> data, String currentParent) {
        if(parentIter == null) {
            parentIter = data.keySet().iterator();
        }
        return parentIter.hasNext();
    }

    @Override
    public boolean hasNextChild(Map<String, String> data, String parent, String currentChild) {
        return currentChild == null; // only one item
    }

    @Override
    public String getNextParent(Map<String, String> data, String currentParent) {
        return parentIter.next();
    }

    @Override
    public String getNextChild(Map<String, String> data, String parent, String currentChild) {
        return data.get(parent);
    }

    public static class StringViewBinding extends ViewBinding<String> {

        public StringViewBinding() {
            this(R.layout.exif_list_group_layout, R.id.lblListItemHeader);
        }

        public StringViewBinding(@LayoutRes int layoutId, @IdRes int textViewId) {
            super(layoutId);
            addViewIdToFieldBinding(textViewId, "DATA");
        }

        @Override
        Map<String, ?> buildDataMap(String item) {
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put("DATA", item);
            return dataMap;
        }
    }
}
