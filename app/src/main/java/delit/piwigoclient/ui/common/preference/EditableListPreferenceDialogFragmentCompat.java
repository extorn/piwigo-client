package delit.piwigoclient.ui.common.preference;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.fragment.app.DialogFragment;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.button.CustomImageButton;
import delit.piwigoclient.ui.common.list.recycler.RecyclerViewMargin;
import delit.piwigoclient.ui.common.util.BundleUtils;
import delit.piwigoclient.util.CollectionUtils;
import delit.piwigoclient.util.DisplayUtils;

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

    private RecyclerView listRecyclerView;
    private DefaultListContentsAdapter adapter;


    public static class ListAction implements Serializable {
        public final String entryValue;

        public ListAction(String entryValue) {
            this.entryValue = entryValue;
        }
    }
    public static class Removal extends ListAction{
        public Removal(String entryValue) {
            super(entryValue);
        }
    }
    public static class Addition extends ListAction{
        public Addition(String entryValue) {
            super(entryValue);
        }
    }
    public static class Replacement extends ListAction{
        public final String newEntryValue;
        public Replacement(String entryValue, String newValue) {
            super(entryValue);
            this.newEntryValue = newValue;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            userSelectedItems = BundleUtils.getStringHashSet(savedInstanceState, STATE_USER_SELECTED_ITEMS);
            entriesList = savedInstanceState.getStringArrayList(STATE_LIST_ITEMS);
            itemEditingAllowed = savedInstanceState.getBoolean(STATE_ITEM_EDIT_ALLOWED);
            multiSelect = savedInstanceState.getBoolean(STATE_MULTI_SELECT_MODE);
            alwaysSelectAll = savedInstanceState.getBoolean(STATE_ALWAYS_SELECT_ALL);
            actions = BundleUtils.getSerializable(savedInstanceState, STATE_LIST_ACTIONS, actions.getClass());
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
    public Preference findPreference(CharSequence key) {
        return getPreference();
    }
    
    public EditableListPreference getPreference() {
        return (EditableListPreference)super.getPreference();
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

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        View view = buildEditableListView();
        builder.setView(view);
    }

    private View buildEditableListView() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_fullsize_recycler_list, null, false);

        AdView adView = view.findViewById(R.id.list_adView);
        if (AdsManager.getInstance().shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        if (!multiSelect || alwaysSelectAll) {
            Button selectAllButton = view.findViewById(R.id.list_action_toggle_all_button);
            selectAllButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onToggleAll();
                }
            });
            selectAllButton.setVisibility(View.GONE);
        }
        TextView heading = view.findViewById(R.id.heading);
        heading.setVisibility(View.INVISIBLE);

        listRecyclerView = view.findViewById(R.id.list);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getContext());
        listRecyclerView.setLayoutManager(mLayoutManager);

        if (userSelectedItems == null) {
            userSelectedItems = new HashSet<>();
        }

        adapter = buildNewRecyclerViewAdapter(getContext(), entriesList, entriesList, userSelectedItems);
        listRecyclerView.setAdapter(adapter);
        listRecyclerView.addItemDecoration(new RecyclerViewMargin(getContext(), RecyclerViewMargin.DEFAULT_MARGIN_DP, 1));

        CustomImageButton addListItemButton = view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setVisibility(View.VISIBLE);
        addListItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewItemToList();
            }
        });

        Button saveChangesButton = view.findViewById(R.id.list_action_save_button);
        saveChangesButton.setVisibility(View.GONE);
        return view;
    }

    private void onToggleAll() {
        adapter.selectAll();
    }

    /**
     * Implement to allow addition of items to the list (called on add button click).
     */
    public void addNewItemToList() {
        showEditBox(false, null);
    }

    private void showEditBox(final boolean editingExistingValue, final String initialValue) {
        // popup with text entry field.
        androidx.appcompat.app.AlertDialog.Builder b = new androidx.appcompat.app.AlertDialog.Builder(getContext());
        if (editingExistingValue) {
            b.setMessage(R.string.title_editing_item);
        } else {
            b.setMessage(R.string.title_adding_item);
        }
        // Set up the input
        final EditText input = new EditText(getContext());
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        if (initialValue != null) {
            input.setText(initialValue);
        }
        b.setView(input);
        // Set up the buttons
        b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    String userInputValue = input.getText().toString();
                    String filteredValue = getPreference().filterUserInput(userInputValue);

                    dialog.dismiss();
                    if (editingExistingValue) {
                        if (!filteredValue.equals(userInputValue)) {
                            userInputValue = filteredValue;
                            input.setText(userInputValue);
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

    public void alterListItem(String item) {
        showEditBox(true, item);
    }

    /**
     */
    public DefaultListContentsAdapter buildNewRecyclerViewAdapter(Context context, List<String> entries, List<String> entryValues, Set<String> currentValues) {
        return new DefaultListContentsAdapter(context, entries, entryValues, currentValues);
    }

    protected void onChangeItem(String oldValue, String newValue) {

        if (entriesList.contains(newValue)) {
            androidx.appcompat.app.AlertDialog.Builder b = new androidx.appcompat.app.AlertDialog.Builder(getContext());
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
            listRecyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    protected void onAddNewItemToList(String newItem) {
        if (entriesList.contains(newItem)) {
            androidx.appcompat.app.AlertDialog.Builder b = new androidx.appcompat.app.AlertDialog.Builder(getContext());
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
            listRecyclerView.getAdapter().notifyDataSetChanged();
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

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        BundleUtils.putStringSet(outState, STATE_USER_SELECTED_ITEMS, userSelectedItems);
        outState.putStringArrayList(STATE_LIST_ITEMS, entriesList);
        outState.putSerializable(STATE_LIST_ACTIONS, actions);
        outState.putBoolean(STATE_ITEM_EDIT_ALLOWED, itemEditingAllowed);
        outState.putBoolean(STATE_MULTI_SELECT_MODE, multiSelect);
        outState.putBoolean(STATE_ALWAYS_SELECT_ALL, alwaysSelectAll);
    }

    private class DefaultListContentsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final Set<String> currentSelectedValues;
        private final List<String> entriesList;
        private final List<String> entryValues;

        public DefaultListContentsAdapter(@NonNull Context context, @NonNull List<String> entriesList, @NonNull List<String> entryValues, Set<String> currentSelectedValues) {
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

        protected ViewClickListener buildViewClickListener(RecyclerView.ViewHolder vh) {
            return new ViewClickListener(vh);
        }

        @Override
        public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int position) {
            ActionableListItemViewHolder viewHolder = (ActionableListItemViewHolder) holder;
//            viewHolder.selected.setVisibility(View.GONE);

            String thisValue = entriesList.get(holder.getAdapterPosition());

            boolean isSelectedValue = currentSelectedValues.contains(thisValue);

            viewHolder.itemName.setText(thisValue);
            viewHolder.itemName.setEnabled(false);
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

            protected final CustomImageButton deleteButton;
            protected final AppCompatCheckBox selected;
            protected final TextView itemDescription;
            protected final TextView itemName;

            public ActionableListItemViewHolder(View itemView) {
                super(itemView);
                selected = itemView.findViewById(R.id.checked);
                selected.setVisibility(alwaysSelectAll ? View.GONE : View.VISIBLE);
                if (multiSelect) {
                    selected.setButtonDrawable(R.drawable.checkbox);
                } else {
                    selected.setButtonDrawable(R.drawable.radio_button);
                }
                itemName = itemView.findViewById(R.id.name);
                itemDescription = itemView.findViewById(R.id.details);
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
                alterListItem(itemValue);
                return true;
            }
        }

        protected class ViewClickListener implements View.OnClickListener {
            private final RecyclerView.ViewHolder vh;

            public ViewClickListener(RecyclerView.ViewHolder vh) {
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
                        alterListItem(itemValue);
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
                    dialog.dismiss();
                }
            }

        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DisplayUtils.hideKeyboardFrom(getContext(), dialog);
        super.onClick(dialog, which);
    }

}