package delit.piwigoclient.ui.common.list;

import android.content.Context;
import android.util.LongSparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.Enableable;
import delit.piwigoclient.ui.common.button.AppCompatCheckboxTriState;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.util.DisplayUtils;
import delit.piwigoclient.util.SetUtils;

/**
 * Created by gareth on 22/06/17.
 */

public abstract class MultiSourceListAdapter<T, S extends BaseRecyclerViewAdapterPreferences> extends BaseAdapter implements Enableable, SelectableItemsAdapter<T> {

    private final Context context;
    private final S adapterPrefs;
    private HashSet<Long> initialSelectedResourceIds = new HashSet<>(0);
    private HashSet<Long> indirectlySelectedItems;
    private final ArrayList<T> availableItems;
    private LongSparseArray<Integer> itemIdToLevelMap;
    private LongSparseArray<Integer> idPositionMap;
    private ListView parentList;

    public MultiSourceListAdapter(Context context, ArrayList<T> availableItems, S adapterPrefs) {
        this(context, availableItems, null, adapterPrefs);
    }

    public MultiSourceListAdapter(Context context, ArrayList<T> availableItems, HashSet<Long> indirectlySelectedItems, S adapterPrefs) {
        this.context = context;
        this.adapterPrefs = adapterPrefs;
        this.availableItems = availableItems;
        this.indirectlySelectedItems = indirectlySelectedItems;
        buildTreeLayoutMetadata();
    }

    public S getAdapterPrefs() {
        return adapterPrefs;
    }

    private void buildTreeLayoutMetadata() {
        itemIdToLevelMap = new LongSparseArray<>(availableItems.size());
        for (T item : availableItems) {
            long id = getItemId(item);
            Long parentId = getItemParentId(item);
            int level = -1;
            if (parentId != null) {
                Integer mappedLevel = itemIdToLevelMap.get(parentId);
                level = mappedLevel != null ? mappedLevel : -1;
            }
            itemIdToLevelMap.put(id, level + 1);
        }
    }

    /**
     * Override this if the list is a tree
     *
     * @param item
     * @return
     */
    public Long getItemParentId(T item) {
        return null;
    }

    @Override
    public int getCount() {
        return availableItems.size();
    }

    @Override
    public T getItem(int position) {
        return availableItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItemId(availableItems.get(position));
    }

    public abstract long getItemId(T item);

    @Override
    public boolean hasStableIds() {
        return true;
    }

    private LongSparseArray<Integer> buildIdPositionMap() {
        idPositionMap = new LongSparseArray<>();
        for (int i = 0; i < getCount(); i++) {
            T thisItem = getItem(i);
            idPositionMap.put(getItemId(thisItem), i);
        }
        return idPositionMap;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if(idPositionMap == null) {
            idPositionMap = buildIdPositionMap();
        }
        View view = convertView; // re-use an existing view, if one is supplied
        if (view == null) {
            // otherwise create a pkg one
            view = LayoutInflater.from(context).inflate(R.layout.layout_permission_list_item, parent, false);
        }
        // set view properties to reflect data for the given row

        T item = availableItems.get(position);
        long thisItemId = getItemId(item);
        int levelInTreeOfItem = itemIdToLevelMap.get(thisItemId);

        setViewContentForItemDisplay(view, item, levelInTreeOfItem);

        final AppCompatCheckboxTriState imageView = view.findViewById(R.id.permission_status_icon);
        imageView.setEnabled(adapterPrefs.isEnabled());
        boolean alwaysChecked = indirectlySelectedItems != null && indirectlySelectedItems.contains(thisItemId);
        imageView.setAlwaysChecked(alwaysChecked);
        imageView.setOnCheckedChangeListener(new ItemSelectionListener(position));

        imageView.setClickable(false);
        imageView.setFocusable(false);
        imageView.setFocusableInTouchMode(false);

        // return the view, populated with data, for display
        return view;
    }

