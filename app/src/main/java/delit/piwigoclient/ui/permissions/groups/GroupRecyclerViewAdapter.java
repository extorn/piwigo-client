package delit.piwigoclient.ui.permissions.groups;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoGroups;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.common.recyclerview.BaseViewHolder;
import delit.piwigoclient.ui.common.recyclerview.IdentifiableListViewAdapter;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Group}
 */

public class GroupRecyclerViewAdapter extends IdentifiableListViewAdapter<BaseRecyclerViewAdapterPreferences, Group, PiwigoGroups, GroupRecyclerViewAdapter.GroupViewHolder, BaseRecyclerViewAdapter.MultiSelectStatusListener<Group>> {

    public GroupRecyclerViewAdapter(final PiwigoGroups groups, MultiSelectStatusListener<Group> multiSelectStatusListener, BaseRecyclerViewAdapterPreferences prefs) {
        super(null, groups, multiSelectStatusListener, prefs);
    }

    @NonNull
    @Override
    public GroupViewHolder buildViewHolder(View view, int viewType) {
        return new GroupViewHolder(view);
    }

    public class GroupViewHolder extends BaseViewHolder<BaseRecyclerViewAdapterPreferences, Group> {

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
            getCheckBox().setVisibility(isMultiSelectionAllowed() ? View.VISIBLE : View.GONE);
            getCheckBox().setChecked(getSelectedItemIds().contains(newItem.getId()));
            getCheckBox().setEnabled(isEnabled());
        }
    }

}
