package delit.piwigoclient.ui.album.expandingListSelect;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsAdminResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.CommunityGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionCompleteEvent;

/**
 * UNUSED - Use this once it works!
 * Created by gareth on 26/05/17.
 */
public class AlbumSelectExpandableFragment<F extends AlbumSelectExpandableFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends MyFragment<F,FUIH> {

    private static final String STATE_AVAILABLE_ITEMS = "availableItems";
    private static final String STATE_ACTION_ID = "actionId";
    private static final String STATE_CURRENT_SELECTION = "currentSelection";
    private static final String STATE_INITIAL_SELECTION = "initialSelection";
    private static final String STATE_SELECT_TOGGLE = "selectToggle";
    private static final String STATE_ROOT_ALBUM = "rootAlbum";
    private static final String TAG = "ExpAlbFrag";
    private CategoryItem rootAlbum;
    private ArrayList<CategoryItem> availableAlbums;
    private int actionId;
    private HashSet<Long> currentSelection;
    private HashSet<Long> initialSelection;
    private ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences viewPrefs;
    private boolean selectToggle;
    // UI components
    private Button saveChangesButton;
    private Button toggleAllSelectionButton;
    private ExpandableAlbumsListAdapter listAdapter;
    private ExpandableListView expandableListView;
    private ExtendedFloatingActionButton reloadListButton;

    public static AlbumSelectExpandableFragment newInstance(ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences prefs, int actionId, HashSet<Long> initialSelection) {
        AlbumSelectExpandableFragment fragment = new AlbumSelectExpandableFragment();
        fragment.setArguments(buildArgsBundle(prefs, actionId, initialSelection));
        return fragment;
    }

    private static Bundle buildArgsBundle(ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences prefs, int actionId, HashSet<Long> initialSelection) {
        Bundle args = new Bundle();
        prefs.storeToBundle(args);
        args.putInt(STATE_ACTION_ID, actionId);
        BundleUtils.putLongHashSet(args, STATE_INITIAL_SELECTION, initialSelection);
        return args;
    }

