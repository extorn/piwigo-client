package delit.piwigoclient.ui.common.preference;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.ads.AdView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.button.CustomImageButton;
import delit.piwigoclient.ui.common.list.recycler.RecyclerViewMargin;
import delit.piwigoclient.util.DisplayUtils;

public class EditableListPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {
    private RecyclerView listRecyclerView;
    private String userSelectedItem;
    ArrayList<String> entriesList;
    ArrayList<ListAction> actions = new ArrayList<>();
    private String STATE_USER_SELECTED_ITEM = "EditableListPreference.userSelectedItem";
    private String STATE_LIST_ITEMS = "EditableListPreference.listItems";
    private String STATE_LIST_ACTIONS = "EditableListPreference.pendingListActions";
    private boolean itemEditingAllowed;
    private String STATE_ITEM_EDIT_ALLOWED = "EditableListPreference.itemEditAllowed";


    public class ListAction implements Serializable {
        public final String entryValue;

        public ListAction(String entryValue) {
            this.entryValue = entryValue;
        }
    }
    public class Removal extends ListAction{
        public Removal(String entryValue) {
            super(entryValue);
        }
    }
    public class Addition extends ListAction{
        public Addition(String entryValue) {
            super(entryValue);
        }
    }
    public class Replacement extends ListAction{
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
            userSelectedItem = savedInstanceState.getString(STATE_USER_SELECTED_ITEM);
            actions = (ArrayList<ListAction>) savedInstanceState.getSerializable(STATE_LIST_ACTIONS);
            entriesList = savedInstanceState.getStringArrayList(STATE_LIST_ITEMS);
            itemEditingAllowed = savedInstanceState.getBoolean(STATE_ITEM_EDIT_ALLOWED);
        } else {
            EditableListPreference pref = getPreference();
            pref.loadEntries();
            entriesList = new ArrayList<>(pref.getEntryValues());
            userSelectedItem = pref.getValue();
            itemEditingAllowed = pref.isAllowItemEdit();
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
        if (positiveResult && userSelectedItem != null && pref.getEntryValues() != null) {
            String value = userSelectedItem;
            if (pref.callChangeListener(value)) {
                if(actions.size() > 0) {
                    pref.updateEntryValues(actions);
                }
                actions.clear();
                pref.setValue(value);
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
        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);

        TextView heading = view.findViewById(R.id.heading);
        heading.setVisibility(View.INVISIBLE);

        listRecyclerView = view.findViewById(R.id.list);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getContext());
        listRecyclerView.setLayoutManager(mLayoutManager);

        String currentSelection = userSelectedItem;
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = buildNewRecyclerViewAdapter(getContext(), entriesList, entriesList, currentSelection);
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

    /**
     * Implement to allow addition of items to the list (called on add button click).
     */
    public void addNewItemToList() {
        showEditBox(false, null);
    }

    private void showEditBox(final boolean editingExistingValue, final String initialValue) {
        // popup with text entry field.
        android.support.v7.app.AlertDialog.Builder b = new android.support.v7.app.AlertDialog.Builder(getContext());
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
                dialog.dismiss();
                if (editingExistingValue) {
                    String newValue = input.getText().toString();
                    if (!newValue.equals(initialValue)) {
                        onChangeItem(initialValue, newValue);
                    }
                } else {
                    onAddNewItemToList(input.getText().toString());
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
    public RecyclerView.Adapter<RecyclerView.ViewHolder> buildNewRecyclerViewAdapter(Context context, List<String> entries, List<String> entryValues, String currentValue) {
        return new DefaultListContentsAdapter(context, entries, entryValues, currentValue);
    }

    protected void onChangeItem(String oldValue, String newValue) {

        if (entriesList.contains(newValue)) {
            android.support.v7.app.AlertDialog.Builder b = new android.support.v7.app.AlertDialog.Builder(getContext());
            b.setTitle(R.string.alert_error);
            b.setMessage(R.string.alert_error_item_not_unique);
            b.create();
            b.show();
        } else {
            entriesList.remove(oldValue);
            entriesList.add(newValue);
            if(oldValue.equals(userSelectedItem)) {
                userSelectedItem = newValue;
            }
            actions.add(new Replacement(oldValue, newValue));
            listRecyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    protected void onAddNewItemToList(String newItem) {
        if (entriesList.contains(newItem)) {
            android.support.v7.app.AlertDialog.Builder b = new android.support.v7.app.AlertDialog.Builder(getContext());
            b.setTitle(R.string.alert_error);
            b.setMessage(R.string.alert_error_item_not_unique);
            b.create();
            b.show();
        } else {
            entriesList.add(newItem);
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

    private class DefaultListContentsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final String currentSelectedValue;
        private final List<String> entriesList;
        private final List<String> entryValues;

        public DefaultListContentsAdapter(@NonNull Context context, @NonNull List<String> entriesList, @NonNull List<String> entryValues, String currentSelectedValue) {
            this.entriesList = entriesList;
            this.entryValues = entryValues;
            this.currentSelectedValue = currentSelectedValue;
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
            viewHolder.itemName.setText(thisValue);
            viewHolder.itemName.setEnabled(false);
            viewHolder.itemName.setActivated(thisValue.equals(currentSelectedValue));
            viewHolder.selected.setChecked(thisValue.equals(currentSelectedValue));
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
            actions.add(new Removal(item));
            notifyItemRangeRemoved(position, 1);
        }

        protected class ActionableListItemViewHolder extends RecyclerView.ViewHolder {

            protected final CustomImageButton deleteButton;
            protected final AppCompatCheckBox selected;
            protected final TextView itemDescription;
            protected final TextView itemName;

            public ActionableListItemViewHolder(View itemView) {
                super(itemView);
                selected = itemView.findViewById(R.id.checked);
                selected.setButtonDrawable(R.drawable.radio_button);
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
                String newValue;
                if (vh.getItemId() == RecyclerView.NO_ID) {
                    newValue = entriesList.get(vh.getAdapterPosition());
                } else {
                    throw new IllegalStateException("Stable IDs are not supported by default ViewClickListener");
                }

                userSelectedItem = newValue;
                // now close the dialog.
                Dialog dialog = getDialog();
                EditableListPreferenceDialogFragmentCompat.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                dialog.dismiss();
            }

        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_USER_SELECTED_ITEM, userSelectedItem);
        outState.putStringArrayList(STATE_LIST_ITEMS, entriesList);
        outState.putSerializable(STATE_LIST_ACTIONS, actions);
        outState.putBoolean(STATE_ITEM_EDIT_ALLOWED, itemEditingAllowed);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DisplayUtils.hideKeyboardFrom(getContext(), dialog);
        super.onClick(dialog, which);
    }

}