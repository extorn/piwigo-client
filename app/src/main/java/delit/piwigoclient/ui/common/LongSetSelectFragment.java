package delit.piwigoclient.ui.common;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;

/**
 * Created by gareth on 26/05/17.
 */

public abstract class LongSetSelectFragment<X extends Enableable> extends MyFragment {

    private static final String STATE_ALLOW_MULTISELECT = "multiSelectEnabled";
    private static final String STATE_ALLOW_EDITING = "editingEnabled";
    private static final String STATE_CURRENT_SELECTION = "currentSelection";
    private static final String STATE_ACTION_ID = "actionId";
    private static final String STATE_SELECT_TOGGLE = "selectToggle";
    private ListView list;
    private X listAdapter;
    private Button saveChangesButton;
    private FloatingActionButton reloadListButton;
    // Maintained state
    private boolean multiSelectEnabled;
    private int actionId;
    private HashSet<Long> currentSelection;
    private boolean editingEnabled;
    private Button toggleAllSelectionButton;
    private boolean selectToggle;

    public static Bundle buildArgsBundle(boolean multiSelectEnabled, boolean allowEditing, int actionId, HashSet<Long> initialSelection) {
        Bundle args = new Bundle();
        args.putBoolean(STATE_ALLOW_EDITING, allowEditing);
        args.putBoolean(STATE_ALLOW_MULTISELECT, multiSelectEnabled);
        args.putInt(STATE_ACTION_ID, actionId);
        args.putSerializable(STATE_CURRENT_SELECTION, initialSelection != null ? initialSelection : new HashSet<Long>(0));
        return args;
    }

    public ListView getList() {
        return list;
    }

    public int getActionId() {
        return actionId;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDetach() {
        EventBus.getDefault().unregister(this);
        super.onDetach();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            multiSelectEnabled = args.getBoolean(STATE_ALLOW_MULTISELECT);
            actionId = args.getInt(STATE_ACTION_ID);
            currentSelection = (HashSet<Long>) args.getSerializable(STATE_CURRENT_SELECTION);
            editingEnabled = args.getBoolean(STATE_ALLOW_EDITING);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_ALLOW_MULTISELECT, multiSelectEnabled);
        outState.putInt(STATE_ACTION_ID, actionId);
        outState.putSerializable(STATE_CURRENT_SELECTION, currentSelection);
        outState.putBoolean(STATE_ALLOW_EDITING, editingEnabled);
        outState.putBoolean(STATE_SELECT_TOGGLE, selectToggle);
    }

    private boolean isNotAuthorisedToAlterState() {
        return (!PiwigoSessionDetails.isAdminUser()) || isAppInReadOnlyMode();
    }

    public boolean isMultiSelectEnabled() {
        return multiSelectEnabled;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        if (savedInstanceState != null) {
            multiSelectEnabled = savedInstanceState.getBoolean(STATE_ALLOW_MULTISELECT);
            actionId = savedInstanceState.getInt(STATE_ACTION_ID);
            currentSelection = (HashSet<Long>) savedInstanceState.getSerializable(STATE_CURRENT_SELECTION);
            editingEnabled = savedInstanceState.getBoolean(STATE_ALLOW_EDITING);
            selectToggle = savedInstanceState.getBoolean(STATE_SELECT_TOGGLE);
        }

        View view = inflater.inflate(R.layout.layout_fullsize_list, container, false);

        AdView adView = view.findViewById(R.id.list_adView);
        if(AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            adView.loadAd(new AdRequest.Builder().build());
            adView.setVisibility(View.VISIBLE);
        } else {
            adView.setVisibility(View.GONE);
        }


        TextView headingField = view.findViewById(R.id.heading);
        setPageHeading(headingField);

        list = view.findViewById(R.id.list);

        CustomImageButton addListItemButton = view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setVisibility(View.GONE);
//        addListItemButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                addAlbum();
//            }
//        });
        Button cancelChangesButton = view.findViewById(R.id.list_action_cancel_button);
        cancelChangesButton.setVisibility(View.VISIBLE);
        cancelChangesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCancelChanges();
            }
        });

        toggleAllSelectionButton = view.findViewById(R.id.list_action_toggle_all_button);
        toggleAllSelectionButton.setVisibility(multiSelectEnabled?View.VISIBLE:View.GONE);
        toggleAllSelectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggleAllSelection();
            }
        });

        saveChangesButton = view.findViewById(R.id.list_action_save_button);
        saveChangesButton.setVisibility(View.VISIBLE);
        saveChangesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSaveChanges();
            }
        });

        reloadListButton = view.findViewById(R.id.list_retryAction_actionButton);
        reloadListButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getActionMasked() == MotionEvent.ACTION_UP) {
                    reloadListButton.setVisibility(View.GONE);
                    populateListWithItems();
                }
                return true;
            }
        });

        return view;
    }

    protected abstract void setPageHeading(TextView headingField);

    protected void selectAllListItems() {
        for (int i = 0; i < list.getCount(); i++) {
            list.setItemChecked(i, true);
        }
//        list.deferNotifyDataSetChanged();
    }

    protected void selectNoneListItems() {
        for (int i = 0; i < list.getCount(); i++) {
            list.setItemChecked(i, false);
        }
    }

    private void onToggleAllSelection() {
        if (!selectToggle) {
            selectAllListItems();
            toggleAllSelectionButton.setText(getString(R.string.none));
            selectToggle = true;
        } else if (selectToggle) {
            selectNoneListItems();
            toggleAllSelectionButton.setText(getString(R.string.all));
            selectToggle = false;
        }
    }

    private void onCancelChanges() {
        getFragmentManager().popBackStackImmediate();
    }

    public X getListAdapter() {
        return listAdapter;
    }

    public void setListAdapter(X listAdapter) {
        this.listAdapter = listAdapter;
    }

    protected void onListItemLoadFailed() {
        reloadListButton.setVisibility(View.VISIBLE);
    }

    protected abstract void populateListWithItems();

    private void onSaveChanges() {
        long[] selectedItemIds = list.getCheckedItemIds();

        // convert the array of long to a set of Long
        HashSet<Long> selectedIdsSet = new HashSet<>(selectedItemIds.length);
        for(long selectedId : selectedItemIds) {
            selectedIdsSet.add(selectedId);
        }
        onSelectActionComplete(selectedIdsSet);
    }

    protected abstract void onSelectActionComplete(HashSet<Long> selectedIdsSet);

    protected void cancel() {
        getFragmentManager().popBackStackImmediate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAppLockedEvent(AppLockedEvent event) {
        setAppropriateComponentState();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAppUnlockedEvent(AppUnlockedEvent event) {
        setAppropriateComponentState();
    }

    public HashSet<Long> getCurrentSelection() {
        return currentSelection;
    }

    protected void setAppropriateComponentState() {
        boolean enabled = editingEnabled && !isNotAuthorisedToAlterState();
        saveChangesButton.setEnabled(enabled);
        toggleAllSelectionButton.setEnabled(enabled);
        listAdapter.setEnabled(enabled);
    }

    public boolean isEditingEnabled() {
        return editingEnabled;
    }
}