    /**
     * Override this to tweak the display of the items when using the default layout.
     *
     * @param view View of an item
     * @param item item to be displayed
     * @param levelInTreeOfItem level within the tree of the item (root is 0)
     */
    protected void setViewContentForItemDisplay(View view, T item, int levelInTreeOfItem) {
        int px = DisplayUtils.dpToPx(view.getContext(), levelInTreeOfItem * 15);

        TextView textField = view.findViewById(R.id.permission_text);
        textField.setPadding(px, 0, 0, 0);

        textField.setText(item.toString());
    }

    @Override
    public void setEnabled(boolean enabled) {
        adapterPrefs.setEnabled(enabled);
    }

    @Override
    public boolean isEnabled() {
        return adapterPrefs.isEnabled();
    }

    @Override
    public boolean areAllItemsEnabled() {
        return adapterPrefs.isEnabled();
    }

    @Override
    public boolean isEnabled(int position) {
        return adapterPrefs.isEnabled();
    }

    public T getItemById(Long selectedId) {
        return getItem(getPosition(selectedId));
    }

    public HashSet<Long> getIndirectlySelectedItems() {
        return new HashSet<>(indirectlySelectedItems);
    }

    public int getPosition(Long itemId) {
        if(itemId == null) {
            return -1;
        }
        if(idPositionMap == null) {
            idPositionMap = buildIdPositionMap();
        }
        Integer pos = idPositionMap.get(itemId);
        if(pos != null) {
            return pos;
        }
        return -1;
    }

    public void setIndirectlySelectedItems(HashSet<Long> indirectAlbumPermissions) {
        indirectlySelectedItems = indirectAlbumPermissions;
        notifyDataSetChanged();
    }

    @Override
    public HashSet<T> getSelectedItems() {
        try {
            HashSet<T> selectedItems = new HashSet<>();
            for (long selectedItemId : parentList.getCheckedItemIds()) {
                selectedItems.add(getItemById(selectedItemId));
            }
            return selectedItems;
        } catch(RuntimeException e) {
            throw new IllegalStateException("Not all items are loaded yet. Unable to provide a list of them");
        }
    }

    @Override
    public HashSet<Long> getSelectedItemIds() {
        return SetUtils.asSet(parentList.getCheckedItemIds());
    }

    @Override
    public void clearSelectedItemIds() {
        parentList.clearChoices();
    }

    @Override
    public void selectAllItemIds() {
        for(int i = 0; i < parentList.getCount(); i++) {
            parentList.setItemChecked(i, true);
        }
    }

    @Override
    public void setItemSelected(Long selectedItemId) {
        parentList.setItemChecked(getPosition(selectedItemId), true);
    }

    @Override
    public void setInitiallySelectedItems(HashSet<Long> initialSelection) {
        this.initialSelectedResourceIds = initialSelection != null ? new HashSet<>(initialSelection) : new HashSet<Long>(0);
    }

    @Override
    public void setSelectedItems(HashSet<Long> selectedResourceIds) {
        if(initialSelectedResourceIds == null) {
            throw new IllegalStateException("initially selected items should never be null at this point");
        }
        parentList.clearChoices();
        HashSet<Long> resourcesToSelect = selectedResourceIds != null ? new HashSet<>(selectedResourceIds) : new HashSet<Long>(initialSelectedResourceIds);
        for(Long resourceId : resourcesToSelect) {
            parentList.setItemChecked(getPosition(resourceId), true);
        }

    }

    @Override
    public boolean isAllowItemDeselection(long itemId) {
        return !adapterPrefs.isInitialSelectionLocked() || !initialSelectedResourceIds.contains(itemId);
    }

    public void linkToListView(final ListView listView, HashSet<Long> initialSelection, HashSet<Long> currentSelection) {
        parentList = listView;
        if(adapterPrefs.isMultiSelectionEnabled()) {
            listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        } else {
            listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        }
        // this will also reset list view choices to no selection.
        listView.setAdapter(this);
        // set the initial selection.
        setInitiallySelectedItems(initialSelection);
        setSelectedItems(currentSelection);
    }

    protected class ItemSelectionListener implements CompoundButton.OnCheckedChangeListener {

        private final int position;

        public ItemSelectionListener(int position) {
            this.position = position;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            long itemId = getItemId(position);
            if (!isChecked) {
                if(!isAllowItemDeselection(itemId)) {
                    parentList.setItemChecked(position, true);
                }
            }
        }
    }
}
