package delit.libs.ui.view.recycler;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.crashlytics.android.Crashlytics;

import java.util.HashSet;
import java.util.Iterator;

import delit.libs.ui.view.Enableable;
import delit.libs.ui.view.list.SelectableItemsAdapter;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.GalleryItem;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GalleryItem}
 *
 * @param <T> Item
 * @param <S> ViewHolder for each Item
 */
public abstract class BaseRecyclerViewAdapter<V extends BaseRecyclerViewAdapterPreferences, T, S extends CustomViewHolder<V, T>, P extends BaseRecyclerViewAdapter.MultiSelectStatusListener<T>> extends RecyclerView.Adapter<S> implements Enableable, SelectableItemsAdapter<T> {

    private static final String TAG = "BaseRecyclerViewAdapter";
    private final P multiSelectStatusListener;
    private Context context;
    private V prefs;
    private HashSet<Long> selectedResourceIds = new HashSet<>(0);
    private HashSet<Long> initialSelectedResourceIds = new HashSet<>(0);

    public BaseRecyclerViewAdapter(P multiSelectStatusListener, V prefs) {
        this.setHasStableIds(true);
        this.multiSelectStatusListener = multiSelectStatusListener;
        this.prefs = prefs;
    }

    public V getAdapterPrefs() {
        return prefs;
    }

    @Override
    public abstract long getItemId(int position);

    public abstract @NonNull S buildViewHolder(View view, int viewType);

    protected abstract T getItemById(Long selectedId);

    public abstract int getItemPosition(T item);

    protected abstract void removeItemFromInternalStore(int idxToRemove);

    protected abstract void replaceItemInInternalStore(int idxToReplace, T newItem);

    protected abstract T getItemFromInternalStoreMatching(T item);

    protected abstract void addItemToInternalStore(T item);

    public abstract T getItemByPosition(int position);

    public abstract boolean isHolderOutOfSync(S holder, T newItem);

    public abstract HashSet<Long> getItemsSelectedButNotLoaded();

    @Override
    public abstract int getItemCount();

    public boolean isItemSelected(Long itemId) {
        return selectedResourceIds.contains(itemId);
    }

