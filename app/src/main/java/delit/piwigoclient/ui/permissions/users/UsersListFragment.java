package delit.piwigoclient.ui.permissions.users;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
    private final ConcurrentHashMap<Long, User> deleteActionsPending = new ConcurrentHashMap<>();
    private FloatingActionButton retryActionButton;
    private PiwigoUsers usersModel = new PiwigoUsers();
    private UserRecyclerViewAdapter viewAdapter;
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
        Bundle b = savedInstanceState;
        if(b == null) {
            b = getArguments();
        }
        if (b != null) {
            viewPrefs = new BaseRecyclerViewAdapterPreferences().loadFromBundle(b);
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
        outState.putParcelable(USERS_MODEL, usersModel);
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

        if (savedInstanceState != null && !isSessionDetailsChanged()) {
            usersModel = savedInstanceState.getParcelable(USERS_MODEL);
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
                retryActionButton.hide();
                loadUsersPage(usersModel.getNextPageToReload());
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
                int pageToLoad = page;
                if (usersModel.isPageLoadedOrBeingLoaded(page) || usersModel.isFullyLoaded()) {
                    Integer missingPage = usersModel.getAMissingPage();
                    if(missingPage != null) {
                        pageToLoad = missingPage;
                    } else {
                        // already load this one by default so lets not double load it (or we've already loaded all items).
                        return;
                    }
                }
                loadUsersPage(pageToLoad);
            }
        };
        scrollListener.configure(usersModel.getPagesLoaded(), usersModel.getItemCount());
        recyclerView.addOnScrollListener(scrollListener);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if(!PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile()) || (isSessionDetailsChanged() && !isServerConnectionChanged())){
            //trigger total screen refresh. Any errors will result in screen being closed.
            usersModel.clear();
        } else if((!PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) || isAppInReadOnlyMode()) {
            // immediately leave this screen.
            getFragmentManager().popBackStack();
            return;
        }
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
        usersModel.acquirePageLoadLock();
        try {
            if (!usersModel.isPageLoadedOrBeingLoaded(pageToLoad) && !usersModel.isFullyLoaded()) {
                int pageSize = prefs.getInt(getString(R.string.preference_users_request_pagesize_key), getResources().getInteger(R.integer.preference_users_request_pagesize_default));
                usersModel.recordPageBeingLoaded(addActiveServiceCall(R.string.progress_loading_users, new UsersGetListResponseHandler(pageToLoad, pageSize).invokeAsync(getContext())), pageToLoad);
            }
        } finally {
            usersModel.releasePageLoadLock();
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

    private void onUsersLoaded(final UsersGetListResponseHandler.PiwigoGetUsersListResponse response) {
        usersModel.acquirePageLoadLock();
        try {
            usersModel.recordPageLoadSucceeded(response.getMessageId());
            retryActionButton.hide();
            int firstIdxAdded = usersModel.addItemPage(response.getPage(), response.getPageSize(), response.getUsers());
            viewAdapter.notifyItemRangeInserted(firstIdxAdded, response.getUsers().size());
        } finally {
            usersModel.releasePageLoadLock();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(UserDeletedEvent event) {
        viewAdapter.remove(event.getUser());
        getUiHelper().showDetailedMsg(R.string.alert_information, String.format(getString(R.string.alert_user_delete_success_pattern), event.getUser().getUsername()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(UserUpdatedEvent event) {
        viewAdapter.replaceOrAddItem(event.getUser());
    }

    private void onUserDeleted(final UserDeleteResponseHandler.PiwigoDeleteUserResponse response) {
        User user = deleteActionsPending.remove(response.getMessageId());
        viewAdapter.remove(user);
        getUiHelper().showDetailedMsg(R.string.alert_information, String.format(getString(R.string.alert_user_delete_success_pattern), user.getUsername()));
    }

    private void onUserDeleteFailed(final long messageId) {
        User user = deleteActionsPending.remove(messageId);
        getUiHelper().showDetailedMsg(R.string.alert_information, String.format(getString(R.string.alert_user_delete_failed_pattern), user.getUsername()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
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
            if (isVisible()) {
                if (!PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
                    getFragmentManager().popBackStack();
                    return;
                }
            }
            if (response instanceof UsersGetListResponseHandler.PiwigoGetUsersListResponse) {
                onUsersLoaded((UsersGetListResponseHandler.PiwigoGetUsersListResponse) response);
            } else if (response instanceof UserDeleteResponseHandler.PiwigoDeleteUserResponse) {
                onUserDeleted((UserDeleteResponseHandler.PiwigoDeleteUserResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.ErrorResponse) {
                if(usersModel.isTrackingPageLoaderWithId(response.getMessageId())) {
                    onUsersLoadFailed(response);
                } else if (deleteActionsPending.size() == 0) {
                    // assume this to be a list reload that's required.
                    retryActionButton.show();
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
