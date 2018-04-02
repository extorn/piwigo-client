package delit.piwigoclient.ui.permissions.groups;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoAccessService;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.CustomClickTouchListener;
import delit.piwigoclient.ui.common.CustomImageButton;
import delit.piwigoclient.ui.common.MyFragment;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.GroupDeletedEvent;
import delit.piwigoclient.ui.events.GroupUpdatedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionNeededEvent;
import delit.piwigoclient.ui.permissions.AlbumSelectionListAdapter;

/**
 * Created by gareth on 21/06/17.
 */

public class GroupFragment extends MyFragment {

    private static final String CURRENT_GROUP_MEMBERS = "currentGroupMembers";
    private static final String CURRENT_GROUP = "currentGroup";
    private static final String CURRENT_ACCESSIBLE_ALBUM_IDS = "currentAccessibleAlbumIds";
    private static final String NEW_GROUP_MEMBERS = "newGroupMembers";
    private static final String NEW_GROUP = "newGroup";
    private static final String NEW_ACCESSIBLE_ALBUM_IDS = "newAccessibleAlbumIds";
    private static final String AVAILABLE_ALBUMS = "availableAlbums";
    private static final String STATE_FIELDS_EDITABLE = "fieldsAreEditable";
    private static final String IN_FLIGHT_MEMBER_SAVE_ACTION_IDS = "memberSaveActionIds";
    private static final String IN_FLIGHT_PERMISSIONS_SAVE_ACTION_IDS = "permissionsSaveActionIds";
    public static final String STATE_SELECT_USERS_ACTION_ID = "selectUsersActionId";

    // Fields
    private TextView groupNameField;
    private TextView membersField;
    private ListView albumAccessRightsField;
    private CustomImageButton editButton;
    private CustomImageButton discardButton;
    private CustomImageButton saveButton;
    private CustomImageButton deleteButton;

    // stuff stored in state.
    private Group currentGroup;
    private ArrayList<Username> currentGroupMembers;
    private HashSet<Long> currentAccessibleAlbumIds;
    private Group newGroup;
    private ArrayList<Username> newGroupMembers;
    private HashSet<Long> newAccessibleAlbumIds;
    private ArrayList<CategoryItemStub> availableGalleries;
    private boolean fieldsEditable;
    private CheckBox isDefaultField;
    private HashSet<Long> memberSaveActionIds;
    private HashSet<Long> permissionsSaveActionIds;
    private int selectUsersActionId;


