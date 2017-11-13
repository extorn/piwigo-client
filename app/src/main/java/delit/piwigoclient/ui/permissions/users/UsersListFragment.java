package delit.piwigoclient.ui.permissions.users;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentHashMap;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoAccessService;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.CustomImageButton;
import delit.piwigoclient.ui.common.MyFragment;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.UserDeletedEvent;
import delit.piwigoclient.ui.events.UserUpdatedEvent;
import delit.piwigoclient.ui.events.ViewUserEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class UsersListFragment extends MyFragment implements UsersListAdapter.UserActionListener {

    private static final String AVAILABLE_USERS = "availableUsers";
    private ArrayList<User> availableUsers;
    private ListView list;
    private ConcurrentHashMap<Long, User> deleteActionsPending = new ConcurrentHashMap<>();
    private CustomImageButton addListItemButton;
    private FloatingActionButton retryActionButton;

    public static UsersListFragment newInstance() {
        UsersListFragment fragment = new UsersListFragment();
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
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(AVAILABLE_USERS, availableUsers);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if((!PiwigoSessionDetails.isAdminUser()) || isAppInReadOnlyMode()) {
            // immediately leave this screen.
            getFragmentManager().popBackStack();
            return null;
        }
        super.onCreateView(inflater, container, savedInstanceState);

        if (savedInstanceState != null) {
            availableUsers = (ArrayList) savedInstanceState.getSerializable(AVAILABLE_USERS);
        }

        View view = inflater.inflate(R.layout.layout_fullsize_list, container, false);

        AdView adView = (AdView)view.findViewById(R.id.list_adView);
        if(AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            adView.loadAd(new AdRequest.Builder().build());
            adView.setVisibility(View.VISIBLE);
        } else {
            adView.setVisibility(View.GONE);
        }

        TextView heading = (TextView) view.findViewById(R.id.heading);
        heading.setText(R.string.users_heading);
        heading.setVisibility(View.VISIBLE);

        list = (ListView) view.findViewById(R.id.list);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                User selectedUser = (User) list.getAdapter().getItem(position);
                onUserSelected(selectedUser);
            }
        });

        Button cancelButton = (Button) view.findViewById(R.id.list_action_cancel_button);
        cancelButton.setVisibility(View.GONE);

        Button toggleAllButton = (Button) view.findViewById(R.id.list_action_toggle_all_button);
        toggleAllButton.setVisibility(View.GONE);

        Button saveButton = (Button) view.findViewById(R.id.list_action_save_button);
        saveButton.setVisibility(View.GONE);

        addListItemButton = (CustomImageButton)view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setVisibility(View.VISIBLE);
        addListItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewUser();
            }
        });

        retryActionButton = (FloatingActionButton)view.findViewById(R.id.list_retryAction_actionButton);
        retryActionButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                retryActionButton.setVisibility(View.GONE);
                addActiveServiceCall(R.string.progress_loading_users, PiwigoAccessService.startActionGetUsersList(0, 100, getContext()));
            }
        });

        if (availableUsers == null) {
            //TODO FEATURE: Support Users paging (load page size from settings)
            addActiveServiceCall(R.string.progress_loading_users, PiwigoAccessService.startActionGetUsersList(0, 100, getContext()));
        } else {
            populateListWithUsers();
        }

        return view;
    }

    private void addNewUser() {
        EventBus.getDefault().post(new ViewUserEvent(new User()));
    }

    private void onUserSelected(User selectedUser) {
        EventBus.getDefault().post(new ViewUserEvent(selectedUser));
//        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_information_coming_soon));
    }


    private void populateListWithUsers() {
        list.setAdapter(new UsersListAdapter(getContext(), availableUsers, this));
        list.requestLayout();
    }

    @Override
    public void onDeleteItem(int position, final User thisItem) {

        String currentUser = PiwigoSessionDetails.getInstance().getUsername();
        if (currentUser.equals(thisItem.getUsername())) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, String.format(getString(R.string.alert_error_unable_to_delete_yourself_pattern), currentUser));
        } else {

            String message = getString(R.string.alert_confirm_really_delete_user);
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_cancel, R.string.button_ok, new UIHelper.QuestionResultListener() {
                @Override
                public void onDismiss(AlertDialog dialog) {
                }

                @Override
                public void onResult(AlertDialog dialog, boolean positiveAnswer) {
                    if(positiveAnswer) {
                        deleteUserNow(thisItem);
                    }
                }
            });
        }
    }

    private void deleteUserNow(User thisItem) {
        long deleteActionId = PiwigoAccessService.startActionDeleteUser(thisItem.getId(), this.getContext());
        this.deleteActionsPending.put(deleteActionId, thisItem);
        addActiveServiceCall(R.string.progress_delete_user, deleteActionId);
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetUsersListResponse) {
                onUsersLoaded((PiwigoResponseBufferingHandler.PiwigoGetUsersListResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoDeleteUserResponse) {
                onUserDeleted((PiwigoResponseBufferingHandler.PiwigoDeleteUserResponse) response);
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



    public void onUsersLoaded(final PiwigoResponseBufferingHandler.PiwigoGetUsersListResponse response) {

        getUiHelper().dismissProgressDialog();
        if (response.getItemsOnPage() == response.getPageSize()) {
            //TODO FEATURE: Support Users paging
            getUiHelper().showOrQueueDialogMessage(R.string.alert_title_error_too_many_users, getString(R.string.alert_error_too_many_users_message));
        }
        availableUsers = response.getUsers();
        populateListWithUsers();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UserDeletedEvent event) {
        ((UsersListAdapter) list.getAdapter()).remove(event.getUser());
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, String.format(getString(R.string.alert_user_delete_success_pattern), event.getUser().getUsername()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UserUpdatedEvent event) {
        ListIterator<User> iter = availableUsers.listIterator();

        boolean newUserAdded = false;
        while(iter.hasNext()) {
            User u = iter.next();
            if (u.getId() == event.getUser().getId()) {
                iter.remove();
                if(u.getUsername().equals(event.getUser().getUsername())) {
                    iter.add(event.getUser());
                    newUserAdded = true;
                }
                break;
            }
        }
        if(!newUserAdded) {
            //add new user.
            iter = availableUsers.listIterator();
            while(iter.hasNext()) {
                if(iter.next().getUsername().compareTo(event.getUser().getUsername()) > 0) {
                    iter.previous();
                    iter.add(event.getUser());
                    newUserAdded = true;
                    break;
                }
            }
            if(!newUserAdded) {
                // just add it to the end.
                availableUsers.add(event.getUser());
            }
        }
        list.setAdapter(new UsersListAdapter(getContext(), availableUsers, this));
    }

    public void onUserDeleted(final PiwigoResponseBufferingHandler.PiwigoDeleteUserResponse response) {
        User user = deleteActionsPending.remove(response.getMessageId());
        ((UsersListAdapter) list.getAdapter()).remove(user);
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, String.format(getString(R.string.alert_user_delete_success_pattern), user.getUsername()));
    }

    public void onUserDeleteFailed(final long messageId) {
        User user = deleteActionsPending.remove(messageId);
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, String.format(getString(R.string.alert_user_delete_failed_pattern), user.getUsername()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AppLockedEvent event) {
        if(isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }

}
