package delit.piwigoclient.ui.permissions.users;

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
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUsers;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.UserDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UsersGetListResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.UserDeletedEvent;
import delit.piwigoclient.ui.events.UserUpdatedEvent;
import delit.piwigoclient.ui.events.ViewUserEvent;
import delit.piwigoclient.ui.model.PiwigoUsersModel;

/**
 * Created by gareth on 26/05/17.
 */

public class UsersListFragment<F extends UsersListFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends MyFragment<F,FUIH> {

    private static final String TAG = "UsrListFrag";
    private final ConcurrentHashMap<Long, User> deleteActionsPending = new ConcurrentHashMap<>();
    private ExtendedFloatingActionButton retryActionButton;
    private PiwigoUsers usersModel;
    private UserRecyclerViewAdapter<?,?,?> viewAdapter;
    private UserRecyclerViewAdapter.UserRecyclerViewAdapterPreferences viewPrefs;

    public static UsersListFragment<?,?> newInstance() {
        UserRecyclerViewAdapter.UserRecyclerViewAdapterPreferences prefs = new UserRecyclerViewAdapter.UserRecyclerViewAdapterPreferences(true);
        Bundle args = new Bundle();
        prefs.storeToBundle(args);
        UsersListFragment<?,?> fragment = new UsersListFragment<>();
        fragment.setTheme(R.style.Theme_App_EditPages);
        fragment.setArguments(args);
        return fragment;
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
        usersModel = new ViewModelProvider(this).get(PiwigoUsersModel.class).getPiwigoUsers().getValue();

        if (isSessionDetailsChanged()) {
            usersModel.clear();
        }
        if (savedInstanceState != null) {
            viewPrefs = new UserRecyclerViewAdapter.UserRecyclerViewAdapterPreferences(savedInstanceState);
        } else {
            viewPrefs = new UserRecyclerViewAdapter.UserRecyclerViewAdapterPreferences(getArguments());
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
        addListItemButton.setOnClickListener(v -> addNewUser());

        retryActionButton = view.findViewById(R.id.list_retryAction_actionButton);
        retryActionButton.setOnClickListener(v -> {
            retryActionButton.hide();
            loadUsersPage(usersModel.getNextPageToReload());
        });

        RecyclerView recyclerView = view.findViewById(R.id.list);

        RecyclerView.LayoutManager layoutMan = new GridLayoutManager(getContext(), OtherPreferences.getColumnsOfUsers(getPrefs(), requireActivity()));

        recyclerView.setLayoutManager(layoutMan);

        viewAdapter = new UserRecyclerViewAdapter(getContext(), usersModel, new UserMultiSelectListener<>(), viewPrefs);

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
        scrollListener.configure(usersModel.getPagesLoadedIdxToSizeMap(), usersModel.getItemCount());
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
            Logging.log(Log.INFO, TAG, "removing from activity as not admin user");
            getParentFragmentManager().popBackStack();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (usersModel.getPagesLoadedIdxToSizeMap() == 0 && viewAdapter != null) {
            viewAdapter.notifyDataSetChanged();
            loadUsersPage(0);
        }
    }

    private void loadUsersPage(int pageToLoad) {
        usersModel.acquirePageLoadLock();
        try {
            if (!usersModel.isPageLoadedOrBeingLoaded(pageToLoad) && !usersModel.isFullyLoaded()) {
                int pageSize = prefs.getInt(getString(R.string.preference_users_request_pagesize_key), getResources().getInteger(R.integer.preference_users_request_pagesize_default));
                usersModel.recordPageBeingLoaded(addActiveServiceCall(R.string.progress_loading_users, new UsersGetListResponseHandler(pageToLoad, pageSize)), pageToLoad);
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
    }

    private void onDeleteUser(final User thisItem) {
        String currentUser = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile()).getUsername();
        if (currentUser.equals(thisItem.getUsername())) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_unable_to_delete_yourself_pattern, currentUser));
        } else {

            String message = getString(R.string.alert_confirm_really_delete_user);
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_cancel, R.string.button_ok, new OnDeleteUserAction<>(getUiHelper(), thisItem));
        }
    }

    private static class OnDeleteUserAction<F extends UsersListFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends UIHelper.QuestionResultAdapter<FUIH,F> implements Parcelable {

        private final User user;

        public OnDeleteUserAction(FUIH uiHelper, User user) {
            super(uiHelper);
            this.user = user;
        }

        protected OnDeleteUserAction(Parcel in) {
            super(in);
            user = in.readParcelable(User.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeParcelable(user, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<OnDeleteUserAction<?,?>> CREATOR = new Creator<OnDeleteUserAction<?,?>>() {
            @Override
            public OnDeleteUserAction<?,?> createFromParcel(Parcel in) {
                return new OnDeleteUserAction<>(in);
            }

            @Override
            public OnDeleteUserAction<?,?>[] newArray(int size) {
                return new OnDeleteUserAction[size];
            }
        };

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                F fragment = getUiHelper().getParent();
                fragment.deleteUserNow(user);
            }
        }
    }

    protected void deleteUserNow(User thisItem) {
        this.deleteActionsPending.put(addActiveServiceCall(R.string.progress_delete_user, new UserDeleteResponseHandler(thisItem.getId())), thisItem);
    }

    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener<>();
    }

    protected void onUsersLoaded(final UsersGetListResponseHandler.PiwigoGetUsersListResponse response) {
        usersModel.acquirePageLoadLock();
        try {
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
        getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_user_delete_success_pattern, event.getUser().getUsername()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(UserUpdatedEvent event) {
        viewAdapter.replaceOrAddItem(event.getUser());
    }

    protected void onUserDeleted(final UserDeleteResponseHandler.PiwigoDeleteUserResponse response) {
        User user = deleteActionsPending.remove(response.getMessageId());
        viewAdapter.remove(user);
        getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_user_delete_success_pattern, user.getUsername()));
    }

    protected void onUserDeleteFailed(final long messageId) {
        User user = deleteActionsPending.remove(messageId);
        getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_user_delete_failed_pattern, user.getUsername()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AppLockedEvent event) {
        if (isVisible()) {
            Logging.log(Log.INFO, TAG, "removing from activity immediately as app locked event rxd");
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    private static class CustomPiwigoResponseListener<F extends UsersListFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends BasicPiwigoResponseListener<FUIH,F> {

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
                    Logging.log(Log.INFO, TAG, "removing from activity as not admin user");
                    getParent().getParentFragmentManager().popBackStack();
                    return;
                }
            }
            if (response instanceof UsersGetListResponseHandler.PiwigoGetUsersListResponse) {
                getParent().onUsersLoaded((UsersGetListResponseHandler.PiwigoGetUsersListResponse) response);
            } else if (response instanceof UserDeleteResponseHandler.PiwigoDeleteUserResponse) {
                getParent().onUserDeleted((UserDeleteResponseHandler.PiwigoDeleteUserResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.ErrorResponse) {
                if(getParent().getUsersModel().isTrackingPageLoaderWithId(response.getMessageId())) {
                    onUsersLoadFailed(response);
                } else if (getParent().getDeleteActionsPending().size() == 0) {
                    // assume this to be a list reload that's required.
                    getParent().showRetryActionButton();
                }
            }
        }

        protected void onUsersLoadFailed(PiwigoResponseBufferingHandler.Response response) {
            getParent().getUsersModel().acquirePageLoadLock();
            try {
                getParent().getUsersModel().recordPageLoadFailed(response.getMessageId());
//                onListItemLoadFailed();
            } finally {
                getParent().getUsersModel().releasePageLoadLock();
            }
        }

        @Override
        protected void handlePiwigoHttpErrorResponse(PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse msg) {
            if (getParent().getDeleteActionsPending().containsKey(msg.getMessageId())) {
                getParent().onUserDeleteFailed(msg.getMessageId());
            } else {
                super.handlePiwigoHttpErrorResponse(msg);
            }
        }

        @Override
        protected void handlePiwigoUnexpectedReplyErrorResponse(PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse msg) {
            if (getParent().getDeleteActionsPending().containsKey(msg.getMessageId())) {
                getParent().onUserDeleteFailed(msg.getMessageId());
            } else {
                super.handlePiwigoUnexpectedReplyErrorResponse(msg);
            }
        }

        @Override
        protected void handlePiwigoServerErrorResponse(PiwigoResponseBufferingHandler.PiwigoServerErrorResponse msg) {
            if (getParent().getDeleteActionsPending().containsKey(msg.getMessageId())) {
                getParent().onUserDeleteFailed(msg.getMessageId());
            } else {
                super.handlePiwigoServerErrorResponse(msg);
            }
        }
    }

    protected void showRetryActionButton() {
        retryActionButton.show();
    }

    protected ConcurrentHashMap<Long, User> getDeleteActionsPending() {
        return deleteActionsPending;
    }

    protected PiwigoUsers getUsersModel() {
        return usersModel;
    }

    private class UserMultiSelectListener<MSL extends UserMultiSelectListener<MSL,LVA,VH>, LVA extends UserRecyclerViewAdapter<LVA,VH,MSL>,VH extends UserRecyclerViewAdapter.UserViewHolder<VH,LVA,MSL>> extends UserRecyclerViewAdapter.MultiSelectStatusAdapter<MSL,LVA, UserRecyclerViewAdapter.UserRecyclerViewAdapterPreferences, User,VH> {
        @Override
        public  void onItemDeleteRequested(LVA adapter, User u) {
            onDeleteUser(u);
        }

        @Override
        public void onItemClick(LVA adapter, User item) {
            EventBus.getDefault().post(new ViewUserEvent(item));
        }
    }
}