    public static GroupFragment newInstance(Group group) {
        GroupFragment fragment = new GroupFragment();
        Bundle args = new Bundle();
        args.putSerializable(CURRENT_GROUP, group);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentGroup = (Group) getArguments().getSerializable(CURRENT_GROUP);
        }
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
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        // This has to be done here because the albumAccessRightsField maintains a checked state internally (ignorant of any other alterations to that field).
        if (availableGalleries != null) {
            populateAlbumPermissionsList();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(CURRENT_GROUP_MEMBERS, currentGroupMembers);
        outState.putSerializable(CURRENT_ACCESSIBLE_ALBUM_IDS, currentAccessibleAlbumIds);
        outState.putSerializable(CURRENT_GROUP, currentGroup);
        outState.putSerializable(NEW_GROUP_MEMBERS, newGroupMembers);
        outState.putSerializable(NEW_ACCESSIBLE_ALBUM_IDS, newAccessibleAlbumIds);
        outState.putSerializable(NEW_GROUP, newGroup);
        outState.putSerializable(AVAILABLE_ALBUMS, availableGalleries);
        outState.putBoolean(STATE_FIELDS_EDITABLE, fieldsEditable);
        outState.putSerializable(IN_FLIGHT_MEMBER_SAVE_ACTION_IDS, memberSaveActionIds);
        outState.putSerializable(IN_FLIGHT_PERMISSIONS_SAVE_ACTION_IDS, permissionsSaveActionIds);
        outState.putInt(STATE_SELECT_USERS_ACTION_ID, selectUsersActionId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        if((!PiwigoSessionDetails.isAdminUser()) || isAppInReadOnlyMode() || isServerConnectionChanged()) {
            // immediately leave this screen.
            getFragmentManager().popBackStack();
            return null;
        }

        View v = inflater.inflate(R.layout.fragment_group, container, false);

        AdView adView = v.findViewById(R.id.group_adView);
        if(AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            adView.loadAd(new AdRequest.Builder().build());
            adView.setVisibility(View.VISIBLE);
        } else {
            adView.setVisibility(View.GONE);
        }

        if (savedInstanceState != null) {
            currentGroupMembers = (ArrayList) savedInstanceState.getSerializable(CURRENT_GROUP_MEMBERS);
            currentGroup = (Group) savedInstanceState.getSerializable(CURRENT_GROUP);
            currentAccessibleAlbumIds = (HashSet<Long>) savedInstanceState.getSerializable(CURRENT_ACCESSIBLE_ALBUM_IDS);
            newGroupMembers = (ArrayList) savedInstanceState.getSerializable(NEW_GROUP_MEMBERS);
            newGroup = (Group) savedInstanceState.getSerializable(NEW_GROUP);
            newAccessibleAlbumIds = (HashSet<Long>) savedInstanceState.getSerializable(NEW_ACCESSIBLE_ALBUM_IDS);
            availableGalleries = (ArrayList<CategoryItemStub>) savedInstanceState.getSerializable(AVAILABLE_ALBUMS);
            fieldsEditable = savedInstanceState.getBoolean(STATE_FIELDS_EDITABLE);
            memberSaveActionIds = (HashSet<Long>) savedInstanceState.getSerializable(IN_FLIGHT_MEMBER_SAVE_ACTION_IDS);
            permissionsSaveActionIds = (HashSet<Long>) savedInstanceState.getSerializable(IN_FLIGHT_PERMISSIONS_SAVE_ACTION_IDS);
            selectUsersActionId = savedInstanceState.getInt(STATE_SELECT_USERS_ACTION_ID);
        }

        groupNameField = v.findViewById(R.id.group_name);
        membersField = v.findViewById(R.id.group_members);
        membersField.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectGroupMembers();
            }
        });
        isDefaultField = v.findViewById(R.id.group_is_default);

        albumAccessRightsField = v.findViewById(R.id.group_access_rights);
        albumAccessRightsField.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        albumAccessRightsField.setOnTouchListener(new CustomClickTouchListener(getContext()) {
            @Override
            public boolean onClick() {
                onExpandPermissions();
                return true;
            }
        });

        editButton = v.findViewById(R.id.group_action_edit_button);
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setFieldsEditable(true);
            }
        });
        discardButton = v.findViewById(R.id.group_action_discard_button);
        discardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newGroupMembers = null;
                newGroup = null;
                newAccessibleAlbumIds = null;
                setFieldsFromModel(currentGroup);
                populateAlbumPermissionsList();
                setFieldsEditable(currentGroup.getId() < 0);
            }
        });
        saveButton = v.findViewById(R.id.group_action_save_button);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveGroupChanges();
            }
        });
        deleteButton = v.findViewById(R.id.group_action_delete_button);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteGroup(currentGroup);
            }
        });

        if(currentGroup.getId() < 0) {
            currentAccessibleAlbumIds = new HashSet<>();
            currentGroupMembers = new ArrayList<>();
            fieldsEditable = true;
        }

        setFieldsEditable(fieldsEditable);

        if(newGroup != null) {
            setFieldsFromModel(newGroup);
        } else {
            setFieldsFromModel(currentGroup);
        }

        ScrollView scrollview = v.findViewById(R.id.group_edit_scrollview);
        scrollview.fullScroll(View.FOCUS_UP);

        return v;
    }

    private HashSet<Long> buildPreselectedUserIds(List<Username> selectedUsernames) {
        HashSet<Long> preselectedUsernames = null;
        if (selectedUsernames != null) {
            preselectedUsernames = new HashSet<>(selectedUsernames.size());
            int i = 0;
            for (Username u : selectedUsernames) {
                preselectedUsernames.add(u.getId());
            }
        } else {
            preselectedUsernames = new HashSet<>(0);
        }
        return preselectedUsernames;
    }

    private void selectGroupMembers() {
        ArrayList<Username> currentSelection = getLatestGroupMembers();

        HashSet<Long> preselectedUsernames = buildPreselectedUserIds(currentSelection);
        UsernameSelectionNeededEvent usernameSelectionNeededEvent = new UsernameSelectionNeededEvent(true, fieldsEditable, new HashSet<Long>(0), preselectedUsernames);
        selectUsersActionId =  usernameSelectionNeededEvent.getActionId();
        EventBus.getDefault().post(usernameSelectionNeededEvent);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UsernameSelectionCompleteEvent usernameSelectionCompleteEvent) {

        if(selectUsersActionId == usernameSelectionCompleteEvent.getActionId()) {
            selectUsersActionId = -1;
            newGroupMembers = new ArrayList<>(usernameSelectionCompleteEvent.getSelectedItems());
            populateGroupMembersField();
        }
    }


    private void saveGroupChanges() {
        fieldsEditable = false;
        setFieldsEditable(fieldsEditable);

        memberSaveActionIds = new HashSet<>(2);
        permissionsSaveActionIds = new HashSet<>(2);
        String name = groupNameField.getText().toString();
        boolean isDefault = isDefaultField.isChecked();
        newGroup = new Group(currentGroup.getId(), name, isDefault);
        if(newGroup.getId() < 0) {
            long saveActionId = PiwigoAccessService.startActionAddGroup(newGroup, getContext());
            addActiveServiceCall(R.string.progress_adding_group, saveActionId);

        } else {
            long saveActionId = PiwigoAccessService.startActionUpdateGroupInfo(currentGroup, newGroup, getContext());
            addActiveServiceCall(R.string.progress_saving_changes, saveActionId);
        }
    }

    private HashSet<Long> getLatestAlbumPermissions() {
        HashSet<Long> albumPermissions;
        if(newAccessibleAlbumIds != null) {
            albumPermissions = newAccessibleAlbumIds;
        } else if(currentAccessibleAlbumIds != null) {
            albumPermissions = currentAccessibleAlbumIds;
        } else {
            currentAccessibleAlbumIds = new HashSet<>();
            albumPermissions = currentAccessibleAlbumIds;
        }
        return albumPermissions;
    }

    private void setFieldsFromModel(Group group) {
        groupNameField.setText(group.getName());
        isDefaultField.setChecked(group.isDefault());

        populateGroupMembersField();

        if (currentGroupMembers == null) {
            ArrayList<Long> groups = new ArrayList<>(1);
            groups.add(group.getId());
            addActiveServiceCall(R.string.progress_loading_group_details,PiwigoAccessService.startActionGetUsernamesList(groups, 0, 100, getContext()));
        }

        if (availableGalleries == null) {
            addActiveServiceCall(R.string.progress_loading_group_details, PiwigoAccessService.startActionGetSubCategoryNames(CategoryItem.ROOT_ALBUM.getId(), true, getContext()));
        }

        if (currentAccessibleAlbumIds == null) {
            addActiveServiceCall(R.string.progress_loading_group_details, PiwigoAccessService.startActionGetAllAlbumPermissionsForGroup(group.getId(), getContext()));
        }

        if (availableGalleries != null) {
            populateAlbumPermissionsList();
        }

        deleteButton.setEnabled(group.getId() >= 0);
    }

    private void deleteGroup(final Group group) {
        String message = getString(R.string.alert_confirm_really_delete_group);
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_cancel, R.string.button_ok, new UIHelper.QuestionResultListener() {
            @Override
            public void onDismiss(AlertDialog dialog) {

            }

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if(Boolean.TRUE == positiveAnswer) {
                    deleteGroupNow(group);
                }
            }
        });
    }

    private void deleteGroupNow(Group group) {
        addActiveServiceCall(R.string.progress_delete_group, PiwigoAccessService.startActionDeleteGroup(group.getId(), getContext()));
    }

    private void setFieldsEditable(boolean editable) {

        fieldsEditable = editable;

        groupNameField.setEnabled(editable);
        isDefaultField.setEnabled(editable);

        editButton.setEnabled(!editable);
        discardButton.setEnabled(editable);
        saveButton.setEnabled(editable);


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
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoDeleteGroupResponse) {
                onGroupDeleted((PiwigoResponseBufferingHandler.PiwigoDeleteGroupResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGroupPermissionsRetrievedResponse) {
                onGroupPermissionsRetrieved((PiwigoResponseBufferingHandler.PiwigoGroupPermissionsRetrievedResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) {
                onGetSubGalleries((PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGroupUpdateInfoResponse) {
                onGroupInfoUpdated((PiwigoResponseBufferingHandler.PiwigoGroupUpdateInfoResponse)response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoAddGroupResponse) {
                onGroupAdded((PiwigoResponseBufferingHandler.PiwigoAddGroupResponse)response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGroupAddMembersResponse) {
                onGroupMembersAdded((PiwigoResponseBufferingHandler.PiwigoGroupAddMembersResponse)response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGroupRemoveMembersResponse) {
                onGroupMembersRemoved((PiwigoResponseBufferingHandler.PiwigoGroupRemoveMembersResponse)response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGroupPermissionsAddedResponse) {
                onGroupPermissionsAdded((PiwigoResponseBufferingHandler.PiwigoGroupPermissionsAddedResponse)response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGroupPermissionsRemovedResponse) {
                onGroupPermissionsRemoved((PiwigoResponseBufferingHandler.PiwigoGroupPermissionsRemovedResponse)response);
            }
            //TODO check I'm handling all data flow streams for adding and updating an existing group... including sending that refreshed gorup back to previous groups list screen.
        }
    }

    private void publishGroupAddedOrAlteredEventIfFinished() {
        if(newGroup == null && newAccessibleAlbumIds == null && newGroupMembers == null) {
            EventBus.getDefault().post(new GroupUpdatedEvent(currentGroup));
        }
        setFieldsFromModel(currentGroup);
    }

    private void onGroupPermissionsRemoved(PiwigoResponseBufferingHandler.PiwigoGroupPermissionsRemovedResponse response) {
        synchronized (permissionsSaveActionIds) {
            permissionsSaveActionIds.remove(response.getMessageId());
            if(permissionsSaveActionIds.size() == 0) {
                currentAccessibleAlbumIds = newAccessibleAlbumIds;
                newAccessibleAlbumIds = null;
            }
        }
        publishGroupAddedOrAlteredEventIfFinished();
    }

    private void onGroupPermissionsAdded(PiwigoResponseBufferingHandler.PiwigoGroupPermissionsAddedResponse response) {
        synchronized (permissionsSaveActionIds) {
            permissionsSaveActionIds.remove(response.getMessageId());
            if(permissionsSaveActionIds.size() == 0) {
                currentAccessibleAlbumIds = newAccessibleAlbumIds;
                newAccessibleAlbumIds = null;
            }
        }
        publishGroupAddedOrAlteredEventIfFinished();
    }

    private void onGroupMembersAdded(PiwigoResponseBufferingHandler.PiwigoGroupAddMembersResponse response) {
        synchronized (memberSaveActionIds) {
            memberSaveActionIds.remove(response.getMessageId());
            currentGroup = response.getGroup();
            if(memberSaveActionIds.size() == 0) {
                currentGroupMembers = newGroupMembers;
                newGroupMembers = null;
            }
        }
        publishGroupAddedOrAlteredEventIfFinished();
    }

    private void onGroupMembersRemoved(PiwigoResponseBufferingHandler.PiwigoGroupRemoveMembersResponse response) {
        synchronized (memberSaveActionIds) {
            memberSaveActionIds.remove(response.getMessageId());
            currentGroup = response.getGroup();
            if(memberSaveActionIds.size() == 0) {
                currentGroupMembers = newGroupMembers;
                newGroupMembers = null;
            }
        }
        publishGroupAddedOrAlteredEventIfFinished();
    }

    private Set<Long> getUserIds(Collection<Username> usernameList) {
        Set<Long> userIds = new HashSet<>();
        for(Username username : usernameList) {
            userIds.add(username.getId());
        }
        return userIds;
    }

    private void saveGroupMembershipChangesIfRequired() {
        if(newGroupMembers == null) {
            //nothing to do.
            return;
        }

        Set<Long> allWantedMembers = getUserIds(newGroupMembers);
        Set<Long> oldGroupMembersSet = getUserIds(currentGroupMembers);
        Set<Long> newGroupMembersSet = new HashSet<>(allWantedMembers);

        newGroupMembersSet.removeAll(oldGroupMembersSet);
        boolean hasAddedNewPermissions = newGroupMembersSet.size() > 0;
        oldGroupMembersSet.removeAll(allWantedMembers);
        boolean hasRemovedPermissions = oldGroupMembersSet.size() > 0;

        if (hasRemovedPermissions) {
            long saveActionId = PiwigoAccessService.startActionRemoveUsersFromGroup(currentGroup.getId(), new ArrayList<>(oldGroupMembersSet), getContext());
            memberSaveActionIds.add(saveActionId);
            addActiveServiceCall(R.string.progress_saving_changes, saveActionId);
        }
        if (hasAddedNewPermissions) {
            long saveActionId = PiwigoAccessService.startActionAddUsersToGroup(currentGroup.getId(), new ArrayList<>(newGroupMembersSet), getContext());
            memberSaveActionIds.add(saveActionId);
            addActiveServiceCall(R.string.progress_saving_changes, saveActionId);
        }
    }

    private void saveUserPermissionsChangesIfRequired() {
        if(newAccessibleAlbumIds == null) {
            //nothing to do.
            return;
        }

        Set<Long> oldPermissionsSet = new HashSet<>(currentAccessibleAlbumIds);
        Set<Long> newPermissionsSet = new HashSet<>(newAccessibleAlbumIds);

        newPermissionsSet.removeAll(oldPermissionsSet);
        boolean hasAddedNewPermissions = newPermissionsSet.size() > 0;
        oldPermissionsSet.removeAll(newAccessibleAlbumIds);
        boolean hasRemovedPermissions = oldPermissionsSet.size() > 0;

        if (hasRemovedPermissions) {
            long saveActionId = PiwigoAccessService.startActionGroupRemovePermissions(currentGroup.getId(), new ArrayList<>(oldPermissionsSet), getContext());
            permissionsSaveActionIds.add(saveActionId);
            addActiveServiceCall(R.string.progress_saving_changes, saveActionId);
        }
        if (hasAddedNewPermissions) {
            long saveActionId = PiwigoAccessService.startActionGroupAddPermissions(currentGroup.getId(), new ArrayList<>(newPermissionsSet), getContext());
            permissionsSaveActionIds.add(saveActionId);
            addActiveServiceCall(R.string.progress_saving_changes, saveActionId);
        }
    }

    private void onGroupInfoUpdated(PiwigoResponseBufferingHandler.PiwigoGroupUpdateInfoResponse response) {
        newGroup = null;
        currentGroup = response.getGroup();
        saveGroupMembershipChangesIfRequired();
        saveUserPermissionsChangesIfRequired();
        publishGroupAddedOrAlteredEventIfFinished();
    }

    private void onGroupAdded(PiwigoResponseBufferingHandler.PiwigoAddGroupResponse response) {
        newGroup = null;
        currentGroup = response.getGroup();
        saveGroupMembershipChangesIfRequired();
        saveUserPermissionsChangesIfRequired();
        publishGroupAddedOrAlteredEventIfFinished();
    }

    private void onGroupDeleted(PiwigoResponseBufferingHandler.PiwigoDeleteGroupResponse response) {
        EventBus.getDefault().post(new GroupDeletedEvent(currentGroup));
        // return to previous screen
        if(isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }

    public void onUsernamesLoaded(PiwigoResponseBufferingHandler.PiwigoGetUsernamesListResponse response) {
        currentGroupMembers = response.getUsernames();
        populateGroupMembersField();
    }

    public void onGroupPermissionsRetrieved(PiwigoResponseBufferingHandler.PiwigoGroupPermissionsRetrievedResponse response) {

        this.currentAccessibleAlbumIds = response.getAllowedAlbums();
        if (availableGalleries != null) {
            populateAlbumPermissionsList();
        }

    }

    public void onGetSubGalleries(PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse response) {
        this.availableGalleries = response.getAlbumNames();
        if (currentAccessibleAlbumIds != null) {
            populateAlbumPermissionsList();
        }
    }

    private ArrayList<Username> getLatestGroupMembers() {
        ArrayList<Username> latestGroupMembers;
        if(currentGroup.getId() < 0) {
            currentGroupMembers = new ArrayList<>(0);
        }
        latestGroupMembers = currentGroupMembers;
        if(newGroupMembers != null) {
            latestGroupMembers = newGroupMembers;
        }
        return latestGroupMembers;
    }

    private void populateGroupMembersField() {

        ArrayList<Username> groupMembers = getLatestGroupMembers();
        if (groupMembers == null) {
            membersField.setText(R.string.loading_please_wait);
        } else {
            StringBuilder sb = new StringBuilder();
            if (groupMembers.size() == 0) {
                sb.append(getString(R.string.none_value));
            } else {
                Collections.sort(groupMembers, new Comparator<Username>() {
                    @Override
                    public int compare(Username o1, Username o2) {
                        return o1.getUsername().compareTo(o2.getUsername());
                    }
                });
                Iterator<Username> iter = groupMembers.iterator();
                while (iter.hasNext()) {
                    sb.append(iter.next().getUsername());
                    if (iter.hasNext()) {
                        sb.append(", ");
                    }
                }
            }
            membersField.setText(sb.toString());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAlbumPermissionsSelectedEvent(AlbumPermissionsSelectionCompleteEvent event) {
        if(getUiHelper().isTrackingRequest(event.getActionId())) {
            newAccessibleAlbumIds = event.getSelectedAlbums();
        }
    }

    private void onExpandPermissions() {
        HashSet<Long> latestSelectedAlbumIds = getLatestAlbumPermissions();
        latestSelectedAlbumIds = new HashSet<>(latestSelectedAlbumIds);
        AlbumPermissionsSelectionNeededEvent evt = new AlbumPermissionsSelectionNeededEvent(availableGalleries, latestSelectedAlbumIds, fieldsEditable);
        getUiHelper().setTrackingRequest(evt.getActionId());
        EventBus.getDefault().post(evt);
    }

    private void populateAlbumPermissionsList() {
        AlbumSelectionListAdapter adapter = (AlbumSelectionListAdapter)albumAccessRightsField.getAdapter();
        if(adapter == null) {
            adapter = new AlbumSelectionListAdapter(this.getContext(), availableGalleries, null, false);
            albumAccessRightsField.setAdapter(adapter);

        }
        albumAccessRightsField.clearChoices();
        for (Long selectedAlbum : getLatestAlbumPermissions()) {
            int itemPos = adapter.getPosition(selectedAlbum);
            if (itemPos >= 0) {
                albumAccessRightsField.setItemChecked(itemPos, true);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAppLockedEvent(AppLockedEvent event) {
        if(isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }
}
