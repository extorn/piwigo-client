package delit.piwigoclient.ui.permissions.users;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import java.util.Arrays;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoUsers;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.ui.common.recyclerview.BaseViewHolder;
import delit.piwigoclient.ui.common.recyclerview.IdentifiableListViewAdapter;

/**
 * {@link RecyclerView.Adapter} that can display a {@link User}
 */
public class UserRecyclerViewAdapter extends IdentifiableListViewAdapter<User, PiwigoUsers, UserRecyclerViewAdapter.UserViewHolder> {

    private final List<String> userTypes;
    private final List<String> userTypeValues;

    public UserRecyclerViewAdapter(final Context context, final PiwigoUsers users, MultiSelectStatusListener multiSelectStatusListener, boolean captureActionClicks) {
        super(users, multiSelectStatusListener, captureActionClicks);
        userTypes = Arrays.asList(context.getResources().getStringArray(R.array.user_types_array));
        userTypeValues = Arrays.asList(context.getResources().getStringArray(R.array.user_types_values_array));
    }

    @Override
    public UserViewHolder buildViewHolder(View view) {
        return new UserViewHolder(view);
    }

    public class UserViewHolder extends BaseViewHolder<User> {

        public UserViewHolder(View view) {
            super(view);
        }

        public void fillValues(Context context, User newItem, boolean allowItemDeletion) {
            setItem(newItem);
            getTxtTitle().setText(newItem.getUsername());

            String userType = userTypes.get(userTypeValues.indexOf(newItem.getUserType()));

            getDetailsTitle().setText(userType);
            if (!allowItemDeletion) {
                getDeleteButton().setVisibility(View.GONE);
            }
            getCheckBox().setVisibility(isCaptureActionClicks() ? View.VISIBLE : View.GONE);
            getCheckBox().setChecked(getSelectedItemIds().contains(newItem.getId()));
            getCheckBox().setEnabled(isEnabled());
        }
    }
}
