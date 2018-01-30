package delit.piwigoclient.ui.permissions.groups;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoGroups;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseViewHolder;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Group}
 */
public class GroupRecyclerViewAdapter extends BaseRecyclerViewAdapter<Group, GroupRecyclerViewAdapter.GroupViewHolder> {

    private final PiwigoGroups groups;


    public GroupRecyclerViewAdapter(final PiwigoGroups groups, MultiSelectStatusListener multiSelectStatusListener, boolean captureActionClicks) {
        super(multiSelectStatusListener, captureActionClicks);
        this.groups = groups;
    }

    @Override
    public long getItemId(int position) {
        return groups.getItems().get(position).getId();
    }

    @Override
    public GroupViewHolder buildViewHolder(View view) {
        return new GroupViewHolder(view);
    }

    @Override
    protected void removeItemFromInternalStore(int idxRemoved) {
        groups.getItems().remove(idxRemoved);
    }

    @Override
    public HashSet<Long> getItemsSelectedButNotLoaded() {
        HashSet<Long> loadedSelectedItemIds = new HashSet<>(getSelectedItemIds());
        for(Group group : groups.getItems()) {
            loadedSelectedItemIds.remove(group.getId());
        }
        return loadedSelectedItemIds;
    }

    @Override
    protected void replaceItemInInternalStore(int idxToReplace, Group newItem) {
        groups.getItems().remove(idxToReplace);
        groups.getItems().add(idxToReplace, newItem);
    }

    @Override
    protected Group getItemFromInternalStoreMatching(Group item) {
        return groups.getGroupById(item.getId());
    }

    @Override
    protected void addItemToInternalStore(Group item) {
        groups.getItems().add(item);
    }

    @Override
    public Group getItemByPosition(int position) {
        return groups.getItems().get(position);
    }

    @Override
    public boolean isHolderOutOfSync(GroupViewHolder holder, Group newItem) {
        return !(holder.getOldPosition() < 0 && holder.getItem() != null && holder.getItem().getId() == newItem.getId());
    }

    @Override
    public int getItemCount() {
        return groups.getItems().size();
    }

    @Override
    public Group getItemById(Long selectedId) {
        return groups.getGroupById(selectedId);
    }

    @Override
    protected int getItemPosition(Group item) {
        return groups.getItems().indexOf(item);
    }

    public class GroupViewHolder extends BaseViewHolder<Group> {

        public GroupViewHolder(View view) {
            super(view);
        }

        public void fillValues(Context context, Group newItem, boolean allowItemDeletion) {
            setItem(newItem);
            getTxtTitle().setText(newItem.getName());
            getDetailsTitle().setText(String.format(context.getString(R.string.group_members_pattern), newItem.getMemberCount()));
            if (!allowItemDeletion) {
                getDeleteButton().setVisibility(View.GONE);
            }
            getCheckBox().setVisibility(isCaptureActionClicks() ? View.VISIBLE : View.GONE);
            getCheckBox().setChecked(getSelectedItemIds().contains(newItem.getId()));
            getCheckBox().setEnabled(isEnabled());
        }
    }

}
