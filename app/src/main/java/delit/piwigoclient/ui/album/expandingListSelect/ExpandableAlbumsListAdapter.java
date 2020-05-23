package delit.piwigoclient.ui.album.expandingListSelect;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.LayoutRes;

import java.util.HashSet;
import java.util.Set;

import delit.libs.ui.view.button.MaterialCheckboxTriState;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.util.SetUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.PiwigoUtils;

/**
 * UNUSED - Use this once it works!
 */
public class ExpandableAlbumsListAdapter extends BaseExpandableListAdapter {

    private ExpandableAlbumsListAdapterPreferences prefs;
    private final CategoryItem parentAlbum;
    private boolean childrenAreSelectable = true;
    private Set<CategoryItem> selectedItems = new HashSet<>();
    private Set<Long> currentSelectionIds = new HashSet<>();

    public ExpandableAlbumsListAdapter(CategoryItem parentAlbum, ExpandableAlbumsListAdapterPreferences prefs) {
        this.parentAlbum = parentAlbum;
        this.prefs = prefs;
    }

    @Override
    public int getGroupCount() {
        return parentAlbum.getChildAlbumCount();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return parentAlbum.getChildAlbums().get(groupPosition).getChildAlbumCount() == 0 ? 0 : 1;
    }

    @Override
    public CategoryItem getGroup(int groupPosition) {
        return parentAlbum.getChildAlbums().get(groupPosition);
    }

    @Override
    public CategoryItem getChild(int groupPosition, int childPosition) {
        return getGroup(groupPosition).getChildAlbums().get(childPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return getGroup(groupPosition).getId();
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return getGroup(groupPosition).getChildAlbums().get(childPosition).getId();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = newGroupView(isExpanded, parent);
        }
        bindDataToGroupView(v, getGroup(groupPosition), isExpanded);
        return v;
    }
    
    private static @LayoutRes int getChildrenLayoutId(ExpandableAlbumsListAdapterPreferences prefs) {
        return R.layout.layout_nested_expanding_list;
    }

    private static @LayoutRes int getItemLayoutId(ExpandableAlbumsListAdapterPreferences prefs) {
        return prefs.isMultiSelectionEnabled() ? R.layout.layout_actionable_simple_triselect_list_item : R.layout.layout_actionable_simple_select_list_item;
    }

    /**
     * An item. This item may then have a list of children underneath it. Those children will themselves be individually one of these wrapped in a list
     * if the overall component can be described as a multi level expanding list
     *
     * @param isExpanded
     * @param parent
     * @return
     */
    protected View newGroupView(boolean isExpanded, ViewGroup parent) {
        return LayoutInflater.from(parent.getContext()).inflate(getItemLayoutId(prefs), parent, false);
    }

    private void bindDataToGroupView(View v, CategoryItem item, boolean isExpanded) {

        if(prefs.isMultiSelectionEnabled()) {
            MaterialCheckboxTriState checkbox = v.findViewById(R.id.actionable_list_item_checked);
            checkbox.setChecked(isChecked(item));
            checkbox.setOnClickListener(new ItemClickListener(item));
            checkbox.setCheckboxAtEnd(true);
        } else {
            RadioButton button = v.findViewById(R.id.actionable_list_item_checked);
            if(button.isChecked() != isChecked(item)) {
                button.toggle();
                button.setOnClickListener(new ItemClickListener(item));
            }
        }

        TextView tv = v.findViewById(R.id.actionable_list_item_text);
        tv.setText(item.getName());

        // delete is forbidden.
        v.findViewById(R.id.actionable_list_item_delete_button).setVisibility(View.GONE);
    }

    private boolean isChecked(CategoryItem item) {
        return currentSelectionIds.add(item.getId());
    }

    public Set<CategoryItem> getCheckedItems() {
        if(selectedItems.size() < currentSelectionIds.size()) {
            //fill in the missing items by painful tree traversal. :-(
            HashSet<Long> missingIds = SetUtils.difference(currentSelectionIds, PiwigoUtils.toSetOfIds(selectedItems));
            for(Long missingId : missingIds) {
                CategoryItem child = parentAlbum.findChild(missingId);
                if(child != null) {
                    selectedItems.add(child);
                } else {
                    throw new IllegalStateException("could not find child matching id in tree : " + missingId);
                }
            }
        }
        return selectedItems;
    }

    private void toggleChecked(CategoryItem item) {
        if(isChecked(item)) {
            currentSelectionIds.remove(item.getId());
            selectedItems.remove(item);
        } else {
            selectedItems.add(item);
            currentSelectionIds.add(item.getId());
        }
    }

    public void setCurrentSelection(Set<Long> currentSelectionIds) {
        this.currentSelectionIds = currentSelectionIds;
    }

    /**
     * Used internally to share the selected items with the children views
     * @param currentSelectionIds
     * @param selectedItems
     */
    private void setCurrentSelection(Set<Long> currentSelectionIds, Set<CategoryItem> selectedItems) {
        this.currentSelectionIds = currentSelectionIds;
        this.selectedItems = selectedItems;
    }

    public Set<Long> getCurrentSelectionIds() {
        return currentSelectionIds;
    }


    private class ItemClickListener implements View.OnClickListener {

        private CategoryItem item;

        public ItemClickListener(CategoryItem item) {
            this.item = item;
        }

        public void bindItem(CategoryItem item) {
            this.item = item;
        }

        @Override
        public void onClick(View v) {
            toggleChecked(item);
        }
    }



    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        View v;
        if (convertView == null) {
            v = newChildrenListView(isLastChild, parent);
        } else {
            v = convertView;
        }
        v.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        parent.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        bindChildrenDataToChildrenListView(parent.getContext(), v.findViewById(R.id.expandingListView), getGroup(groupPosition), childPosition);
        parent.invalidate();
        return v;
    }

    private void bindChildrenDataToChildrenListView(Context context, ExpandableListView childrenView, CategoryItem parent, int childPosition) {
        if(childPosition == 0) {
            ExpandableAlbumsListAdapter childAdapter = new ExpandableAlbumsListAdapter(parent, prefs);
            childAdapter.setCurrentSelection(currentSelectionIds, selectedItems);
            childrenView.setAdapter(childAdapter);
        }
    }

    private View newChildrenListView(boolean isLastChild, ViewGroup parent) {
        return LayoutInflater.from(parent.getContext()).inflate(getChildrenLayoutId(prefs), parent, false);
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return childrenAreSelectable;
    }

    public void setEnabled(boolean enabled) {
        childrenAreSelectable = enabled;
    }

    public static class ExpandableAlbumsListAdapterPreferences extends BaseRecyclerViewAdapterPreferences {
        private boolean allowRootAlbumSelection;

        public ExpandableAlbumsListAdapterPreferences withRootAlbumSelectionAllowed() {
            allowRootAlbumSelection = true;
            return this;
        }

        @Override
        public Bundle storeToBundle(Bundle parent) {
            Bundle b = new Bundle();
            parent.putBundle("ExpandableAlbumsListAdapterPreferences", b);
            super.storeToBundle(b);
            return parent;
        }

        @Override
        public ExpandableAlbumsListAdapterPreferences loadFromBundle(Bundle parent) {
            Bundle b = parent.getBundle("ExpandableAlbumsListAdapterPreferences");
            super.loadFromBundle(b);
            return this;
        }

        public boolean isAllowRootAlbumSelection() {
            return allowRootAlbumSelection;
        }
    }
}
