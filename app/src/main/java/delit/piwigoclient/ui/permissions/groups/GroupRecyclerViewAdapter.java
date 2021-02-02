package delit.piwigoclient.ui.permissions.groups;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.ui.view.recycler.BaseViewHolder;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoGroups;
import delit.piwigoclient.ui.common.recyclerview.IdentifiableListViewAdapter;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Group}
 */

//public class GroupRecyclerViewAdapter extends IdentifiableListViewAdapter<GroupRecyclerViewAdapter.GroupViewAdapterPreferences, Group, PiwigoGroups, GroupRecyclerViewAdapter.GroupViewHolder, BaseRecyclerViewAdapter.MultiSelectStatusListener<Group>> {
public class GroupRecyclerViewAdapter<RVA extends GroupRecyclerViewAdapter<RVA, VH,MSL>, VH extends GroupRecyclerViewAdapter.GroupViewHolder<VH, RVA,MSL>, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<MSL,RVA, GroupRecyclerViewAdapter.GroupViewAdapterPreferences,Group,VH>> extends IdentifiableListViewAdapter<RVA, GroupRecyclerViewAdapter.GroupViewAdapterPreferences, Group, PiwigoGroups, VH, MSL> {

    public GroupRecyclerViewAdapter(Context context, final PiwigoGroups groups, MSL multiSelectStatusListener, GroupViewAdapterPreferences prefs) {
        super(context, null, groups, multiSelectStatusListener, prefs);
    }

    @NonNull
    @Override
    public VH buildViewHolder(View view, int viewType) {
        return (VH) new GroupViewHolder<>(view);
    }

    public static class GroupViewAdapterPreferences extends BaseRecyclerViewAdapterPreferences<GroupViewAdapterPreferences> {
        public GroupViewAdapterPreferences(Bundle bundle) {
            loadFromBundle(bundle);
        }

        @Override
        protected String getBundleName() {
            return "GroupViewAdapterPreferences";
        }
    }

    public static class GroupViewHolder<VH extends GroupViewHolder<VH, LVA,MSL>, LVA extends GroupRecyclerViewAdapter<LVA,VH,MSL>, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<MSL,LVA,GroupViewAdapterPreferences,Group,VH>> extends BaseViewHolder<VH, GroupViewAdapterPreferences, Group, LVA,MSL> {

        private GroupViewAdapterPreferences adapterPrefs;

        public GroupViewHolder(View view) {
            super(view);
        }

        @Override
        public void cacheViewFieldsAndConfigure(GroupViewAdapterPreferences adapterPrefs) {
            super.cacheViewFieldsAndConfigure(adapterPrefs);
            this.adapterPrefs = adapterPrefs;
        }

        public void fillValues(Group newItem, boolean allowItemDeletion) {
            setItem(newItem);
            getTxtTitle().setText(newItem.getName());
            getDetailsTitle().setText(String.format(itemView.getContext().getString(R.string.group_members_pattern), newItem.getMemberCount()));
            if (!allowItemDeletion) {
                getDeleteButton().setVisibility(View.GONE);
            }
            getCheckBox().setVisibility(adapterPrefs.isMultiSelectionEnabled() ? View.VISIBLE : View.GONE);
//            getCheckBox().setChecked(adapterPrefs.getSelectedItemIds().contains(newItem.getId()));
            getCheckBox().setEnabled(adapterPrefs.isEnabled());
        }
    }

}
