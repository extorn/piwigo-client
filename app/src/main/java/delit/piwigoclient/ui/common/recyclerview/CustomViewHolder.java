package delit.piwigoclient.ui.common.recyclerview;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;

public abstract class CustomViewHolder<V extends BaseRecyclerViewAdapterPreferences, T> extends RecyclerView.ViewHolder {

    private T item;
    private CustomClickListener itemActionListener;

    public CustomViewHolder(View view) {
        super(view);
    }

    public abstract void fillValues(Context context, T item, boolean allowItemDeletion);

    public abstract void cacheViewFieldsAndConfigure();

    public abstract void setChecked(boolean checked);

    public T getItem() {
        return item;
    }

    public void setItem(T item) {
        this.item = item;
    }

    public void internalCacheViewFieldsAndConfigure(CustomClickListener<V, T, ? extends CustomViewHolder<V, T>> itemActionListener) {
        this.itemActionListener = itemActionListener;
        itemView.setOnClickListener(itemActionListener);
        itemView.setOnLongClickListener(itemActionListener);
        cacheViewFieldsAndConfigure();
    }

    public CustomClickListener getItemActionListener() {
        return itemActionListener;
    }

    public <T> void redisplayOldValues(Context context, T newItem, boolean allowItemDeletion) {
    }
}