    @NonNull
    protected View inflateView(@NonNull ViewGroup parent, int viewType) {
        return LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_actionable_triselect_list_item, parent, false);
    }

    /**
     * @param holder
     * @return true if this holder has never been used before (or is totally clean)
     */
    protected boolean isDirtyItemViewHolder(S holder, T newItem) {
        return holder.getItem() == null || !holder.getItem().equals(newItem);
    }

    @NonNull
    @Override
    public S onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        setContext(parent.getContext());
        View view = inflateView(parent, viewType);

        final S viewHolder = buildViewHolder(view, viewType);
        CustomClickListener<V, T, S> clickListener = buildCustomClickListener(viewHolder);
        viewHolder.internalCacheViewFieldsAndConfigure(clickListener, getAdapterPrefs());
        return viewHolder;
    }

    protected CustomClickListener<V, T, S> buildCustomClickListener(S viewHolder) {
        return new CustomClickListener<>(viewHolder, this);
    }

    @Override
    public int getItemViewType(int position) {
        //items.getItemByIdx(position).getType();
        // override this method for multiple item types
        return super.getItemViewType(position);
    }

    @Override
    public void onBindViewHolder(@NonNull S holder, int position) {
        T newItem = getItemByPosition(position);
        if (isHolderOutOfSync(holder, newItem)) {
            // store the item in this recyclable holder.
            holder.fillValues(getContext(), newItem, prefs.isAllowItemDeletion());
        } else {
            holder.redisplayOldValues(getContext(), newItem, prefs.isAllowItemDeletion());
        }
        try {
            holder.setChecked(isItemSelected(getItemId(position)));
        } catch (UnsupportedOperationException e) {
            // this is fine. Clearly selection is not wished for.
        }
    }

    protected Context getContext() {
        return context;
    }

    protected final void setContext(Context context) {
        this.context = context;
    }

    @Override
    public void setInitiallySelectedItems(HashSet<Long> initialSelection) {
        this.initialSelectedResourceIds = initialSelection != null ? new HashSet<>(initialSelection) : new HashSet<Long>(0);
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
     *
     * @return
     */
    @Override
    public HashSet<T> getSelectedItems() {
        try {
            HashSet<T> selectedItems = new HashSet<>();
            for (Long selectedItemId : selectedResourceIds) {
                T selectedItem = getItemById(selectedItemId);
                if(selectedItem != null) {
                    selectedItems.add(selectedItem);
                }
            }
            return selectedItems;
        } catch (RuntimeException e) {
            Crashlytics.logException(e);
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
    public void clearSelectedItemIds() {
        selectedResourceIds.clear();
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void selectAllItemIds() {
        for (int i = 0; i < getItemCount(); i++) {
            selectedResourceIds.add(getItemId(i));
        }
        notifyItemRangeChanged(0, getItemCount());
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

    public void deselectItem(Long itemId, boolean force) {
        if (force || isAllowItemDeselection(itemId)) {
            selectedResourceIds.add(itemId);
            try {
                T item = getItemById(itemId);
                int idx = getItemPosition(item);
                notifyItemChanged(idx);
            } catch (IllegalArgumentException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Item not available to deselect (probably not loaded yet)", e);
                }
            }
        }
    }

    public void removeItemByPosition(int idxRemoved) {
        if (idxRemoved >= 0) {
            removeItemFromInternalStore(idxRemoved);
            notifyItemRemoved(idxRemoved);
        }
    }

    public void remove(T item) {
        int idxRemoved = getItemPosition(item);
        if (idxRemoved >= 0) {
            removeItemFromInternalStore(idxRemoved);
            notifyItemRemoved(idxRemoved);
        }
    }

    public final void replaceOrAddItem(T item) {
        T itemToBeReplaced = null;
        try {
            itemToBeReplaced = getItemFromInternalStoreMatching(item);
        } catch (IllegalArgumentException e) {
            Crashlytics.logException(e);
            // thrown if the item isn't present.
        }
        if (itemToBeReplaced != null) {
            int replaceIdx = getItemPosition(itemToBeReplaced);
            replaceItemInInternalStore(replaceIdx, item);
            notifyItemChanged(replaceIdx);
        } else {
            addItem(item);
        }
    }

    public final void addItem(T item) {
        addItemToInternalStore(item);
        notifyItemInserted(getItemPosition(item));
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

    public ItemSelectionListener<? extends BaseRecyclerViewAdapter<V, T, S, P>> buildItemSelectionListener(S viewHolder) {
        return new ItemSelectionListener<>(this, viewHolder);
    }

    @Override
    public boolean isAllowItemDeselection(long itemId) {
        return (!prefs.isInitialSelectionLocked() || !initialSelectedResourceIds.contains(itemId)) && (isMultiSelectionAllowed() || selectedResourceIds.size() > 1);
    }

    public boolean isMultiSelectionAllowed() {
        return prefs.isMultiSelectionEnabled();
    }

    public void onDeleteItem(S viewHolder, View v) {
        multiSelectStatusListener.onItemDeleteRequested(this, viewHolder.getItem());
    }

    public P getMultiSelectStatusListener() {
        return multiSelectStatusListener;
    }

    public interface MultiSelectStatusListener<T> {
        <A extends BaseRecyclerViewAdapter> void onMultiSelectStatusChanged(A adapter, boolean multiSelectEnabled);

        <A extends BaseRecyclerViewAdapter> void onItemSelectionCountChanged(A adapter, int size);

        <A extends BaseRecyclerViewAdapter> void onItemDeleteRequested(A adapter, T g);

        <A extends BaseRecyclerViewAdapter> void onItemClick(A adapter, T item);

        <A extends BaseRecyclerViewAdapter> void onItemLongClick(A adapter, T item);

        <A extends BaseRecyclerViewAdapter> void onDisabledItemClick(A adapter, T item);
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

        @Override
        public <A extends BaseRecyclerViewAdapter> void onDisabledItemClick(A adapter, T item) {

        }
    }

    protected class ItemSelectionListener<X extends BaseRecyclerViewAdapter<V, T, S, P>> implements CompoundButton.OnCheckedChangeListener {

        private final S holder;
        private X adapter;

        public ItemSelectionListener(X adapter, S holder) {
            this.adapter = adapter;
            this.holder = holder;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            boolean changed = false;
            if (isChecked) {
                changed = selectedResourceIds.add(holder.getItemId());
                if (selectedResourceIds.size() > 1 && !isMultiSelectionAllowed()) {
                    changed |= deselectAllOtherItems();
                }
            } else {
                if (selectedResourceIds.contains(holder.getItemId())) {
                    if (isAllowItemDeselection(holder.getItemId())) {
                        changed = selectedResourceIds.remove(holder.getItemId());
                    } else {
                        // re-check the button.
                        buttonView.setChecked(true);
                    }
                }
            }
            if (changed) {
                multiSelectStatusListener.onItemSelectionCountChanged(adapter, selectedResourceIds.size());
            }
        }

        private boolean deselectAllOtherItems() {
            boolean changed = false;
            Iterator<Long> iter = selectedResourceIds.iterator();
            while (iter.hasNext()) {
                Long selectedId = iter.next();
                if (!selectedId.equals(holder.getItemId()) && isAllowItemDeselection(selectedId)) {
                    iter.remove();
                    notifyItemChanged(getItemPosition(getItemById(selectedId)));
                    changed = true;
                }
            }
            return changed;
        }
    }

}
