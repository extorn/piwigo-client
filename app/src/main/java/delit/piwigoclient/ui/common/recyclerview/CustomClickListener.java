package delit.piwigoclient.ui.common.recyclerview;

import android.view.View;

public class CustomClickListener<V extends BaseRecyclerViewAdapterPreferences, T, S extends CustomViewHolder<V, T>> implements View.OnClickListener, View.OnLongClickListener {

    private final S viewHolder;
    private final BaseRecyclerViewAdapter<V, T, S> parentAdapter;

    public CustomClickListener(S viewHolder, BaseRecyclerViewAdapter<V, T, S> parentAdapter) {
        this.viewHolder = viewHolder;
        this.parentAdapter = parentAdapter;
    }

    @Override
    public void onClick(View v) {
        if (!parentAdapter.isEnabled()) {
            return;
        }
        //TODO Note - the way this works, click event is sunk if item selection is enabled... allow override?
       if (parentAdapter.isMultiSelectionAllowed()) {
//                 multi selection mode is enabled.
            if (parentAdapter.getSelectedItemIds().contains(viewHolder.getItemId())) {
                viewHolder.setChecked(false);
            } else {
                viewHolder.setChecked(true);
            }
            //TODO Not sure why we'd call this?
            viewHolder.itemView.setPressed(false);
        } else if (parentAdapter.isItemSelectionAllowed()) {
           //If not currently in multiselect mode
           parentAdapter.getMultiSelectStatusListener().onItemClick(parentAdapter, viewHolder.getItem());
       }
    }

    @Override
    public boolean onLongClick(View v) {
        if (!parentAdapter.isEnabled()) {
            return false;
        }
        parentAdapter.getMultiSelectStatusListener().onItemLongClick(parentAdapter, viewHolder.getItem());
        return true;
    }

    public BaseRecyclerViewAdapter<V, T, S> getParentAdapter() {
        return parentAdapter;
    }
}