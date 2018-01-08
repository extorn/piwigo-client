package delit.piwigoclient.ui.permissions.users;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoUsernames;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.ui.common.AppCompatCheckboxTriState;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapter;
import delit.piwigoclient.ui.common.recyclerview.CustomClickListener;
import delit.piwigoclient.ui.common.recyclerview.CustomViewHolder;

/**
 * {@link android.support.v7.widget.RecyclerView.Adapter} that can display a {@link Username}
 */
public class UsernameRecyclerViewAdapter extends BaseRecyclerViewAdapter<Username, UsernameRecyclerViewAdapter.UsernameViewHolder> {

    private final PiwigoUsernames usernames;
    private final List<String> userTypes;
    private final List<String> userTypeValues;
    private final HashSet<Long> indirectlySelectedItems;


    public UsernameRecyclerViewAdapter(final Context context, final PiwigoUsernames usernames, HashSet<Long> indirectlySelectedItems, MultiSelectStatusListener multiSelectStatusListener, boolean captureActionClicks) {
        super(multiSelectStatusListener, captureActionClicks);
        this.usernames = usernames;
        setContext(context);
        this.indirectlySelectedItems = indirectlySelectedItems;
        userTypes = Arrays.asList(context.getResources().getStringArray(R.array.user_types_array));
        userTypeValues = Arrays.asList(context.getResources().getStringArray(R.array.user_types_values_array));
    }

    @Override
    public long getItemId(int position) {
        return usernames.getItems().get(position).getId();
    }

    @Override
    public UsernameViewHolder buildViewHolder(View view) {
        new CustomClickListener(null, this);
        return new UsernameViewHolder(view);
    }

    @Override
    protected void removeItemFromInternalStore(int idxRemoved) {
        usernames.getItems().remove(idxRemoved);
    }

    @Override
    protected void replaceItemInInternalStore(int idxToReplace, Username newItem) {
        usernames.getItems().remove(idxToReplace);
        usernames.getItems().add(idxToReplace, newItem);
    }

    @Override
    protected Username getItemFromInternalStoreMatching(Username item) {
        return usernames.getUsernameById(item.getId());
    }

    @Override
    public HashSet<Long> getItemsSelectedButNotLoaded() {
        HashSet<Long> loadedSelectedItemIds = new HashSet<>(getSelectedItemIds());
        for(Username username : usernames.getItems()) {
            loadedSelectedItemIds.remove(username.getId());
        }
        return loadedSelectedItemIds;
    }

    @Override
    protected void addItemToInternalStore(Username item) {
        usernames.getItems().add(item);
    }

    @Override
    public Username getItemByPosition(int position) {
        return usernames.getItems().get(position);
    }

    @Override
    public boolean isHolderOutOfSync(UsernameViewHolder holder, Username newItem) {
        return !(holder.getOldPosition() < 0 && holder.getItem() != null && holder.getItem().getId() == newItem.getId());
    }

    @Override
    public int getItemCount() {
        return usernames.getItems().size();
    }

    @Override
    public Username getItemById(Long selectedId) {
        return usernames.getUsernameById(selectedId);
    }

    @Override
    protected int getItemPosition(Username item) {
        return usernames.getItems().indexOf(item);
    }

    @Override
    public UsernameViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.actionable_triselect_list_item_layout, parent, false);

        final UsernameViewHolder viewHolder = buildViewHolder(view);
        viewHolder.internalCacheViewFieldsAndConfigure(buildCustomClickListener(viewHolder));

        return viewHolder;
    }

    public class UsernameViewHolder extends CustomViewHolder<Username> {
        private TextView txtTitle;
        private TextView detailsTitle;
        private View deleteButton;
        private AppCompatCheckboxTriState checkBox;

        public UsernameViewHolder(View view) {
            super(view);
        }

        public TextView getTxtTitle() {
            return txtTitle;
        }

        public TextView getDetailsTitle() {
            return detailsTitle;
        }

        public AppCompatCheckboxTriState getCheckBox() {
            return checkBox;
        }

        public View getDeleteButton() {
            return deleteButton;
        }

        @Override
        public String toString() {
            return super.toString() + " '" + txtTitle.getText() + "'";
        }

        @Override
        public void setChecked(boolean checked) {
            checkBox.setChecked(checked);
        }

        @Override
        public void cacheViewFieldsAndConfigure() {

            checkBox = itemView.findViewById(R.id.checked);
//            checkBox.setClickable(isItemSelectionAllowed());
            checkBox.setOnCheckedChangeListener(new ItemSelectionListener(this));

            txtTitle = itemView.findViewById(R.id.name);

            detailsTitle = itemView.findViewById(R.id.details);

            deleteButton = itemView.findViewById(R.id.list_item_delete_button);
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDeleteItem(UsernameViewHolder.this, v);
                }
            });
        }

        public void fillValues(Context context, Username newItem, boolean allowItemDeletion) {
            setItem(newItem);
            getTxtTitle().setText(newItem.getUsername());

            String userType = userTypes.get(userTypeValues.indexOf(newItem.getUserType()));

            getDetailsTitle().setText(userType);
            if (!allowItemDeletion) {
                getDeleteButton().setVisibility(View.GONE);
            }
            getCheckBox().setVisibility(isCaptureActionClicks() ? View.VISIBLE : View.GONE);
            getCheckBox().setAlwaysChecked(indirectlySelectedItems.contains(newItem.getId()));
            getCheckBox().setChecked(getSelectedItemIds().contains(newItem.getId()));
            getCheckBox().setEnabled(isEnabled());
        }
    }
}