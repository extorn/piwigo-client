package delit.piwigoclient.ui.permissions.groups;

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
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashSet;

import delit.libs.core.util.Logging;
import delit.libs.ui.view.recycler.EndlessRecyclerViewScrollListener;
import delit.libs.ui.view.recycler.RecyclerViewMargin;
import delit.piwigoclient.R;
import delit.piwigoclient.business.OtherPreferences;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PagedList;
import delit.piwigoclient.model.piwigo.PiwigoGroups;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupsGetListResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.events.GroupUpdatedEvent;
import delit.piwigoclient.ui.events.ViewGroupEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionCompleteEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class GroupSelectFragment<F extends GroupSelectFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends RecyclerViewLongSetSelectFragment<GroupRecyclerViewAdapter<?,?,?>, GroupRecyclerViewAdapter.GroupViewAdapterPreferences, Group> {

    private static final String GROUPS_MODEL = "groupsModel";
    private static final String TAG = "GrpSelFrag";
    private PiwigoGroups groupsModel;

    public static GroupSelectFragment<?,?> newInstance(GroupRecyclerViewAdapter.GroupViewAdapterPreferences prefs, int actionId, HashSet<Long> initialSelection) {
        GroupSelectFragment<?,?> fragment = new GroupSelectFragment<>();
        fragment.setTheme(R.style.Theme_App_EditPages);
        fragment.setArguments(buildArgsBundle(prefs, actionId, initialSelection));
        return fragment;
    }

    @Override
    protected GroupRecyclerViewAdapter.GroupViewAdapterPreferences loadPreferencesFromBundle(Bundle bundle) {
        return new GroupRecyclerViewAdapter.GroupViewAdapterPreferences(bundle);
    }
/*
    public static class GroupSelectAdapterPreferences extends BaseRecyclerViewAdapterPreferences<GroupSelectAdapterPreferences> {
    }*/

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(GROUPS_MODEL, groupsModel);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = super.onCreateView(inflater, container, savedInstanceState);

        if (savedInstanceState != null) {
            if(!isSessionDetailsChanged()) {
                groupsModel = savedInstanceState.getParcelable(GROUPS_MODEL);
            }
        }

        if(groupsModel == null) {
            groupsModel = new PiwigoGroups();
        }

        if (isNotAuthorisedToAlterState()) {
            getViewPrefs().readonly();
        }

        GroupRecyclerViewAdapter<?,?,?> viewAdapter = new GroupRecyclerViewAdapter(requireContext(), groupsModel, new GroupSelectMultiSelectListener<>(), getViewPrefs());
        if (!viewAdapter.isItemSelectionAllowed()) {
            viewAdapter.toggleItemSelection();
        }
        // need to load this before the list adapter is added else will load from the list adapter which hasn't been inited yet!
        HashSet<Long> currentSelection = getCurrentSelection();

        // will restore previous selection from state if any
        setListAdapter(viewAdapter);


        // select the items to view.
        viewAdapter.setInitiallySelectedItems(getInitialSelection());
        viewAdapter.setSelectedItems(currentSelection);


        RecyclerView.LayoutManager layoutMan = new GridLayoutManager(getContext(), OtherPreferences.getColumnsOfGroups(getPrefs(), getActivity()));
        getList().setLayoutManager(layoutMan);
        getList().setAdapter(viewAdapter);
        getList().addItemDecoration(new RecyclerViewMargin(getContext(), RecyclerViewMargin.DEFAULT_MARGIN_DP, 1));


        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(layoutMan) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                int pageToLoad = page;
                if (groupsModel.isPageLoadedOrBeingLoaded(page) || groupsModel.isFullyLoaded()) {
                    Integer missingPage = groupsModel.getAMissingPage();
                    if(missingPage != null) {
                        pageToLoad = missingPage;
                    } else {
                        // already load this one by default so lets not double load it (or we've already loaded all items).
                        return;
                    }
                }
                loadGroupsPage(pageToLoad);
            }
        };

        scrollListener.configure(groupsModel.getPagesLoadedIdxToSizeMap(), groupsModel.getItemCount());
        getList().addOnScrollListener(scrollListener);

        return v;
    }
    private static class GroupSelectMultiSelectListener<MSL extends GroupSelectMultiSelectListener<MSL,LVA,P,VH>, LVA extends GroupRecyclerViewAdapter<LVA,VH,MSL>, P extends GroupRecyclerViewAdapter.GroupViewAdapterPreferences, VH extends GroupRecyclerViewAdapter.GroupViewHolder<VH,LVA,MSL>> extends GroupRecyclerViewAdapter.MultiSelectStatusAdapter<MSL,LVA, GroupRecyclerViewAdapter.GroupViewAdapterPreferences, Group,VH> {

        @Override
        public void onItemLongClick(LVA adapter, Group item) {
            EventBus.getDefault().post(new ViewGroupEvent(item));
        }

        @Override
        public void onDisabledItemClick(LVA adapter, Group item) {
            EventBus.getDefault().post(new ViewGroupEvent(item));
        }
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
        return getString(R.string.groups_heading);
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

        if (!groupsModel.isPageLoadedOrBeingLoaded(0)) {
            getListAdapter().notifyDataSetChanged();
            loadGroupsPage(0);
        }
    }

    private void loadGroupsPage(int pageToLoad) {

        groupsModel.acquirePageLoadLock();
        try {
            if (groupsModel.isPageLoadedOrBeingLoaded(pageToLoad)) {
                return;
            }

            int pageSize = prefs.getInt(getString(R.string.preference_groups_request_pagesize_key), getResources().getInteger(R.integer.preference_groups_request_pagesize_default));
            groupsModel.recordPageBeingLoaded(addActiveServiceCall(R.string.progress_loading_groups, new GroupsGetListResponseHandler(pageToLoad, pageSize)), pageToLoad);
        } finally {
            groupsModel.releasePageLoadLock();
        }
    }

    @Override
    protected void rerunRetrievalForFailedPages() {
        groupsModel.acquirePageLoadLock();
        try {
            for (Integer reloadPageNum = null; reloadPageNum != null; reloadPageNum = groupsModel.getNextPageToReload()) {
                loadGroupsPage(reloadPageNum);
            }

        } finally {
            groupsModel.releasePageLoadLock();
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
        GroupRecyclerViewAdapter listAdapter = getListAdapter();
        HashSet<Long> groupsNeededToBeLoaded = listAdapter.getItemsSelectedButNotLoaded();
        if (groupsNeededToBeLoaded.size() > 0) {
            groupsModel.acquirePageLoadLock();
            try {
                if (groupsModel.isPageLoadedOrBeingLoaded(PagedList.MISSING_ITEMS_PAGE)) {
                    // already in progress... wait it out.
                    return;
                }
                //TODO what if there are more than the max page size?! Paging needed :-(
                // flag that this is a special one off load.
                groupsModel.recordPageBeingLoaded(addActiveServiceCall(R.string.progress_loading_groups, new GroupsGetListResponseHandler(groupsNeededToBeLoaded)), PagedList.MISSING_ITEMS_PAGE);
                return;
            } finally {
                groupsModel.releasePageLoadLock();
            }
        }
        HashSet<Group> selectedItems = listAdapter.getSelectedItems();
        EventBus.getDefault().post(new GroupSelectionCompleteEvent(getActionId(), selectedIdsSet, selectedItems));
        // now pop this screen off the stack.
        if (isVisible()) {
            Logging.log(Log.INFO, TAG, "removing from activity immediately as select action complete");
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener<>();
    }

    protected void onGroupsLoadFailed(PiwigoResponseBufferingHandler.Response response) {
        groupsModel.acquirePageLoadLock();
        try {
            groupsModel.recordPageLoadFailed(response.getMessageId());
            onListItemLoadFailed();
        } finally {
            groupsModel.releasePageLoadLock();
        }
    }

    protected void onGroupsLoaded(final GroupsGetListResponseHandler.PiwigoGetGroupsListRetrievedResponse response) {
        groupsModel.acquirePageLoadLock();
        try {
            if (response.getPage() == PagedList.MISSING_ITEMS_PAGE) {
                // this is a special page of all missing items from those selected.
                int firstIdxAdded = groupsModel.addItemPage(groupsModel.getPagesLoadedIdxToSizeMap(), response.getPageSize(), response.getGroups());
                getListAdapter().notifyItemRangeInserted(firstIdxAdded, response.getGroups().size());
                if (groupsModel.hasNoFailedPageLoads()) {
                    onListItemLoadSuccess();
                }
                setAppropriateComponentState();
                onSelectActionComplete(getListAdapter().getSelectedItemIds());
                return;
            }
            int firstIdxAdded = groupsModel.addItemPage(response.getPage(), response.getPageSize(), response.getGroups());
            getListAdapter().notifyItemRangeInserted(firstIdxAdded, response.getGroups().size());
            setAppropriateComponentState();
            if (groupsModel.hasNoFailedPageLoads()) {
                onListItemLoadSuccess();
            }
        } finally {
            groupsModel.releasePageLoadLock();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(GroupUpdatedEvent event) {
        getListAdapter().replaceOrAddItem(event.getGroup());
    }

    private static class CustomPiwigoResponseListener<F extends GroupSelectFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends BasicPiwigoResponseListener<FUIH,F> {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof GroupsGetListResponseHandler.PiwigoGetGroupsListRetrievedResponse) {
                getParent().onGroupsLoaded((GroupsGetListResponseHandler.PiwigoGetGroupsListRetrievedResponse) response);
            } else {
                getParent().onGroupsLoadFailed(response);
            }
        }
    }
}
