package delit.piwigoclient.ui.permissions.groups;

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
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoGroups;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupsGetListResponseHandler;
import delit.piwigoclient.ui.common.list.recycler.EndlessRecyclerViewScrollListener;
import delit.piwigoclient.ui.common.fragment.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.events.trackable.GroupSelectionCompleteEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class GroupSelectFragment extends RecyclerViewLongSetSelectFragment<GroupRecyclerViewAdapter, BaseRecyclerViewAdapterPreferences> {

    private static final String GROUPS_MODEL = "groupsModel";
    private static final String GROUPS_PAGE_BEING_LOADED = "groupsPageBeingLoaded";
    private PiwigoGroups groupsModel = new PiwigoGroups();
    private int pageToLoadNow = -1;

    public static GroupSelectFragment newInstance(BaseRecyclerViewAdapterPreferences prefs, int actionId, HashSet<Long> initialSelection) {
        GroupSelectFragment fragment = new GroupSelectFragment();
        fragment.setArguments(buildArgsBundle(prefs, actionId, initialSelection));
        return fragment;
    }

    @Override
    protected BaseRecyclerViewAdapterPreferences createEmptyPrefs() {
        return new BaseRecyclerViewAdapterPreferences();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(GROUPS_MODEL, groupsModel);
        outState.putInt(GROUPS_PAGE_BEING_LOADED, pageToLoadNow);
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

        GroupRecyclerViewAdapter viewAdapter = new GroupRecyclerViewAdapter(groupsModel, new GroupRecyclerViewAdapter.MultiSelectStatusAdapter() {
        }, getViewPrefs());
        if(!viewAdapter.isItemSelectionAllowed()) {
            viewAdapter.toggleItemSelection();
        }

        setListAdapter(viewAdapter);

        RecyclerView.LayoutManager layoutMan = new LinearLayoutManager(getContext());
        getList().setLayoutManager(layoutMan);
        getList().setAdapter(viewAdapter);


        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(layoutMan) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                int pageToLoad = groupsModel.getPagesLoaded();
                if (pageToLoad == 0 || groupsModel.isFullyLoaded()) {
                    // already load this one by default so lets not double load it (or we've already loaded all items).
                    return;
                }
                loadGroupsPage(pageToLoad);
            }
        };
        scrollListener.configure(groupsModel.getPagesLoaded(), groupsModel.getItemCount());
        getList().addOnScrollListener(scrollListener);

        if (savedInstanceState != null) {
            groupsModel = (PiwigoGroups) savedInstanceState.getSerializable(GROUPS_MODEL);
            pageToLoadNow = savedInstanceState.getInt(GROUPS_PAGE_BEING_LOADED);
        }

        return v;
    }

    @Override
    protected void setPageHeading(TextView headingField) {
        headingField.setText(R.string.groups_heading);
        headingField.setVisibility(View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if(isServerConnectionChanged()) {
            return;
        }
        if(groupsModel.getPagesLoaded() == 0) {
            getListAdapter().notifyDataSetChanged();
            loadGroupsPage(0);
        }
    }

    private void loadGroupsPage(int pageToLoad) {
        this.pageToLoadNow = pageToLoad;
        int pageSize = prefs.getInt(getString(R.string.preference_groups_request_pagesize_key), getResources().getInteger(R.integer.preference_groups_request_pagesize_default));
        addActiveServiceCall(R.string.progress_loading_groups,new GroupsGetListResponseHandler(pageToLoad, pageSize).invokeAsync(getContext()));
    }

    @Override
    protected void populateListWithItems() {
        if(pageToLoadNow > 0) {
            loadGroupsPage(pageToLoadNow);
        }
    }

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        GroupRecyclerViewAdapter listAdapter = getListAdapter();
        HashSet<Long> groupsNeededToBeLoaded = listAdapter.getItemsSelectedButNotLoaded();
        if(groupsNeededToBeLoaded.size() > 0) {
            //TODO what if there are more than the max page size?! Paging needed :-(
            pageToLoadNow = Integer.MAX_VALUE; // flag that this is a special one off load.
            addActiveServiceCall(R.string.progress_loading_groups, new GroupsGetListResponseHandler(groupsNeededToBeLoaded, 0, groupsNeededToBeLoaded.size()).invokeAsync(getContext()));
            return;
        }
        HashSet<Group> selectedItems = listAdapter.getSelectedItems();
        EventBus.getDefault().post(new GroupSelectionCompleteEvent(getActionId(), selectedIdsSet, selectedItems));
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
            if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse) {
                onGroupsLoaded((PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse) response);
            } else {
                onListItemLoadFailed();
            }
        }
    }

    private void onGroupsLoaded(final PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse response) {
        synchronized (this) {
            if(pageToLoadNow == Integer.MAX_VALUE) {
                // this is a special page of all missing items from those selected.
                pageToLoadNow = -1;
                int firstIdxAdded = groupsModel.addItemPage(groupsModel.getPagesLoaded(), response.getPageSize(), response.getGroups());
                HashSet<Long> selectedItemIds = getListAdapter().getSelectedItemIds();
                for (Long selectedItemId : selectedItemIds) {
                    getListAdapter().setItemSelected(selectedItemId);
                }
                getListAdapter().notifyItemRangeInserted(firstIdxAdded, response.getGroups().size());
                onListItemLoadSuccess();
                setAppropriateComponentState();
                onSelectActionComplete(selectedItemIds);
                return;
            }
            pageToLoadNow = -1;
            int firstIdxAdded = groupsModel.addItemPage(response.getPage(), response.getPageSize(), response.getGroups());
            HashSet<Long> selectedItemIds = getListAdapter().getSelectedItemIds();
            for (Long selectedItemId : selectedItemIds) {
                getListAdapter().setItemSelected(selectedItemId);
            }
            getListAdapter().notifyItemRangeInserted(firstIdxAdded, response.getGroups().size());
            onListItemLoadSuccess();
            setAppropriateComponentState();
        }
    }
}
