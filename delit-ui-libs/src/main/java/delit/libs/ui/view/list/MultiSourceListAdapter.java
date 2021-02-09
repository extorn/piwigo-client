package delit.libs.ui.view.list;

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

import androidx.annotation.LayoutRes;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashSet;

import delit.libs.R;
import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.Enableable;
import delit.libs.ui.view.button.MaterialCheckboxTriState;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.util.SetUtils;

/**
 * Created by gareth on 22/06/17.
 */

public abstract class MultiSourceListAdapter<T, S extends BaseRecyclerViewAdapterPreferences> extends BaseAdapter implements Enableable, SelectableItemsAdapter<T> {

    private final S adapterPrefs;
    private final ArrayList<T> availableItems;
    private HashSet<Long> initialSelectedResourceIds = new HashSet<>(0);
    private HashSet<Long> indirectlySelectedItems;
    private LongSparseArray<Integer> itemIdToLevelMap;
    private LongSparseArray<Integer> idPositionMap;
    private ListView parentList;

    public MultiSourceListAdapter(ArrayList<T> availableItems, S adapterPrefs) {
        this(availableItems, null, adapterPrefs);
    }

    public MultiSourceListAdapter(ArrayList<T> availableItems, HashSet<Long> indirectlySelectedItems, S adapterPrefs) {
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

    public int getPosition(T item) {
        return availableItems.indexOf(item);
    }

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
        if (idPositionMap == null) {
            idPositionMap = buildIdPositionMap();
        }
        View view = convertView; // re-use an existing view, if one is supplied
        if (view == null) {
            // otherwise create a pkg one
            view = buildNewItemView(parent);
        }
        // set view properties to reflect data for the given row

        T item = availableItems.get(position);
        long thisItemId = getItemId(item);
        int levelInTreeOfItem = itemIdToLevelMap.get(thisItemId);


        setViewContentForItemDisplay(parent.getContext(), view, item, levelInTreeOfItem);


        final MaterialCheckboxTriState imageView = getAppCompatCheckboxTriState(view);

        imageView.setVisibility(showItemSelectedMarker(imageView) ? View.VISIBLE : View.GONE);

        if (!getAdapterPrefs().isMultiSelectionEnabled()) {
            imageView.setButtonDrawable(ContextCompat.getDrawable(parent.getContext(),R.drawable.radio_button));
        }


        boolean alwaysChecked = indirectlySelectedItems != null && indirectlySelectedItems.contains(thisItemId);
        imageView.setAlwaysChecked(alwaysChecked);
        imageView.setEnabled(adapterPrefs.isEnabled());
        imageView.setOnCheckedChangeListener(new ItemSelectionListener(position));
//
//        imageView.setClickable(false);
//        imageView.setFocusable(false);
//        imageView.setFocusableInTouchMode(false);

        // return the view, populated with data, for display
        return view;
    }

    protected boolean showItemSelectedMarker(MaterialCheckboxTriState imageView) {
        return adapterPrefs.isAllowItemSelection();
    }

    protected MaterialCheckboxTriState getAppCompatCheckboxTriState(View view) {
        return view.findViewById(R.id.permission_status_icon);
    }

    protected View buildNewItemView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext()).inflate(getItemViewLayoutRes(), parent, false);
    }

    protected @LayoutRes
    int getItemViewLayoutRes() {
        return R.layout.layout_list_item_permission;
    }

    /**
     * Override this to tweak the display of the items when using the default layout.
     *
     * @param view              View of an item
     * @param item              item to be displayed
     * @param levelInTreeOfItem level within the tree of the item (root is 0)
     */
    protected void setViewContentForItemDisplay(Context context, View view, T item, int levelInTreeOfItem) {
        TextView textView = view.findViewById(R.id.permission_text);
        int defaultPaddingStartDp = 8;
        int paddingStartPx = DisplayUtils.dpToPx(context, defaultPaddingStartDp + (levelInTreeOfItem * 15));
        textView.setPaddingRelative(paddingStartPx, textView.getPaddingTop(), textView.getPaddingEnd(), textView.getPaddingBottom());
        textView.setText(item.toString());
    }

    @Override
    public boolean isEnabled() {
        return adapterPrefs.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        adapterPrefs.setEnabled(enabled);
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

    public void setIndirectlySelectedItems(HashSet<Long> indirectAlbumPermissions) {
        indirectlySelectedItems = indirectAlbumPermissions;
        notifyDataSetChanged();
    }

    public int getPosition(Long itemId) {
        if (itemId == null) {
            return -1;
        }
        if (idPositionMap == null) {
            idPositionMap = buildIdPositionMap();
        }
        Integer pos = idPositionMap.get(itemId);
        if (pos != null) {
            return pos;
        }
        return -1;
    }

    @Override
    public HashSet<T> getSelectedItems() {
        try {
            HashSet<T> selectedItems = new HashSet<>();
            for (long selectedItemId : parentList.getCheckedItemIds()) {
                selectedItems.add(getItemById(selectedItemId));
            }
            return selectedItems;
        } catch (RuntimeException e) {
            Logging.recordException(e);
            throw new IllegalStateException("Not all items are loaded yet. Unable to provide a list of them");
        }
    }

    @Override
    public void setSelectedItems(HashSet<Long> selectedResourceIds) {
        if (initialSelectedResourceIds == null) {
            throw new IllegalStateException("initially selected items should never be null at this point");
        }
        parentList.clearChoices();
        HashSet<Long> resourcesToSelect = selectedResourceIds != null ? selectedResourceIds : new HashSet<>(initialSelectedResourceIds);
        for (Long resourceId : resourcesToSelect) {
            int position = getPosition(resourceId);
            if(position >= 0) {
                parentList.setItemChecked(position, true);
            }
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
        for (int i = 0; i < parentList.getCount(); i++) {
            parentList.setItemChecked(i, true);
        }
    }

    @Override
    public void setItemSelected(Long selectedItemId) {
        parentList.setItemChecked(getPosition(selectedItemId), true);
    }

    @Override
    public void setInitiallySelectedItems(HashSet<Long> initialSelection) {
        this.initialSelectedResourceIds = initialSelection != null ? new HashSet<>(initialSelection) : new HashSet<>(0);
    }

    @Override
    public boolean isAllowItemDeselection(long itemId) {
        return !adapterPrefs.isInitialSelectionLocked() || !initialSelectedResourceIds.contains(itemId);
    }

    public void linkToListView(final ListView listView, HashSet<Long> initialSelection, HashSet<Long> currentSelection) {
        parentList = listView;
        if (adapterPrefs.isMultiSelectionEnabled()) {
            listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        } else if (adapterPrefs.isAllowItemSelection()) {
            listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        } else {
            listView.setChoiceMode(AbsListView.CHOICE_MODE_NONE);
        }
        // this will also reset list view choices to no selection.
        listView.setAdapter(this);
        // set the initial selection.
        setInitiallySelectedItems(initialSelection);
        setSelectedItems(currentSelection);
    }

    public ArrayList<Long> getItemIds() {
        ArrayList<Long> ids = new ArrayList<>(availableItems.size());
        for (T item : availableItems) {
            ids.add(getItemId(item));
        }
        return ids;
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
                if (!isAllowItemDeselection(itemId)) {
                    parentList.setItemChecked(position, true);
                }
            }
        }
    }

}
