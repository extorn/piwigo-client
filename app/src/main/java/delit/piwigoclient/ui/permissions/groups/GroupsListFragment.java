package delit.piwigoclient.ui.permissions.groups;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.concurrent.ConcurrentHashMap;

import delit.libs.core.util.Logging;
import delit.libs.ui.view.recycler.EndlessRecyclerViewScrollListener;
import delit.libs.ui.view.recycler.RecyclerViewMargin;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.OtherPreferences;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoGroups;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupsGetListResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.GroupDeletedEvent;
import delit.piwigoclient.ui.events.GroupUpdatedEvent;
import delit.piwigoclient.ui.events.ViewGroupEvent;
import delit.piwigoclient.ui.model.PiwigoGroupsModel;

/**
 * Created by gareth on 26/05/17.
 */

public class GroupsListFragment<F extends GroupsListFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends MyFragment<F,FUIH> {

    private static final String GROUPS_MODEL = "groupsModel";
    private static final String TAG = "GrpLstFrag";
    private final ConcurrentHashMap<Long, Group> deleteActionsPending = new ConcurrentHashMap<>();
    private ExtendedFloatingActionButton retryActionButton;
    private PiwigoGroups groupsModel;
    private GroupRecyclerViewAdapter viewAdapter;
    private GroupRecyclerViewAdapter.GroupViewAdapterPreferences viewPrefs;

    public static GroupsListFragment<?,?> newInstance() {
        GroupRecyclerViewAdapter.GroupViewAdapterPreferences prefs = new GroupRecyclerViewAdapter.GroupViewAdapterPreferences().deletable();
        prefs.setAllowItemAddition(true);
        prefs.setEnabled(true);
        Bundle args = new Bundle();
        prefs.storeToBundle(args);
        GroupsListFragment<?,?> fragment = new GroupsListFragment<>();
        fragment.setTheme(R.style.Theme_App_EditPages);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Bundle b = savedInstanceState;
        if(b == null) {
            b = getArguments();
        }
        if (b != null) {
            viewPrefs = new GroupRecyclerViewAdapter.GroupViewAdapterPreferences(b);
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
        outState.putParcelable(GROUPS_MODEL, groupsModel);
        viewPrefs.storeToBundle(outState);
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.groups_heading);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);
        groupsModel = new ViewModelProvider(this).get(PiwigoGroupsModel.class).getPiwigoGroups().getValue();

        if (isSessionDetailsChanged()) {
            groupsModel.clear();
        } else if (savedInstanceState != null) {
            viewPrefs = new GroupRecyclerViewAdapter.GroupViewAdapterPreferences(savedInstanceState);
        }

        View view = inflater.inflate(R.layout.layout_fullsize_recycler_list, container, false);

        AdView adView = view.findViewById(R.id.list_adView);
        if (AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        TextView heading = view.findViewById(R.id.heading);
        heading.setVisibility(View.INVISIBLE);

        Button cancelButton = view.findViewById(R.id.list_action_cancel_button);
        cancelButton.setVisibility(View.GONE);

        Button toggleAllButton = view.findViewById(R.id.list_action_toggle_all_button);
        toggleAllButton.setVisibility(View.GONE);

        Button saveButton = view.findViewById(R.id.list_action_save_button);
        saveButton.setVisibility(View.GONE);

        ExtendedFloatingActionButton addListItemButton = view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setVisibility(viewPrefs.isAllowItemAddition() ? View.VISIBLE : View.GONE);
        addListItemButton.setOnClickListener(v -> addNewGroup());

        retryActionButton = view.findViewById(R.id.list_retryAction_actionButton);
        retryActionButton.setOnClickListener(v -> {
            retryActionButton.hide();
            loadGroupsPage(groupsModel.getNextPageToReload());
        });

        RecyclerView recyclerView = view.findViewById(R.id.list);

        RecyclerView.LayoutManager layoutMan = new GridLayoutManager(getContext(), OtherPreferences.getColumnsOfGroups(getPrefs(), requireActivity()));

        recyclerView.setLayoutManager(layoutMan);

        viewAdapter = new GroupRecyclerViewAdapter(getContext(), groupsModel, new GroupListMultiSelectListener<>(), viewPrefs);

        recyclerView.setAdapter(viewAdapter);
        recyclerView.addItemDecoration(new RecyclerViewMargin(getContext(), RecyclerViewMargin.DEFAULT_MARGIN_DP, 1));

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
        recyclerView.addOnScrollListener(scrollListener);

        return view;
    }

