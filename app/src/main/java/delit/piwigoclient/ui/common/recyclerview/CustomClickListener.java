package delit.piwigoclient.ui.common.recyclerview;

import android.util.Log;
import android.view.View;

import com.crashlytics.android.Crashlytics;

public class CustomClickListener<V extends BaseRecyclerViewAdapterPreferences, T, S extends CustomViewHolder<V, T>> implements View.OnClickListener, View.OnLongClickListener {

    private final S viewHolder;
    private final BaseRecyclerViewAdapter<V, T, S> parentAdapter;

    public <Q extends BaseRecyclerViewAdapter<V, T, S>> CustomClickListener(S viewHolder, Q parentAdapter) {
        this.viewHolder = viewHolder;
        this.parentAdapter = parentAdapter;
    }

    public S getViewHolder() {
        return viewHolder;
    }

    @Override
    public void onClick(View v) {
        if (!parentAdapter.isEnabled()) {
            if(parentAdapter.getMultiSelectStatusListener() != null) {
                parentAdapter.getMultiSelectStatusListener().onDisabledItemClick(parentAdapter, viewHolder.getItem());
            } else {
                Crashlytics.log(Log.ERROR, "CustomClickListener", "Adapter of type "+parentAdapter.getClass().getName()+" does not have a mulit select status listener");
            }
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
        } else if (!parentAdapter.isItemSelectionAllowed()) {
            //If not currently in list item selection mode
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

    public <Q extends BaseRecyclerViewAdapter<V, T, S>> Q getParentAdapter() {
        return (Q) parentAdapter;
    }

    /**
     * Called when the values are added to the view holder
     */
    public void onFillValues() {
    }
}