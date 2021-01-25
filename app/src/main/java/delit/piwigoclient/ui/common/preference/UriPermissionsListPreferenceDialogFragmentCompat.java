package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.ui.view.recycler.CustomViewHolder;
import delit.libs.ui.view.recycler.SimpleRecyclerViewAdapter;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.database.UriPermissionUse;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.util.LiveDataTransientObserver;

import static android.view.View.GONE;

public class UriPermissionsListPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {

    private static final String SAVED_STATE_ITEMS_FOR_DELETE = "itemsForDelete";
    private final Set<UriPermissionUse> itemsForDelete = new HashSet<>();
    private RecyclerView listView;
    private UriPermissionsListAdapter.UriPermissionsAdapterPrefs viewPrefs;
    private boolean selectToggle;
    private Button toggleAllSelectionButton;
    private UriPermissionsListAdapter<?,?,?,UriPermissionUse> listAdapter;
    private Button deleteSelectedButton;

    private UriPermissionsListPreferenceDialogFragmentCompat(){}

    public static UriPermissionsListPreferenceDialogFragmentCompat newInstance(String key) {
        final UriPermissionsListPreferenceDialogFragmentCompat fragment =
                new UriPermissionsListPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
//            UriPermissionsListPreference pref = getPreference();
            BundleUtils.readSet(savedInstanceState, SAVED_STATE_ITEMS_FOR_DELETE, itemsForDelete);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        BundleUtils.putSet(outState, SAVED_STATE_ITEMS_FOR_DELETE, itemsForDelete);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        loadListValues();
    }

    @Override
    protected View onCreateDialogView(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_fullsize_recycler_list, null, false);
        AdView adView = view.findViewById(R.id.list_adView);
        if (AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        TextView heading = view.findViewById(R.id.heading);
        heading.setText(getPreference().getSummary());
        heading.setVisibility(View.VISIBLE);

        listView = view.findViewById(R.id.list);

        viewPrefs = new UriPermissionsListAdapter.UriPermissionsAdapterPrefs();
        viewPrefs.selectable(true, false);
        viewPrefs.deletable();

        toggleAllSelectionButton = view.findViewById(R.id.list_action_toggle_all_button);
        toggleAllSelectionButton.setVisibility(viewPrefs.isMultiSelectionEnabled() ? View.VISIBLE : View.GONE);
        toggleAllSelectionButton.setOnClickListener(v -> onToggleAllSelection());
        setToggleSelectionButtonText();

        deleteSelectedButton = view.findViewById(R.id.list_action_cancel_button);
        deleteSelectedButton.setOnClickListener(v -> deleteSelectedItems());
        deleteSelectedButton.setText(R.string.button_delete);
        deleteSelectedButton.setEnabled(false);

        //view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
//        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_add_item_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_save_button).setVisibility(View.INVISIBLE);// if gone, then the list covers the toggle button

        return view;
    }

    private void deleteSelectedItems() {
        for(UriPermissionUse item : listAdapter.getSelectedItems()) {
            onItemDelete(item);
        }
    }

    private void setToggleSelectionButtonText() {
        if (selectToggle) {
            toggleAllSelectionButton.setText(getString(R.string.button_select_none));
        } else {
            toggleAllSelectionButton.setText(getString(R.string.button_select_all));
        }
    }

    private void onToggleAllSelection() {
        if (!selectToggle) {
            selectAllListItems();
            selectToggle = true;
        } else {
            selectNoneListItems();
            selectToggle = false;
        }
        setToggleSelectionButtonText();
    }

    protected void selectAllListItems() {
        listAdapter.selectAllItemIds();
    }

    protected void selectNoneListItems() {
        listAdapter.clearSelectedItemIds();
    }

    @Override
    public UriPermissionsListPreference getPreference() {
        return (UriPermissionsListPreference) super.getPreference();
    }

