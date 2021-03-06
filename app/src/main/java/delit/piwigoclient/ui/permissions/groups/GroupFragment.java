package delit.piwigoclient.ui.permissions.groups;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;

import com.google.android.gms.ads.AdView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.view.CustomClickTouchListener;
import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.StaticCategoryItem;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetChildAlbumNamesResponseHandler;
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
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.GroupDeletedEvent;
import delit.piwigoclient.ui.events.GroupUpdatedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionNeededEvent;
import delit.piwigoclient.ui.permissions.AlbumSelectionListAdapter;
import delit.piwigoclient.ui.permissions.AlbumSelectionListAdapterPreferences;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Created by gareth on 21/06/17.
 */

public class GroupFragment<F extends GroupFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends MyFragment<F,FUIH> {

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
    private static final String TAG = "GrpFrag";
    private final HashSet<Long> memberSaveActionIds = new HashSet<>(2);
    private final HashSet<Long> permissionsSaveActionIds = new HashSet<>(2);
    // Fields
    private TextView groupNameField;
    private TextView membersField;
    private ListView albumAccessRightsField;
    private MaterialButton editButton;
    private MaterialButton discardButton;
    private MaterialButton saveButton;
    private MaterialButton deleteButton;
    // stuff stored in state.
    private Group currentGroup;
    private ArrayList<Username> currentGroupMembers;
    private HashSet<Long> currentAccessibleAlbumIds;
    private Group newGroup;
    private ArrayList<Username> newGroupMembers;
    private HashSet<Long> newAccessibleAlbumIds;
    private ArrayList<CategoryItemStub> availableGalleries;
    private boolean fieldsEditable;
    private SwitchMaterial isDefaultField;
    private int selectUsersActionId;

