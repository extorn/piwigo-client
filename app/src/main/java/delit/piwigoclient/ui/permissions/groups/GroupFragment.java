package delit.piwigoclient.ui.permissions.groups;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

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
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupAddMembersResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupAddResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupGetPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupPermissionsAddResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupPermissionsRemovedResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupRemoveMembersResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupsGetListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UsernamesGetListResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.CustomClickTouchListener;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.button.CustomImageButton;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.common.util.BundleUtils;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.GroupDeletedEvent;
import delit.piwigoclient.ui.events.GroupUpdatedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionNeededEvent;
import delit.piwigoclient.ui.permissions.AlbumSelectionListAdapter;
import delit.piwigoclient.util.SetUtils;

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
    private static final String STATE_SELECT_USERS_ACTION_ID = "selectUsersActionId";
    private final HashSet<Long> memberSaveActionIds = new HashSet<>(2);
    private final HashSet<Long> permissionsSaveActionIds = new HashSet<>(2);
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
    private int selectUsersActionId;

    public static GroupFragment newInstance(Group group) {
        GroupFragment fragment = new GroupFragment();
        Bundle args = new Bundle();
        args.putParcelable(CURRENT_GROUP, group);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentGroup = getArguments().getParcelable(CURRENT_GROUP);
        }
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.group_heading);
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
        outState.putParcelableArrayList(CURRENT_GROUP_MEMBERS, currentGroupMembers);
        BundleUtils.putLongHashSet(outState, CURRENT_ACCESSIBLE_ALBUM_IDS, currentAccessibleAlbumIds);
        outState.putParcelable(CURRENT_GROUP, currentGroup);
        outState.putParcelableArrayList(NEW_GROUP_MEMBERS, newGroupMembers);
        BundleUtils.putLongHashSet(outState, NEW_ACCESSIBLE_ALBUM_IDS, newAccessibleAlbumIds);
        outState.putParcelable(NEW_GROUP, newGroup);
        outState.putParcelableArrayList(AVAILABLE_ALBUMS, availableGalleries);
        outState.putBoolean(STATE_FIELDS_EDITABLE, fieldsEditable);
        BundleUtils.putLongHashSet(outState, IN_FLIGHT_MEMBER_SAVE_ACTION_IDS, memberSaveActionIds);
        BundleUtils.putLongHashSet(outState, IN_FLIGHT_PERMISSIONS_SAVE_ACTION_IDS, permissionsSaveActionIds);
        outState.putInt(STATE_SELECT_USERS_ACTION_ID, selectUsersActionId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        View v = inflater.inflate(R.layout.fragment_group, container, false);

        AdView adView = v.findViewById(R.id.group_adView);
        if (AdsManager.getInstance().shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        if (savedInstanceState != null) {
            currentGroupMembers =savedInstanceState.getParcelableArrayList(CURRENT_GROUP_MEMBERS);
            currentGroup = savedInstanceState.getParcelable(CURRENT_GROUP);
            currentAccessibleAlbumIds = BundleUtils.getLongHashSet(savedInstanceState, CURRENT_ACCESSIBLE_ALBUM_IDS);
            newGroupMembers = savedInstanceState.getParcelableArrayList(NEW_GROUP_MEMBERS);
            newGroup = savedInstanceState.getParcelable(NEW_GROUP);
            newAccessibleAlbumIds = BundleUtils.getLongHashSet(savedInstanceState, NEW_ACCESSIBLE_ALBUM_IDS);
            availableGalleries = savedInstanceState.getParcelableArrayList(AVAILABLE_ALBUMS);
            fieldsEditable = savedInstanceState.getBoolean(STATE_FIELDS_EDITABLE);
            SetUtils.setNotNull(memberSaveActionIds, BundleUtils.getLongHashSet(savedInstanceState, IN_FLIGHT_MEMBER_SAVE_ACTION_IDS));

            permissionsSaveActionIds.clear();
            permissionsSaveActionIds.addAll(BundleUtils.getLongHashSet(savedInstanceState, IN_FLIGHT_PERMISSIONS_SAVE_ACTION_IDS));
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
        albumAccessRightsField.setOnTouchListener(new CustomClickTouchListener(albumAccessRightsField) {
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

        if (currentGroup.getId() < 0) {
            currentAccessibleAlbumIds = new HashSet<>();
            currentGroupMembers = new ArrayList<>();
            fieldsEditable = true;
        }

        setFieldsEditable(fieldsEditable);

        if (newGroup != null) {
            setFieldsFromModel(newGroup);
        } else {
            setFieldsFromModel(currentGroup);
        }


        if(isOnInitialCreate()) {
            albumAccessRightsField.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (top != oldTop) {
                        NestedScrollView scrollview = v.getRootView().findViewById(R.id.group_edit_scrollview);
                        scrollview.fullScroll(View.FOCUS_UP);
                        v.removeOnLayoutChangeListener(this);
                    }
                }
            });
        }

        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if(!PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile()) || (isSessionDetailsChanged() && !isServerConnectionChanged())){
            //trigger total screen refresh. Any errors will result in screen being closed.
            UIHelper.Action action = new UIHelper.Action<GroupFragment, GroupsGetListResponseHandler.PiwigoGetGroupsListRetrievedResponse>() {

                @Override
                public boolean onSuccess(UIHelper<GroupFragment> uiHelper, GroupsGetListResponseHandler.PiwigoGetGroupsListRetrievedResponse response) {
                    HashSet<Group> groups = response.getGroups();
                    if(groups.isEmpty()) {
                        getFragmentManager().popBackStack();
                        return false;
                    }
                    currentGroup = groups.iterator().next();
                    if(newGroup == null) {
                        setFieldsFromModel(currentGroup);
                    }
                    return false;
                }

                @Override
                public boolean onFailure(UIHelper<GroupFragment> uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
                    getFragmentManager().popBackStack();
                    return false;
                }
            };
            getUiHelper().invokeActiveServiceCall(R.string.progress_loading_group_details, new GroupsGetListResponseHandler(currentGroup.getId()), action);
        } else if((!PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) || isAppInReadOnlyMode() || isServerConnectionChanged()) {
            // immediately leave this screen.
            getFragmentManager().popBackStack();
        }
    }

    private HashSet<Long> buildPreselectedUserIds(List<Username> selectedUsernames) {
        HashSet<Long> preselectedUsernames = PiwigoUtils.toSetOfIds(selectedUsernames);
        if (preselectedUsernames == null) {
            preselectedUsernames = new HashSet<>(0);
        }
        return preselectedUsernames;
    }

    private void selectGroupMembers() {
        ArrayList<Username> currentSelection = getLatestGroupMembers();

        HashSet<Long> preselectedUsernames = buildPreselectedUserIds(currentSelection);
        UsernameSelectionNeededEvent usernameSelectionNeededEvent = new UsernameSelectionNeededEvent(true, fieldsEditable, new HashSet<Long>(0), preselectedUsernames);
        selectUsersActionId = usernameSelectionNeededEvent.getActionId();
        EventBus.getDefault().post(usernameSelectionNeededEvent);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(UsernameSelectionCompleteEvent usernameSelectionCompleteEvent) {

        if (selectUsersActionId == usernameSelectionCompleteEvent.getActionId()) {
            selectUsersActionId = -1;
            newGroupMembers = new ArrayList<>(usernameSelectionCompleteEvent.getSelectedItems());
            populateGroupMembersField();
        }
    }


    private void saveGroupChanges() {
        setFieldsEditable(false);
        String name = groupNameField.getText().toString();
        boolean isDefault = isDefaultField.isChecked();
        newGroup = new Group(currentGroup.getId(), name, isDefault);
        if (newGroup.getId() < 0) {
            long saveActionId = new GroupAddResponseHandler(newGroup).invokeAsync(getContext());
            addActiveServiceCall(R.string.progress_adding_group, saveActionId);

        } else {
            long saveActionId = new GroupUpdateInfoResponseHandler(currentGroup, newGroup).invokeAsync(getContext());
            addActiveServiceCall(R.string.progress_saving_changes, saveActionId);
        }
    }

    private HashSet<Long> getLatestAlbumPermissions() {
        HashSet<Long> albumPermissions;
        if (newAccessibleAlbumIds != null) {
            albumPermissions = newAccessibleAlbumIds;
        } else if (currentAccessibleAlbumIds != null) {
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
            addActiveServiceCall(R.string.progress_loading_group_details, new UsernamesGetListResponseHandler(groups, 0, 100).invokeAsync(getContext()));
        }

        if (availableGalleries == null) {
            addActiveServiceCall(R.string.progress_loading_group_details, new AlbumGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId(), true).invokeAsync(getContext()));
        }

        if (currentAccessibleAlbumIds == null) {
            addActiveServiceCall(R.string.progress_loading_group_details, new GroupGetPermissionsResponseHandler(group.getId()).invokeAsync(getContext()));
        }

        if (availableGalleries != null) {
            populateAlbumPermissionsList();
        }

        deleteButton.setEnabled(group.getId() >= 0);
    }

    private void deleteGroup(final Group group) {
        String message = getString(R.string.alert_confirm_really_delete_group);
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_cancel, R.string.button_ok, new OnDeleteGroupAction(getUiHelper(), group));
    }

    private static class OnDeleteGroupAction extends UIHelper.QuestionResultAdapter {
        private final Group group;

        public OnDeleteGroupAction(UIHelper uiHelper, Group group) {
            super(uiHelper);
            this.group = group;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                GroupFragment fragment = (GroupFragment) getUiHelper().getParent();
                fragment.deleteGroupNow(group);
            }
        }
    }

    private void deleteGroupNow(Group group) {
        addActiveServiceCall(R.string.progress_delete_group, new GroupDeleteResponseHandler(group.getId()).invokeAsync(getContext()));
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

    private void publishGroupAddedOrAlteredEventIfFinished() {
        if (newGroup == null && newAccessibleAlbumIds == null && newGroupMembers == null) {
            EventBus.getDefault().post(new GroupUpdatedEvent(currentGroup));
        }
        setFieldsFromModel(currentGroup);
    }

    private void onGroupPermissionsRemoved(GroupPermissionsRemovedResponseHandler.PiwigoGroupPermissionsRemovedResponse response) {
        synchronized (permissionsSaveActionIds) {
            permissionsSaveActionIds.remove(response.getMessageId());
            if (permissionsSaveActionIds.size() == 0) {
                currentAccessibleAlbumIds = newAccessibleAlbumIds;
                newAccessibleAlbumIds = null;
            }
        }
        publishGroupAddedOrAlteredEventIfFinished();
    }

    private void onGroupPermissionsAdded(GroupPermissionsAddResponseHandler.PiwigoGroupPermissionsAddedResponse response) {
        synchronized (permissionsSaveActionIds) {
            permissionsSaveActionIds.remove(response.getMessageId());
            if (permissionsSaveActionIds.size() == 0) {
                currentAccessibleAlbumIds = newAccessibleAlbumIds;
                newAccessibleAlbumIds = null;
            }
        }
        publishGroupAddedOrAlteredEventIfFinished();
    }

    private void onGroupMembersAdded(GroupAddMembersResponseHandler.PiwigoGroupAddMembersResponse response) {
        synchronized (memberSaveActionIds) {
            memberSaveActionIds.remove(response.getMessageId());
            currentGroup = response.getGroup();
            if (memberSaveActionIds.size() == 0) {
                currentGroupMembers = newGroupMembers;
                newGroupMembers = null;
            }
        }
        publishGroupAddedOrAlteredEventIfFinished();
    }

    private void onGroupMembersRemoved(GroupRemoveMembersResponseHandler.PiwigoGroupRemoveMembersResponse response) {
        synchronized (memberSaveActionIds) {
            memberSaveActionIds.remove(response.getMessageId());
            currentGroup = response.getGroup();
            if (memberSaveActionIds.size() == 0) {
                currentGroupMembers = newGroupMembers;
                newGroupMembers = null;
            }
        }
        publishGroupAddedOrAlteredEventIfFinished();
    }

    private Set<Long> getUserIds(Collection<Username> usernameList) {
        Set<Long> userIds = new HashSet<>();
        for (Username username : usernameList) {
            userIds.add(username.getId());
        }
        return userIds;
    }

    private void saveGroupMembershipChangesIfRequired() {
        if (newGroupMembers == null) {
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
            long saveActionId = new GroupRemoveMembersResponseHandler(currentGroup.getId(), new ArrayList<>(oldGroupMembersSet)).invokeAsync(getContext());
            memberSaveActionIds.add(saveActionId);
            addActiveServiceCall(R.string.progress_saving_changes, saveActionId);
        }
        if (hasAddedNewPermissions) {
            long saveActionId = new GroupAddMembersResponseHandler(currentGroup.getId(), new ArrayList<>(newGroupMembersSet)).invokeAsync(getContext());
            memberSaveActionIds.add(saveActionId);
            addActiveServiceCall(R.string.progress_saving_changes, saveActionId);
        }
    }

    private void saveUserPermissionsChangesIfRequired() {
        if (newAccessibleAlbumIds == null) {
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
            long saveActionId = new GroupPermissionsRemovedResponseHandler(currentGroup.getId(), new ArrayList<>(oldPermissionsSet)).invokeAsync(getContext());
            permissionsSaveActionIds.add(saveActionId);
            addActiveServiceCall(R.string.progress_saving_changes, saveActionId);
        }
        if (hasAddedNewPermissions) {
            long saveActionId = new GroupPermissionsAddResponseHandler(currentGroup.getId(), new ArrayList<>(newPermissionsSet)).invokeAsync(getContext());
            permissionsSaveActionIds.add(saveActionId);
            addActiveServiceCall(R.string.progress_saving_changes, saveActionId);
        }
    }

    private void onGroupInfoUpdated(GroupUpdateInfoResponseHandler.PiwigoGroupUpdateInfoResponse response) {
        newGroup = null;
        currentGroup = response.getGroup();
        saveGroupMembershipChangesIfRequired();
        saveUserPermissionsChangesIfRequired();
        publishGroupAddedOrAlteredEventIfFinished();
    }

    private void onGroupAdded(GroupAddResponseHandler.PiwigoAddGroupResponse response) {
        newGroup = null;
        currentGroup = response.getGroup();
        saveGroupMembershipChangesIfRequired();
        saveUserPermissionsChangesIfRequired();
        publishGroupAddedOrAlteredEventIfFinished();
    }

    private void onGroupDeleted(GroupDeleteResponseHandler.PiwigoDeleteGroupResponse response) {
        EventBus.getDefault().post(new GroupDeletedEvent(currentGroup));
        // return to previous screen
        if (isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }

    private void onUsernamesLoaded(UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse response) {
        currentGroupMembers = response.getUsernames();
        populateGroupMembersField();
    }

    private void onGroupPermissionsRetrieved(GroupGetPermissionsResponseHandler.PiwigoGroupPermissionsRetrievedResponse response) {

        this.currentAccessibleAlbumIds = response.getAllowedAlbums();
        if (availableGalleries != null) {
            populateAlbumPermissionsList();
        }

    }

    private void onGetSubGalleries(AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse response) {
        this.availableGalleries = response.getAlbumNames();
        if (currentAccessibleAlbumIds != null) {
            populateAlbumPermissionsList();
        }
    }

    private ArrayList<Username> getLatestGroupMembers() {
        ArrayList<Username> latestGroupMembers;
        if (currentGroup.getId() < 0) {
            currentGroupMembers = new ArrayList<>(0);
        }
        latestGroupMembers = currentGroupMembers;
        if (newGroupMembers != null) {
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

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onAlbumPermissionsSelectedEvent(AlbumPermissionsSelectionCompleteEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            newAccessibleAlbumIds = event.getSelectedAlbumIds();
            populateAlbumPermissionsList();
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
        AlbumSelectionListAdapter adapter = (AlbumSelectionListAdapter) albumAccessRightsField.getAdapter();
        if (adapter == null) {
            BaseRecyclerViewAdapterPreferences adapterPreferences = new BaseRecyclerViewAdapterPreferences();
            adapterPreferences.selectable(true, false);
            adapterPreferences.readonly();
            AlbumSelectionListAdapter availableItemsAdapter = new AlbumSelectionListAdapter(getContext(), availableGalleries, adapterPreferences);
            availableItemsAdapter.linkToListView(albumAccessRightsField, getLatestAlbumPermissions(), getLatestAlbumPermissions());
        } else {
            adapter.setSelectedItems(getLatestAlbumPermissions());
            adapter.notifyDataSetChanged();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onAppLockedEvent(AppLockedEvent event) {
        if (isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (isVisible()) {
                if (!PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
                    getFragmentManager().popBackStack();
                    return;
                }
            }
            if (response instanceof UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse) {
                onUsernamesLoaded((UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse) response);
            } else if (response instanceof GroupDeleteResponseHandler.PiwigoDeleteGroupResponse) {
                onGroupDeleted((GroupDeleteResponseHandler.PiwigoDeleteGroupResponse) response);
            } else if (response instanceof GroupGetPermissionsResponseHandler.PiwigoGroupPermissionsRetrievedResponse) {
                onGroupPermissionsRetrieved((GroupGetPermissionsResponseHandler.PiwigoGroupPermissionsRetrievedResponse) response);
            } else if (response instanceof AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) {
                onGetSubGalleries((AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) response);
            } else if (response instanceof GroupUpdateInfoResponseHandler.PiwigoGroupUpdateInfoResponse) {
                onGroupInfoUpdated((GroupUpdateInfoResponseHandler.PiwigoGroupUpdateInfoResponse) response);
            } else if (response instanceof GroupAddResponseHandler.PiwigoAddGroupResponse) {
                onGroupAdded((GroupAddResponseHandler.PiwigoAddGroupResponse) response);
            } else if (response instanceof GroupAddMembersResponseHandler.PiwigoGroupAddMembersResponse) {
                onGroupMembersAdded((GroupAddMembersResponseHandler.PiwigoGroupAddMembersResponse) response);
            } else if (response instanceof GroupRemoveMembersResponseHandler.PiwigoGroupRemoveMembersResponse) {
                onGroupMembersRemoved((GroupRemoveMembersResponseHandler.PiwigoGroupRemoveMembersResponse) response);
            } else if (response instanceof GroupPermissionsAddResponseHandler.PiwigoGroupPermissionsAddedResponse) {
                onGroupPermissionsAdded((GroupPermissionsAddResponseHandler.PiwigoGroupPermissionsAddedResponse) response);
            } else if (response instanceof GroupPermissionsRemovedResponseHandler.PiwigoGroupPermissionsRemovedResponse) {
                onGroupPermissionsRemoved((GroupPermissionsRemovedResponseHandler.PiwigoGroupPermissionsRemovedResponse) response);
            }
            //TODO check I'm handling all data flow streams for adding and updating an existing group... including sending that refreshed gorup back to previous groups list screen.
        }
    }
}
