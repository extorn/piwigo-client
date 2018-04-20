package delit.piwigoclient.ui.common;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;

/**
 * Created by gareth on 26/05/17.
 */

public abstract class LongSetSelectFragment<Y extends View, X extends Enableable> extends MyFragment {

    private static final String STATE_ALLOW_MULTISELECT = "multiSelectEnabled";
    private static final String STATE_ALLOW_EDITING = "editingEnabled";
    private static final String STATE_ALLOW_ADDITION = "additionEnabled";
    private static final String STATE_CURRENT_SELECTION = "currentSelection";
    private static final String STATE_INITIAL_SELECTION_LOCKED = "initialSelectionLocked";
    private static final String STATE_INITIAL_SELECTION = "initialSelection";
    private static final String STATE_ACTION_ID = "actionId";
    private static final String STATE_SELECT_TOGGLE = "selectToggle";
    private Y list;
    private X listAdapter;
    private Button saveChangesButton;
    private FloatingActionButton reloadListButton;
    // Maintained state
    private boolean multiSelectEnabled;
    private int actionId;
    private HashSet<Long> currentSelection;
    private HashSet<Long> initialSelection;
    private boolean editingEnabled;
    private Button toggleAllSelectionButton;
    private boolean selectToggle;
    private CustomImageButton addListItemButton;
    private boolean additionEnabled;
    private boolean initialSelectionLocked;

    public static Bundle buildArgsBundle(boolean multiSelectEnabled, boolean allowEditing, boolean allowAddition, boolean initialSelectionLocked, int actionId, HashSet<Long> initialSelection) {
        Bundle args = new Bundle();
        args.putBoolean(STATE_ALLOW_EDITING, allowEditing);
        args.putBoolean(STATE_ALLOW_ADDITION, allowAddition);
        args.putBoolean(STATE_ALLOW_MULTISELECT, multiSelectEnabled);
        args.putInt(STATE_ACTION_ID, actionId);
        HashSet<Long> selection = initialSelection != null ? initialSelection : new HashSet<Long>(0);
        args.putSerializable(STATE_INITIAL_SELECTION, selection);
        args.putSerializable(STATE_INITIAL_SELECTION_LOCKED, initialSelectionLocked);
        return args;
    }

    public Y getList() {
        return list;
    }

    public int getActionId() {
        return actionId;
    }

    public CustomImageButton getAddListItemButton() {
        return addListItemButton;
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
            loadStateFromBundle(args);
        }
    }

    protected void loadStateFromBundle(Bundle args) {
        multiSelectEnabled = args.getBoolean(STATE_ALLOW_MULTISELECT);
        actionId = args.getInt(STATE_ACTION_ID);
        currentSelection = (HashSet<Long>) args.getSerializable(STATE_CURRENT_SELECTION);
        initialSelection = (HashSet<Long>) args.getSerializable(STATE_INITIAL_SELECTION);
        if(currentSelection == null) {
            currentSelection = initialSelection;
        }
        initialSelectionLocked = args.getBoolean(STATE_INITIAL_SELECTION_LOCKED);
        editingEnabled = args.getBoolean(STATE_ALLOW_EDITING);
        additionEnabled = args.getBoolean(STATE_ALLOW_ADDITION);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_ALLOW_MULTISELECT, multiSelectEnabled);
        outState.putInt(STATE_ACTION_ID, actionId);
        outState.putSerializable(STATE_CURRENT_SELECTION, getCurrentSelection());
        outState.putBoolean(STATE_ALLOW_EDITING, editingEnabled);
        outState.putBoolean(STATE_SELECT_TOGGLE, selectToggle);
    }

    private boolean isNotAuthorisedToAlterState() {
        return (!PiwigoSessionDetails.isAdminUser()) || isAppInReadOnlyMode();
    }

    public boolean isMultiSelectEnabled() {
        return multiSelectEnabled;
    }

    @LayoutRes
    protected abstract int getViewId();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        if (savedInstanceState != null) {
            loadStateFromBundle(savedInstanceState);
        }

        View view = inflater.inflate(getViewId(), container, false);

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

        addListItemButton = view.findViewById(R.id.list_action_add_item_button);

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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setAppropriateComponentState();
    }

    protected abstract void setPageHeading(TextView headingField);

    protected abstract void selectAllListItems();

    protected abstract void selectNoneListItems();

    protected abstract void selectOnlyListItems(Set<Long> selectionIds);

    private void onToggleAllSelection() {
        if (!selectToggle) {
            selectAllListItems();
            toggleAllSelectionButton.setText(getString(R.string.none));
            selectToggle = true;
        } else if (selectToggle) {
            if(initialSelectionLocked) {
                selectOnlyListItems(Collections.unmodifiableSet(initialSelection));
            } else {
                selectNoneListItems();
            }
            toggleAllSelectionButton.setText(getString(R.string.all));
            selectToggle = false;
        }
    }

    private void onCancelChanges() {
        if(isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }

    public X getListAdapter() {
        return listAdapter;
    }

    public void setListAdapter(X listAdapter) {
        this.listAdapter = listAdapter;
        if(listAdapter instanceof SelectableItemsAdapter) {
            ((SelectableItemsAdapter)listAdapter).setSelectedItems(currentSelection);
            ((SelectableItemsAdapter) listAdapter).setInitiallySelectedItems(initialSelection, initialSelectionLocked);
        } else if(initialSelectionLocked) {
            throw new IllegalStateException("Support for initial selection locking requires adapter to implement SelectableItemsAdapter");
        }
    }

    protected void onListItemLoadFailed() {
        reloadListButton.setVisibility(View.VISIBLE);
    }

    protected void onListItemLoadSuccess() {
        reloadListButton.setVisibility(View.GONE);
    }

    protected abstract void populateListWithItems();

    protected abstract long[] getSelectedItemIds();

    private void onSaveChanges() {
        long[] selectedItemIds = getSelectedItemIds();

        // convert the array of long to a set of Long
        HashSet<Long> selectedIdsSet = new HashSet<>(selectedItemIds.length);
        for(long selectedId : selectedItemIds) {
            selectedIdsSet.add(selectedId);
        }
        // Now just for added security - make certain it has all the initial selection if locked
        if(initialSelectionLocked) {
            selectedIdsSet.addAll(initialSelection);
        }
        onSelectActionComplete(selectedIdsSet);
    }

    protected abstract void onSelectActionComplete(HashSet<Long> selectedIdsSet);

    protected void cancel() {
        if(isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
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
        if(listAdapter != null && listAdapter instanceof SelectableItemsAdapter) {
            currentSelection = ((SelectableItemsAdapter)listAdapter).getSelectedItemIds();
        }
        return currentSelection;
    }

    protected void setAppropriateComponentState() {
        boolean enabled = editingEnabled && !isNotAuthorisedToAlterState();
        saveChangesButton.setEnabled(enabled);
        toggleAllSelectionButton.setEnabled(enabled);
        if(listAdapter != null) {
            listAdapter.setEnabled(enabled);
        }
        addListItemButton.setVisibility(!isNotAuthorisedToAlterState() && additionEnabled?View.VISIBLE:View.GONE);
    }

    public boolean isEditingEnabled() {
        return editingEnabled;
    }

    public boolean isInitialSelectionLocked() {
        return initialSelectionLocked;
    }
}
