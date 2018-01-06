package delit.piwigoclient.ui.permissions.groups;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;

import java.util.Date;
import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.PiwigoGroups;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapter;
import delit.piwigoclient.ui.common.Enableable;
import delit.piwigoclient.ui.common.SelectableItemsAdapter;
import delit.piwigoclient.ui.common.SquareLinearLayout;
import delit.piwigoclient.ui.events.AlbumItemSelectedEvent;
import delit.piwigoclient.ui.events.ViewGroupEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionCompleteEvent;

import static android.view.View.GONE;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GalleryItem}
 */
public class GroupRecyclerViewAdapter extends RecyclerView.Adapter<GroupRecyclerViewAdapter.ViewHolder> implements Enableable, SelectableItemsAdapter<Group> {

    private final PiwigoGroups groups;
    private Context context;
    private boolean allowItemSelection;
    private MultiSelectStatusListener multiSelectStatusListener;
    private HashSet<Long> selectedResourceIds = new HashSet<>();
    private boolean captureActionClicks;
    private boolean allowItemDeletion;
    private boolean enabled;


    public GroupRecyclerViewAdapter(final PiwigoGroups groups, MultiSelectStatusListener multiSelectStatusListener, boolean captureActionClicks) {
        this.groups = groups;
        this.setHasStableIds(true);
        this.multiSelectStatusListener = multiSelectStatusListener;
        this.captureActionClicks = captureActionClicks;
    }

    public void setAllowItemDeletion(boolean allowItemDeletion) {
        this.allowItemDeletion = allowItemDeletion;
    }

    public void setCaptureActionClicks(boolean captureActionClicks) {

        this.captureActionClicks = captureActionClicks;
        if(!captureActionClicks) {
            if (allowItemSelection) {
                toggleItemSelection();
            }
        }
    }

    @Override
    public long getItemId(int position) {
        return groups.getItems().get(position).getId();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.actionable_list_item_layout, parent, false);

        final ViewHolder viewHolder = new ViewHolder(view);

