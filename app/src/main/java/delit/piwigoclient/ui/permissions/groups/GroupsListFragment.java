package delit.piwigoclient.ui.permissions.groups;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.concurrent.ConcurrentHashMap;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoGroups;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupsGetListResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.CustomImageButton;
import delit.piwigoclient.ui.common.EndlessRecyclerViewScrollListener;
import delit.piwigoclient.ui.common.MyFragment;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.GroupDeletedEvent;
import delit.piwigoclient.ui.events.GroupUpdatedEvent;
import delit.piwigoclient.ui.events.ViewGroupEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class GroupsListFragment extends MyFragment {

    private static final String GROUPS_MODEL = "groupsModel";
    private static final String GROUPS_PAGE_BEING_LOADED = "groupsPageBeingLoaded";
    private final ConcurrentHashMap<Long, Group> deleteActionsPending = new ConcurrentHashMap<>();
    private FloatingActionButton retryActionButton;
    private PiwigoGroups groupsModel = new PiwigoGroups();
    private GroupRecyclerViewAdapter viewAdapter;
    private int pageToLoadNow = -1;
    private BaseRecyclerViewAdapterPreferences viewPrefs;

    public static GroupsListFragment newInstance() {
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences().selectable(false, false).deletable();
        prefs.setAllowItemAddition(true);
        Bundle args = new Bundle();
        prefs.storeToBundle(args);
        GroupsListFragment fragment = new GroupsListFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        if(getArguments() != null) {
            viewPrefs = new BaseRecyclerViewAdapterPreferences().loadFromBundle(getArguments());
            setArguments(null);
        }
        super.onCreate(savedInstanceState);
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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(GROUPS_MODEL, groupsModel);
        outState.putInt(GROUPS_PAGE_BEING_LOADED, pageToLoadNow);
        viewPrefs.storeToBundle(outState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        if((!PiwigoSessionDetails.isAdminUser()) || isAppInReadOnlyMode()) {
            // immediately leave this screen.
            getFragmentManager().popBackStack();
            return null;
        }

        if (savedInstanceState != null && !isSessionDetailsChanged()) {
            groupsModel = (PiwigoGroups) savedInstanceState.getSerializable(GROUPS_MODEL);
            pageToLoadNow = savedInstanceState.getInt(GROUPS_PAGE_BEING_LOADED);
        }

        View view = inflater.inflate(R.layout.layout_fullsize_recycler_list, container, false);

        AdView adView = view.findViewById(R.id.list_adView);
        if(AdsManager.getInstance().shouldShowAdverts()) {
            adView.loadAd(new AdRequest.Builder().build());
            adView.setVisibility(View.VISIBLE);
        } else {
            adView.setVisibility(View.GONE);
        }

        TextView heading = view.findViewById(R.id.heading);
        heading.setText(R.string.groups_heading);
        heading.setVisibility(View.VISIBLE);

        Button cancelButton = view.findViewById(R.id.list_action_cancel_button);
        cancelButton.setVisibility(View.GONE);

        Button toggleAllButton = view.findViewById(R.id.list_action_toggle_all_button);
        toggleAllButton.setVisibility(View.GONE);

        Button saveButton = view.findViewById(R.id.list_action_save_button);
        saveButton.setVisibility(View.GONE);

        CustomImageButton addListItemButton = view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setVisibility(viewPrefs.isAllowItemAddition()?View.VISIBLE:View.GONE);
        addListItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewGroup();
            }
        });

        retryActionButton = view.findViewById(R.id.list_retryAction_actionButton);
        retryActionButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                retryActionButton.setVisibility(View.GONE);
                loadGroupsPage(pageToLoadNow);
            }
        });

        RecyclerView recyclerView = view.findViewById(R.id.list);

        RecyclerView.LayoutManager layoutMan = new LinearLayoutManager(getContext()); //new GridLayoutManager(getContext(), 1);

        recyclerView.setLayoutManager(layoutMan);

        viewAdapter = new GroupRecyclerViewAdapter(groupsModel, new GroupRecyclerViewAdapter.MultiSelectStatusAdapter<Group>() {

            @Override
            public <A extends BaseRecyclerViewAdapter> void onItemDeleteRequested(A adapter, Group item) {
                onDeleteGroup(item);
            }

            @Override
            public <A extends BaseRecyclerViewAdapter> void onItemClick(A adapter, Group item) {
                EventBus.getDefault().post(new ViewGroupEvent(item));
            }

        }, viewPrefs);

        recyclerView.setAdapter(viewAdapter);

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
        recyclerView.addOnScrollListener(scrollListener);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if(groupsModel.getPagesLoaded() == 0 && viewAdapter != null) {
            viewAdapter.notifyDataSetChanged();
            loadGroupsPage(0);
        }
    }

    private void loadGroupsPage(int pageToLoad) {
        this.pageToLoadNow = pageToLoad;
        int pageSize = prefs.getInt(getString(R.string.preference_groups_request_pagesize_key), getResources().getInteger(R.integer.preference_groups_request_pagesize_default));
        addActiveServiceCall(R.string.progress_loading_groups,new GroupsGetListResponseHandler(pageToLoad, pageSize).invokeAsync( getContext()));
    }

    private void addNewGroup() {
        EventBus.getDefault().post(new ViewGroupEvent(new Group()));
    }

    private void onGroupSelected(Group selectedGroup) {
        EventBus.getDefault().post(new ViewGroupEvent(selectedGroup));
//        getUiHelper().showOrQueueMessage(R.string.alert_information, getString(R.string.alert_information_coming_soon));
    }

    private void onDeleteGroup(final Group thisItem) {
        String message = getString(R.string.alert_confirm_really_delete_group);
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_cancel, R.string.button_ok, new UIHelper.QuestionResultListener() {
            @Override
            public void onDismiss(AlertDialog dialog) {
            }

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if(Boolean.TRUE == positiveAnswer) {
                    deleteGroupNow(thisItem);
                }
            }
        });
    }

    private void deleteGroupNow(Group thisItem) {
        long deleteActionId = new GroupDeleteResponseHandler(thisItem.getId()).invokeAsync(this.getContext());
        this.deleteActionsPending.put(deleteActionId, thisItem);
        addActiveServiceCall(R.string.progress_delete_group,deleteActionId);
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {

        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if(isVisible()) {
                updateActiveSessionDetails();
            }
            super.onBeforeHandlePiwigoResponse(response);
        }

        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof PiwigoResponseBufferingHandler.PiwigoDeleteGroupResponse) {
                onGroupDeleted((PiwigoResponseBufferingHandler.PiwigoDeleteGroupResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse) {
                onGroupsLoaded((PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse) response);
            } else if(response instanceof PiwigoResponseBufferingHandler.ErrorResponse){
                if(deleteActionsPending.size() == 0) {
                    // assume this to be a list reload that's required.
                    retryActionButton.setVisibility(View.VISIBLE);
                }
            }
        }

        @Override
        protected void handlePiwigoHttpErrorResponse(PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse msg) {
            if (deleteActionsPending.containsKey(msg.getMessageId())) {
                onGroupDeleteFailed(msg.getMessageId());
            } else {
                super.handlePiwigoHttpErrorResponse(msg);
            }
        }

        @Override
        protected void handlePiwigoServerErrorResponse(PiwigoResponseBufferingHandler.PiwigoServerErrorResponse msg) {
            if (deleteActionsPending.containsKey(msg.getMessageId())) {
                onGroupDeleteFailed(msg.getMessageId());
            } else {
                super.handlePiwigoServerErrorResponse(msg);
            }
        }

        @Override
        protected void handlePiwigoUnexpectedReplyErrorResponse(PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse msg) {
            if (deleteActionsPending.containsKey(msg.getMessageId())) {
                onGroupDeleteFailed(msg.getMessageId());
            } else {
                super.handlePiwigoUnexpectedReplyErrorResponse(msg);
            }
        }
    }

    private void onGroupsLoaded(final PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse response) {
        synchronized (this) {
            pageToLoadNow = -1;
            retryActionButton.setVisibility(View.GONE);
            int firstIdxAdded = groupsModel.addItemPage(response.getPage(), response.getPageSize(), response.getGroups());
            viewAdapter.notifyItemRangeInserted(firstIdxAdded, response.getGroups().size());

        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(GroupDeletedEvent event) {
        viewAdapter.remove(event.getGroup());
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, String.format(getString(R.string.alert_group_delete_success_pattern), event.getGroup().getName()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(GroupUpdatedEvent event) {
        viewAdapter.replaceOrAddItem(event.getGroup());
    }

    private void onGroupDeleted(final PiwigoResponseBufferingHandler.PiwigoDeleteGroupResponse response) {
        Group group = deleteActionsPending.remove(response.getMessageId());
        viewAdapter.remove(group);
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, String.format(getString(R.string.alert_group_delete_success_pattern), group.getName()));
    }

    private void onGroupDeleteFailed(final long messageId) {
        Group group = deleteActionsPending.remove(messageId);
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, String.format(getString(R.string.alert_group_delete_failed_pattern), group.getName()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AppLockedEvent event) {
        if(isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }
}
