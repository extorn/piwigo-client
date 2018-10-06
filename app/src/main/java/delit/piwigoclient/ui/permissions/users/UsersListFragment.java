package delit.piwigoclient.ui.permissions.users;

import android.support.v7.app.AlertDialog;
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

import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.concurrent.ConcurrentHashMap;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUsers;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.UserDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UsersGetListResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.button.CustomImageButton;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.common.list.recycler.EndlessRecyclerViewScrollListener;
import delit.piwigoclient.ui.common.list.recycler.RecyclerViewMargin;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.UserDeletedEvent;
import delit.piwigoclient.ui.events.UserUpdatedEvent;
import delit.piwigoclient.ui.events.ViewUserEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class UsersListFragment extends MyFragment {

    private static final String USERS_MODEL = "usersModel";
    private static final String USERS_PAGE_BEING_LOADED = "usersPageBeingLoaded";
    private final ConcurrentHashMap<Long, User> deleteActionsPending = new ConcurrentHashMap<>();
    private FloatingActionButton retryActionButton;
    private PiwigoUsers usersModel = new PiwigoUsers();
    private UserRecyclerViewAdapter viewAdapter;
    private int pageToLoadNow = -1;
    private BaseRecyclerViewAdapterPreferences viewPrefs;

    public static UsersListFragment newInstance() {
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences().deletable();
        prefs.setAllowItemAddition(true);
        prefs.setEnabled(true);
        Bundle args = new Bundle();
        prefs.storeToBundle(args);
        UsersListFragment fragment = new UsersListFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            viewPrefs = new BaseRecyclerViewAdapterPreferences().loadFromBundle(getArguments());
            setArguments(null);
        }
        if (savedInstanceState != null) {
            viewPrefs = new BaseRecyclerViewAdapterPreferences().loadFromBundle(savedInstanceState);
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
        outState.putSerializable(USERS_MODEL, usersModel);
        outState.putInt(USERS_PAGE_BEING_LOADED, pageToLoadNow);
        viewPrefs.storeToBundle(outState);
    }


    @Override
    protected String buildPageHeading() {
        return getString(R.string.users_heading);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        if ((!PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) || isAppInReadOnlyMode()) {
            // immediately leave this screen.
            getFragmentManager().popBackStack();
            return null;
        }

        if (savedInstanceState != null && !isSessionDetailsChanged()) {
            usersModel = (PiwigoUsers) savedInstanceState.getSerializable(USERS_MODEL);
            pageToLoadNow = savedInstanceState.getInt(USERS_PAGE_BEING_LOADED);
            viewPrefs = new BaseRecyclerViewAdapterPreferences().loadFromBundle(savedInstanceState);
        }

        View view = inflater.inflate(R.layout.layout_fullsize_recycler_list, container, false);

        AdView adView = view.findViewById(R.id.list_adView);
        if (AdsManager.getInstance().shouldShowAdverts()) {
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

        CustomImageButton addListItemButton = view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setVisibility(viewPrefs.isAllowItemAddition() ? View.VISIBLE : View.GONE);
        addListItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewUser();
            }
        });

        retryActionButton = view.findViewById(R.id.list_retryAction_actionButton);
        retryActionButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                retryActionButton.setVisibility(View.GONE);
                loadUsersPage(pageToLoadNow);
            }
        });

        RecyclerView recyclerView = view.findViewById(R.id.list);

        RecyclerView.LayoutManager layoutMan = new LinearLayoutManager(getContext()); //new GridLayoutManager(getContext(), 1);

        recyclerView.setLayoutManager(layoutMan);

        viewAdapter = new UserRecyclerViewAdapter(container.getContext(), usersModel, new UserRecyclerViewAdapter.MultiSelectStatusAdapter<User>() {

            @Override
            public <A extends BaseRecyclerViewAdapter> void onItemDeleteRequested(A adapter, User u) {
                onDeleteUser(u);
            }

            @Override
            public <A extends BaseRecyclerViewAdapter> void onItemClick(A adapter, User item) {
                EventBus.getDefault().post(new ViewUserEvent(item));
            }
        }, viewPrefs);

        recyclerView.setAdapter(viewAdapter);
        recyclerView.addItemDecoration(new RecyclerViewMargin(getContext(), RecyclerViewMargin.DEFAULT_MARGIN_DP, 1));

        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(layoutMan) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                int pageToLoad = usersModel.getPagesLoaded();
                if (pageToLoad == 0 || usersModel.isFullyLoaded()) {
                    // already load this one by default so lets not double load it (or we've already loaded all items).
                    return;
                }
                loadUsersPage(pageToLoad);
            }
        };
        scrollListener.configure(usersModel.getPagesLoaded(), usersModel.getItemCount());
        recyclerView.addOnScrollListener(scrollListener);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (usersModel.getPagesLoaded() == 0 && viewAdapter != null) {
            viewAdapter.notifyDataSetChanged();
            loadUsersPage(0);
        }
    }

    private void loadUsersPage(int pageToLoad) {
        if (!usersModel.isPageLoaded(pageToLoad)) {
            this.pageToLoadNow = pageToLoad;
            int pageSize = prefs.getInt(getString(R.string.preference_users_request_pagesize_key), getResources().getInteger(R.integer.preference_users_request_pagesize_default));
            usersModel.recordPageBeingLoaded(addActiveServiceCall(R.string.progress_loading_users, new UsersGetListResponseHandler(pageToLoad, pageSize).invokeAsync(getContext())), pageToLoad);
        }
    }

    private void addNewUser() {
        EventBus.getDefault().post(new ViewUserEvent(new User()));
    }

    private void onUserSelected(User selectedUser) {
        EventBus.getDefault().post(new ViewUserEvent(selectedUser));
//        getUiHelper().showOrQueueMessage(R.string.alert_information, getString(R.string.alert_information_coming_soon));
    }

    private void onDeleteUser(final User thisItem) {
        String currentUser = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile()).getUsername();
        if (currentUser.equals(thisItem.getUsername())) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, String.format(getString(R.string.alert_error_unable_to_delete_yourself_pattern), currentUser));
        } else {

            String message = getString(R.string.alert_confirm_really_delete_user);
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_cancel, R.string.button_ok, new UIHelper.QuestionResultAdapter() {

                @Override
                public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                    if (Boolean.TRUE == positiveAnswer) {
                        deleteUserNow(thisItem);
                    }
                }
            });
        }
    }

    private void deleteUserNow(User thisItem) {
        long deleteActionId = new UserDeleteResponseHandler(thisItem.getId()).invokeAsync(this.getContext());
        this.deleteActionsPending.put(deleteActionId, thisItem);
        addActiveServiceCall(R.string.progress_delete_user, deleteActionId);
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private void onUsersLoaded(final PiwigoResponseBufferingHandler.PiwigoGetUsersListResponse response) {
        usersModel.acquirePageLoadLock();
        try {
            usersModel.recordPageLoadSucceeded(response.getMessageId());
            pageToLoadNow = -1;
            retryActionButton.setVisibility(View.GONE);
            int firstIdxAdded = usersModel.addItemPage(response.getPage(), response.getPageSize(), response.getUsers());
            viewAdapter.notifyItemRangeInserted(firstIdxAdded, response.getUsers().size());
        } finally {
            usersModel.releasePageLoadLock();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UserDeletedEvent event) {
        viewAdapter.remove(event.getUser());
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, String.format(getString(R.string.alert_user_delete_success_pattern), event.getUser().getUsername()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UserUpdatedEvent event) {
        viewAdapter.replaceOrAddItem(event.getUser());
    }

    private void onUserDeleted(final PiwigoResponseBufferingHandler.PiwigoDeleteUserResponse response) {
        User user = deleteActionsPending.remove(response.getMessageId());
        viewAdapter.remove(user);
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, String.format(getString(R.string.alert_user_delete_success_pattern), user.getUsername()));
    }

    private void onUserDeleteFailed(final long messageId) {
        User user = deleteActionsPending.remove(messageId);
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, String.format(getString(R.string.alert_user_delete_failed_pattern), user.getUsername()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AppLockedEvent event) {
        if (isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {

        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (isVisible()) {
                updateActiveSessionDetails();
            }
            super.onBeforeHandlePiwigoResponse(response);
        }

        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetUsersListResponse) {
                onUsersLoaded((PiwigoResponseBufferingHandler.PiwigoGetUsersListResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoDeleteUserResponse) {
                onUserDeleted((PiwigoResponseBufferingHandler.PiwigoDeleteUserResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.ErrorResponse) {
                if(usersModel.isTrackingPageLoaderWithId(response.getMessageId())) {
                    onUsersLoadFailed(response);
                } else if (deleteActionsPending.size() == 0) {
                    // assume this to be a list reload that's required.
                    retryActionButton.setVisibility(View.VISIBLE);
                }
            }
        }

        protected void onUsersLoadFailed(PiwigoResponseBufferingHandler.Response response) {
            usersModel.acquirePageLoadLock();
            try {
                usersModel.recordPageLoadFailed(response.getMessageId());
//                onListItemLoadFailed();
            } finally {
                usersModel.releasePageLoadLock();
            }
        }

        @Override
        protected void handlePiwigoHttpErrorResponse(PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse msg) {
            if (deleteActionsPending.containsKey(msg.getMessageId())) {
                onUserDeleteFailed(msg.getMessageId());
            } else {
                super.handlePiwigoHttpErrorResponse(msg);
            }
        }

        @Override
        protected void handlePiwigoUnexpectedReplyErrorResponse(PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse msg) {
            if (deleteActionsPending.containsKey(msg.getMessageId())) {
                onUserDeleteFailed(msg.getMessageId());
            } else {
                super.handlePiwigoUnexpectedReplyErrorResponse(msg);
            }
        }

        @Override
        protected void handlePiwigoServerErrorResponse(PiwigoResponseBufferingHandler.PiwigoServerErrorResponse msg) {
            if (deleteActionsPending.containsKey(msg.getMessageId())) {
                onUserDeleteFailed(msg.getMessageId());
            } else {
                super.handlePiwigoServerErrorResponse(msg);
            }
        }
    }

}
