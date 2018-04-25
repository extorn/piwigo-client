package delit.piwigoclient.ui.permissions.users;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupGetPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupsGetListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserAddResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserGetPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserPermissionsAddResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserPermissionsRemovedResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserUpdateInfoResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.CustomClickTouchListener;
import delit.piwigoclient.ui.common.CustomImageButton;
import delit.piwigoclient.ui.common.MyFragment;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.UserDeletedEvent;
import delit.piwigoclient.ui.events.UserUpdatedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionNeededEvent;
import delit.piwigoclient.ui.permissions.AlbumSelectionListAdapter;
import delit.piwigoclient.util.SetUtils;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Created by gareth on 21/06/17.
 */

public class UserFragment extends MyFragment {

    private static final String CURRENT_GROUP_MEMBERSHIPS = "groupMemberships";
    private static final String CURRENT_DIRECT_ALBUM_PERMISSIONS = "currentDirectAlbumPermissions";
    private static final String CURRENT_INDIRECT_ALBUM_PERMISSIONS = "currentIndirectAlbumPermissions";
    private static final String NEW_DIRECT_ALBUM_PERMISSIONS = "newDirectAlbumPermissions";
    private static final String NEW_INDIRECT_ALBUM_PERMISSIONS = "newIndirectAlbumPermissions";
    private static final String ARG_USER = "user";
    private static final String AVAILABLE_ALBUMS = "availableAlbums";
    private static final String NEW_USER = "newUser";
    private static final String STATE_FIELDS_EDITABLE = "fieldsAreEditable";
    private static final String STATE_NEW_GROUP_MEMBERSHIP = "newGroupMembership";
    private static final String IN_FLIGHT_SAVE_ACTION_IDS = "saveActionIds";
    private static final String STATE_SELECT_GROUPS_ACTION_ID = "selectGroupsActionId";

    // fields
    private EditText usernameField;
    private Spinner usertypeField;
    private EditText emailField;
    private CheckBox highDefinitionEnabled;
    private TextView lastVisitedField;
    private CustomImageButton editButton;
    private CustomImageButton discardButton;
    private CustomImageButton saveButton;
    private CustomImageButton deleteButton;
    private Spinner userPrivacyLevelField;
    private TextView userGroupsField;
    private EditText passwordField;
    private TextView lastVisitedFieldLabel;

    // other stuff
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss", Locale.UK);

    // field value options
    private int[] userPrivacyLevelValues;
    private List<String> userTypeValues;

    // following fields are stored in state
    private User newUser;
    private User user;
    private ArrayList<CategoryItemStub> availableGalleries;
    private boolean fieldsEditable = false;
    private ListView albumPermissionsField;
    private final HashSet<Long> saveActionIds = new HashSet<>(5);
    private HashSet<Long> currentDirectAlbumPermissions;
    private HashSet<Long> currentIndirectAlbumPermissions;
    private HashSet<Long> newDirectAlbumPermissions;
    private HashSet<Long> newIndirectAlbumPermissions;
    private HashSet<Group> currentGroupMembership;
    private HashSet<Group> newGroupMembership;
    private int selectGroupsActionId;

    public static UserFragment newInstance(User user) {
        UserFragment fragment = new UserFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_USER, user);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            user = (User) getArguments().getSerializable(ARG_USER);
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