        return viewHolder;
    }

    @Override
    public int getItemViewType(int position) {
        //groups.getItems().get(position).getType();
        // override this method for multiple item types
        return super.getItemViewType(position);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        Group newItem = groups.getItems().get(position);
        if (holder.getOldPosition() < 0 && holder.getItem() != null && holder.getItem().getId() == newItem.getId()) {
            // rendering the same item.
            return;
        }

        // store the item in this recyclable holder.
        holder.fillValues(getContext(), newItem, allowItemDeletion);

    }

    private Context getContext() {
        return context;
    }

    @Override
    public int getItemCount() {
        return groups.getItems().size();
    }

    public void toggleItemSelection() {
        this.allowItemSelection = !allowItemSelection;
        if(!allowItemSelection) {
            selectedResourceIds.clear();
        }
        notifyItemRangeChanged(0, getItemCount());
        multiSelectStatusListener.onMultiSelectStatusChanged(allowItemSelection);
        multiSelectStatusListener.onItemSelectionCountChanged(selectedResourceIds.size());
    }

    public boolean isItemSelectionAllowed() {
        return allowItemSelection;
    }

    @Override
    public HashSet<Long> getSelectedItemIds() {
        return selectedResourceIds;
    }

    @Override
    public HashSet<Group> getSelectedItems() {
        HashSet<Group> selectedItems = new HashSet<>();
        for(Long selectedItemId : selectedResourceIds) {
            selectedItems.add(groups.getGroupById(selectedItemId));
        }
        return selectedItems;
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
        int idx = groups.getItems().indexOf(groups.getGroupById(selectedItemId));
        notifyItemChanged(idx);
    }

    public void remove(Group group) {
        int idxRemoved = groups.getItems().indexOf(group);
        groups.getItems().remove(idxRemoved);
        notifyItemRemoved(idxRemoved);
    }

    public void replaceOrAddItem(Group group) {
        Group itemToBeReplaced = null;
        try {
            itemToBeReplaced = groups.getGroupById(group.getId());
        } catch(IllegalArgumentException e) {
            // thrown if the group isn't present.
        }
        if(itemToBeReplaced != null) {
            int replaceIdx = groups.getItems().indexOf(itemToBeReplaced);
            groups.getItems().remove(replaceIdx);
            groups.getItems().add(replaceIdx, group);
        } else {
            groups.getItems().add(group);

        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean changed = false;
        if(this.enabled != enabled) {
            changed = true;
        }
        this.enabled = enabled;
        if(changed) {
            notifyDataSetChanged();
        }
    }

    public Group getItemById(Long selectedId) {
        return groups.getGroupById(selectedId);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
//        public final AppCompatCheckBox checkBox;
        private final TextView txtTitle;
        private final TextView detailsTitle;
        private final View deleteButton;
        private final AppCompatCheckBox checkBox;
        private Group item;
        public CustomClickListener itemActionListener;

        public ViewHolder(View view) {
            super(view);

            checkBox = view.findViewById(R.id.checked);
            checkBox.setClickable(allowItemSelection);
            checkBox.setOnCheckedChangeListener(new ItemSelectionListener(this));

            txtTitle = view.findViewById(R.id.name);

            detailsTitle = view.findViewById(R.id.details);

            deleteButton = view.findViewById(R.id.list_item_delete_button);
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDeleteItem(ViewHolder.this, v);
                }
            });

            itemActionListener = new CustomClickListener(this);
            itemView.setOnClickListener(itemActionListener);
            itemView.setOnLongClickListener(itemActionListener);
        }

        public Group getItem() {
            return item;
        }

        @Override
        public String toString() {
            return super.toString() + " '" + txtTitle.getText() + "'";
        }

        public void fillValues(Context context, Group newItem, boolean allowItemDeletion) {
            this.item = newItem;
            txtTitle.setText(newItem.getName());
            detailsTitle.setText(String.format(getContext().getString(R.string.group_members_pattern), newItem.getMemberCount()));
            if(!allowItemDeletion) {
                deleteButton.setVisibility(View.GONE);
            }
            checkBox.setVisibility(captureActionClicks?View.VISIBLE:View.GONE);
            checkBox.setChecked(selectedResourceIds.contains(newItem.getId()));
            checkBox.setEnabled(enabled);
        }
    }

    public interface MultiSelectStatusListener {
        void onMultiSelectStatusChanged(boolean multiSelectEnabled);

        void onItemSelectionCountChanged(int size);

        void onItemDeleteRequested(Group g);

//        void onCategoryLongClick(CategoryItem album);
    }

    private class ItemSelectionListener implements CompoundButton.OnCheckedChangeListener {

        private ViewHolder holder;

        public ItemSelectionListener(ViewHolder holder) {
            this.holder = holder;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            boolean changed = false;
            if(isChecked) {
                changed = selectedResourceIds.add(holder.getItemId());
            } else {
                changed = selectedResourceIds.remove(holder.getItemId());
            }
            if(changed) {
                multiSelectStatusListener.onItemSelectionCountChanged(selectedResourceIds.size());
            }
        }
    }

    private void onDeleteItem(ViewHolder viewHolder, View v) {
        multiSelectStatusListener.onItemDeleteRequested(viewHolder.getItem());
    }

    private class CustomClickListener implements View.OnClickListener, View.OnLongClickListener {

        private final ViewHolder viewHolder;

        public CustomClickListener(ViewHolder viewHolder) {
            this.viewHolder = viewHolder;
        }

        @Override
        public void onClick(View v) {
            if(!enabled) {
                return;
            }
            if (!allowItemSelection) {
                //If not currently in multiselect mode
                EventBus.getDefault().post(new ViewGroupEvent(viewHolder.getItem()));
            } else if (captureActionClicks) {
//                 multi selection mode is enabled.
                if (selectedResourceIds.contains(viewHolder.getItemId())) {
                    viewHolder.checkBox.setChecked(false);
                } else {
                    viewHolder.checkBox.setChecked(true);
                }
                //TODO Not sure why we'd call this?
                viewHolder.itemView.setPressed(false);
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if(!enabled) {
                return false;
            }
            // TODO do something on long click if needed.
            return true;
        }
    }

}
