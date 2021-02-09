package delit.libs.ui.view.recycler;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

public abstract class CustomViewHolder<VH extends CustomViewHolder<VH, LVA, P,T,MSL>, LVA extends BaseRecyclerViewAdapter<LVA, P,T,VH,MSL>, P extends BaseRecyclerViewAdapterPreferences<P>, T, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<MSL,LVA,P,T,VH>> extends RecyclerView.ViewHolder {

    private T item;
    private CustomClickListener<MSL, LVA, P,T,VH> itemActionListener;

    public CustomViewHolder(View view) {
        super(view);
    }

    public abstract void fillValues(T item, boolean allowItemDeletion);

    public abstract void cacheViewFieldsAndConfigure(P adapterPrefs);

    public abstract void setChecked(boolean checked);

    public T getItem() {
        return item;
    }

    public void setItem(T item) {
        this.item = item;
    }

    public void internalCacheViewFieldsAndConfigure(CustomClickListener<MSL, LVA, P, T, VH> itemActionListener, P adapterPrefs) {
        this.itemActionListener = itemActionListener;
        itemView.setOnClickListener(itemActionListener);
        itemView.setOnLongClickListener(itemActionListener);
        cacheViewFieldsAndConfigure(adapterPrefs);
    }

    public CustomClickListener<MSL,LVA, P,T,VH> getItemActionListener() {
        return itemActionListener;
    }

    public void redisplayOldValues(T currentItem, boolean allowItemDeletion) {
    }
}