        // This has to be done here because the albumPermissionsField maintains a checked state internally (ignorant of any other alterations to that field).
        if (availableGalleries != null && !getUiHelper().isServiceCallInProgress()) {
            HashSet<Long> directAlbumPermissions = getLatestDirectAlbumPermissions();
            HashSet<Long> indirectAlbumPermissions = getLatestIndirectAlbumPermissions();
            populateAlbumPermissionsList(directAlbumPermissions, indirectAlbumPermissions);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(CURRENT_GROUP_MEMBERSHIPS, currentGroupMembership);
        outState.putSerializable(CURRENT_DIRECT_ALBUM_PERMISSIONS, currentDirectAlbumPermissions);
        outState.putSerializable(CURRENT_INDIRECT_ALBUM_PERMISSIONS, currentIndirectAlbumPermissions);
        outState.putSerializable(NEW_DIRECT_ALBUM_PERMISSIONS, newDirectAlbumPermissions);
        outState.putSerializable(NEW_INDIRECT_ALBUM_PERMISSIONS, newIndirectAlbumPermissions);
        outState.putSerializable(AVAILABLE_ALBUMS, availableGalleries);
        outState.putSerializable(ARG_USER, user);
        outState.putSerializable(NEW_USER, newUser);
        outState.putBoolean(STATE_FIELDS_EDITABLE, fieldsEditable);
        outState.putSerializable(STATE_NEW_GROUP_MEMBERSHIP, newGroupMembership);
        outState.putSerializable(IN_FLIGHT_SAVE_ACTION_IDS, saveActionIds);
        outState.putInt(STATE_SELECT_GROUPS_ACTION_ID, selectGroupsActionId);

    }

    private int getArrayIndex(int[] array, int item) {
        for(int i = 0; i < array.length; i++) {
            if(array[i] == item) {
                return i;
            }
        }
        return -1;
    }

    private HashSet<Long> getGroupMembership(HashSet<Group> groupMembership) {
        HashSet<Long> groupIds = PiwigoUtils.toSetOfIds(groupMembership);
        if(groupIds == null) {
            groupIds = new HashSet<>(0);
        }
        return groupIds;
    }

    private User setModelFromFields(User aUser) {
        aUser.setId(user.getId());
        aUser.setLastVisit(user.getLastVisit());
        aUser.setUsername(usernameField.getText().toString());
        String email = emailField.getText().toString();
        if(!email.equals(getString(R.string.none_value))) {
            aUser.setEmail(email);
        }
        String passwordFieldText = passwordField.getText().toString();
        if(!passwordFieldText.equals(getString(R.string.password_unchanged))) {
            aUser.setPassword(passwordField.getText().toString());
        } else {
            aUser.setPassword(null);
        }
        aUser.setHighDefinitionEnabled(highDefinitionEnabled.isChecked());
        aUser.setUserType(userTypeValues.get(usertypeField.getSelectedItemPosition()));
        aUser.setPrivacyLevel(userPrivacyLevelValues[userPrivacyLevelField.getSelectedItemPosition()]);
        if(newGroupMembership != null) {
            aUser.setGroups(getGroupMembership(newGroupMembership));
        } else {
            aUser.setGroups(getGroupMembership(currentGroupMembership));
        }
        return aUser;
    }

