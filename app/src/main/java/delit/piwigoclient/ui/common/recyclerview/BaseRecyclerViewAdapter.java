package delit.piwigoclient.ui.common.recyclerview;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import java.util.HashSet;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.ui.common.Enableable;
import delit.piwigoclient.ui.common.SelectableItemsAdapter;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GalleryItem}
 *
 * @param <T> Item
 * @param <S> ViewHolder for each Item
 */
public abstract class BaseRecyclerViewAdapter<V extends BaseRecyclerViewAdapterPreferences, T, S extends CustomViewHolder<V, T>> extends RecyclerView.Adapter<S> implements Enableable, SelectableItemsAdapter<T> {

    private static final String TAG = "BaseRecyclerViewAdapter";
    private Context context;
    private final MultiSelectStatusListener<T> multiSelectStatusListener;
    private V prefs;
    private HashSet<Long> selectedResourceIds = new HashSet<>(0);
    private HashSet<Long> initialSelectedResourceIds = new HashSet<>(0);

    public BaseRecyclerViewAdapter(MultiSelectStatusListener multiSelectStatusListener, V prefs) {
        this.setHasStableIds(true);
        this.multiSelectStatusListener = multiSelectStatusListener;
        this.prefs = prefs;
    }

    @Override
    public abstract long getItemId(int position);

    public abstract S buildViewHolder(View view);

    protected abstract T getItemById(Long selectedId);

    public abstract int getItemPosition(T item);

    protected abstract void removeItemFromInternalStore(int idxRemoved);

    protected abstract void replaceItemInInternalStore(int idxToReplace, T newItem);

    protected abstract T getItemFromInternalStoreMatching(T item);

    protected abstract void addItemToInternalStore(T item);

    public abstract T getItemByPosition(int position);

    public abstract boolean isHolderOutOfSync(S holder, T newItem);

    public abstract HashSet<Long> getItemsSelectedButNotLoaded();

    @Override
    public abstract int getItemCount();

    @Override
    public void setInitiallySelectedItems(HashSet<Long> initialSelection) {
        this.initialSelectedResourceIds = initialSelection != null ? new HashSet<>(initialSelection) : new HashSet<Long>(0);
    }

