package delit.piwigoclient.ui.common.fragment;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.Enableable;
import delit.piwigoclient.ui.common.button.CustomImageButton;
import delit.piwigoclient.ui.common.list.SelectableItemsAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.common.util.BundleUtils;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;

/**
 * Created by gareth on 26/05/17.
 */

public abstract class LongSetSelectFragment<Y extends View, X extends Enableable, Z extends BaseRecyclerViewAdapterPreferences> extends MyFragment {

    private static final String STATE_CURRENT_SELECTION = "currentSelection";
    private static final String STATE_INITIAL_SELECTION = "initialSelection";
    private static final String STATE_ACTION_ID = "actionId";
    private static final String STATE_SELECT_TOGGLE = "selectToggle";
    private Y list;
    private X listAdapter;
    private Button saveChangesButton;
    private FloatingActionButton reloadListButton;
    // Maintained state
    private int actionId;
    private HashSet<Long> currentSelection;
    private HashSet<Long> initialSelection;
    private Button toggleAllSelectionButton;
    private boolean selectToggle;
    private CustomImageButton addListItemButton;
    private Z viewPrefs;

    public static Bundle buildArgsBundle(BaseRecyclerViewAdapterPreferences prefs, int actionId, HashSet<Long> initialSelection) {
        Bundle args = new Bundle();
        prefs.storeToBundle(args);
        args.putInt(STATE_ACTION_ID, actionId);
        BundleUtils.putLongHashSet(args, STATE_INITIAL_SELECTION, initialSelection);
        return args;
    }

    public Y getList() {
        return list;
    }

    public Z getViewPrefs() {
        return viewPrefs;
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

    protected abstract Z createEmptyPrefs();

    private void loadStateFromBundle(Bundle bundle) {
        viewPrefs = createEmptyPrefs();
        viewPrefs.loadFromBundle(bundle);
        actionId = bundle.getInt(STATE_ACTION_ID);
        currentSelection = BundleUtils.getLongHashSet(bundle, STATE_CURRENT_SELECTION);
        initialSelection = BundleUtils.getLongHashSet(bundle, STATE_INITIAL_SELECTION);
        if (currentSelection == null) {
            currentSelection = initialSelection;
        }
        selectToggle = bundle.getBoolean(STATE_SELECT_TOGGLE);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        viewPrefs.storeToBundle(outState);
        outState.putInt(STATE_ACTION_ID, actionId);
        BundleUtils.putLongHashSet(outState, STATE_CURRENT_SELECTION, getCurrentSelection());
        BundleUtils.putLongHashSet(outState, STATE_INITIAL_SELECTION, getInitialSelection());
        outState.putBoolean(STATE_SELECT_TOGGLE, selectToggle);
    }

    protected boolean isNotAuthorisedToAlterState() {
        return (!PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) || isAppInReadOnlyMode();
    }

    public boolean isMultiSelectEnabled() {
        return viewPrefs.isMultiSelectionEnabled();
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
        if (AdsManager.getInstance().shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
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
        toggleAllSelectionButton.setVisibility(viewPrefs.isMultiSelectionEnabled() ? View.VISIBLE : View.GONE);
        toggleAllSelectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggleAllSelection();
            }
        });
        setToggleSelectionButtonText();

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
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    reloadListButton.hide();
                    rerunRetrievalForFailedPages();
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

    private void setToggleSelectionButtonText() {
        if (selectToggle) {
            if (initialSelection != null) {
                toggleAllSelectionButton.setText(getString(R.string.button_reset));
            } else {
                toggleAllSelectionButton.setText(getString(R.string.button_none));
            }
        } else {
            toggleAllSelectionButton.setText(getString(R.string.button_all));
        }
    }

    private void onToggleAllSelection() {
        if (!selectToggle) {
            selectAllListItems();
            selectToggle = true;
        } else {
            if (viewPrefs.isInitialSelectionLocked() && initialSelection != null) {
                selectOnlyListItems(Collections.unmodifiableSet(initialSelection));
            } else {
                selectNoneListItems();
            }
            selectToggle = false;
        }
        setToggleSelectionButtonText();
    }

    public X getListAdapter() {
        return listAdapter;
    }

    public void setListAdapter(X listAdapter) {
        this.listAdapter = listAdapter;
    }

    protected void onListItemLoadFailed() {
        reloadListButton.show();
    }

    protected void onListItemLoadSuccess() {
        reloadListButton.hide();
    }

    protected abstract void rerunRetrievalForFailedPages();

    protected abstract long[] getSelectedItemIds();

    private void onSaveChanges() {
        long[] selectedItemIds = getSelectedItemIds();

        // convert the array of long to a set of Long
        HashSet<Long> selectedIdsSet = new HashSet<>(selectedItemIds.length);
        for (long selectedId : selectedItemIds) {
            selectedIdsSet.add(selectedId);
        }
        // Now just for added security - make ertain it has all the initial selection if readonly
        if (viewPrefs.isInitialSelectionLocked() && initialSelection != null) {
            selectedIdsSet.addAll(initialSelection);
        }
        onSelectActionComplete(selectedIdsSet);
    }

    protected abstract void onSelectActionComplete(HashSet<Long> selectedIdsSet);

    protected void onCancelChanges() {
        if (isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onAppLockedEvent(AppLockedEvent event) {
        setAppropriateComponentState();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onAppUnlockedEvent(AppUnlockedEvent event) {
        setAppropriateComponentState();
    }

    public HashSet<Long> getCurrentSelection() {
        if (listAdapter instanceof SelectableItemsAdapter) {
            currentSelection = ((SelectableItemsAdapter) listAdapter).getSelectedItemIds();
        }
        return currentSelection;
    }

    protected HashSet<Long> getInitialSelection() {
        return initialSelection;
    }

    protected void setAppropriateComponentState() {
        boolean enabled = !viewPrefs.isReadOnly() && !isNotAuthorisedToAlterState();
        saveChangesButton.setEnabled(enabled);
        toggleAllSelectionButton.setEnabled(enabled);
        if (listAdapter != null) {
            listAdapter.setEnabled(enabled);
        }
        addListItemButton.setVisibility(!isNotAuthorisedToAlterState() && viewPrefs.isAllowItemAddition() ? View.VISIBLE : View.GONE);
        setToggleSelectionButtonText();
    }

    public boolean isEditingEnabled() {
        return !viewPrefs.isReadOnly();
    }

}
