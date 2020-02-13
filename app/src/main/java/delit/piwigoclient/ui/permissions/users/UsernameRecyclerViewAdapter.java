package delit.piwigoclient.ui.permissions.users;

import android.content.Context;
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
public class UsernameRecyclerViewAdapter extends IdentifiableListViewAdapter<BaseRecyclerViewAdapterPreferences, Username, PiwigoUsernames, UsernameRecyclerViewAdapter.UsernameViewHolder, BaseRecyclerViewAdapter.MultiSelectStatusListener<Username>> {

    private final List<String> userTypes;
    private final List<String> userTypeValues;
    private final HashSet<Long> indirectlySelectedItems;


    public UsernameRecyclerViewAdapter(final Context context, final PiwigoUsernames usernames, HashSet<Long> indirectlySelectedItems, MultiSelectStatusListener<Username> multiSelectStatusListener, BaseRecyclerViewAdapterPreferences prefs) {
        super(null, usernames, multiSelectStatusListener, prefs);
        setContext(context);
        this.indirectlySelectedItems = indirectlySelectedItems;
        userTypes = Arrays.asList(context.getResources().getStringArray(R.array.user_types_array));
        userTypeValues = Arrays.asList(context.getResources().getStringArray(R.array.user_types_values_array));
    }

    @NonNull
    @Override
    public UsernameViewHolder buildViewHolder(View view, int viewType) {
        return new UsernameViewHolder(view);
    }

    public class UsernameViewHolder extends BaseViewHolder<BaseRecyclerViewAdapterPreferences, Username> {

        public UsernameViewHolder(View view) {
            super(view);
        }

        public void fillValues(Context context, Username newItem, boolean allowItemDeletion) {
            setItem(newItem);
            getTxtTitle().setText(newItem.getUsername());

            String userType = userTypes.get(userTypeValues.indexOf(newItem.getUserType()));

            getDetailsTitle().setText(userType);
            if (!allowItemDeletion) {
                getDeleteButton().setVisibility(View.GONE);
            }
            getCheckBox().setVisibility(isMultiSelectionAllowed() ? View.VISIBLE : View.GONE);
            getCheckBox().setAlwaysChecked(indirectlySelectedItems.contains(newItem.getId()));
            getCheckBox().setChecked(getSelectedItemIds().contains(newItem.getId()));
            getCheckBox().setEnabled(isEnabled());
        }
    }
}