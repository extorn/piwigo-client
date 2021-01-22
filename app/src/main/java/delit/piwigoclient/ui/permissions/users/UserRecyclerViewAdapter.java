package delit.piwigoclient.ui.permissions.users;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.ui.view.recycler.BaseViewHolder;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoUsers;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.ui.common.recyclerview.IdentifiableListViewAdapter;

/**
 * {@link RecyclerView.Adapter} that can display a {@link User}
 */
public class UserRecyclerViewAdapter<RVA extends UserRecyclerViewAdapter<RVA, VH, MSL>, VH extends UserRecyclerViewAdapter.UserViewHolder<VH, RVA,MSL>, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<MSL,RVA,User>> extends IdentifiableListViewAdapter<RVA,UserRecyclerViewAdapter.UserRecyclerViewAdapterPreferences, User, PiwigoUsers, VH, MSL> {

    private final List<String> userTypes;
    private final List<String> userTypeValues;

    protected static class UserRecyclerViewAdapterPreferences extends BaseRecyclerViewAdapterPreferences<UserRecyclerViewAdapterPreferences>{}

    public UserRecyclerViewAdapter(final Context context, final PiwigoUsers users, MSL multiSelectStatusListener, UserRecyclerViewAdapterPreferences prefs) {
        super(context, null, users, multiSelectStatusListener, prefs);
        userTypes = Arrays.asList(context.getResources().getStringArray(R.array.user_types_array));
        userTypeValues = Arrays.asList(context.getResources().getStringArray(R.array.user_types_values_array));
    }

    @NonNull
    @Override
    public VH buildViewHolder(View view, int viewType) {
        return (VH)new UserViewHolder<>(view, userTypes, userTypeValues);
    }

    public static class UserViewHolder<VH extends UserViewHolder<VH, LVA,MSL>, LVA extends UserRecyclerViewAdapter<LVA,VH,MSL>, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<MSL,LVA,User>> extends BaseViewHolder<VH,UserRecyclerViewAdapterPreferences, User, LVA,MSL> {

        private final List<String> userTypes;
        private final List<String> userTypeValues;
        private UserRecyclerViewAdapterPreferences adapterPrefs;

        public UserViewHolder(View view, List<String> userTypes, List<String> userTypeValues) {
            super(view);
            this.userTypes = userTypes;
            this.userTypeValues = userTypeValues;
        }

        @Override
        public void cacheViewFieldsAndConfigure(UserRecyclerViewAdapterPreferences adapterPrefs) {
            super.cacheViewFieldsAndConfigure(adapterPrefs);
            this.adapterPrefs = adapterPrefs;
        }

        public void fillValues(User newItem, boolean allowItemDeletion) {
            setItem(newItem);
            getTxtTitle().setText(newItem.getUsername());
            String userType = userTypes.get(userTypeValues.indexOf(newItem.getUserType()));

            getDetailsTitle().setText(userType);
            if (!allowItemDeletion) {
                getDeleteButton().setVisibility(View.GONE);
            }
            getCheckBox().setVisibility(adapterPrefs.isMultiSelectionEnabled() ? View.VISIBLE : View.GONE);
//            getCheckBox().setChecked(adapterPrefs.getSelectedItemIds().contains(newItem.getId()));
            getCheckBox().setEnabled(adapterPrefs.isEnabled());
        }
    }
}
