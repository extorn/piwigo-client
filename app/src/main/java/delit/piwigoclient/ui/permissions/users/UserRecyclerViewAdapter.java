package delit.piwigoclient.ui.permissions.users;

import android.content.Context;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.model.piwigo.PiwigoUsers;
import delit.piwigoclient.ui.common.Enableable;
import delit.piwigoclient.ui.common.SelectableItemsAdapter;
import delit.piwigoclient.ui.events.ViewUserEvent;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GalleryItem}
 */
public class UserRecyclerViewAdapter extends RecyclerView.Adapter<UserRecyclerViewAdapter.ViewHolder> implements Enableable, SelectableItemsAdapter<User> {

    private final PiwigoUsers users;
    private Context context;
    private boolean allowItemSelection;
    private MultiSelectStatusListener multiSelectStatusListener;
    private HashSet<Long> selectedResourceIds = new HashSet<>();
    private boolean captureActionClicks;
    private boolean allowItemDeletion;
    private boolean enabled;
    private final List<String> userTypes;
    private final List<String> userTypeValues;


    public UserRecyclerViewAdapter(Context context, final PiwigoUsers users, MultiSelectStatusListener multiSelectStatusListener, boolean captureActionClicks) {
        this.users = users;
        this.setHasStableIds(true);
        this.multiSelectStatusListener = multiSelectStatusListener;
        this.captureActionClicks = captureActionClicks;
        userTypes = Arrays.asList(context.getResources().getStringArray(R.array.user_types_array));
        userTypeValues = Arrays.asList(context.getResources().getStringArray(R.array.user_types_values_array));
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
        return users.getItems().get(position).getId();
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
        //users.getItems().get(position).getType();
        // override this method for multiple item types
        return super.getItemViewType(position);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        User newItem = users.getItems().get(position);
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
        return users.getItems().size();
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
    public HashSet<User> getSelectedItems() {
        HashSet<User> selectedItems = new HashSet<>();
        for(Long selectedItemId : selectedResourceIds) {
            selectedItems.add(users.getUserById(selectedItemId));
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
        int idx = users.getItems().indexOf(users.getUserById(selectedItemId));
        notifyItemChanged(idx);
    }

    public void remove(User user) {
        int idxRemoved = users.getItems().indexOf(user);
        users.getItems().remove(idxRemoved);
        notifyItemRemoved(idxRemoved);
    }

    public void replaceOrAddItem(User user) {
        User itemToBeReplaced = null;
        try {
            itemToBeReplaced = users.getUserById(user.getId());
        } catch(IllegalArgumentException e) {
            // thrown if the user isn't present.
        }
        if(itemToBeReplaced != null) {
            int replaceIdx = users.getItems().indexOf(itemToBeReplaced);
            users.getItems().remove(replaceIdx);
            users.getItems().add(replaceIdx, user);
        } else {
            users.getItems().add(user);

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

    public User getItemById(Long selectedId) {
        return users.getUserById(selectedId);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
//        public final AppCompatCheckBox checkBox;
        private final TextView txtTitle;
        private final TextView detailsTitle;
        private final View deleteButton;
        private final AppCompatCheckBox checkBox;
        private User item;
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

        public User getItem() {
            return item;
        }

        @Override
        public String toString() {
            return super.toString() + " '" + txtTitle.getText() + "'";
        }

        public void fillValues(Context context, User newItem, boolean allowItemDeletion) {
            this.item = newItem;
            txtTitle.setText(item.getUsername());

            String userType = userTypes.get(userTypeValues.indexOf(item.getUserType()));

            detailsTitle.setText(userType);
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

        void onItemDeleteRequested(User g);

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
                EventBus.getDefault().post(new ViewUserEvent(viewHolder.getItem()));
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