    private class GroupListMultiSelectListener<MSL extends GroupListMultiSelectListener<MSL,LVA,VH>, LVA extends GroupRecyclerViewAdapter<LVA,VH,MSL>,VH extends GroupRecyclerViewAdapter.GroupViewHolder<VH,LVA,MSL>> extends GroupRecyclerViewAdapter.MultiSelectStatusAdapter<MSL,LVA, GroupRecyclerViewAdapter.GroupViewAdapterPreferences,Group,VH> {

        @Override
        public  void onItemDeleteRequested(LVA adapter, Group item) {
            onDeleteGroup(item);
        }

        @Override
        public void onItemClick(LVA adapter, Group item) {
            EventBus.getDefault().post(new ViewGroupEvent(item));
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if(!PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile()) || (isSessionDetailsChanged() && !isServerConnectionChanged())){
            //trigger total screen refresh. Any errors will result in screen being closed.
            groupsModel.clear();
        } else if((!PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) || isAppInReadOnlyMode()) {
            // immediately leave this screen.
            Logging.log(Log.INFO, TAG, "removing from activity as not admin user");
            getParentFragmentManager().popBackStack();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (groupsModel.getPagesLoadedIdxToSizeMap() == 0 && viewAdapter != null) {
            viewAdapter.notifyDataSetChanged();
            loadGroupsPage(0);
        }
    }

    private void loadGroupsPage(int pageToLoad) {
        groupsModel.acquirePageLoadLock();
        try {
            if (!groupsModel.isPageLoadedOrBeingLoaded(pageToLoad) && !groupsModel.isFullyLoaded()) {
                int pageSize = prefs.getInt(getString(R.string.preference_groups_request_pagesize_key), getResources().getInteger(R.integer.preference_groups_request_pagesize_default));
                groupsModel.recordPageBeingLoaded(addActiveServiceCall(R.string.progress_loading_groups, new GroupsGetListResponseHandler(pageToLoad, pageSize)), pageToLoad);
            }
        } finally {
            groupsModel.releasePageLoadLock();
        }
    }

    private void addNewGroup() {
        EventBus.getDefault().post(new ViewGroupEvent(new Group()));
    }

    private void onGroupSelected(Group selectedGroup) {
        EventBus.getDefault().post(new ViewGroupEvent(selectedGroup));
    }

    private void onDeleteGroup(final Group thisItem) {
        if(thisItem.getMemberCount() > 0) {
            String message = getString(R.string.alert_confirm_really_delete_group);
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_cancel, R.string.button_ok, new OnDeleteGroupAction<>(getUiHelper(), thisItem));
        } else {
            deleteGroupNow(thisItem);
        }
    }

    private static class OnDeleteGroupAction<F extends GroupsListFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends UIHelper.QuestionResultAdapter<FUIH,F> implements Parcelable {

        private final Group group;

        public OnDeleteGroupAction(FUIH uiHelper, Group group) {
            super(uiHelper);
            this.group = group;
        }

        protected OnDeleteGroupAction(Parcel in) {
            super(in);
            group = in.readParcelable(Group.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeParcelable(group, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<OnDeleteGroupAction> CREATOR = new Creator<OnDeleteGroupAction>() {
            @Override
            public OnDeleteGroupAction createFromParcel(Parcel in) {
                return new OnDeleteGroupAction<>(in);
            }

            @Override
            public OnDeleteGroupAction[] newArray(int size) {
                return new OnDeleteGroupAction[size];
            }
        };

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                GroupsListFragment<?,?> fragment = getUiHelper().getParent();
                fragment.deleteGroupNow(group);
            }
        }
    }

    private void deleteGroupNow(Group thisItem) {
        this.deleteActionsPending.put(addActiveServiceCall(R.string.progress_delete_group, new GroupDeleteResponseHandler(thisItem.getId())), thisItem);
    }

    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener<>();
    }

