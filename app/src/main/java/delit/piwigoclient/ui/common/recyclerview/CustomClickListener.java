package delit.piwigoclient.ui.common.recyclerview;

import android.view.View;

public class CustomClickListener<T, S extends CustomViewHolder<T>> implements View.OnClickListener, View.OnLongClickListener {

    private final S viewHolder;
    private final BaseRecyclerViewAdapter<T, S> parentAdapter;

    public CustomClickListener(S viewHolder, BaseRecyclerViewAdapter<T, S> parentAdapter) {
        this.viewHolder = viewHolder;
        this.parentAdapter = parentAdapter;
    }

    @Override
    public void onClick(View v) {
        if (!parentAdapter.isEnabled()) {
            return;
        }
        //TODO Note - the way this works, click event is sunk if item selection is enabled... allow override?
        if (!parentAdapter.isItemSelectionAllowed()) {
            //If not currently in multiselect mode
            parentAdapter.getMultiSelectStatusListener().onItemClick((T) viewHolder.getItem());
        } else if (parentAdapter.isCaptureActionClicks()) {
//                 multi selection mode is enabled.
            if (parentAdapter.getSelectedItemIds().contains(viewHolder.getItemId())) {
                viewHolder.setChecked(false);
            } else {
                viewHolder.setChecked(true);
            }
            //TODO Not sure why we'd call this?
            viewHolder.itemView.setPressed(false);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (!parentAdapter.isEnabled()) {
            return false;
        }
        parentAdapter.getMultiSelectStatusListener().onItemLongClick(viewHolder.getItem());
        return true;
    }

    public BaseRecyclerViewAdapter<T, S> getParentAdapter() {
        return parentAdapter;
    }
}