    @NonNull
    @Override
    public S onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        setContext(parent.getContext());
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.actionable_triselect_list_item_layout, parent, false);

        final S viewHolder = buildViewHolder(view);
        viewHolder.internalCacheViewFieldsAndConfigure(buildCustomClickListener(viewHolder));

        return viewHolder;
    }

    protected CustomClickListener<V, T, S> buildCustomClickListener(S viewHolder) {
        return new CustomClickListener<>(viewHolder, this);
    }

    @Override
    public int getItemViewType(int position) {
        //groups.getItemByIdx(position).getType();
        // override this method for multiple item types
        return super.getItemViewType(position);
    }

    @Override
    public void onBindViewHolder(@NonNull S holder, int position) {
        T newItem = getItemByPosition(position);
        if (isHolderOutOfSync(holder, newItem)) {
            // store the item in this recyclable holder.
            holder.fillValues(getContext(), newItem, prefs.isAllowItemDeletion());
        }
    }

    protected final void setContext(Context context) {
        this.context = context;
    }

    private Context getContext() {
        return context;
    }

    public void setSelectedItems(HashSet<Long> selectedResourceIds) {
        this.selectedResourceIds = selectedResourceIds;
    }

    public void toggleItemSelection() {
        prefs.setAllowItemSelection(!prefs.isAllowItemSelection());
        if (!prefs.isAllowItemSelection()) {
            selectedResourceIds.clear();
        }
        notifyItemRangeChanged(0, getItemCount());
        multiSelectStatusListener.onMultiSelectStatusChanged(this, prefs.isAllowItemSelection());
        multiSelectStatusListener.onItemSelectionCountChanged(this, selectedResourceIds.size());
    }

    public boolean isItemSelectionAllowed() {
        return prefs.isAllowItemSelection();
    }

    @Override
    public HashSet<Long> getSelectedItemIds() {
        return selectedResourceIds;
    }

    /**
     * Note: May throw an IllegalStateException if not all items loaded yet
     * @return
     */
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
    public void clearSelectedItemIds() {
        selectedResourceIds.clear();
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void selectAllItemIds() {

        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void setItemSelected(Long selectedItemId) {
        selectedResourceIds.add(selectedItemId);
        try {
            T item = getItemById(selectedItemId);
            int idx = getItemPosition(item);
            notifyItemChanged(idx);
        } catch(IllegalArgumentException e) {
            if(BuildConfig.DEBUG) {
                Log.e(TAG, "Item not available to select (probably not loaded yet)", e);
            }
        }
    }

    public void remove(T group) {
        int idxRemoved = getItemPosition(group);
        if(idxRemoved >= 0) {
            removeItemFromInternalStore(idxRemoved);
            notifyItemRemoved(idxRemoved);
        }
    }

    public void replaceOrAddItem(T group) {
        T itemToBeReplaced = null;
        try {
            itemToBeReplaced = getItemFromInternalStoreMatching(group);
        } catch (IllegalArgumentException e) {
            // thrown if the group isn't present.
        }
        if (itemToBeReplaced != null) {
            int replaceIdx = getItemPosition(itemToBeReplaced);
            replaceItemInInternalStore(replaceIdx, group);
        } else {
            addItemToInternalStore(group);
        }
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


    public static class MultiSelectStatusAdapter<T> implements MultiSelectStatusListener<T> {

        @Override
        public <A extends BaseRecyclerViewAdapter> void onMultiSelectStatusChanged(A adapter, boolean multiSelectEnabled) {

        }

        @Override
        public <A extends BaseRecyclerViewAdapter> void onItemSelectionCountChanged(A adapter, int size) {

        }

        @Override
        public <A extends BaseRecyclerViewAdapter> void onItemDeleteRequested(A adapter, T g) {

        }

        @Override
        public <A extends BaseRecyclerViewAdapter> void onItemClick(A adapter, T item) {

        }

        @Override
        public <A extends BaseRecyclerViewAdapter> void onItemLongClick(A adapter, T item) {

        }
    }

    public interface MultiSelectStatusListener<T> {
        <A extends BaseRecyclerViewAdapter> void onMultiSelectStatusChanged(A adapter, boolean multiSelectEnabled);

        <A extends BaseRecyclerViewAdapter> void onItemSelectionCountChanged(A adapter, int size);

        <A extends BaseRecyclerViewAdapter> void onItemDeleteRequested(A adapter, T g);

        <A extends BaseRecyclerViewAdapter> void onItemClick(A adapter, T item);

        <A extends BaseRecyclerViewAdapter> void onItemLongClick(A adapter, T item);

    }

    protected class ItemSelectionListener implements CompoundButton.OnCheckedChangeListener {

        private final S holder;
        private BaseRecyclerViewAdapter<V,T,S> adapter;

        public ItemSelectionListener(BaseRecyclerViewAdapter<V,T,S> adapter, S holder) {
            this.adapter = adapter;
            this.holder = holder;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            boolean changed = false;
            if (isChecked) {
                changed = selectedResourceIds.add(holder.getItemId());
            } else {
                if(isAllowItemDeselection(holder.getItemId())) {
                    changed = selectedResourceIds.remove(holder.getItemId());
                } else {
                    // re-check the button.
                    buttonView.setChecked(true);
                }
            }
            if (changed) {
                multiSelectStatusListener.onItemSelectionCountChanged(adapter, selectedResourceIds.size());
            }
        }
    }

    @Override
    public boolean isAllowItemDeselection(long itemId) {
        return !prefs.isInitialSelectionLocked() || !initialSelectedResourceIds.contains(itemId);
    }

    public boolean isMultiSelectionAllowed() {
        return prefs.isMultiSelectionEnabled();
    }

    protected void onDeleteItem(S viewHolder, View v) {
        multiSelectStatusListener.onItemDeleteRequested(this, viewHolder.getItem());
    }


    public MultiSelectStatusListener<T> getMultiSelectStatusListener() {
        return multiSelectStatusListener;
    }

}