    private void setFieldsEditable(boolean editable) {

        fieldsEditable = editable;

        usernameField.setEnabled(editable);
        passwordField.setEnabled(editable);

        usertypeField.setEnabled(editable);
        userPrivacyLevelField.setEnabled(editable);

        emailField.setEnabled(editable);
        highDefinitionEnabled.setEnabled(editable);
        editButton.setEnabled(!editable);
        discardButton.setEnabled(editable);
        saveButton.setEnabled(editable);

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

        View v = inflater.inflate(R.layout.fragment_user, container, false);

        AdView adView = v.findViewById(R.id.user_adView);
        if(AdsManager.getInstance().shouldShowAdverts()) {
            adView.loadAd(new AdRequest.Builder().build());
            adView.setVisibility(View.VISIBLE);
        } else {
            adView.setVisibility(View.GONE);
        }

        if (savedInstanceState != null) {
            currentGroupMembership = (HashSet<Group>) savedInstanceState.getSerializable(CURRENT_GROUP_MEMBERSHIPS);
            currentDirectAlbumPermissions = (HashSet<Long>) savedInstanceState.getSerializable(CURRENT_DIRECT_ALBUM_PERMISSIONS);
            currentIndirectAlbumPermissions = (HashSet<Long>) savedInstanceState.getSerializable(CURRENT_INDIRECT_ALBUM_PERMISSIONS);
            newDirectAlbumPermissions = (HashSet<Long>) savedInstanceState.getSerializable(NEW_DIRECT_ALBUM_PERMISSIONS);
            newIndirectAlbumPermissions = (HashSet<Long>) savedInstanceState.getSerializable(NEW_INDIRECT_ALBUM_PERMISSIONS);
            user = (User) savedInstanceState.getSerializable(ARG_USER);
            availableGalleries = (ArrayList<CategoryItemStub>) savedInstanceState.getSerializable(AVAILABLE_ALBUMS);
            newUser = (User) savedInstanceState.getSerializable(NEW_USER);
            fieldsEditable = savedInstanceState.getBoolean(STATE_FIELDS_EDITABLE);
            newGroupMembership = (HashSet<Group>) savedInstanceState.getSerializable(STATE_NEW_GROUP_MEMBERSHIP);
            SetUtils.setNotNull(saveActionIds,(HashSet<Long>) savedInstanceState.getSerializable(IN_FLIGHT_SAVE_ACTION_IDS));
            selectGroupsActionId = savedInstanceState.getInt(STATE_SELECT_GROUPS_ACTION_ID);
        }

        RelativeLayout userEditControls = v.findViewById(R.id.user_edit_controls);

        editButton = v.findViewById(R.id.user_action_edit_button);
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setFieldsEditable(true);
            }
        });
        discardButton = v.findViewById(R.id.user_action_discard_button);
        discardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newGroupMembership = null;
                newDirectAlbumPermissions = null;
                newIndirectAlbumPermissions = null;
                setFieldsFromModel(user);
                populateAlbumPermissionsList(currentDirectAlbumPermissions, currentIndirectAlbumPermissions);
                setFieldsEditable(user.getId() < 0);
            }
        });
        saveButton = v.findViewById(R.id.user_action_save_button);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveUserChanges();
            }
        });
        deleteButton = v.findViewById(R.id.user_action_delete_button);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteUser(user);
            }
        });

        usernameField = v.findViewById(R.id.user_username);

        usertypeField = v.findViewById(R.id.user_usertype);
        userTypeValues = Arrays.asList(getResources().getStringArray(R.array.user_types_array));
        List<String> userTypes = Arrays.asList(getResources().getStringArray(R.array.user_types_array));
        ArrayAdapter<String> userTypesAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, userTypes);
        userTypesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        usertypeField.setAdapter(userTypesAdapter);

        userPrivacyLevelField = v.findViewById(R.id.user_privacy_level);
        userPrivacyLevelValues = getResources().getIntArray(R.array.privacy_levels_values_array);
        List<String> userPrivacyLevels = Arrays.asList(getResources().getStringArray(R.array.privacy_levels_array));
        ArrayAdapter<String> userPrivacyLevelsAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, userPrivacyLevels);
        userPrivacyLevelsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        userPrivacyLevelField.setAdapter(userPrivacyLevelsAdapter);


        emailField = v.findViewById(R.id.user_email_field);

        lastVisitedFieldLabel = v.findViewById(R.id.user_lastvisit_label);
        lastVisitedField = v.findViewById(R.id.user_lastvisit);

        TextView passwordFieldLabel = v.findViewById(R.id.user_password_label);
        passwordField = v.findViewById(R.id.user_password_field);

        if(user.getId() < 0) {
            // this is a new user creation.
            fieldsEditable = true;
            currentGroupMembership = new HashSet<>(0);
        }

        highDefinitionEnabled = v.findViewById(R.id.user_high_definition_field);

        userGroupsField = v.findViewById(R.id.user_groups);
        userGroupsField.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectGroupMembership();
            }
        });

        albumPermissionsField = v.findViewById(R.id.user_access_rights);
        albumPermissionsField.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        albumPermissionsField.setOnTouchListener(new CustomClickTouchListener(getContext()) {
            @Override
            public boolean onClick() {
                onExpandPermissions();
                return true;
            }
        });

        setFieldsEditable(fieldsEditable);
        if(newUser != null) {
            setFieldsFromModel(newUser);
        } else {
            setFieldsFromModel(user);
        }

        if (availableGalleries == null) {
            addActiveServiceCall(R.string.progress_loading_user_details, new AlbumGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId(), true).invokeAsync(getContext()));
        }

        if(user.getId() < 0) {
            currentGroupMembership = new HashSet<>();
            currentDirectAlbumPermissions = new HashSet<>();
            currentIndirectAlbumPermissions = new HashSet<>();
        } else {
            if (currentGroupMembership == null) {
                if (user.getGroups().size() > 0) {
                    addActiveServiceCall(R.string.progress_loading_user_details, new GroupsGetListResponseHandler(user.getGroups(), 0, 100).invokeAsync(getContext()));
                } else {
                    currentGroupMembership = new HashSet<>();
                }
            }
            fillGroupMembershipField();
            if (currentDirectAlbumPermissions == null) {
                addActiveServiceCall(R.string.progress_loading_user_details, new UserGetPermissionsResponseHandler(user.getId()).invokeAsync(getContext()));
            }
        }

        ScrollView scrollview = v.findViewById(R.id.user_edit_scrollview);
        scrollview.fullScroll(View.FOCUS_UP);

        return v;
    }

    private void saveUserChanges() {
        newUser = setModelFromFields(new User());
        setFieldsEditable(false);
        if(newUser.getId() < 0) {
            long saveActionId = new UserAddResponseHandler(newUser).invokeAsync(getContext());
            saveActionIds.add(saveActionId);
            addActiveServiceCall(R.string.progress_adding_user, saveActionId);
        } else {
            long saveActionId = new UserUpdateInfoResponseHandler(newUser).invokeAsync(getContext());
            saveActionIds.add(saveActionId);
            addActiveServiceCall(R.string.progress_saving_changes, saveActionId);
            saveUserPermissionsChangesIfRequired();
        }

    }

    private HashSet<Group> getLatestGroupMembership() {
        HashSet<Group> currentSelection;
        if(newGroupMembership != null) {
            currentSelection = newGroupMembership;
        } else {
            currentSelection = currentGroupMembership;
        }
        return currentSelection;
    }

    private void setFieldsFromModel(User user) {
        usernameField.setText(user.getUsername());
        int selectedUserType = userTypeValues.indexOf(user.getUserType());
        usertypeField.setSelection(selectedUserType);

        int selectedPrivacyLevelPosition = getArrayIndex(userPrivacyLevelValues, user.getPrivacyLevel());
        userPrivacyLevelField.setSelection(selectedPrivacyLevelPosition);
        String emailAddr = user.getEmail();
        if (emailAddr != null) {
            emailField.setText(emailAddr);
        } else {
            emailField.setText(R.string.none_value);
        }

        if(user.getPassword() != null) {
            passwordField.setText(user.getPassword());
        } else if(user.getId() >= 0){
            passwordField.setText(R.string.password_unchanged);
        } else {
            passwordField.setText("");
        }

        fillGroupMembershipField();

        if(user.getId() < 0) {
            // this is a new user creation.
            lastVisitedField.setVisibility(GONE);
            lastVisitedFieldLabel.setVisibility(GONE);
        } else {
            lastVisitedField.setVisibility(VISIBLE);
            lastVisitedFieldLabel.setVisibility(VISIBLE);
            if(user.getLastVisit() != null) {
                lastVisitedField.setText(dateFormatter.format(user.getLastVisit()));
            } else {
                lastVisitedField.setText(R.string.last_visit_never);
            }
        }

        highDefinitionEnabled.setChecked(user.isHighDefinitionEnabled());

        deleteButton.setEnabled(user.getId() >= 0);
    }

    private void selectGroupMembership() {
        HashSet<Group> currentSelection = getLatestGroupMembership();
        HashSet<Long> preselectedGroups = new HashSet<>(currentSelection.size());

        for(Group g : currentSelection) {
            preselectedGroups.add(g.getId());
        }
        GroupSelectionNeededEvent groupSelectionNeededEvent = new GroupSelectionNeededEvent(true, fieldsEditable, preselectedGroups);
        selectGroupsActionId = groupSelectionNeededEvent.getActionId();
        EventBus.getDefault().post(groupSelectionNeededEvent);
    }

    private void deleteUser(final User user) {
        String currentUser = PiwigoSessionDetails.getInstance().getUsername();
        if (currentUser.equals(user.getUsername())) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, String.format(getString(R.string.alert_error_unable_to_delete_yourself_pattern), currentUser));
        } else {

            String message = getString(R.string.alert_confirm_really_delete_user);
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_cancel, R.string.button_ok, new UIHelper.QuestionResultListener() {
                @Override
                public void onDismiss(AlertDialog dialog) {
                }

                @Override
                public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                    if(Boolean.TRUE == positiveAnswer) {
                        deleteUserNow(user);
                    }
                }
            });
        }
    }

    private void deleteUserNow(User thisItem) {

        addActiveServiceCall(R.string.progress_delete_user, new UserDeleteResponseHandler(thisItem.getId()).invokeAsync(getContext()));
    }

    private void onUserSaved(PiwigoResponseBufferingHandler.PiwigoUpdateUserInfoResponse response) {
        // copy across album permissions as they aren't normally retrieved.
        synchronized (saveActionIds) {
            saveActionIds.remove(response.getMessageId());
            // save the new user.
            user = response.getUser();
            // Groups don't come back set so lets set them here
            user.setGroups(newUser.getGroups());
            newUser = null;

            if(newGroupMembership == null && user.getGroups().size() == 0) {
                currentGroupMembership = new HashSet<>(0);
            } else {
                currentGroupMembership = newGroupMembership;
                newGroupMembership = null;
            }
            if (saveActionIds.size() == 0) {
                // user object now contains all changes from the newUser object.
                newDirectAlbumPermissions = null;
                newIndirectAlbumPermissions = null;
            }

            setFieldsFromModel(user);
            EventBus.getDefault().post(new UserUpdatedEvent(user));
        }
    }

    private void saveUserPermissionsChangesIfRequired() {
        if(newDirectAlbumPermissions == null) {
            //nothing to do.
            return;
        }

        HashSet<Long> userPermissionsToRemove = SetUtils.difference(currentDirectAlbumPermissions, newDirectAlbumPermissions);
        HashSet<Long> userPermissionsToAdd = SetUtils.difference(newDirectAlbumPermissions, currentDirectAlbumPermissions);

        if (userPermissionsToRemove.size() > 0) {
            long saveActionId = new UserPermissionsRemovedResponseHandler(newUser.getId(), userPermissionsToRemove).invokeAsync(getContext());
            saveActionIds.add(saveActionId);
            addActiveServiceCall(R.string.progress_saving_changes, saveActionId);
        }
        if (userPermissionsToAdd.size() > 0) {
            long saveActionId = new UserPermissionsAddResponseHandler(newUser.getId(), userPermissionsToAdd).invokeAsync(getContext());
            saveActionIds.add(saveActionId);
            addActiveServiceCall(R.string.progress_saving_changes, saveActionId);
        }
    }

    private void onExpandPermissions() {
        HashSet<Long> directAlbumPermissions = getLatestDirectAlbumPermissions();
        HashSet<Long> indirectAlbumPermissions = getLatestIndirectAlbumPermissions();
        AlbumPermissionsSelectionNeededEvent evt = new AlbumPermissionsSelectionNeededEvent(availableGalleries, new HashSet<>(directAlbumPermissions), indirectAlbumPermissions, fieldsEditable);
        getUiHelper().setTrackingRequest(evt.getActionId());
        EventBus.getDefault().post(evt);
    }

    private HashSet<Long> getLatestDirectAlbumPermissions() {
        HashSet<Long> directAlbumPermissions;
        if(newDirectAlbumPermissions != null) {
            directAlbumPermissions = newDirectAlbumPermissions;
        } else if(currentDirectAlbumPermissions != null) {
            directAlbumPermissions = currentDirectAlbumPermissions;
        } else {
            currentDirectAlbumPermissions = new HashSet<>();
            directAlbumPermissions = currentDirectAlbumPermissions;
        }
        return directAlbumPermissions;
    }

    private HashSet<Long> getLatestIndirectAlbumPermissions() {
        HashSet<Long> indirectAlbumPermissions;
        if(newIndirectAlbumPermissions != null) {
            indirectAlbumPermissions = newIndirectAlbumPermissions;
        } else if(currentIndirectAlbumPermissions != null) {
            indirectAlbumPermissions = currentIndirectAlbumPermissions;
        } else {
            currentIndirectAlbumPermissions = new HashSet<>();
            indirectAlbumPermissions = currentIndirectAlbumPermissions;
        }
        return indirectAlbumPermissions;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumPermissionsSelectionCompleteEvent event) {
        if(getUiHelper().isTrackingRequest(event.getActionId())) {
            newDirectAlbumPermissions = event.getSelectedAlbums();
        }
    }

    private void fillGroupMembershipField() {


        HashSet<Group> groupMembership = getLatestGroupMembership();

        if (groupMembership == null) {
            userGroupsField.setText(R.string.loading_please_wait);
        } else {
            StringBuilder sb = new StringBuilder();
            if (groupMembership.size() == 0) {
                sb.append(getString(R.string.none_value));
            } else {
                ArrayList<Group> sortedList = new ArrayList<>(groupMembership);
                Collections.sort(sortedList, new Comparator<Group>() {
                    @Override
                    public int compare(Group o1, Group o2) {
                        return o1.getName().compareTo(o2.getName());
                    }
                });
                Iterator<Group> iter = sortedList.iterator();
                while (iter.hasNext()) {
                    sb.append(iter.next().getName());
                    if (iter.hasNext()) {
                        sb.append(", ");
                    }
                }
            }
            userGroupsField.setText(sb.toString());
        }
    }

    private void onUserPermissionsRemoved(PiwigoResponseBufferingHandler.PiwigoUserPermissionsRemovedResponse response) {
        synchronized (saveActionIds) {
            saveActionIds.remove(response.getMessageId());
            currentDirectAlbumPermissions.removeAll(response.getAlbumsForWhichPermissionRemoved());
            if (saveActionIds.size() == 0) {
                // user object now contains all changes from the newUser object.
                newDirectAlbumPermissions = null;
                newIndirectAlbumPermissions = null;
            }
        }
    }

    private void onUserPermissionsAdded(PiwigoResponseBufferingHandler.PiwigoUserPermissionsAddedResponse response) {
        synchronized (saveActionIds) {
            saveActionIds.remove(response.getMessageId());
            currentDirectAlbumPermissions.addAll(response.getAlbumsForWhichPermissionAdded());
            if (saveActionIds.size() == 0) {
                // user object now contains all changes from the newUser object.
                newDirectAlbumPermissions = null;
                newIndirectAlbumPermissions = null;
            }
        }
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if(response instanceof PiwigoResponseBufferingHandler.PiwigoUserPermissionsRemovedResponse) {
                onUserPermissionsRemoved((PiwigoResponseBufferingHandler.PiwigoUserPermissionsRemovedResponse) response);
            } else if(response instanceof PiwigoResponseBufferingHandler.PiwigoUserPermissionsAddedResponse) {
                onUserPermissionsAdded((PiwigoResponseBufferingHandler.PiwigoUserPermissionsAddedResponse) response);
            } else if(response instanceof PiwigoResponseBufferingHandler.PiwigoAddUserResponse) {
                onUserCreated((PiwigoResponseBufferingHandler.PiwigoAddUserResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoUpdateUserInfoResponse) {
                onUserSaved((PiwigoResponseBufferingHandler.PiwigoUpdateUserInfoResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse) {
                onGroupsLoaded((PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoUserPermissionsResponse) {
                onUserPermissionsRetrieved((PiwigoResponseBufferingHandler.PiwigoUserPermissionsResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) {
                onGetSubGalleries((PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoDeleteUserResponse) {
                onUserDeleted((PiwigoResponseBufferingHandler.PiwigoDeleteUserResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGroupPermissionsRetrievedResponse) {
                onGroupMembershipAlbumPermissionsRetrieved((PiwigoResponseBufferingHandler.PiwigoGroupPermissionsRetrievedResponse)response);
            }
        }
    }

    private void onUserCreated(PiwigoResponseBufferingHandler.PiwigoAddUserResponse response) {
        saveActionIds.remove(response.getMessageId());
        User savedUser = response.getUser();
        newUser.setId(savedUser.getId());
        newUser.setPassword(null);
        setFieldsFromModel(newUser);
        addActiveServiceCall(R.string.progress_saving_changes, new UserUpdateInfoResponseHandler(newUser).invokeAsync(getContext()));
        saveUserPermissionsChangesIfRequired();
    }

    private void onGroupsLoaded(PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse response) {
        currentGroupMembership = response.getGroups();
        fillGroupMembershipField();
    }

    private void onGroupMembershipAlbumPermissionsRetrieved(PiwigoResponseBufferingHandler.PiwigoGroupPermissionsRetrievedResponse response) {
        // retrieve all those albums indirectly accessibly by this user.
        newIndirectAlbumPermissions = response.getAllowedAlbums();
        populateAlbumPermissionsList(getLatestDirectAlbumPermissions(), getLatestIndirectAlbumPermissions());
    }

    private void onUserPermissionsRetrieved(PiwigoResponseBufferingHandler.PiwigoUserPermissionsResponse response) {
        // We're retrieving the current configuration
        currentDirectAlbumPermissions = response.getDirectlyAccessibleAlbumIds();
        currentIndirectAlbumPermissions = response.getIndirectlyAccessibleAlbumIds();
        if (availableGalleries != null) {
            populateAlbumPermissionsList(currentDirectAlbumPermissions, currentIndirectAlbumPermissions);
        }
    }

    private void onGetSubGalleries(PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse response) {
        this.availableGalleries = response.getAlbumNames();
        if (currentIndirectAlbumPermissions != null) {
            populateAlbumPermissionsList(currentDirectAlbumPermissions, currentIndirectAlbumPermissions);
        }
    }

    private void onUserDeleted(final PiwigoResponseBufferingHandler.PiwigoDeleteUserResponse response) {
        EventBus.getDefault().post(new UserDeletedEvent(user));
        // return to previous screen
        if(isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }


    private synchronized void populateAlbumPermissionsList(HashSet<Long> directAlbumPermissions, HashSet<Long> indirectAlbumPermissions) {
        AlbumSelectionListAdapter adapter = (AlbumSelectionListAdapter)albumPermissionsField.getAdapter();
        if(adapter == null) {
            adapter = new AlbumSelectionListAdapter(this.getContext(), availableGalleries, indirectAlbumPermissions, false);
            albumPermissionsField.setAdapter(adapter);

        } else if(!SetUtils.equals(adapter.getIndirectlySelectedItems(), indirectAlbumPermissions)) {
            adapter.setIndirectlySelectedItems(indirectAlbumPermissions);
        }
        albumPermissionsField.clearChoices();
        if(directAlbumPermissions != null && directAlbumPermissions.size() > 0) {
            for (Long selectedAlbum : directAlbumPermissions) {
                int itemPos = adapter.getPosition(selectedAlbum);
                if (itemPos >= 0) {
                    albumPermissionsField.setItemChecked(itemPos, true);
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(GroupSelectionCompleteEvent groupSelectionCompleteEvent) {

        if(selectGroupsActionId == groupSelectionCompleteEvent.getActionId()) {
            newGroupMembership = new HashSet<>(groupSelectionCompleteEvent.getSelectedItems());
            fillGroupMembershipField();

            HashSet<Long> newGroupsMembership = new HashSet<>(groupSelectionCompleteEvent.getCurrentSelection());
            if(newGroupsMembership.size() > 0) {
                addActiveServiceCall(R.string.progress_reloading_album_permissions, new GroupGetPermissionsResponseHandler(newGroupsMembership).invokeAsync(getContext()));
            } else {
                newIndirectAlbumPermissions = new HashSet<>(0);
            }
            selectGroupsActionId = -1;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AppLockedEvent event) {
        if(isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }
}
