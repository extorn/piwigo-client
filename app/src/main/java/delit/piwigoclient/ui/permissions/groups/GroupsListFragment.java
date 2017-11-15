package delit.piwigoclient.ui.permissions.groups;

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
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoAccessService;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.CustomImageButton;
import delit.piwigoclient.ui.common.MyFragment;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.GroupDeletedEvent;
import delit.piwigoclient.ui.events.GroupUpdatedEvent;
import delit.piwigoclient.ui.events.ViewGroupEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class GroupsListFragment extends MyFragment implements GroupsListAdapter.GroupActionListener {

    private static final String AVAILABLE_GROUPS = "availableGroups";
    private ArrayList<Group> availableGroups;
    private ListView list;
    private ConcurrentHashMap<Long, Group> deleteActionsPending = new ConcurrentHashMap<>();
    private CustomImageButton addListItemButton;
    private FloatingActionButton retryActionButton;

    public static GroupsListFragment newInstance() {
        GroupsListFragment fragment = new GroupsListFragment();
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
        outState.putSerializable(AVAILABLE_GROUPS, availableGroups);
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
            availableGroups = (ArrayList) savedInstanceState.getSerializable(AVAILABLE_GROUPS);
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
        heading.setText(R.string.groups_heading);
        heading.setVisibility(View.VISIBLE);

        list = (ListView) view.findViewById(R.id.list);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Group selectedGroup = (Group) list.getAdapter().getItem(position);
                onGroupSelected(selectedGroup);
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
                addNewGroup();
            }
        });

        retryActionButton = (FloatingActionButton)view.findViewById(R.id.list_retryAction_actionButton);
        retryActionButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                retryActionButton.setVisibility(View.GONE);
                addActiveServiceCall(R.string.progress_loading_groups,PiwigoAccessService.startActionGetGroupsList(0, 100, getContext()));
            }
        });

        if (availableGroups == null) {
            //TODO FEATURE: Support groups paging (load page size from settings)
            addActiveServiceCall(R.string.progress_loading_groups,PiwigoAccessService.startActionGetGroupsList(0, 100, getContext()));
        } else {
            populateListWithGroups();
        }

        return view;
    }

    private void addNewGroup() {
        EventBus.getDefault().post(new ViewGroupEvent(new Group()));
    }

    private void onGroupSelected(Group selectedGroup) {
        EventBus.getDefault().post(new ViewGroupEvent(selectedGroup));
//        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_information_coming_soon));
    }

    private void populateListWithGroups() {
        list.setAdapter(new GroupsListAdapter(getContext(), availableGroups, this));
        list.requestLayout();
    }

    @Override
    public void onDeleteItem(int position, final Group thisItem) {
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
        long deleteActionId = PiwigoAccessService.startActionDeleteGroup(thisItem.getId(), this.getContext());
        this.deleteActionsPending.put(deleteActionId, thisItem);
        addActiveServiceCall(R.string.progress_delete_group,deleteActionId);
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
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




    public void onGroupsLoaded(final PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse response) {

        getUiHelper().dismissProgressDialog();
        if (response.getItemsOnPage() == response.getPageSize()) {
            //TODO FEATURE: Support groups paging
            getUiHelper().showOrQueueDialogMessage(R.string.alert_title_error_too_many_groups, getString(R.string.alert_error_too_many_groups_message));
        }
        availableGroups = new ArrayList<>(response.getGroups());
        populateListWithGroups();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(GroupDeletedEvent event) {
        ((GroupsListAdapter) list.getAdapter()).remove(event.getGroup());
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, String.format(getString(R.string.alert_group_delete_success_pattern), event.getGroup().getName()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(GroupUpdatedEvent event) {
        ListIterator<Group> iter = availableGroups.listIterator();

        boolean newGroupAdded = false;
        while(iter.hasNext()) {
            Group g = iter.next();
            if (g.getId() == event.group.getId()) {
                iter.remove();
                if(g.getName().equals(event.group.getName())) {
                    iter.add(event.group);
                    newGroupAdded = true;
                }
                break;
            }
        }
        if(!newGroupAdded) {
            //add new group.
            iter = availableGroups.listIterator();
            while(iter.hasNext()) {
                if(iter.next().getName().compareTo(event.group.getName()) > 0) {
                    iter.previous();
                    iter.add(event.group);
                    newGroupAdded = true;
                    break;
                }
            }
            if(!newGroupAdded) {
                // just add it to the end.
                availableGroups.add(event.group);
            }
        }
        list.setAdapter(new GroupsListAdapter(getContext(), availableGroups, this));
    }

    public void onGroupDeleted(final PiwigoResponseBufferingHandler.PiwigoDeleteGroupResponse response) {
        Group group = deleteActionsPending.remove(response.getMessageId());
        ((GroupsListAdapter) list.getAdapter()).remove(group);
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, String.format(getString(R.string.alert_group_delete_success_pattern), group.getName()));
    }

    public void onGroupDeleteFailed(final long messageId) {
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