    protected void onGroupsLoaded(final GroupsGetListResponseHandler.PiwigoGetGroupsListRetrievedResponse response) {
        groupsModel.acquirePageLoadLock();
        try {
            retryActionButton.hide();
            int firstIdxAdded = groupsModel.addItemPage(response.getPage(), response.getPageSize(), response.getGroups());
            viewAdapter.notifyItemRangeInserted(firstIdxAdded, response.getGroups().size());
        } finally {
            groupsModel.releasePageLoadLock();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(GroupDeletedEvent event) {
        viewAdapter.remove(event.getGroup());
        getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_group_delete_success_pattern, event.getGroup().getName()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(GroupUpdatedEvent event) {
        viewAdapter.replaceOrAddItem(event.getGroup());
    }

    protected void onGroupDeleted(final GroupDeleteResponseHandler.PiwigoDeleteGroupResponse response) {
        Group group = deleteActionsPending.remove(response.getMessageId());
        viewAdapter.remove(group);
        getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_group_delete_success_pattern, group.getName()));
    }

    protected void onGroupDeleteFailed(final long messageId) {
        Group group = deleteActionsPending.remove(messageId);
        getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_group_delete_failed_pattern, group.getName()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AppLockedEvent event) {
        if (isVisible()) {
            Logging.log(Log.INFO, TAG, "removing from activity immediately as app locked event rxd");
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    private static class CustomPiwigoResponseListener<F extends GroupsListFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends BasicPiwigoResponseListener<FUIH,F> {

        private static final String TAG = "GrpLstPwgResLstnr";

        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (getParent().isVisible()) {
                getParent().updateActiveSessionDetails();
            }
            super.onBeforeHandlePiwigoResponse(response);
        }

        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (getParent().isVisible()) {
                if (!PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
                    Logging.log(Log.INFO, TAG, "removing from activity as not admin");
                    getParent().getParentFragmentManager().popBackStack();
                    return;
                }
            }
            if (response instanceof GroupDeleteResponseHandler.PiwigoDeleteGroupResponse) {
                getParent().onGroupDeleted((GroupDeleteResponseHandler.PiwigoDeleteGroupResponse) response);
            } else if (response instanceof GroupsGetListResponseHandler.PiwigoGetGroupsListRetrievedResponse) {
                getParent().onGroupsLoaded((GroupsGetListResponseHandler.PiwigoGetGroupsListRetrievedResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.ErrorResponse) {
                if(getParent().getGroupsModel().isTrackingPageLoaderWithId(response.getMessageId())) {
                    getParent().onGroupsLoadFailed(response);
                } else if (getParent().getDeleteActionsPending().size() == 0) {
                    // assume this to be a list reload that's required.
                    getParent().showRetryActionButton();
                }
            }
        }

        @Override
        protected void handlePiwigoHttpErrorResponse(PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse msg) {
            if (getParent().getDeleteActionsPending().containsKey(msg.getMessageId())) {
                getParent().onGroupDeleteFailed(msg.getMessageId());
            } else {
                super.handlePiwigoHttpErrorResponse(msg);
            }
        }

        @Override
        protected void handlePiwigoServerErrorResponse(PiwigoResponseBufferingHandler.PiwigoServerErrorResponse msg) {
            if (getParent().getDeleteActionsPending().containsKey(msg.getMessageId())) {
                getParent().onGroupDeleteFailed(msg.getMessageId());
            } else {
                super.handlePiwigoServerErrorResponse(msg);
            }
        }

        @Override
        protected void handlePiwigoUnexpectedReplyErrorResponse(PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse msg) {
            if (getParent().getDeleteActionsPending().containsKey(msg.getMessageId())) {
                getParent().onGroupDeleteFailed(msg.getMessageId());
            } else {
                super.handlePiwigoUnexpectedReplyErrorResponse(msg);
            }
        }
    }

    protected void showRetryActionButton() {
        retryActionButton.show();
    }

    protected ConcurrentHashMap<Long, Group> getDeleteActionsPending() {
        return deleteActionsPending;
    }

    protected PiwigoGroups getGroupsModel() {
        return groupsModel;
    }

    protected void onGroupsLoadFailed(PiwigoResponseBufferingHandler.Response response) {
        groupsModel.acquirePageLoadLock();
        try {
            groupsModel.recordPageLoadFailed(response.getMessageId());
//            onListItemLoadFailed();
        } finally {
            groupsModel.releasePageLoadLock();
        }
    }
}
