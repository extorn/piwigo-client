package delit.piwigoclient.ui.permissions.groups;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoGroups;
import delit.piwigoclient.ui.common.recyclerview.BaseViewHolder;
import delit.piwigoclient.ui.common.recyclerview.IdentifiableListViewAdapter;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Group}
 */
public class GroupRecyclerViewAdapter extends IdentifiableListViewAdapter<Group, PiwigoGroups, GroupRecyclerViewAdapter.GroupViewHolder> {

    public GroupRecyclerViewAdapter(final PiwigoGroups groups, MultiSelectStatusListener multiSelectStatusListener, boolean captureActionClicks) {
        super(groups, multiSelectStatusListener, captureActionClicks);
    }

    @Override
    public GroupViewHolder buildViewHolder(View view) {
        return new GroupViewHolder(view);
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