    public static GroupFragment<?,?> newInstance(Group group) {
        GroupFragment<?,?> fragment = new GroupFragment<>();
        fragment.setTheme(R.style.Theme_App_EditPages);
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
        if (AdsManager.getInstance(getContext()).shouldShowAdverts()) {
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
            CollectionUtils.addToCollectionNullSafe(memberSaveActionIds, BundleUtils.getLongHashSet(savedInstanceState, IN_FLIGHT_MEMBER_SAVE_ACTION_IDS));

            permissionsSaveActionIds.clear();
            CollectionUtils.addToCollectionNullSafe(permissionsSaveActionIds, BundleUtils.getLongHashSet(savedInstanceState, IN_FLIGHT_PERMISSIONS_SAVE_ACTION_IDS));
            selectUsersActionId = savedInstanceState.getInt(STATE_SELECT_USERS_ACTION_ID);
        }

        groupNameField = v.findViewById(R.id.group_name);
        membersField = v.findViewById(R.id.group_members);
        // can't just use a std click listener as it first focuses the field :-(
        CustomClickTouchListener.callClickOnTouch(membersField, (mf)->selectGroupMembers());

        isDefaultField = v.findViewById(R.id.group_is_default);

        albumAccessRightsField = v.findViewById(R.id.group_access_rights);
        CustomClickTouchListener.callClickOnTouch(albumAccessRightsField, (aarf)->onExpandPermissions());

        editButton = v.findViewById(R.id.group_action_edit_button);
        editButton.setOnClickListener(v15 -> setFieldsEditable(true));
        discardButton = v.findViewById(R.id.group_action_discard_button);
        discardButton.setOnClickListener(v14 -> {
            newGroupMembers = null;
            newGroup = null;
            newAccessibleAlbumIds = null;
            setFieldsFromModel(currentGroup);
            populateAlbumPermissionsList();
            setFieldsEditable(currentGroup.getId() < 0);
        });
        saveButton = v.findViewById(R.id.group_action_save_button);
        saveButton.setOnClickListener(v13 -> saveGroupChanges());
        deleteButton = v.findViewById(R.id.group_action_delete_button);
        deleteButton.setOnClickListener(v12 -> deleteGroup(currentGroup));

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


        albumAccessRightsField.addOnLayoutChangeListener(new LayoutChangeListener());

        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if(!PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile()) || (isSessionDetailsChanged() && !isServerConnectionChanged())){
            //trigger total screen refresh. Any errors will result in screen being closed.
            UIHelper.Action action = new GroupFragmentAction<>();
            getUiHelper().invokeActiveServiceCall(R.string.progress_loading_group_details, new GroupsGetListResponseHandler(currentGroup.getId()), action);
        } else if((!PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) || isAppInReadOnlyMode() || isServerConnectionChanged()) {
            // immediately leave this screen.
            Logging.log(Log.INFO, TAG, "removing from activity as not admin user");
            getParentFragmentManager().popBackStack();
        }
    }

    private static class GroupFragmentAction<F extends GroupFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends UIHelper.Action<FUIH, F, GroupsGetListResponseHandler.PiwigoGetGroupsListRetrievedResponse> implements Parcelable {

        protected GroupFragmentAction(){
            super();
        }

        protected GroupFragmentAction(Parcel in) {
            super(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<GroupFragmentAction<?,?>> CREATOR = new Creator<GroupFragmentAction<?,?>>() {
            @Override
            public GroupFragmentAction<?,?> createFromParcel(Parcel in) {
                return new GroupFragmentAction<>(in);
            }

            @Override
            public GroupFragmentAction<?,?>[] newArray(int size) {
                return new GroupFragmentAction[size];
            }
        };

        @Override
        public boolean onSuccess(FUIH uiHelper, GroupsGetListResponseHandler.PiwigoGetGroupsListRetrievedResponse response) {
            ArrayList<Group> groups = response.getGroups();
            F groupFragment = uiHelper.getParent();
            if(groups.isEmpty()) {
                Logging.log(Log.INFO, TAG, "removing from activity as group not available any more");
                groupFragment.getParentFragmentManager().popBackStack();
                return false;
            }
            groupFragment.setCurrentGroup(groups.iterator().next());
            if(groupFragment.getNewGroup() == null) {
                groupFragment.setFieldsFromModel(groupFragment.getCurrentGroup());
            }
            return false;
        }

        @Override
        public boolean onFailure(FUIH uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            Logging.log(Log.INFO, TAG, "removing from activity on piwigo error response rxd");
            uiHelper.getParent().getParentFragmentManager().popBackStack();
            return false;
        }
    }

    public Group getNewGroup() {
        return newGroup;
    }

    public void setCurrentGroup(Group currentGroup) {
        this.currentGroup = currentGroup;
    }

    public Group getCurrentGroup() {
        return currentGroup;
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
        UsernameSelectionNeededEvent usernameSelectionNeededEvent = new UsernameSelectionNeededEvent(true, fieldsEditable, new HashSet<>(0), preselectedUsernames);
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
            addActiveServiceCall(R.string.progress_adding_group, new GroupAddResponseHandler(newGroup));

        } else {
            addActiveServiceCall(R.string.progress_saving_changes, new GroupUpdateInfoResponseHandler(currentGroup, newGroup));
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

    protected void setFieldsFromModel(Group group) {
        groupNameField.setText(group.getName());
        isDefaultField.setChecked(group.isDefault());

        populateGroupMembersField();

        if (currentGroupMembers == null) {
            ArrayList<Long> groups = new ArrayList<>(1);
            groups.add(group.getId());
            addActiveServiceCall(R.string.progress_loading_group_details, new UsernamesGetListResponseHandler(groups, 0, 100));
        }

        if (availableGalleries == null) {
            addActiveServiceCall(R.string.progress_loading_group_details, new AlbumGetChildAlbumNamesResponseHandler(StaticCategoryItem.ROOT_ALBUM.getId(), true));
        }

        if (currentAccessibleAlbumIds == null) {
            addActiveServiceCall(R.string.progress_loading_group_details, new GroupGetPermissionsResponseHandler(group.getId()));
        }

        if (availableGalleries != null) {
            populateAlbumPermissionsList();
        }

        deleteButton.setEnabled(group.getId() >= 0);
    }

    private void deleteGroup(final Group group) {
        String message = getString(R.string.alert_confirm_really_delete_group);
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_cancel, R.string.button_ok, new OnDeleteGroupAction<>(getUiHelper(), group));
    }

    private static class OnDeleteGroupAction<F extends GroupFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends QuestionResultAdapter<FUIH,F> implements Parcelable {

        private final Group group;

        OnDeleteGroupAction(FUIH uiHelper, Group group) {
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

        public static final Creator<OnDeleteGroupAction<?,?>> CREATOR = new Creator<OnDeleteGroupAction<?,?>>() {
            @Override
            public OnDeleteGroupAction<?,?> createFromParcel(Parcel in) {
                return new OnDeleteGroupAction<>(in);
            }

            @Override
            public OnDeleteGroupAction<?,?>[] newArray(int size) {
                return new OnDeleteGroupAction[size];
            }
        };

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                GroupFragment<?,?> fragment = getUiHelper().getParent();
                fragment.deleteGroupNow(group);
            }
        }
    }

    private void deleteGroupNow(Group group) {
        addActiveServiceCall(R.string.progress_delete_group, new GroupDeleteResponseHandler(group.getId()));
    }

    private void setFieldsEditable(boolean editable) {

        fieldsEditable = editable;

        groupNameField.setEnabled(editable);
        isDefaultField.setEnabled(editable);

        //        editButton.setEnabled(!editable);
        editButton.setVisibility(!editable ? VISIBLE : GONE);
//        discardButton.setEnabled(editable);
        discardButton.setVisibility(editable ? VISIBLE : GONE);
//        saveButton.setEnabled(editable);
        saveButton.setVisibility(editable ? VISIBLE : GONE);


    }

    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener<>();
    }

    private void publishGroupAddedOrAlteredEventIfFinished() {
        if (newGroup == null && newAccessibleAlbumIds == null && newGroupMembers == null) {
            EventBus.getDefault().post(new GroupUpdatedEvent(currentGroup));
        }
        setFieldsFromModel(currentGroup);
    }

    protected void onGroupPermissionsRemoved(GroupPermissionsRemovedResponseHandler.PiwigoGroupPermissionsRemovedResponse response) {
        synchronized (permissionsSaveActionIds) {
            permissionsSaveActionIds.remove(response.getMessageId());
            if (permissionsSaveActionIds.size() == 0) {
                currentAccessibleAlbumIds = newAccessibleAlbumIds;
                newAccessibleAlbumIds = null;
            }
        }
        publishGroupAddedOrAlteredEventIfFinished();
    }

    protected void onGroupPermissionsAdded(GroupPermissionsAddResponseHandler.PiwigoGroupPermissionsAddedResponse response) {
        synchronized (permissionsSaveActionIds) {
            permissionsSaveActionIds.remove(response.getMessageId());
            if (permissionsSaveActionIds.size() == 0) {
                currentAccessibleAlbumIds = newAccessibleAlbumIds;
                newAccessibleAlbumIds = null;
            }
        }
        publishGroupAddedOrAlteredEventIfFinished();
    }

    protected void onGroupMembersAdded(GroupAddMembersResponseHandler.PiwigoGroupAddMembersResponse response) {
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

    protected void onGroupMembersRemoved(GroupRemoveMembersResponseHandler.PiwigoGroupRemoveMembersResponse response) {
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

    private void saveGroupMembershipChangesIfRequired() {
        if (newGroupMembers == null) {
            //nothing to do.
            return;
        }

        Set<Long> allWantedMembers = PiwigoUtils.toSetOfIds(newGroupMembers);
        Set<Long> oldGroupMembersSet = PiwigoUtils.toSetOfIds(currentGroupMembers);
        Set<Long> newGroupMembersSet = new HashSet<>(allWantedMembers);

        newGroupMembersSet.removeAll(oldGroupMembersSet);
        boolean hasAddedNewPermissions = newGroupMembersSet.size() > 0;
        oldGroupMembersSet.removeAll(allWantedMembers);
        boolean hasRemovedPermissions = oldGroupMembersSet.size() > 0;

        if (hasRemovedPermissions) {
            memberSaveActionIds.add(addActiveServiceCall(R.string.progress_saving_changes, new GroupRemoveMembersResponseHandler<>(currentGroup.getId(), new ArrayList<>(oldGroupMembersSet))));
        }
        if (hasAddedNewPermissions) {
            memberSaveActionIds.add(addActiveServiceCall(R.string.progress_saving_changes, new GroupAddMembersResponseHandler<>(currentGroup.getId(), new ArrayList<>(newGroupMembersSet))));
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
            permissionsSaveActionIds.add(addActiveServiceCall(R.string.progress_saving_changes, new GroupPermissionsRemovedResponseHandler(currentGroup.getId(), new ArrayList<>(oldPermissionsSet))));

        }
        if (hasAddedNewPermissions) {
            permissionsSaveActionIds.add(addActiveServiceCall(R.string.progress_saving_changes, new GroupPermissionsAddResponseHandler(currentGroup.getId(), new ArrayList<>(newPermissionsSet))));
        }
    }

    protected void onGroupInfoUpdated(GroupUpdateInfoResponseHandler.PiwigoGroupUpdateInfoResponse response) {
        newGroup = null;
        currentGroup = response.getGroup();
        saveGroupMembershipChangesIfRequired();
        saveUserPermissionsChangesIfRequired();
        publishGroupAddedOrAlteredEventIfFinished();
    }

    protected void onGroupAdded(GroupAddResponseHandler.PiwigoAddGroupResponse response) {
        newGroup = null;
        currentGroup = response.getGroup();
        saveGroupMembershipChangesIfRequired();
        saveUserPermissionsChangesIfRequired();
        publishGroupAddedOrAlteredEventIfFinished();
    }

    protected void onGroupDeleted(GroupDeleteResponseHandler.PiwigoDeleteGroupResponse response) {
        EventBus.getDefault().post(new GroupDeletedEvent(currentGroup));
        // return to previous screen
        if (isVisible()) {
            Logging.log(Log.INFO, TAG, "removing from activity immediately on this group deleted");
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    protected void onUsernamesLoaded(UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse response) {
        currentGroupMembers = response.getUsernames();
        populateGroupMembersField();
    }

    protected void onGroupPermissionsRetrieved(GroupGetPermissionsResponseHandler.PiwigoGroupPermissionsRetrievedResponse response) {

        this.currentAccessibleAlbumIds = response.getAllowedAlbums();
        if (availableGalleries != null) {
            populateAlbumPermissionsList();
        }

    }

    protected void onGetSubGalleries(AlbumGetChildAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse response) {
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
                Collections.sort(groupMembers);
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
    private void onAlbumPermissionsSelectedEvent(AlbumPermissionsSelectionCompleteEvent event) {
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
            AlbumSelectionListAdapterPreferences adapterPreferences = new AlbumSelectionListAdapterPreferences(true, false, false, true, false);
            AlbumSelectionListAdapter availableItemsAdapter = new AlbumSelectionListAdapter(availableGalleries, adapterPreferences);
            availableItemsAdapter.linkToListView(albumAccessRightsField, getLatestAlbumPermissions(), getLatestAlbumPermissions());
        } else {
            adapter.setSelectedItems(getLatestAlbumPermissions());
            adapter.notifyDataSetChanged();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onAppLockedEvent(AppLockedEvent event) {
        if (isVisible()) {
            Logging.log(Log.INFO, TAG, "removing from activity immediately as app locked event rxd");
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    private static class CustomPiwigoResponseListener<F extends GroupFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends BasicPiwigoResponseListener<FUIH,F> {
        private static final String TAG = "GroupFrag";

        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (getParent().isVisible()) {
                if (!PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
                    Logging.log(Log.INFO, TAG, "removing from activity as not admin user");
                    getParent().getParentFragmentManager().popBackStack();
                    return;
                }
            }
            if (response instanceof UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse) {
                getParent().onUsernamesLoaded((UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse) response);
            } else if (response instanceof GroupDeleteResponseHandler.PiwigoDeleteGroupResponse) {
                getParent().onGroupDeleted((GroupDeleteResponseHandler.PiwigoDeleteGroupResponse) response);
            } else if (response instanceof GroupGetPermissionsResponseHandler.PiwigoGroupPermissionsRetrievedResponse) {
                getParent().onGroupPermissionsRetrieved((GroupGetPermissionsResponseHandler.PiwigoGroupPermissionsRetrievedResponse) response);
            } else if (response instanceof AlbumGetChildAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) {
                getParent().onGetSubGalleries((AlbumGetChildAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) response);
            } else if (response instanceof GroupUpdateInfoResponseHandler.PiwigoGroupUpdateInfoResponse) {
                getParent().onGroupInfoUpdated((GroupUpdateInfoResponseHandler.PiwigoGroupUpdateInfoResponse) response);
            } else if (response instanceof GroupAddResponseHandler.PiwigoAddGroupResponse) {
                getParent().onGroupAdded((GroupAddResponseHandler.PiwigoAddGroupResponse) response);
            } else if (response instanceof GroupAddMembersResponseHandler.PiwigoGroupAddMembersResponse) {
                getParent().onGroupMembersAdded((GroupAddMembersResponseHandler.PiwigoGroupAddMembersResponse) response);
            } else if (response instanceof GroupRemoveMembersResponseHandler.PiwigoGroupRemoveMembersResponse) {
                getParent().onGroupMembersRemoved((GroupRemoveMembersResponseHandler.PiwigoGroupRemoveMembersResponse) response);
            } else if (response instanceof GroupPermissionsAddResponseHandler.PiwigoGroupPermissionsAddedResponse) {
                getParent().onGroupPermissionsAdded((GroupPermissionsAddResponseHandler.PiwigoGroupPermissionsAddedResponse) response);
            } else if (response instanceof GroupPermissionsRemovedResponseHandler.PiwigoGroupPermissionsRemovedResponse) {
                getParent().onGroupPermissionsRemoved((GroupPermissionsRemovedResponseHandler.PiwigoGroupPermissionsRemovedResponse) response);
            }
            //TODO check I'm handling all data flow streams for adding and updating an existing group... including sending that refreshed gorup back to previous groups list screen.
        }
    }

    private static class LayoutChangeListener implements View.OnLayoutChangeListener {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if (top != oldTop) {
                NestedScrollView scrollview = v.getRootView().findViewById(R.id.group_edit_scrollview);
                scrollview.fullScroll(View.FOCUS_UP);
                v.removeOnLayoutChangeListener(this);
            }
        }
    }
}
