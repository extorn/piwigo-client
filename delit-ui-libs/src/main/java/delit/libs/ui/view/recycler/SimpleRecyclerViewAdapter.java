package delit.libs.ui.view.recycler;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public abstract class SimpleRecyclerViewAdapter<LVA extends SimpleRecyclerViewAdapter<LVA,T,P,VH,MSL>, T, P extends BaseRecyclerViewAdapterPreferences<P>, VH extends CustomViewHolder<VH, LVA, P,T,MSL>, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<MSL,LVA,P,T,VH>> extends BaseRecyclerViewAdapter<LVA, P, T, VH, MSL> {

    private List<T> itemStore;

    /**
     * WARNING: setStableIds must be false unless they are stable.
     * They are stable here unless the item store is sorted
     * @param multiSelectStatusListener
     * @param prefs
     */
    public SimpleRecyclerViewAdapter(MSL multiSelectStatusListener, P prefs) {
        super(multiSelectStatusListener, prefs);
        setHasStableIds(true);
    }

    public void setItems(List<T> items) {
        this.itemStore = items;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public long getItemId(T item) {
        return itemStore.indexOf(item);
    }

    @Override
    protected T getItemById(@NonNull Long selectedId) {
        return itemStore.get(selectedId.intValue());
    }

    @Override
    public int getItemPosition(@NonNull T item) {
        return itemStore.indexOf(item);
    }

    @Override
    protected T removeItemFromInternalStore(int idxToRemove) {
        return itemStore.remove(idxToRemove);
    }

    @Override
    protected void replaceItemInInternalStore(int idxToReplace, @NonNull T newItem) {
        itemStore.remove(idxToReplace);
        itemStore.add(idxToReplace, newItem);
    }

    @Override
    protected @NonNull T getItemFromInternalStoreMatching(@NonNull T item) {
        int idx = itemStore.indexOf(item);
        return itemStore.get(idx);
    }

    @Override
    protected void addItemToInternalStore(@NonNull T item) {
        itemStore.add(item);
    }

    @NonNull
    @Override
    public T getItemByPosition(int position) {
        return itemStore.get(position);
    }

    @Override
    public HashSet<Long> getItemsSelectedButNotLoaded() {
        HashSet<Long> loadedSelectedItemIds = new HashSet<>(getSelectedItemIds());
        for (T item : itemStore) {
            loadedSelectedItemIds.remove(getItemId(item));
        }
        return loadedSelectedItemIds;
    }

    @Override
    public int getItemCount() {
        return itemStore.size();
    }

    @Override
    public boolean isHolderOutOfSync(VH holder, T newItem) {
        return !Objects.equals(holder.getItem(),newItem);
    }

}
