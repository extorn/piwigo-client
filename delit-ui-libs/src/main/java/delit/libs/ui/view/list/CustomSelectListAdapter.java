package delit.libs.ui.view.list;

import android.content.Context;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;

import delit.libs.BuildConfig;
import delit.libs.core.util.Logging;
import delit.libs.ui.view.Enableable;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;

/**
 * Created by gareth on 13/06/17.
 */

public abstract class CustomSelectListAdapter<P extends BaseRecyclerViewAdapterPreferences, T> extends ArrayAdapter<T> implements Enableable, SelectableItemsAdapter<T> {

    private static final String TAG = "CustomSelLisAdapter";
    private final P prefs;
    private HashSet<Long> selectedResourceIds = new HashSet<>(0);
    private HashSet<Long> initialSelectedResourceIds = new HashSet<>(0);
    private LongSparseArray<Integer> idPositionMap;

    protected CustomSelectListAdapter(@NonNull Context context, P prefs, @LayoutRes int resource) {
        this(context, prefs, resource, 0);
    }

    protected CustomSelectListAdapter(@NonNull Context context, P prefs, @LayoutRes int resource, @IdRes int txtViewId) {
        super(context, resource, txtViewId);
        this.prefs = prefs;
    }

    public P getPrefs() {
        return prefs;
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View aView = super.getDropDownView(position, convertView, parent);
        if (idPositionMap == null) {
            idPositionMap = buildIdPositionMap();
        }
        setViewData(position, aView, true);
        return aView;
    }

    @NonNull
    @Override
    public View getView(final int position, View view, @NonNull ViewGroup parent) {
        View aView = super.getView(position, view, parent);
        if (idPositionMap == null) {
            idPositionMap = buildIdPositionMap();
        }
//        aView.setEnabled(prefs.isAllowItemSelection());
//        aView.setClickable(prefs.isAllowItemSelection());
        setViewData(position, aView, false);
        return aView;
    }

    /**
     * Note: May throw an IllegalStateException if not all items loaded yet
     *
     * @return
     */
    @Override
    public HashSet<T> getSelectedItems() {
        try {
            HashSet<T> selectedItems = new HashSet<>();
            for (Long selectedItemId : selectedResourceIds) {
                T selectedItem = getItemById(selectedItemId);
                if (selectedItem != null) {
                    selectedItems.add(selectedItem);
                }
            }
            return selectedItems;
        } catch (RuntimeException e) {
            Logging.recordException(e);
            throw new IllegalStateException("Not all items are loaded yet. Unable to provide a list of them", e);
        }
    }

    @Override
    public void setSelectedItems(HashSet<Long> selectedResourceIds) {
        if (initialSelectedResourceIds == null) {
            throw new IllegalStateException("initially selected items should never be null at this point");
        }
        this.selectedResourceIds = selectedResourceIds != null ? new HashSet<>(selectedResourceIds) : new HashSet<>(initialSelectedResourceIds);
    }

    @Override
    public void setInitiallySelectedItems(HashSet<Long> initialSelection) {
        this.initialSelectedResourceIds = initialSelection != null ? new HashSet<>(initialSelection) : new HashSet<>(0);
    }

    @Override
    public void clearSelectedItemIds() {
        selectedResourceIds.clear();
        notifyItemRangeChanged(0, getItemCount());
    }

    private void notifyItemRangeChanged(int i, int itemCount) {
        notifyDataSetChanged();
    }

    @Override
    public void selectAllItemIds() {
        for (int i = 0; i < getItemCount(); i++) {
            selectedResourceIds.add(getItemId(i));
        }
        notifyItemRangeChanged(0, getItemCount());
    }

    public int getItemCount() {
        return idPositionMap.size();
    }

    @Override
    public void setItemSelected(Long selectedItemId) {
        selectedResourceIds.add(selectedItemId);
        try {
            T item = getItemById(selectedItemId);
            int idx = getItemPosition(item);
            notifyItemChanged(idx);
        } catch (IllegalArgumentException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Item not available to select (probably not loaded yet)", e);
            }
        }
    }

    protected void notifyItemChanged(int idx) {
        notifyDataSetChanged();
    }

    private int getItemPosition(T item) {
        return getPosition(item);
    }

    protected abstract void setViewData(int position, View aView, boolean isDropdown);

    protected abstract Long getItemId(T item);

    public boolean isItemSelected(Long itemId) {
        return selectedResourceIds.contains(itemId);
    }

    @Override
    public boolean isAllowItemDeselection(long itemId) {
        return (!prefs.isInitialSelectionLocked() || !initialSelectedResourceIds.contains(itemId)) && (isMultiSelectionAllowed() || selectedResourceIds.size() > 1);
    }

    public boolean isMultiSelectionAllowed() {
        return prefs.isMultiSelectionEnabled();
    }

    public void toggleItemSelection() {
        prefs.setAllowItemSelection(!prefs.isAllowItemSelection());
        if (!prefs.isAllowItemSelection()) {
            selectedResourceIds.clear();
        }
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public HashSet<Long> getSelectedItemIds() {
        return selectedResourceIds;
    }

    @Override
    public void notifyDataSetChanged() {
        idPositionMap = null;
        super.notifyDataSetChanged();
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
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public long getItemId(int position) {
        int itemCount = getCount();
        if (itemCount > position) {
            T item = getItem(position);
            return getItemId(item);
        } else {
            throw new IllegalStateException("The adapter is out of sync with the screen for some reason. Wanted item at position " + position + " but there are only " + getCount() + " items in the adapter. The local id map contains " + (idPositionMap != null ? idPositionMap.size() : 0) + " items");
        }
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
    public boolean areAllItemsEnabled() {
        return prefs.isEnabled();
    }

    @Override
    public boolean isEnabled(int position) {
        return prefs.isEnabled();
    }

    @Override
    public boolean isEnabled() {
        return prefs.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean changed = false;
        if (enabled != prefs.isEnabled()) {
            changed = true;
        }
        prefs.setEnabled(enabled);
        if (changed) {
            notifyDataSetChanged();
        }
    }

    public T getItemById(Long selectedId) {
        int pos = getPosition(selectedId);
        if (pos < 0) {
            return null;
        }
        return getItem(pos);
    }

}
