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
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUsernames;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.UsernamesGetListResponseHandler;
import delit.piwigoclient.ui.common.EndlessRecyclerViewScrollListener;
import delit.piwigoclient.ui.common.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionCompleteEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class UsernameSelectFragment extends RecyclerViewLongSetSelectFragment<UsernameRecyclerViewAdapter, BaseRecyclerViewAdapterPreferences> {

    private static final String USER_NAMES_MODEL = "usernamesModel";
    private static final String USER_NAMES_PAGE_BEING_LOADED = "usernamesPageBeingLoaded";
    private static final String STATE_INDIRECT_SELECTION = "indirectlySelectedUsernames";
    private PiwigoUsernames usernamesModel = new PiwigoUsernames();
    private int pageToLoadNow = -1;
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
        outState.putInt(USER_NAMES_PAGE_BEING_LOADED, pageToLoadNow);
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

        boolean editingEnabled = PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode();
        if(!editingEnabled) {
            getViewPrefs().readonly();
        }

        UsernameRecyclerViewAdapter viewAdapter = new UsernameRecyclerViewAdapter(getContext(), usernamesModel, indirectSelection, new UsernameRecyclerViewAdapter.MultiSelectStatusAdapter<Username>() {
        }, getViewPrefs());

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
            pageToLoadNow = savedInstanceState.getInt(USER_NAMES_PAGE_BEING_LOADED
            );
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
        this.pageToLoadNow = pageToLoad;
        int pageSize = prefs.getInt(getString(R.string.preference_users_request_pagesize_key), getResources().getInteger(R.integer.preference_users_request_pagesize_default));
        addActiveServiceCall(R.string.progress_loading_users, new UsernamesGetListResponseHandler(pageToLoad, pageSize).invokeAsync(getContext()));
    }

    @Override
    protected void populateListWithItems() {
        if (pageToLoadNow > 0) {
            loadUsernamesPage(pageToLoadNow);
        }
    }

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        UsernameRecyclerViewAdapter listAdapter = getListAdapter();
        HashSet<Long> usernamesNeededToBeLoaded = listAdapter.getItemsSelectedButNotLoaded();
        if(usernamesNeededToBeLoaded.size() > 0) {
            //TODO what if there are more than the max page size?! Paging needed :-(
            pageToLoadNow = Integer.MAX_VALUE; // flag that this is a special one off load.
            addActiveServiceCall(R.string.progress_loading_users, new UsernamesGetListResponseHandler(usernamesNeededToBeLoaded).invokeAsync(getContext()));
            return;
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
                onListItemLoadFailed();
            }
        }
    }


    private void onUsernamesLoaded(final PiwigoResponseBufferingHandler.PiwigoGetUsernamesListResponse response) {
        synchronized (this) {
            if(pageToLoadNow == Integer.MAX_VALUE) {
                // this is a special page of all missing items from those selected.
                pageToLoadNow = -1;
                int firstIdxAdded = usernamesModel.addItemPage(usernamesModel.getPagesLoaded(), response.getPageSize(), response.getUsernames());
                HashSet<Long> selectedItemIds = getListAdapter().getSelectedItemIds();
                for (Long selectedItemId : selectedItemIds) {
                    getListAdapter().setItemSelected(selectedItemId);
                }
                getListAdapter().notifyItemRangeInserted(firstIdxAdded, response.getUsernames().size());
                onListItemLoadSuccess();
                setAppropriateComponentState();
                onSelectActionComplete(getListAdapter().getSelectedItemIds());
                return;
            }

            pageToLoadNow = -1;
            int firstIdxAdded = usernamesModel.addItemPage(response.getPage(), response.getPageSize(), response.getUsernames());
            HashSet<Long> selectedItemIds = getListAdapter().getSelectedItemIds();
            for (Long selectedItemId : selectedItemIds) {
                getListAdapter().setItemSelected(selectedItemId);
            }
            getListAdapter().notifyItemRangeInserted(firstIdxAdded, response.getUsernames().size());
            onListItemLoadSuccess();
            setAppropriateComponentState();
        }
    }
}
