package delit.piwigoclient.ui.permissions.users;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;

import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PagedList;
import delit.piwigoclient.model.piwigo.PiwigoUsernames;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.UsernamesGetListResponseHandler;
import delit.piwigoclient.ui.common.fragment.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.common.list.recycler.EndlessRecyclerViewScrollListener;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionCompleteEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class UsernameSelectFragment extends RecyclerViewLongSetSelectFragment<UsernameRecyclerViewAdapter, BaseRecyclerViewAdapterPreferences> {

    private static final String USER_NAMES_MODEL = "usernamesModel";
    private static final String STATE_INDIRECT_SELECTION = "indirectlySelectedUsernames";
    private PiwigoUsernames usernamesModel = new PiwigoUsernames();
    private HashSet<Long> indirectSelection;

    public static UsernameSelectFragment newInstance(BaseRecyclerViewAdapterPreferences prefs, int actionId, HashSet<Long> indirectSelection, HashSet<Long> initialSelection) {
        UsernameSelectFragment fragment = new UsernameSelectFragment();
        Bundle args = buildArgsBundle(prefs, actionId, initialSelection);
        if (indirectSelection != null) {
            args.putSerializable(STATE_INDIRECT_SELECTION, new HashSet<>(indirectSelection));
        } else {
            args.putSerializable(STATE_INDIRECT_SELECTION, new HashSet<>());
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            indirectSelection = (HashSet<Long>) args.getSerializable(STATE_INDIRECT_SELECTION);
        }
    }

    @Override
    protected BaseRecyclerViewAdapterPreferences createEmptyPrefs() {
        return new BaseRecyclerViewAdapterPreferences();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(USER_NAMES_MODEL, usernamesModel);
        outState.putSerializable(STATE_INDIRECT_SELECTION, indirectSelection);
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = super.onCreateView(inflater, container, savedInstanceState);

        if(isServerConnectionChanged()) {
            // immediately leave this screen.
            getFragmentManager().popBackStack();
            return null;
        }

        if(isNotAuthorisedToAlterState()) {
            getViewPrefs().readonly();
        }

        UsernameRecyclerViewAdapter viewAdapter = new UsernameRecyclerViewAdapter(getContext(), usernamesModel, indirectSelection, new UsernameRecyclerViewAdapter.MultiSelectStatusAdapter<Username>() {
        }, getViewPrefs());

        viewAdapter.setInitiallySelectedItems(getInitialSelection());
        viewAdapter.setSelectedItems(getInitialSelection());
        setListAdapter(viewAdapter);

        RecyclerView.LayoutManager layoutMan = new LinearLayoutManager(getContext());
        getList().setLayoutManager(layoutMan);
        getList().setAdapter(viewAdapter);


        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(layoutMan) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                int pageToLoad = usernamesModel.getPagesLoaded();
                if (pageToLoad == 0 || usernamesModel.isFullyLoaded()) {
                    // already load this one by default so lets not double load it (or we've already loaded all items).
                    return;
                }
                loadUsernamesPage(pageToLoad);
            }
        };
        scrollListener.configure(usernamesModel.getPagesLoaded(), usernamesModel.getItemCount());
        getList().addOnScrollListener(scrollListener);

        if (savedInstanceState != null) {
            usernamesModel = (PiwigoUsernames) savedInstanceState.getSerializable(USER_NAMES_MODEL);
        }

        return v;
    }

    @Override
    protected void setPageHeading(TextView headingField) {
        headingField.setText(R.string.users_heading);
        headingField.setVisibility(View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();

        if(isServerConnectionChanged()) {
            return;
        }

        if (usernamesModel.getPagesLoaded() == 0) {
            getListAdapter().notifyDataSetChanged();
            loadUsernamesPage(0);
        }
    }

    private void loadUsernamesPage(int pageToLoad) {

        usernamesModel.acquirePageLoadLock();
        try {
            if(usernamesModel.isPageLoadedOrBeingLoaded(pageToLoad)) {
                return;
            }

            int pageSize = prefs.getInt(getString(R.string.preference_users_request_pagesize_key), getResources().getInteger(R.integer.preference_users_request_pagesize_default));
            usernamesModel.recordPageBeingLoaded(addActiveServiceCall(R.string.progress_loading_users, new UsernamesGetListResponseHandler(pageToLoad, pageSize).invokeAsync(getContext())), pageToLoad);
        } finally {
            usernamesModel.releasePageLoadLock();
        }
    }

    @Override
    protected void rerunRetrievalForFailedPages() {
        usernamesModel.acquirePageLoadLock();
        try {
            for(Integer reloadPageNum = null; reloadPageNum != null; reloadPageNum = usernamesModel.getNextPageToReload()) {
                loadUsernamesPage(reloadPageNum);
            }

        } finally {
            usernamesModel.releasePageLoadLock();
        }
    }

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        UsernameRecyclerViewAdapter listAdapter = getListAdapter();
        HashSet<Long> usernamesNeededToBeLoaded = listAdapter.getItemsSelectedButNotLoaded();
        if(usernamesNeededToBeLoaded.size() > 0) {
            usernamesModel.acquirePageLoadLock();
            try {
                if(usernamesModel.isPageLoadedOrBeingLoaded(PagedList.MISSING_ITEMS_PAGE)) {
                    // already in progress... wait it out.
                    return;
                }
                //TODO what if there are more than the max page size?! Paging needed :-(
                // flag that this is a special one off load.
                usernamesModel.recordPageBeingLoaded(addActiveServiceCall(R.string.progress_loading_users, new UsernamesGetListResponseHandler(usernamesNeededToBeLoaded).invokeAsync(getContext())), PagedList.MISSING_ITEMS_PAGE);
                return;
            } finally {
                usernamesModel.releasePageLoadLock();
            }
        }
        HashSet<Username> selectedItems = listAdapter.getSelectedItems();
        EventBus.getDefault().post(new UsernameSelectionCompleteEvent(getActionId(), selectedIdsSet, selectedItems));
        // now pop this screen off the stack.
        if(isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetUsernamesListResponse) {
                onUsernamesLoaded((PiwigoResponseBufferingHandler.PiwigoGetUsernamesListResponse) response);
            } else {
                onUsernamesLoadFailed(response);
            }
        }
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

    private void onUsernamesLoaded(final PiwigoResponseBufferingHandler.PiwigoGetUsernamesListResponse response) {
        usernamesModel.acquirePageLoadLock();
        try {
            usernamesModel.recordPageLoadSucceeded(response.getMessageId());
            if(response.getPage() == PagedList.MISSING_ITEMS_PAGE) {
                // this is a special page of all missing items from those selected.
                int firstIdxAdded = usernamesModel.addItemPage(usernamesModel.getPagesLoaded(), response.getPageSize(), response.getUsernames());
                getListAdapter().notifyItemRangeInserted(firstIdxAdded, response.getUsernames().size());
                if(usernamesModel.hasNoFailedPageLoads()) {
                    onListItemLoadSuccess();
                }
                setAppropriateComponentState();
                onSelectActionComplete(getListAdapter().getSelectedItemIds());
                return;
            }
            int firstIdxAdded = usernamesModel.addItemPage(response.getPage(), response.getPageSize(), response.getUsernames());
            getListAdapter().notifyItemRangeInserted(firstIdxAdded, response.getUsernames().size());
            if(usernamesModel.hasNoFailedPageLoads()) {
                onListItemLoadSuccess();
            }
            setAppropriateComponentState();
        } finally {
            usernamesModel.releasePageLoadLock();
        }
    }
}
