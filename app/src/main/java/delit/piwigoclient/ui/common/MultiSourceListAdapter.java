package delit.piwigoclient.ui.common;

import android.content.Context;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.util.DisplayUtils;

/**
 * Created by gareth on 22/06/17.
 */

public abstract class MultiSourceListAdapter<T> extends BaseAdapter implements Enableable, SelectableItemsAdapter<T> {

    private final Context context;
    private HashSet<Long> selectedResourceIds = new HashSet<>(0);
    private HashSet<Long> initialSelectedResourceIds = new HashSet<>(0);
    private HashSet<Long> indirectlySelectedItems;
    private final ArrayList<T> availableItems;
    private boolean enabled;
    private LongSparseArray<Integer> itemIdToLevelMap;
    private LongSparseArray<Integer> idPositionMap;

    public MultiSourceListAdapter(Context context, ArrayList<T> availableItems, boolean enabled) {
        this(context, availableItems, null, enabled);
    }

    public MultiSourceListAdapter(Context context, ArrayList<T> availableItems, HashSet<Long> indirectlySelectedItems, boolean enabled) {
        this.context = context;
        this.availableItems = availableItems;
        this.indirectlySelectedItems = indirectlySelectedItems;
        this.enabled = enabled;
        buildTreeLayoutMetadata();
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

    public void setPermissions(HashSet<Long> directlySelectedItems) {
        setPermissions(directlySelectedItems, null);
    }

    public void setPermissions(HashSet<Long> directlySelectedItems, HashSet<Long> indirectlySelectedItems) {
        this.indirectlySelectedItems = indirectlySelectedItems;
        notifyDataSetChanged();
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
        imageView.setEnabled(enabled);
        boolean alwaysChecked = indirectlySelectedItems != null && indirectlySelectedItems.contains(thisItemId);
        imageView.setAlwaysChecked(alwaysChecked);
        imageView.setOnCheckedChangeListener(new ItemSelectionListener(position));

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
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return enabled;
    }

    @Override
    public boolean isEnabled(int position) {
        return enabled;
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
            for (Long selectedItemId : selectedResourceIds) {
                selectedItems.add(getItemById(selectedItemId));
            }
            return selectedItems;
        } catch(RuntimeException e) {
            throw new IllegalStateException("Not all items are loaded yet. Unable to provide a list of them");
        }
    }

    @Override
    public HashSet<Long> getSelectedItemIds() {
        return selectedResourceIds;
    }

    @Override
    public void clearSelectedItemIds() {
        throw new UnsupportedOperationException("can't do this without an observer in the list");
//        selectedResourceIds.clear();
//        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void selectAllItemIds() {
        throw new UnsupportedOperationException("can't do this without an observer in the list");
//        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void setItemSelected(Long selectedItemId) {
        throw new UnsupportedOperationException("can't do this without an observer in the list");
//        selectedResourceIds.add(selectedItemId);
//        try {
//            int idx = getPosition(selectedItemId);
//            notifyItemChanged(idx);
//        } catch(IllegalArgumentException e) {
//            if(BuildConfig.DEBUG) {
//                Log.e(TAG, "Item not available to select (probably not loaded yet)", e);
//            }
//        }
    }

    @Override
    public void setInitiallySelectedItems(HashSet<Long> initialSelection) {
        this.initialSelectedResourceIds = initialSelection != null ? new HashSet<>(initialSelection) : new HashSet<Long>(0);
    }

    @Override
    public void setSelectedItems(HashSet<Long> selectedResourceIds) {
        this.selectedResourceIds = selectedResourceIds;
    }

    @Override
    public boolean isAllowItemDeselection(long itemId) {
        return !indirectlySelectedItems.contains(itemId);
//        return !prefs.isInitialSelectionLocked() || !initialSelectedResourceIds.contains(itemId);
    }

    protected class ItemSelectionListener implements CompoundButton.OnCheckedChangeListener {

        private final int position;

        public ItemSelectionListener(int position) {
            this.position = position;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            long itemId = getItemId(position);
            boolean changed = false;
            if (isChecked) {
                changed = selectedResourceIds.add(itemId);
            } else {
                if(isAllowItemDeselection(itemId)) {
                    changed = selectedResourceIds.remove(itemId);
                } else {
                    // re-check the button.
                    buttonView.setChecked(true);
                }
            }
            if (changed) {
                //multiSelectStatusListener.onItemSelectionCountChanged(adapter, selectedResourceIds.size());
            }
        }
    }
}