    private SharedPreferences getAppSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            for(UriPermissionUse itemForDelete : itemsForDelete) {
                getPreference().getAppSettingsViewModel().releasePersistableUriPermission(requireContext(), itemForDelete);
            }
        }
    }

    private void loadListValues() {
        LiveData<List<UriPermissionUse>> uriPermissionsData = getPreference().getAppSettingsViewModel().getAll();
        uriPermissionsData.observeForever(new UriLoadPatientObserver(uriPermissionsData));
    }

    private void loadDataIntoList(List<UriPermissionUse> permissionsHeld) {
        ArrayList<UriPermissionUse> copy = new ArrayList<>(permissionsHeld);
        UriPermissionsListAdapter.UriPermissionsMultiSelectStatusAdapter actionListener = new UriPermissionsListAdapter.UriPermissionsMultiSelectStatusAdapter(deleteSelectedButton) {
            @Override
            public void onItemDeleteRequested(UriPermissionsListAdapter adapter, UriPermissionUse item) {
                onItemDelete(item);
            }
        };
        listAdapter = new UriPermissionsListAdapter(actionListener, viewPrefs);
        listAdapter.setItems(copy);
        // not needed here.
//                adapter.setInitiallySelectedItems(getInitialSelection());
//                adapter.setSelectedItems(currentSelection);
        RecyclerView.LayoutManager layoutMan = new LinearLayoutManager(getContext());
        listView.setLayoutManager(layoutMan);
        listView.setAdapter(listAdapter);
    }

    @Override
    public androidx.preference.Preference findPreference(@NonNull CharSequence key) {
        return getPreference();
    }

    private void onItemDelete(UriPermissionUse item) {
        itemsForDelete.add(item);
        listAdapter.remove(item);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DisplayUtils.hideKeyboardFrom(getContext(), dialog);
        super.onClick(dialog, which);
    }

  private static class UriPermissionsListAdapter<LVA extends UriPermissionsListAdapter<LVA,MSL,VH,T>, MSL extends UriPermissionsListAdapter.UriPermissionsMultiSelectStatusAdapter<MSL,LVA, VH,T>, VH extends UriPermissionsListAdapter.UriPermissionsViewHolder<VH, T, LVA, MSL>, T extends UriPermissionUse> extends SimpleRecyclerViewAdapter<LVA, T, UriPermissionsListAdapter.UriPermissionsAdapterPrefs, VH, MSL> {

        public UriPermissionsListAdapter(MSL multiSelectStatusListener, UriPermissionsAdapterPrefs prefs) {
            super(multiSelectStatusListener, prefs);
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public VH buildViewHolder(View view, int viewType) {
            return (VH) new UriPermissionsViewHolder(view);
        }

        @NonNull
        @Override
        protected View inflateView(@NonNull ViewGroup parent, int viewType) {
            return LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.layout_list_item_uri_permission, parent, false);
        }

        public static class UriPermissionsMultiSelectStatusAdapter<MSA extends UriPermissionsMultiSelectStatusAdapter<MSA,LVA,VH,T>, LVA extends UriPermissionsListAdapter<LVA,MSA,VH,T>, VH extends UriPermissionsListAdapter.UriPermissionsViewHolder<VH,T,LVA,MSA>, T extends UriPermissionUse> implements BaseRecyclerViewAdapter.MultiSelectStatusListener<MSA,LVA,UriPermissionsAdapterPrefs,T,VH> {

            Button deleteSelectedButton;

            public UriPermissionsMultiSelectStatusAdapter(Button deleteSelectedButton) {
                this.deleteSelectedButton = deleteSelectedButton;
            }

            @Override
            public void onMultiSelectStatusChanged(LVA adapter, boolean multiSelectEnabled) {

            }

            @Override
            public void onItemSelectionCountChanged(LVA adapter, int size) {
                deleteSelectedButton.setEnabled(size > 0);
            }

            @Override
            public  void onItemDeleteRequested(LVA adapter, T g) {

            }

            @Override
            public void onItemClick(LVA adapter, T item) {

            }

            @Override
            public void onItemLongClick(LVA adapter, T item) {

            }

            @Override
            public void onDisabledItemClick(LVA adapter, T item) {

            }
        }

        private static class UriPermissionsAdapterPrefs extends BaseRecyclerViewAdapterPreferences<UriPermissionsAdapterPrefs> {
        }

        private static class UriPermissionsViewHolder<VH extends UriPermissionsViewHolder<VH,T,LVA,MSA>, T extends UriPermissionUse, LVA extends UriPermissionsListAdapter<LVA,MSA,VH,T>, MSA extends UriPermissionsListAdapter.UriPermissionsMultiSelectStatusAdapter<MSA,LVA,VH,T>> extends CustomViewHolder<VH, LVA, UriPermissionsAdapterPrefs, T,MSA> {

            private TextView nameView;
            private TextView detailView;
            private CheckBox readPermissionView;
            private CheckBox writePermissionView;
            private CheckBox itemChecked;
            private View deleteButton;
            private UriPermissionsAdapterPrefs adapterPrefs;

            public UriPermissionsViewHolder(View view) {
                super(view);
            }

            @Override
            public void fillValues(T item, boolean allowItemDeletion) {
                setItem(item);
                nameView.setText(item.uri);
                detailView.setText(item.localizedConsumerName);
                itemChecked.setVisibility(adapterPrefs.isAllowItemSelection() ? View.VISIBLE : View.GONE);
                itemChecked.setEnabled(adapterPrefs.isEnabled());
                readPermissionView.setChecked(IOUtils.allUriFlagsAreSet(item.flags, IOUtils.URI_PERMISSION_READ));
                writePermissionView.setChecked(IOUtils.allUriFlagsAreSet(item.flags, IOUtils.URI_PERMISSION_WRITE));
                deleteButton.setVisibility(adapterPrefs.isAllowItemDeletion() ? View.VISIBLE : GONE);
                deleteButton.setEnabled(adapterPrefs.isEnabled());
            }

            @Override
            public void cacheViewFieldsAndConfigure(UriPermissionsAdapterPrefs adapterPrefs) {
                this.adapterPrefs = adapterPrefs;

                itemChecked = itemView.findViewById(R.id.list_item_checked);
                itemChecked.setOnCheckedChangeListener(new BaseRecyclerViewAdapter.ItemSelectionListener(getItemActionListener().getParentAdapter(), this));
                if (adapterPrefs.isMultiSelectionEnabled()) {
                    itemChecked.setButtonDrawable(delit.libs.R.drawable.checkbox);
                } else {
                    itemChecked.setButtonDrawable(delit.libs.R.drawable.radio_button);
                }

                nameView = itemView.findViewById(R.id.list_item_name);
                detailView = itemView.findViewById(R.id.list_item_details);
                readPermissionView = itemView.findViewById(R.id.list_item_read_permission);
                writePermissionView = itemView.findViewById(R.id.list_item_write_permission);
                deleteButton = itemView.findViewById(R.id.list_item_delete_button);
                deleteButton.setOnClickListener(this::onDeleteItemButtonClick);
            }

            @Override
            public void setChecked(boolean checked) {
                itemChecked.setChecked(checked);
            }

            private void onDeleteItemButtonClick(View v) {
                if(adapterPrefs.isAllowItemDeletion()) {// bullet proof check (in case button should not be there)
                    getItemActionListener().getParentAdapter().onDeleteItem((VH) this, v);
                } else {
                    deleteButton.setVisibility(GONE);
                }
            }
        }
    }

    private class UriLoadPatientObserver extends LiveDataTransientObserver<List<UriPermissionUse>> {
        public UriLoadPatientObserver(LiveData<List<UriPermissionUse>> uriPermissionsData) {
            super(UriPermissionsListPreferenceDialogFragmentCompat.this.requireContext(), uriPermissionsData);
        }

        @Override
        public void onChangeObserved(List<UriPermissionUse> permissionsHeld) {
            loadDataIntoList(permissionsHeld);
        }
    }
}
