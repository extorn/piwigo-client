package delit.piwigoclient.ui.permissions.users;

import android.content.Context;
import androidx.annotation.NonNull;
import android.view.View;
import android.widget.TextView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoUsernames;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.ui.common.button.AppCompatCheckboxTriState;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.common.recyclerview.CustomViewHolder;
import delit.piwigoclient.ui.common.recyclerview.IdentifiableListViewAdapter;

/**
 * {@link androidx.recyclerview.widget.RecyclerView.Adapter} that can display a {@link Username}
 */
public class UsernameRecyclerViewAdapter extends IdentifiableListViewAdapter<BaseRecyclerViewAdapterPreferences, Username, PiwigoUsernames, UsernameRecyclerViewAdapter.UsernameViewHolder> {

    private final List<String> userTypes;
    private final List<String> userTypeValues;
    private final HashSet<Long> indirectlySelectedItems;


    public UsernameRecyclerViewAdapter(final Context context, final PiwigoUsernames usernames, HashSet<Long> indirectlySelectedItems, MultiSelectStatusListener multiSelectStatusListener, BaseRecyclerViewAdapterPreferences prefs) {
        super(usernames, multiSelectStatusListener, prefs);
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

    public class UsernameViewHolder extends CustomViewHolder<BaseRecyclerViewAdapterPreferences, Username> {
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
            checkBox.setOnCheckedChangeListener(new ItemSelectionListener(UsernameRecyclerViewAdapter.this, this));

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
            getCheckBox().setVisibility(isMultiSelectionAllowed() ? View.VISIBLE : View.GONE);
            getCheckBox().setAlwaysChecked(indirectlySelectedItems.contains(newItem.getId()));
            getCheckBox().setChecked(getSelectedItemIds().contains(newItem.getId()));
            getCheckBox().setEnabled(isEnabled());
        }
    }
}