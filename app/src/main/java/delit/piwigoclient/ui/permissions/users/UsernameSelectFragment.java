package delit.piwigoclient.ui.permissions.users;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.greenrobot.eventbus.EventBus;

import java.util.HashSet;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.view.recycler.EndlessRecyclerViewScrollListener;
import delit.libs.ui.view.recycler.RecyclerViewMargin;
import delit.piwigoclient.R;
import delit.piwigoclient.business.OtherPreferences;
import delit.piwigoclient.model.piwigo.PagedList;
import delit.piwigoclient.model.piwigo.PiwigoUsernames;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.UsernamesGetListResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionCompleteEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class UsernameSelectFragment<F extends UsernameSelectFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends RecyclerViewLongSetSelectFragment<F,FUIH,UsernameRecyclerViewAdapter<?,Username,?,?>, UsernameRecyclerViewAdapter.UsernameRecyclerViewAdapterPreferences, Username> {

    private static final String USER_NAMES_MODEL = "usernamesModel";
    private static final String STATE_INDIRECT_SELECTION = "indirectlySelectedUsernames";
    private static final String TAG = "UsrnameSelFrag";
    private PiwigoUsernames usernamesModel;
    private HashSet<Long> indirectSelection;

    public static UsernameSelectFragment<?,?> newInstance(UsernameRecyclerViewAdapter.UsernameRecyclerViewAdapterPreferences prefs, int actionId, HashSet<Long> indirectSelection, HashSet<Long> initialSelection) {
        UsernameSelectFragment<?,?> fragment = new UsernameSelectFragment<>();
        fragment.setTheme(R.style.Theme_App_EditPages);
        Bundle args = buildArgsBundle(prefs, actionId, initialSelection);
        if (indirectSelection != null) {
            BundleUtils.putLongHashSet(args, STATE_INDIRECT_SELECTION, new HashSet<>(indirectSelection));
        } else {
            BundleUtils.putLongHashSet(args, STATE_INDIRECT_SELECTION, new HashSet<>());
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            indirectSelection = BundleUtils.getLongHashSet(args, STATE_INDIRECT_SELECTION);
        }
    }

    @Override
    protected UsernameRecyclerViewAdapter.UsernameRecyclerViewAdapterPreferences loadPreferencesFromBundle(Bundle bundle) {
        return new UsernameRecyclerViewAdapter.UsernameRecyclerViewAdapterPreferences(bundle);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(USER_NAMES_MODEL, usernamesModel);
        BundleUtils.putLongHashSet(outState, STATE_INDIRECT_SELECTION, indirectSelection);
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = super.onCreateView(inflater, container, savedInstanceState);

        if (isNotAuthorisedToAlterState()) {
            getViewPrefs().readonly();
        }

        if (savedInstanceState != null) {
            indirectSelection = BundleUtils.getLongHashSet(savedInstanceState, STATE_INDIRECT_SELECTION);
            if(!isSessionDetailsChanged()) {
                usernamesModel = savedInstanceState.getParcelable(USER_NAMES_MODEL);
            }
        }

        if(usernamesModel == null) {
            usernamesModel = new PiwigoUsernames();
        }

        UsernameRecyclerViewAdapter viewAdapter = new UsernameRecyclerViewAdapter(requireContext(), usernamesModel, indirectSelection, new UsernameRecyclerViewAdapter.MultiSelectStatusAdapter() {
        }, getViewPrefs());

        // need to load this before the list adapter is added else will load from the list adapter which hasn't been inited yet!
        HashSet<Long> currentSelection = getCurrentSelection();

        // will restore previous selection from state if any
        setListAdapter(viewAdapter);


        // select the items to view.
        viewAdapter.setInitiallySelectedItems(getInitialSelection());
        viewAdapter.setSelectedItems(currentSelection);


        RecyclerView.LayoutManager layoutMan = new GridLayoutManager(getContext(), OtherPreferences.getColumnsOfUsers(getPrefs(), requireActivity()));
        getList().setLayoutManager(layoutMan);
        getList().setAdapter(viewAdapter);
        getList().addItemDecoration(new RecyclerViewMargin(getContext(), RecyclerViewMargin.DEFAULT_MARGIN_DP, 1));


        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(layoutMan) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                int pageToLoad = page;
                if (usernamesModel.isPageLoadedOrBeingLoaded(page) || usernamesModel.isFullyLoaded()) {
                    Integer missingPage = usernamesModel.getAMissingPage();
                    if(missingPage != null) {
                        pageToLoad = missingPage;
                    } else {
                        // already load this one by default so lets not double load it (or we've already loaded all items).
                        return;
                    }
                }
                loadUsernamesPage(pageToLoad);
            }
        };
        scrollListener.configure(usernamesModel.getPagesLoadedIdxToSizeMap(), usernamesModel.getItemCount());
        getList().addOnScrollListener(scrollListener);

        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (isServerConnectionChanged()) {
            // immediately leave this screen.
            Logging.log(Log.INFO, TAG, "removing from activity as server connection changed");
            getParentFragmentManager().popBackStack();
        }
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.users_heading);
    }

    @Override
    protected void setPageHeading(TextView headingField) {
        headingField.setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (isServerConnectionChanged()) {
            return;
        }

        if (!usernamesModel.isPageLoadedOrBeingLoaded(0)) {
            getListAdapter().notifyDataSetChanged();
            loadUsernamesPage(0);
        }
    }

    private void loadUsernamesPage(int pageToLoad) {

        usernamesModel.acquirePageLoadLock();
        try {
            if (usernamesModel.isPageLoadedOrBeingLoaded(pageToLoad)) {
                return;
            }

            int pageSize = prefs.getInt(getString(R.string.preference_users_request_pagesize_key), getResources().getInteger(R.integer.preference_users_request_pagesize_default));
            usernamesModel.recordPageBeingLoaded(addActiveServiceCall(R.string.progress_loading_users, new UsernamesGetListResponseHandler(pageToLoad, pageSize)), pageToLoad);
        } finally {
            usernamesModel.releasePageLoadLock();
        }
    }

    @Override
    protected void rerunRetrievalForFailedPages() {
        usernamesModel.acquirePageLoadLock();
        try {
            for (Integer reloadPageNum = usernamesModel.getNextPageToReload(); reloadPageNum != null; reloadPageNum = usernamesModel.getNextPageToReload()) {
                loadUsernamesPage(reloadPageNum);
            }

        } finally {
            usernamesModel.releasePageLoadLock();
        }
    }


    @Override
    protected void onCancelChanges() {
        // reset the selection to default.
        getListAdapter().setSelectedItems(null);
        onSelectActionComplete(getListAdapter().getSelectedItemIds());
    }

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        UsernameRecyclerViewAdapter listAdapter = getListAdapter();
        HashSet<Long> usernamesNeededToBeLoaded = listAdapter.getItemsSelectedButNotLoaded();
        if (usernamesNeededToBeLoaded.size() > 0) {
            usernamesModel.acquirePageLoadLock();
            try {
                if (usernamesModel.isPageLoadedOrBeingLoaded(PagedList.MISSING_ITEMS_PAGE)) {
                    // already in progress... wait it out.
                    return;
                }
                //TODO what if there are more than the max page size?! Paging needed :-(
                // flag that this is a special one off load.
                usernamesModel.recordPageBeingLoaded(addActiveServiceCall(R.string.progress_loading_users, new UsernamesGetListResponseHandler(usernamesNeededToBeLoaded)), PagedList.MISSING_ITEMS_PAGE);
                return;
            } finally {
                usernamesModel.releasePageLoadLock();
            }
        }
        HashSet<Username> selectedItems = listAdapter.getSelectedItems();
        EventBus.getDefault().post(new UsernameSelectionCompleteEvent(getActionId(), selectedIdsSet, selectedItems));
        // now pop this screen off the stack.
        if (isVisible()) {
            Logging.log(Log.INFO, TAG, "removing from activity immediately on select action complete");
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener<>();
    }

    protected void onUsernamesLoadFailed(PiwigoResponseBufferingHandler.Response response) {
        usernamesModel.acquirePageLoadLock();
        try {
            usernamesModel.recordPageLoadFailed(response.getMessageId());
            onListItemLoadFailed();
        } finally {
            usernamesModel.releasePageLoadLock();
        }
    }

    protected void onUsernamesLoaded(final UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse response) {
        usernamesModel.acquirePageLoadLock();
        try {
            if (response.getPage() == PagedList.MISSING_ITEMS_PAGE) {
                // this is a special page of all missing items from those selected.
                int firstIdxAdded = usernamesModel.addItemPage(usernamesModel.getPagesLoadedIdxToSizeMap(), response.getPageSize(), response.getUsernames());
                getListAdapter().notifyItemRangeInserted(firstIdxAdded, response.getUsernames().size());
                if (usernamesModel.hasNoFailedPageLoads()) {
                    onListItemLoadSuccess();
                }
                setAppropriateComponentState();
                onSelectActionComplete(getListAdapter().getSelectedItemIds());
                return;
            }
            int firstIdxAdded = usernamesModel.addItemPage(response.getPage(), response.getPageSize(), response.getUsernames());
            getListAdapter().notifyItemRangeInserted(firstIdxAdded, response.getUsernames().size());
            if (usernamesModel.hasNoFailedPageLoads()) {
                onListItemLoadSuccess();
            }
            setAppropriateComponentState();
        } finally {
            usernamesModel.releasePageLoadLock();
        }
    }

    private static class CustomPiwigoResponseListener<F extends UsernameSelectFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends BasicPiwigoResponseListener<FUIH,F> {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse) {
                getParent().onUsernamesLoaded((UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse) response);
            } else {
                getParent().onUsernamesLoadFailed(response);
            }
        }
    }
}
