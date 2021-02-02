package delit.piwigoclient.ui.permissions.users;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.ui.view.recycler.BaseViewHolder;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoUsernames;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.ui.common.recyclerview.IdentifiableListViewAdapter;

/**
 * {@link androidx.recyclerview.widget.RecyclerView.Adapter} that can display a {@link Username}
 */
public class UsernameRecyclerViewAdapter<LVA extends UsernameRecyclerViewAdapter<LVA,T,VH,MSL>, T extends Username, VH extends UsernameRecyclerViewAdapter.UsernameViewHolder<VH, LVA,T,MSL>, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<MSL,LVA,UsernameRecyclerViewAdapter.UsernameRecyclerViewAdapterPreferences,T,VH>> extends IdentifiableListViewAdapter<LVA, UsernameRecyclerViewAdapter.UsernameRecyclerViewAdapterPreferences, T, PiwigoUsernames<T>, VH, MSL> {

    private final List<String> userTypes;
    private final List<String> userTypeValues;
    private final HashSet<Long> indirectlySelectedItems;

    public static class UsernameRecyclerViewAdapterPreferences extends BaseRecyclerViewAdapterPreferences<UsernameRecyclerViewAdapterPreferences> {
        public UsernameRecyclerViewAdapterPreferences(Bundle bundle) {
            loadFromBundle(bundle);
        }

        @Override
        protected String getBundleName() {
            return "UsernameRecyclerViewAdapterPreferences";
        }
    }

    public UsernameRecyclerViewAdapter(final Context context, final PiwigoUsernames<T> usernames, HashSet<Long> indirectlySelectedItems, MSL multiSelectStatusListener, UsernameRecyclerViewAdapterPreferences prefs) {
        super(context, null, usernames, multiSelectStatusListener, prefs);
        this.indirectlySelectedItems = indirectlySelectedItems;
        userTypes = Arrays.asList(context.getResources().getStringArray(R.array.user_types_array));
        userTypeValues = Arrays.asList(context.getResources().getStringArray(R.array.user_types_values_array));
    }

    @NonNull
    @Override
    public VH buildViewHolder(View view, int viewType) {
        return (VH) new UsernameViewHolder<>(view, (LVA) this);
    }

    String getUserType(String itemUserTypeVal) {
        return userTypes.get(userTypeValues.indexOf(itemUserTypeVal));
    }

    public static class UsernameViewHolder<VH extends UsernameViewHolder<VH, LVA,T,MSL>, LVA extends UsernameRecyclerViewAdapter<LVA,T,VH,MSL>, T extends Username, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<MSL,LVA,UsernameRecyclerViewAdapter.UsernameRecyclerViewAdapterPreferences,T,VH>> extends BaseViewHolder<VH, UsernameRecyclerViewAdapterPreferences, T, LVA,MSL> {

        LVA parentAdapter;

        public UsernameViewHolder(View view, LVA parentAdapter) {
            super(view);
            this.parentAdapter = parentAdapter;
        }

        String getUserType(String itemUserTypeVal) {
            return parentAdapter.getUserType(itemUserTypeVal);
        }

        boolean isIndirectlySelected(long id) {
            return parentAdapter.isIndirectlySelected(id);
        }

        public void fillValues(T newItem, boolean allowItemDeletion) {
            setItem(newItem);
            getTxtTitle().setText(newItem.getUsername());

            String userType = getUserType(newItem.getUserType());

            getDetailsTitle().setText(userType);
            if (!allowItemDeletion) {
                getDeleteButton().setVisibility(View.GONE);
            }
            getCheckBox().setVisibility(parentAdapter.isMultiSelectionAllowed() ? View.VISIBLE : View.GONE);
            getCheckBox().setAlwaysChecked(isIndirectlySelected(newItem.getId()));
            getCheckBox().setChecked(parentAdapter.getSelectedItemIds().contains(newItem.getId()));
            getCheckBox().setEnabled(parentAdapter.isEnabled());
        }
    }

    boolean isIndirectlySelected(long itemId) {
        return indirectlySelectedItems.contains(itemId);
    }
}