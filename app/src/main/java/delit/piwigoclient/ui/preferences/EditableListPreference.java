package delit.piwigoclient.ui.preferences;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.CustomImageButton;

/**
 * Created by gareth on 23/01/18.
 */

public class EditableListPreference extends DialogPreference {
    private Set<String> entries;
    private boolean entriesAltered;
    private final int entriesPref;
    private String summary;
    private String currentValue;
    private RecyclerView listRecyclerView;
    private boolean currentValueSet = false;
    private EditableListPreferenceChangeListener listener;
    private boolean allowItemEdit;
    private String userSelectedItem;

    public EditableListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.EditableListPreference, defStyleAttr, 0);
        entriesPref = a.getResourceId(R.styleable.EditableListPreference_entriesPref, -1);
        if(entriesPref < 0) {
            throw new IllegalArgumentException("entriesPref is a mandatory field");
        }

        allowItemEdit = a.getBoolean(R.styleable.EditableListPreference_allowItemEdit, false);

        a.recycle();

        summary = String.valueOf(super.getSummary());
    }

    public void setListener(EditableListPreferenceChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        loadEntries(preferenceManager);
        super.onAttachedToHierarchy(preferenceManager);
    }

    /**
     * Override to load list values from a different location than shared preferences.
     * @param preferenceManager
     */
    protected void loadEntries(PreferenceManager preferenceManager) {
        entriesAltered = false;
        entries = preferenceManager.getSharedPreferences().getStringSet(getContext().getString(entriesPref), new HashSet<String>(1));
    }

    public EditableListPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public EditableListPreference(Context context) {
        this(context, null);
    }

    /**
     * Returns the value of the key. This should be one of the entriesList in
     * {@link #getEntryValues()}.
     *
     * @return The value of the key.
     */
    public String getValue() {
        return currentValue;
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedString(currentValue) : (String) defaultValue);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.value = getValue();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setValue(myState.value);
    }

    /**
     * Use identity mapping (i.e. value identical to displayed text).
     * @return
     */
    protected Set<String> getEntryValues() {
        return entries;
    }

    /**
     * Sets the value of the key. This should be one of the entriesList in
     * {@link #getEntryValues()}.
     *
     * @param value The value to set for the key.
     */
    public void setValue(String value) {
        // Always persist/notify the first time.
        String oldValue = currentValue;
        final boolean changed = !TextUtils.equals(currentValue, value);
        if (changed || !currentValueSet) {
            currentValue = value;
            currentValueSet = true;
            persistString(value);

            if (changed) {
                notifyChanged();
                if(listener != null) {
                    listener.onItemSelectionChanged(oldValue, value, entries.contains(oldValue));
                }
            }
        }
        if(currentValueSet) {
            // this is happening because we selected an item.
            persistEntries(entries);
        }
    }

    /**
     * Attempts to persist a set of Strings if this Preference is persistent.
     *
     * @param values The values to persist.
     * @return True if this Preference is persistent. (This is not whether the
     *         value was persisted, since we may not necessarily commit if there
     *         will be a batch commit later.)
     * @see #getPersistedStringSet(Set)
     */
    public boolean persistEntries(Set<String>  values) {
        if (!shouldPersist()) {
            return false;
        }

        PreferenceManager prefMan = getPreferenceManager();
        SharedPreferences.Editor editor = prefMan.getSharedPreferences().edit();
        editor.putStringSet(getContext().getString(entriesPref), values);
        try {
            editor.apply();
        } catch (AbstractMethodError unused) {
            // The app injected its own pre-Gingerbread
            // SharedPreferences.Editor implementation without
            // an apply method.
            editor.commit();
        }
        return true;
    }

    protected String getEntry() {
        return getValue();
    }

    /**
     * Returns the summary of this ListPreference. If the summary
     * has a {@linkplain java.lang.String#format String formatting}
     * marker in it (i.e. "%s" or "%1$s"), then the current entry
     * value will be substituted in its place.
     *
     * @return the summary with appropriate string substitution
     */
    @Override
    public CharSequence getSummary() {
        final CharSequence entry = getEntry();
        if (summary == null) {
            return super.getSummary();
        } else {
            return String.format(summary, entry == null ? "" : entry);
        }
    }

    /**
     * Sets the summary for this Preference with a CharSequence.
     * If the summary has a
     * {@linkplain java.lang.String#format String formatting}
     * marker in it (i.e. "%s" or "%1$s"), then the current entry
     * value will be substituted in its place when it's retrieved.
     *
     * @param summary The summary for the preference.
     */
    @Override
    public void setSummary(CharSequence summary) {
        super.setSummary(summary);
        //FIXME this block was totally broken thus useless. is it needed?
//        if (this.summary == null && summary != null) {
//            this.summary = null;
//        } else if (this.summary != null && !this.summary.contentEquals(summary)) {
//            this.summary = summary.toString();
//        }
    }

    private static class SavedState extends BaseSavedState {
        private String value;

        public SavedState(Parcel source) {
            super(source);
            value = source.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(value);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }
    
    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        View view = buildEditableListView();
        builder.setView(view);
        /*
         * The typical interaction for list-based dialogs is to have
         * click-on-an-item dismiss the dialog instead of the user having to
         * press 'Ok'.
         */
        builder.setPositiveButton(null, null);
    }

    private View buildEditableListView() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_fullsize_recycler_list, null, false);

        AdView adView = view.findViewById(R.id.list_adView);
        if(AdsManager.getInstance().shouldShowAdverts()) {
            adView.loadAd(new AdRequest.Builder().build());
            adView.setVisibility(View.VISIBLE);
        } else {
            adView.setVisibility(View.GONE);
        }

        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);

        TextView heading = view.findViewById(R.id.heading);
        heading.setText(getTitle());
        heading.setVisibility(View.VISIBLE);

        listRecyclerView = view.findViewById(R.id.list);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getContext());
        listRecyclerView.setLayoutManager(mLayoutManager);
        if(entriesAltered) {
            loadEntries(getPreferenceManager());
        }
        List<String> entriesList = new ArrayList<>(entries);
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = buildNewRecyclerViewAdapter(getContext(), entriesList, entriesList, currentValue);
        listRecyclerView.setAdapter(adapter);

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

    public void alterListItem(String item) {
        showEditBox(true, item);
    }

    private void showEditBox(final boolean editingExistingValue, final String initialValue) {
        // popup with text entry field.
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setMessage(R.string.title_adding_item);
        // Set up the input
        final EditText input = new EditText(getContext());
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        b.setView(input);
        // Set up the buttons
        b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if(editingExistingValue) {
                    String newValue = input.getText().toString();
                    if(!newValue.equals(initialValue)) {
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

    protected void onChangeItem(String oldValue, String newValue) {
        if(entries.contains(newValue)) {
            AlertDialog.Builder b = new AlertDialog.Builder(getContext());
            b.setTitle(R.string.alert_error);
            b.setMessage(R.string.alert_error_item_not_unique);
            b.create();
            b.show();
        } else {
            // clone the entries set so we don't inadvertently change the cached property value
            entries = new HashSet<>(entries);
            entriesAltered = true;
            entries.remove(oldValue);
            entries.add(newValue);
            List<String> entriesList = new ArrayList<>(entries);
            RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = buildNewRecyclerViewAdapter(getContext(), entriesList, entriesList, currentValue);
            listRecyclerView.setAdapter(adapter);
            if(listener != null) {
                listener.onItemAltered(oldValue, newValue);
            }
        }
    }

    protected void onAddNewItemToList(String newItem) {
        if(entries.contains(newItem)) {
            AlertDialog.Builder b = new AlertDialog.Builder(getContext());
            b.setTitle(R.string.alert_error);
            b.setMessage(R.string.alert_error_item_not_unique);
            b.create();
            b.show();
        } else {
            // clone the entries set so we don't inadvertently change the cached property value
            entries = new HashSet<>(entries);
            entriesAltered = true;
            entries.add(newItem);
            List<String> entriesList = new ArrayList<>(entries);
            RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = buildNewRecyclerViewAdapter(getContext(), entriesList, entriesList, currentValue);
            listRecyclerView.setAdapter(adapter);
            if(listener != null) {
                listener.onItemAdded(newItem);
            }
        }
    }

    /**
     * Implement to allow deletion of items from the list (called on delete button click)
     *
     * @param position
     * @param entry
     * @param entryValue
     */
    public void deleteItemFromList(int position, String entry, String entryValue) {
        // clone the entries set so we don't inadvertently change the cached property value
        entries = new HashSet<>(entries);
        entriesAltered = true;
        // delete the item from the list.
        entries.remove(entry);
        if(listener != null) {
            listener.onItemRemoved(entryValue);
        }
    }

    /**
     */
    public RecyclerView.Adapter<RecyclerView.ViewHolder> buildNewRecyclerViewAdapter(Context context, List<String> entries, List<String> entryValues, String currentValue) {
        return new DefaultListContentsAdapter(context, entries, entryValues, currentValue);
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
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.actionable_list_item_layout, parent, false);
            return buildViewHolder(view);
        }

        public RecyclerView.ViewHolder buildViewHolder(View view) {
            return new ActionableListItemViewHolder(view);
        }

        protected class ActionableListItemViewHolder extends RecyclerView.ViewHolder {

            protected final CustomImageButton deleteButton;
            protected final AppCompatCheckBox selected;
            protected final TextView itemDescription;
            protected final TextView itemName;

            public ActionableListItemViewHolder(View itemView) {
                super(itemView);
                selected = itemView.findViewById(R.id.checked);
                itemName = itemView.findViewById(R.id.name);
                itemDescription = itemView.findViewById(R.id.details);
                deleteButton = itemView.findViewById(R.id.list_item_delete_button);
                itemView.setOnClickListener(buildViewClickListener(this));
                itemView.setOnLongClickListener(buildViewLongClickListener(this));
            }
        }

        protected View.OnLongClickListener buildViewLongClickListener(RecyclerView.ViewHolder vh) {
            return new ViewLongClickListener(vh);
        }

        protected ViewClickListener buildViewClickListener(RecyclerView.ViewHolder vh) {
            return new ViewClickListener(vh);
        }

        protected class ViewLongClickListener implements View.OnLongClickListener {

            private final RecyclerView.ViewHolder vh;

            public ViewLongClickListener(RecyclerView.ViewHolder vh) {
                this.vh = vh;
            }

            @Override
            public boolean onLongClick(View v) {
                if(!allowItemEdit) {
                    return false;
                }
                String itemValue;
                if(vh.getItemId() == RecyclerView.NO_ID) {
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
                if(vh.getItemId() == RecyclerView.NO_ID) {
                    newValue = entriesList.get(vh.getAdapterPosition());
                } else {
                    throw new IllegalStateException("Stable IDs are not supported by default ViewClickListener");
                }
                // now close the dialog.
                Dialog dialog = getDialog();
                EditableListPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                userSelectedItem = newValue;
                dialog.dismiss();
                if(listener != null) {
                    listener.onItemSelectionChange(currentValue, newValue, entries.contains(currentValue));
                }
            }

        }

        @Override
        public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int position) {
            ActionableListItemViewHolder viewHolder = (ActionableListItemViewHolder)holder;
            viewHolder.selected.setVisibility(View.GONE);
            String thisValue = entriesList.get(holder.getAdapterPosition());
            viewHolder.itemName.setText(thisValue);
            if(thisValue.equals(currentSelectedValue)) {
                viewHolder.itemName.setTypeface(viewHolder.itemName.getTypeface(), Typeface.BOLD);
            }
            viewHolder.itemDescription.setVisibility(View.GONE);
            viewHolder.deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDeleteItem(holder.getAdapterPosition(), v);
                }
            });
        }

        protected void onDeleteItem(int position, View v) {
            deleteItemFromList(position, entriesList.get(position), entryValues.get(position));
            entriesList.remove(position);
            if(entryValues.size() != entriesList.size()) {
                // can't delete the value twice! (test needed for identity mapping where same reference used for values as entries)
                entryValues.remove(position);
            }
            notifyItemRangeRemoved(position, 1);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult && userSelectedItem != null && getEntryValues() != null) {
            String value = userSelectedItem;
            if (callChangeListener(value)) {

                setValue(value);
            }
        }
    }

    public static class EditableListPreferenceChangeAdapter implements EditableListPreferenceChangeListener {

        @Override
        public void onItemAdded(String newItem) {
        }

        @Override
        public void onItemRemoved(String newItem) {
        }

        @Override
        public void onItemSelectionChange(String oldSelection, String newSelection, boolean oldSelectionExists) {
        }

        @Override
        public void onItemSelectionChanged(String oldSelection, String newSelection, boolean oldSelectionExists) {
        }

        @Override
        public void onItemAltered(String oldValue, String newValue) {
        }
    }

    public interface EditableListPreferenceChangeListener {
        void onItemAdded(String newItem);
        void onItemRemoved(String newItem);
        void onItemSelectionChange(String oldSelection, String newSelection, boolean oldSelectionExists);
        void onItemSelectionChanged(String oldSelection, String newSelection, boolean oldSelectionExists);
        void onItemAltered(String oldValue, String newValue);
    }
}