    @LayoutRes
    protected int getViewId() {
        return R.layout.layout_fullsize_expanding_list;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            loadStateFromBundle(args);
        }
    }

    private void loadStateFromBundle(Bundle in) {
        if (in == null) {
            return;
        }
        actionId = in.getInt(STATE_ACTION_ID);
        viewPrefs = createPrefs(in);
        in.getInt(STATE_ACTION_ID);
        currentSelection = BundleUtils.getLongHashSet(in, STATE_CURRENT_SELECTION);
        initialSelection = BundleUtils.getLongHashSet(in, STATE_INITIAL_SELECTION);
        if(currentSelection == null) {
            currentSelection = initialSelection;
        }
        rootAlbum = in.getParcelable(STATE_ROOT_ALBUM);
        availableAlbums = in.getParcelableArrayList(STATE_AVAILABLE_ITEMS);
        selectToggle = in.getBoolean(STATE_SELECT_TOGGLE);
    }

    protected ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences createPrefs(Bundle b) {
        return new ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences(b);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        getViewPrefs().storeToBundle(outState);
        outState.putInt(STATE_ACTION_ID, actionId);
        outState.putParcelable(STATE_ROOT_ALBUM, rootAlbum);
        outState.putParcelableArrayList(STATE_AVAILABLE_ITEMS, availableAlbums);
        BundleUtils.putLongHashSet(outState, STATE_CURRENT_SELECTION, getCurrentSelection());
        BundleUtils.putLongHashSet(outState, STATE_INITIAL_SELECTION, getInitialSelection());
        outState.putBoolean(STATE_SELECT_TOGGLE, selectToggle);
    }

    private HashSet<Long> getInitialSelection() {
        return initialSelection;
    }

    private HashSet<Long> getCurrentSelection() {
        return currentSelection;
    }

    private ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences getViewPrefs() {
        return viewPrefs;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        if (savedInstanceState != null) {
            loadStateFromBundle(savedInstanceState);
        }

        View view = inflater.inflate(getViewId(), container, false);

        AdView adView = view.findViewById(R.id.list_adView);
        if (AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        TextView headingField = view.findViewById(R.id.heading);
        setPageHeading(headingField);

        expandableListView = view.findViewById(R.id.list);

    // Not yet supported
//        addListItemButton = view.findViewById(R.id.list_action_add_item_button);

        Button cancelChangesButton = view.findViewById(R.id.list_action_cancel_button);
        cancelChangesButton.setVisibility(View.VISIBLE);
        cancelChangesButton.setOnClickListener(v -> onCancelChanges());

        toggleAllSelectionButton = view.findViewById(R.id.list_action_toggle_all_button);
        toggleAllSelectionButton.setVisibility(viewPrefs.isMultiSelectionEnabled() ? View.VISIBLE : View.GONE);
        toggleAllSelectionButton.setOnClickListener(v -> onToggleAllSelection());
        setToggleSelectionButtonText();

        saveChangesButton = view.findViewById(R.id.list_action_save_button);
        saveChangesButton.setVisibility(View.VISIBLE);
        saveChangesButton.setOnClickListener(v -> onSaveChanges());

        reloadListButton = view.findViewById(R.id.list_retryAction_actionButton);
        reloadListButton.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                reloadListButton.hide();
                setupListContentLoadingIfNeeded();
            }
            return true;
        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadStateFromBundle(savedInstanceState);
        if (isServerConnectionChanged()) {
            // immediately leave this screen.
            Logging.log(Log.INFO, TAG, "removing from activity as server connection changed");
            getParentFragmentManager().popBackStack();
        }
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.albums_heading);
    }

    protected void setPageHeading(TextView headingField) {
        headingField.setVisibility(View.GONE);
    }

    protected void setAppropriateComponentState() {
        boolean enabled = !viewPrefs.isReadOnly() && !isNotAuthorisedToAlterState();
        saveChangesButton.setEnabled(enabled);
        toggleAllSelectionButton.setEnabled(enabled);
        if (listAdapter != null) {
            listAdapter.setEnabled(enabled);
        }
        // not yet supported
//        addListItemButton.setVisibility(!isNotAuthorisedToAlterState() && viewPrefs.isAllowItemAddition() ? View.VISIBLE : View.GONE);
        setToggleSelectionButtonText();
    }

    private void setToggleSelectionButtonText() {
        if (selectToggle) {
            if (initialSelection != null) {
                toggleAllSelectionButton.setText(getString(R.string.button_reset));
            } else {
                toggleAllSelectionButton.setText(getString(R.string.button_select_none));
            }
        } else {
            toggleAllSelectionButton.setText(getString(R.string.button_select_all));
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (isServerConnectionChanged()) {
            return;
        }

        setupListContentLoadingIfNeeded();
    }

    protected Set<Long> getSelectedItemIds() {
        return listAdapter.getCurrentSelectionIds();
    }

    private ExpandableListView getListView() {
        return expandableListView;
    }

    private void onSaveChanges() {
        Set<Long> selectedItemIds = getSelectedItemIds();

        // convert the array of long to a set of Long
        HashSet<Long> selectedIdsSet = new HashSet<>(selectedItemIds);
        // Now just for added security - make certain it has all the initial selection if readonly
        if (viewPrefs.isInitialSelectionLocked() && initialSelection != null) {
            selectedIdsSet.addAll(initialSelection);
        }
        onSelectActionComplete(selectedIdsSet);
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

    protected void selectNoneListItems() {
        ListView list = getListView();
        for (int i = 0; i < list.getCount(); i++) {
            list.setItemChecked(i, false);
        }
    }

    protected void selectOnlyListItems(Set<Long> selectionIds) {
        ExpandableAlbumsListAdapter listAdapter = getListAdapter();

        ListView list = getListView();
        for (int i = 0; i < list.getCount(); i++) {
            long itemId = list.getItemIdAtPosition(i);
            list.setItemChecked(i, selectionIds.contains(itemId));
        }
    }

    protected void selectAllListItems() {
        ExpandableListView list = getListView();
        for (int i = 0; i < list.getCount(); i++) {
            list.setItemChecked(i, true);
        }
//        list.deferNotifyDataSetChanged();
    }

    protected void setupListContentLoadingIfNeeded() {
        if (availableAlbums == null) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);

            if (PiwigoSessionDetails.isAdminUser(connectionPrefs)) {
                addActiveServiceCall(R.string.progress_loading_albums, new AlbumGetSubAlbumsAdminResponseHandler());
            } else if (sessionDetails != null && sessionDetails.isCommunityApiAvailable()) {
                final boolean recursive = true;
                addActiveServiceCall(R.string.progress_loading_albums, new CommunityGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId()/*currentGallery.id*/, recursive));
            } else {
                final boolean recursive = true;
                addActiveServiceCall(R.string.progress_loading_albums, new AlbumGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId()/*currentGallery.id*/, recursive));
            }
        } else if (getListAdapter() == null) {
            CategoryItem rootItem = CategoryItem.ROOT_ALBUM.clone();
            rootItem.setChildAlbums(availableAlbums);
            listAdapter = new ExpandableAlbumsListAdapter(rootItem, getViewPrefs());
            ExpandableListView listView = getListView();
            listView.setAdapter(listAdapter);
            // clear checked items
            listView.clearChoices();

            // set appropriate selection mode
            if (isMultiSelectEnabled()) {
                listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
            } else if(viewPrefs.isAllowItemSelection()){
                listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            } else {
                listView.setChoiceMode(AbsListView.CHOICE_MODE_NONE);
            }
            listAdapter.setCurrentSelection(currentSelection);

            setAppropriateComponentState();
        }
    }

    public boolean isMultiSelectEnabled() {
        return viewPrefs.isMultiSelectionEnabled();
    }

    private ExpandableAlbumsListAdapter getListAdapter() {
        return listAdapter;
    }

    protected boolean isNotAuthorisedToAlterState() {
        return (!PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) || isAppInReadOnlyMode();
    }

    protected void onCancelChanges() {
        onSelectActionComplete(getInitialSelection());
    }

    protected void onSelectActionComplete(HashSet<Long> selectedAlbumIds) {
        HashSet<CategoryItem> selectedAlbums = new HashSet<>(getListAdapter().getCheckedItems());
        HashMap<CategoryItem, String> albumPaths = new HashMap<>();
        for(CategoryItem selectedItem : selectedAlbums) {
            albumPaths.put(selectedItem, rootAlbum.getAlbumPath(selectedItem));
        }
        EventBus.getDefault().post(new ExpandingAlbumSelectionCompleteEvent(getActionId(), selectedAlbumIds, selectedAlbums, albumPaths));
        // now pop this screen off the stack.
        if (isVisible()) {
            Logging.log(Log.INFO, TAG, "removing from activity immediately on select action complete");
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    private int getActionId() {
        return actionId;
    }

    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener<>();
    }

    void onAlbumsLoaded(final ArrayList<CategoryItem> albums) {
        getUiHelper().hideProgressIndicator();
        availableAlbums = albums;
        setupListContentLoadingIfNeeded();
    }

    private static class CustomPiwigoResponseListener<F extends AlbumSelectExpandableFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends BasicPiwigoResponseListener<FUIH,F> {


        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse) {
                getParent().onAlbumsLoaded(((AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse) response).getAlbums());
            } else if (response instanceof AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) {
                getParent().onAlbumsLoaded(((AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) response).getAdminList().getAlbums());
            }
        }
    }
}
