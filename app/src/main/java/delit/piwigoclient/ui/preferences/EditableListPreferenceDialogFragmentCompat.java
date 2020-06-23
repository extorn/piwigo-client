package delit.piwigoclient.ui.preferences;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textview.MaterialTextView;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.button.MaterialCheckboxTriState;
import delit.libs.ui.view.recycler.RecyclerViewMargin;
import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.AdsManager;

public class EditableListPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {

    private static final String STATE_USER_SELECTED_ITEMS = "EditableListPreference.userSelectedItem";
    private static final String STATE_LIST_ITEMS = "EditableListPreference.listItems";
    private static final String STATE_MULTI_SELECT_MODE = "EditableListPreference.multiSelect";
    private static final String STATE_ITEM_EDIT_ALLOWED = "EditableListPreference.itemEditAllowed";
    private static final String STATE_LIST_ACTIONS = "EditableListPreference.pendingListActions";
    private static final String STATE_ALWAYS_SELECT_ALL = "EditableListPreference.alwaysSelectAll";

    private Set<String> userSelectedItems;
    private ArrayList<String> entriesList;
    private boolean multiSelect;
    private boolean itemEditingAllowed;
    private ArrayList<ListAction> actions = new ArrayList<>();
    private boolean alwaysSelectAll;

    private DefaultListContentsAdapter adapter;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            userSelectedItems = BundleUtils.getStringHashSet(savedInstanceState, STATE_USER_SELECTED_ITEMS);
            entriesList = savedInstanceState.getStringArrayList(STATE_LIST_ITEMS);
            itemEditingAllowed = savedInstanceState.getBoolean(STATE_ITEM_EDIT_ALLOWED);
            multiSelect = savedInstanceState.getBoolean(STATE_MULTI_SELECT_MODE);
            alwaysSelectAll = savedInstanceState.getBoolean(STATE_ALWAYS_SELECT_ALL);
            actions = savedInstanceState.getParcelableArrayList(STATE_LIST_ACTIONS);
        } else {
            EditableListPreference pref = getPreference();
            pref.loadEntries();
            // entries needs to be a list to avoid refreshing entire list with every addition
            entriesList = new ArrayList<>(pref.getEntryValues());
            itemEditingAllowed = pref.isAllowItemEdit();
            multiSelect = pref.isMultiSelectMode();
            alwaysSelectAll = pref.isAlwaysSelectAll();
            userSelectedItems = new HashSet<>(pref.getValuesInternal());
        }
    }

    @Override
    protected View onCreateDialogView(Context context) {
        return buildEditableListView(context);
    }

    private View buildEditableListView(Context context) {
        View view = getLayoutInflater().inflate(R.layout.layout_fullsize_recycler_list, null);

        AdView adView = view.findViewById(R.id.list_adView);
        if (AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        if (!multiSelect || alwaysSelectAll) {
            Button selectAllButton = view.findViewById(R.id.list_action_toggle_all_button);
            selectAllButton.setOnClickListener(v -> onToggleAll());
            selectAllButton.setVisibility(View.GONE);
        }
        TextView heading = view.findViewById(R.id.heading);
        heading.setVisibility(View.INVISIBLE);

        RecyclerView listRecyclerView = view.findViewById(R.id.list);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(context);
        listRecyclerView.setLayoutManager(mLayoutManager);

        if (userSelectedItems == null) {
            userSelectedItems = new HashSet<>();
        }

        adapter = buildNewRecyclerViewAdapter(context, entriesList, entriesList, userSelectedItems);
        listRecyclerView.setAdapter(adapter);
        listRecyclerView.addItemDecoration(new RecyclerViewMargin(context, RecyclerViewMargin.DEFAULT_MARGIN_DP, 1));

        ExtendedFloatingActionButton addListItemButton = view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setVisibility(View.VISIBLE);
        addListItemButton.setOnClickListener(v -> addNewItemToList(v.getContext()));

        Button saveChangesButton = view.findViewById(R.id.list_action_save_button);
        saveChangesButton.setVisibility(View.GONE);
        return view;
    }

    /**
     * Implement to allow addition of items to the list (called on add button click).
     * @param context context in which action occurs
     */
    public void addNewItemToList(Context context) {
        showEditBox(context, false, null);
    }

    private void showEditBox(Context context, final boolean editingExistingValue, final String initialValue) {
        // popup with text entry field.
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(new ContextThemeWrapper(context, R.style.Theme_App_EditPages));
        View view = LayoutInflater.from(b.getContext()).inflate(R.layout.layout_dialog_item_edit, null);
        b.setView(view);
        MaterialTextView titleView = view.findViewById(R.id.title);
        if (editingExistingValue) {
            titleView.setText(R.string.title_editing_item);
        } else {
            titleView.setText(R.string.title_adding_item);
        }
        EditText editField = view.findViewById(R.id.edit_field);
        if (initialValue != null) {
            editField.setText(initialValue);
        }
        // Set up the buttons
        b.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            try {
                String userInputValue = editField.getText().toString();
                String filteredValue = getPreference().filterUserInput(userInputValue);

                dialog.dismiss();
                if (editingExistingValue) {
                    if (!filteredValue.equals(userInputValue)) {
                        userInputValue = filteredValue;
                        editField.setText(userInputValue);
                    }
                    if (!userInputValue.equals(initialValue)) {
                        onChangeItem(initialValue, userInputValue);
                    }
                } else {
                    onAddNewItemToList(filteredValue);
                }
            } catch (IllegalArgumentException e) {
                // user input was invalid
            }
        });
        b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        b.show();
    }

    @Override
    public <T extends Preference> T findPreference(@NotNull CharSequence key) {
        return (T) getPreference();
    }

    @Override
    public EditableListPreference getPreference() {
        return (EditableListPreference) super.getPreference();
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        EditableListPreference pref = getPreference();
        if (positiveResult) {
            if (userSelectedItems != null && pref.getEntryValues() != null) {
                // remove any items not in the present list
                CollectionUtils.removeItemsNotInRhsCollection(userSelectedItems, entriesList);
                if (pref.callChangeListener(userSelectedItems)) {
                    if (actions.size() > 0 && !alwaysSelectAll) {
                        pref.updateEntryValues(actions);
                    }
                    actions.clear();
                    if (multiSelect) {
                        pref.setValues(pref.filterNewUserSelection(userSelectedItems));
                    } else {
                        Set<String> filteredSelection = pref.filterNewUserSelection(userSelectedItems);
                        if (filteredSelection.size() > 0) {
                            pref.setValue(filteredSelection.iterator().next());
                        } else {
                            pref.setValue(null);
                        }

                    }
                }
            } else if (actions.size() > 0) {
                if (pref.callChangeListener(userSelectedItems)) {
                    if (!alwaysSelectAll) {
                        pref.updateEntryValues(actions);
                    }
                    actions.clear();
                }
            }
        }
    }

    public void alterListItem(Context context, String item) {
        showEditBox(context, true, item);
    }

    protected void onChangeItem(String oldValue, String newValue) {

        if (entriesList.contains(newValue)) {
            MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(new ContextThemeWrapper(requireContext(), R.style.Theme_App_EditPages));
            b.setTitle(R.string.alert_error);
            b.setMessage(R.string.alert_error_item_not_unique);
            b.create();
            b.show();
        } else {
            entriesList.remove(oldValue);
            entriesList.add(newValue);
            if (userSelectedItems.remove(oldValue)) {
                userSelectedItems.add(newValue);
            }
            actions.add(new Replacement(oldValue, newValue));

            adapter.notifyDataSetChanged();
        }
    }

    private void onToggleAll() {
        adapter.selectAll();
    }

    protected void onAddNewItemToList(String newItem) {
        if (entriesList.contains(newItem)) {
            MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(new ContextThemeWrapper(requireContext(), R.style.Theme_App_EditPages));
            b.setTitle(R.string.alert_error);
            b.setMessage(R.string.alert_error_item_not_unique);
            b.create();
            b.show();
        } else {
            entriesList.add(newItem);
            if (alwaysSelectAll) {
                userSelectedItems.add(newItem);
            }
            actions.add(new Addition(newItem));
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        BundleUtils.putStringSet(outState, STATE_USER_SELECTED_ITEMS, userSelectedItems);
        outState.putStringArrayList(STATE_LIST_ITEMS, entriesList);
        outState.putParcelableArrayList(STATE_LIST_ACTIONS, actions);
        outState.putBoolean(STATE_ITEM_EDIT_ALLOWED, itemEditingAllowed);
        outState.putBoolean(STATE_MULTI_SELECT_MODE, multiSelect);
        outState.putBoolean(STATE_ALWAYS_SELECT_ALL, alwaysSelectAll);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DisplayUtils.hideKeyboardFrom(requireContext(), dialog);
        super.onClick(dialog, which);
    }

    /**
     *
     */
    public DefaultListContentsAdapter buildNewRecyclerViewAdapter(Context context, List<String> entries, List<String> entryValues, Set<String> currentValues) {
        return new DefaultListContentsAdapter(entries, entryValues, currentValues);
    }

    public static class ListAction implements Serializable, Parcelable {
        public static final Parcelable.Creator<ListAction> CREATOR
                = new Parcelable.Creator<ListAction>() {
            public ListAction createFromParcel(Parcel in) {
                return new ListAction(in);
            }

            public ListAction[] newArray(int size) {
                return new ListAction[size];
            }
        };
        private static final long serialVersionUID = -7463648316529068440L;
        final String entryValue;

        ListAction(String entryValue) {
            this.entryValue = entryValue;
        }

        public ListAction(Parcel in) {
            entryValue = in.readString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(entryValue);
        }
    }

    public static class Removal extends ListAction {
        public static final Parcelable.Creator<Removal> CREATOR
                = new Parcelable.Creator<Removal>() {
            public Removal createFromParcel(Parcel in) {
                return new Removal(in);
            }

            public Removal[] newArray(int size) {
                return new Removal[size];
            }
        };
        private static final long serialVersionUID = 5648116100268737949L;

        Removal(String entryValue) {
            super(entryValue);
        }

        public Removal(Parcel in) {
            this(in.readString());
        }
    }

    public static DialogFragment newInstance(String key) {
        final EditableListPreferenceDialogFragmentCompat fragment =
                new EditableListPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    public static class Addition extends ListAction {
        public static final Parcelable.Creator<Addition> CREATOR
                = new Parcelable.Creator<Addition>() {
            public Addition createFromParcel(Parcel in) {
                return new Addition(in);
            }

            public Addition[] newArray(int size) {
                return new Addition[size];
            }
        };
        private static final long serialVersionUID = -993006485259188330L;

        Addition(String entryValue) {
            super(entryValue);
        }

        public Addition(Parcel in) {
            this(in.readString());
        }
    }

    public static class Replacement extends ListAction {
        public static final Parcelable.Creator<Replacement> CREATOR
                = new Parcelable.Creator<Replacement>() {
            public Replacement createFromParcel(Parcel in) {
                return new Replacement(in);
            }

            public Replacement[] newArray(int size) {
                return new Replacement[size];
            }
        };
        private static final long serialVersionUID = -3693887286489829970L;
        final String newEntryValue;

        Replacement(String entryValue, String newValue) {
            super(entryValue);
            this.newEntryValue = newValue;
        }

        public Replacement(Parcel in) {
            this(in.readString(), in.readString());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(newEntryValue);
        }
    }

    private class DefaultListContentsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final Set<String> currentSelectedValues;
        private final List<String> entriesList;
        private final List<String> entryValues;

        public DefaultListContentsAdapter(@NonNull List<String> entriesList, @NonNull List<String> entryValues, Set<String> currentSelectedValues) {
            this.entriesList = entriesList;
            this.entryValues = entryValues;
            this.currentSelectedValues = currentSelectedValues;
        }

        @Override
        public int getItemCount() {
            return entriesList.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_actionable_triselect_list_item, parent, false);
            return buildViewHolder(view);
        }

        public RecyclerView.ViewHolder buildViewHolder(View view) {
            return new ActionableListItemViewHolder(view);
        }

        protected View.OnLongClickListener buildViewLongClickListener(RecyclerView.ViewHolder vh) {
            return new ViewLongClickListener(vh);
        }

        protected ActionableListItemClickListener buildViewClickListener(RecyclerView.ViewHolder vh) {
            return new ActionableListItemClickListener(vh);
        }

        @Override
        public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int position) {
            ActionableListItemViewHolder viewHolder = (ActionableListItemViewHolder) holder;
//            viewHolder.selected.setVisibility(View.GONE);

            String thisValue = entriesList.get(holder.getAdapterPosition());

            boolean isSelectedValue = currentSelectedValues.contains(thisValue);

            viewHolder.itemName.setText(thisValue);
            viewHolder.itemName.setActivated(isSelectedValue);
            viewHolder.selected.setChecked(isSelectedValue);
            viewHolder.itemDescription.setVisibility(View.GONE);
            viewHolder.deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDeleteItem(holder.getAdapterPosition(), v);
                }
            });
        }

        protected void onDeleteItem(int position, View v) {
            String item = entriesList.remove(position);
            if (entryValues.size() != entriesList.size()) {
                // can't delete the value twice! (test needed for identity mapping where same reference used for values as entries)
                item = entryValues.remove(position);
            }
            userSelectedItems.remove(item);
            actions.add(new Removal(item));
            notifyItemRangeRemoved(position, 1);
        }

        public void selectAll() {
            userSelectedItems.addAll(entryValues);
            notifyItemRangeChanged(0, getItemCount());
        }

        protected class ActionableListItemViewHolder extends RecyclerView.ViewHolder {

            protected final MaterialButton deleteButton;
            protected final MaterialCheckboxTriState selected;
            protected final TextView itemDescription;
            protected final TextView itemName;

            public ActionableListItemViewHolder(View itemView) {
                super(itemView);
                selected = itemView.findViewById(R.id.list_item_checked);
                selected.setVisibility(alwaysSelectAll ? View.GONE : View.VISIBLE);
                if (multiSelect) {
                    selected.setButtonDrawable(R.drawable.checkbox);
                } else {
                    selected.setButtonDrawable(R.drawable.radio_button);
                }
                itemName = itemView.findViewById(R.id.list_item_name);
                itemDescription = itemView.findViewById(R.id.list_item_details);
                deleteButton = itemView.findViewById(R.id.list_item_delete_button);
                itemView.setOnClickListener(buildViewClickListener(this));
                itemView.setOnLongClickListener(buildViewLongClickListener(this));
            }
        }

        protected class ViewLongClickListener implements View.OnLongClickListener {

            private final RecyclerView.ViewHolder vh;

            public ViewLongClickListener(RecyclerView.ViewHolder vh) {
                this.vh = vh;
            }

            @Override
            public boolean onLongClick(View v) {
                if (!itemEditingAllowed) {
                    return false;
                }
                String itemValue;
                if (vh.getItemId() == RecyclerView.NO_ID) {
                    itemValue = entriesList.get(vh.getAdapterPosition());
                } else {
                    throw new IllegalStateException("Stable IDs are not supported by default ViewLongClickListener");
                }
                alterListItem(v.getContext(), itemValue);
                return true;
            }
        }

        protected class ActionableListItemClickListener implements View.OnClickListener {
            private final RecyclerView.ViewHolder vh;

            public ActionableListItemClickListener(RecyclerView.ViewHolder vh) {
                this.vh = vh;
            }

            @Override
            public void onClick(View v) {
                onClick(vh, v);
            }

            protected void onClick(RecyclerView.ViewHolder vh, View v) {
                if (alwaysSelectAll) {
                    if (itemEditingAllowed) {
                        String itemValue;
                        if (vh.getItemId() == RecyclerView.NO_ID) {
                            itemValue = entriesList.get(vh.getAdapterPosition());
                        } else {
                            throw new IllegalStateException("Stable IDs are not supported by default ViewLongClickListener");
                        }
                        alterListItem(v.getContext(), itemValue);
                    }
                }
                String newValue;
                if (vh.getItemId() == RecyclerView.NO_ID) {
                    newValue = entriesList.get(vh.getAdapterPosition());
                } else {
                    throw new IllegalStateException("Stable IDs are not supported by default ViewClickListener");
                }
                if (!multiSelect) {
                    currentSelectedValues.clear();
                }
                if (!currentSelectedValues.remove(newValue)) {
                    currentSelectedValues.add(newValue);
                }

                if (!multiSelect) {
                    // now close the dialog.
                    Dialog dialog = getDialog();
                    EditableListPreferenceDialogFragmentCompat.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                    if (dialog != null) {
                        dialog.dismiss();
                    }
                }
            }

        }
    }

}