package delit.libs.ui.view.recycler;

import android.util.Log;
import android.view.View;

import delit.libs.core.util.Logging;

public class CustomClickListener<MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<T>, LVA extends BaseRecyclerViewAdapter<LVA, P, T, VH, MSL>, P extends BaseRecyclerViewAdapterPreferences<P>, T, VH extends CustomViewHolder<VH, LVA, P, T,MSL>> implements View.OnClickListener, View.OnLongClickListener {

    private final VH viewHolder;
    private final LVA parentAdapter;

    public CustomClickListener(VH viewHolder, LVA parentAdapter) {
        this.viewHolder = viewHolder;
        this.parentAdapter = parentAdapter;
    }

    public VH getViewHolder() {
        return viewHolder;
    }

    @Override
    public void onClick(View v) {
        if (!parentAdapter.isEnabled()) {
            if(parentAdapter.getMultiSelectStatusListener() != null) {
                parentAdapter.getMultiSelectStatusListener().onDisabledItemClick(parentAdapter, viewHolder.getItem());
            } else {
                Logging.log(Log.ERROR, "CustomClickListener", "Adapter of type "+parentAdapter.getClass().getName()+" does not have a mulit select status listener");
            }
            return;
        }
        //TODO Note - the way this works, click event is sunk if item selection is enabled... allow override?
        if (parentAdapter.isMultiSelectionAllowed() || parentAdapter.isItemSelectionAllowed()) { //TODO I've added the isItemSelectionAllowed - need to check works ok in dialogs etc
//                 multi selection mode is enabled.
            if (parentAdapter.getSelectedItemIds().contains(viewHolder.getItemId())) {
                viewHolder.setChecked(false);
            } else {
                viewHolder.setChecked(true);
            }
            //TODO Not sure why we'd call this? (probably when used in a dialog to stop it causing a submit action and closing the dialog instantly)
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

    public LVA getParentAdapter() {
        return parentAdapter;
    }

    /**
     * Called when the values are added to the view holder
     */
    public void onFillValues() {
    }
}