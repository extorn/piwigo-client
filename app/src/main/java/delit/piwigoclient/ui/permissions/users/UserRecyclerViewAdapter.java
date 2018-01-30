package delit.piwigoclient.ui.permissions.users;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoUsers;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseViewHolder;

/**
 * {@link RecyclerView.Adapter} that can display a {@link User}
 */
public class UserRecyclerViewAdapter extends BaseRecyclerViewAdapter<User, UserRecyclerViewAdapter.UserViewHolder> {

    private final PiwigoUsers users;
    private final List<String> userTypes;
    private final List<String> userTypeValues;


    public UserRecyclerViewAdapter(final Context context, final PiwigoUsers users, MultiSelectStatusListener multiSelectStatusListener, boolean captureActionClicks) {
        super(multiSelectStatusListener, captureActionClicks);
        this.users = users;
        userTypes = Arrays.asList(context.getResources().getStringArray(R.array.user_types_array));
        userTypeValues = Arrays.asList(context.getResources().getStringArray(R.array.user_types_values_array));
    }

    @Override
    public long getItemId(int position) {
        return users.getItems().get(position).getId();
    }

    @Override
    public UserViewHolder buildViewHolder(View view) {
        return new UserViewHolder(view);
    }

    @Override
    protected void removeItemFromInternalStore(int idxRemoved) {
        users.getItems().remove(idxRemoved);
    }

    @Override
    public HashSet<Long> getItemsSelectedButNotLoaded() {
        HashSet<Long> loadedSelectedItemIds = new HashSet<>(getSelectedItemIds());
        for(User user : users.getItems()) {
            loadedSelectedItemIds.remove(user.getId());
        }
        return loadedSelectedItemIds;
    }

    @Override
    protected void replaceItemInInternalStore(int idxToReplace, User newItem) {
        users.getItems().remove(idxToReplace);
        users.getItems().add(idxToReplace, newItem);
    }

    @Override
    protected User getItemFromInternalStoreMatching(User item) {
        return users.getUserById(item.getId());
    }

    @Override
    protected void addItemToInternalStore(User item) {
        users.getItems().add(item);
    }

    @Override
    public User getItemByPosition(int position) {
        return users.getItems().get(position);
    }

    @Override
    public boolean isHolderOutOfSync(UserRecyclerViewAdapter.UserViewHolder holder, User newItem) {
        return !(holder.getOldPosition() < 0 && holder.getItem() != null && holder.getItem().getId() == newItem.getId());
    }

    @Override
    public int getItemCount() {
        return users.getItems().size();
    }

    @Override
    public User getItemById(Long selectedId) {
        return users.getUserById(selectedId);
    }

    @Override
    protected int getItemPosition(User item) {
        return users.getItems().indexOf(item);
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
