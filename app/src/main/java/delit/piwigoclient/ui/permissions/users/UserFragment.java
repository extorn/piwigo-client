package delit.piwigoclient.ui.permissions.users;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.view.CustomClickTouchListener;
import delit.libs.ui.view.PasswordInputToggle;
import delit.libs.util.CollectionUtils;
import delit.libs.util.SetUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.StaticCategoryItem;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupGetPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupsGetListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserAddResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserGetPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserPermissionsAddResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserPermissionsRemovedResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserUpdateInfoResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.UserDeletedEvent;
import delit.piwigoclient.ui.events.UserUpdatedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionNeededEvent;
import delit.piwigoclient.ui.permissions.AlbumSelectionListAdapter;
import delit.piwigoclient.ui.permissions.AlbumSelectionListAdapterPreferences;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Created by gareth on 21/06/17.
 */

public class UserFragment<F extends UserFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends MyFragment<F,FUIH> {

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
    private static final String TAG = "UsrFrag";
    // other stuff
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss", Locale.UK);
    private final HashSet<Long> saveActionIds = new HashSet<>(5);
    // fields
    private EditText usernameField;
    private Spinner usertypeField;
    private EditText emailField;
    private SwitchMaterial highDefinitionEnabled;
    private TextView lastVisitedField;
    private MaterialButton editButton;
    private MaterialButton discardButton;
    private MaterialButton saveButton;
    private MaterialButton deleteButton;
    private Spinner userPrivacyLevelField;
    private TextView userGroupsField;
    private EditText passwordField;
    private TextView lastVisitedFieldLabel;
    // field value options
    private int[] userPrivacyLevelValues;
    private List<String> userTypeValues;
    // following fields are stored in state
    private User newUser;
    private User user;
    private ArrayList<CategoryItemStub> availableGalleries;
    private boolean fieldsEditable = false;
    private ListView albumPermissionsField;
    private HashSet<Long> currentDirectAlbumPermissions;
    private HashSet<Long> currentIndirectAlbumPermissions;
    private HashSet<Long> newDirectAlbumPermissions;
    private HashSet<Long> newIndirectAlbumPermissions;
    private HashSet<Group> currentGroupMembership;
    private HashSet<Group> newGroupMembership;
    private int selectGroupsActionId;
    private long userGetPermissionsCallId;

    public static UserFragment<?,?> newInstance(User user) {
        UserFragment<?,?> fragment = new UserFragment<>();
        fragment.setTheme(R.style.Theme_App_EditPages);
        Bundle args = new Bundle();
        args.putParcelable(ARG_USER, user);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            user = getArguments().getParcelable(ARG_USER);
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
        if (availableGalleries != null && !getUiHelper().isServiceCallInProgress(userGetPermissionsCallId)) {
            userGetPermissionsCallId = 0;
            HashSet<Long> directAlbumPermissions = getLatestDirectAlbumPermissions();
            HashSet<Long> indirectAlbumPermissions = getLatestIndirectAlbumPermissions();
            populateAlbumPermissionsList(directAlbumPermissions, indirectAlbumPermissions);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        BundleUtils.putSet(outState, CURRENT_GROUP_MEMBERSHIPS, currentGroupMembership);
        BundleUtils.putLongHashSet(outState, CURRENT_DIRECT_ALBUM_PERMISSIONS, currentDirectAlbumPermissions);
        BundleUtils.putLongHashSet(outState, CURRENT_INDIRECT_ALBUM_PERMISSIONS, currentIndirectAlbumPermissions);
        BundleUtils.putLongHashSet(outState, NEW_DIRECT_ALBUM_PERMISSIONS, newDirectAlbumPermissions);
        BundleUtils.putLongHashSet(outState, NEW_INDIRECT_ALBUM_PERMISSIONS, newIndirectAlbumPermissions);
        outState.putParcelableArrayList(AVAILABLE_ALBUMS, availableGalleries);
        outState.putParcelable(ARG_USER, user);
        outState.putParcelable(NEW_USER, newUser);
        outState.putBoolean(STATE_FIELDS_EDITABLE, fieldsEditable);
        BundleUtils.putSet(outState, STATE_NEW_GROUP_MEMBERSHIP, newGroupMembership);
        BundleUtils.putLongHashSet(outState, IN_FLIGHT_SAVE_ACTION_IDS, saveActionIds);
        outState.putInt(STATE_SELECT_GROUPS_ACTION_ID, selectGroupsActionId);

    }

    private int getArrayIndex(int[] array, int item) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == item) {
                return i;
            }
        }
        return -1;
    }

    private HashSet<Long> getGroupMembership(HashSet<Group> groupMembership) {
        HashSet<Long> groupIds = PiwigoUtils.toSetOfIds(groupMembership);
        if (groupIds == null) {
            groupIds = new HashSet<>(0);
        }
        return groupIds;
    }

    private User setModelFromFields(User aUser) {
        aUser.setId(user.getId());
        aUser.setLastVisit(user.getLastVisit());
        aUser.setUsername(usernameField.getText().toString());
        String email = emailField.getText().toString();
        if (!email.equals(getString(R.string.none_value))) {
            aUser.setEmail(email);
        }
        String passwordFieldText = passwordField.getText().toString();
        if (!passwordFieldText.equals(getString(R.string.password_unchanged))) {
            aUser.setPassword(passwordField.getText().toString());
        } else {
            aUser.setPassword(null);
        }
        aUser.setHighDefinitionEnabled(highDefinitionEnabled.isChecked());
        aUser.setUserType(userTypeValues.get(usertypeField.getSelectedItemPosition()));
        aUser.setPrivacyLevel(userPrivacyLevelValues[userPrivacyLevelField.getSelectedItemPosition()]);
        if (newGroupMembership != null) {
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
        lastVisitedField.setEnabled(editable);

        //TODO this doesn't work as the adapter prefs are marked readonly and still set as disabled after setenabled on the adapter
        // the idea was to make the icons green (secondary colors) when in editable mode.
//        AlbumSelectionListAdapter adapter = ((AlbumSelectionListAdapter) albumPermissionsField.getAdapter());
//        if(adapter != null) {
//            adapter.setEnabled(editable);
//            adapter.notifyDataSetChanged();
//        }

        emailField.setEnabled(editable);
        highDefinitionEnabled.setEnabled(editable);
//        editButton.setEnabled(!editable);
        editButton.setVisibility(!editable ? VISIBLE : GONE);
//        discardButton.setEnabled(editable);
        discardButton.setVisibility(editable ? VISIBLE : GONE);
//        saveButton.setEnabled(editable);
        saveButton.setVisibility(editable ? VISIBLE : GONE);

    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.user_user_heading);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        View v = inflater.inflate(R.layout.fragment_user, container, false);

        AdView adView = v.findViewById(R.id.user_adView);
        if (AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        if (savedInstanceState != null) {
            currentGroupMembership = BundleUtils.getHashSet(savedInstanceState, CURRENT_GROUP_MEMBERSHIPS);
            currentDirectAlbumPermissions = BundleUtils.getLongHashSet(savedInstanceState, CURRENT_DIRECT_ALBUM_PERMISSIONS);
            currentIndirectAlbumPermissions = BundleUtils.getLongHashSet(savedInstanceState, CURRENT_INDIRECT_ALBUM_PERMISSIONS);
            newDirectAlbumPermissions = BundleUtils.getLongHashSet(savedInstanceState, NEW_DIRECT_ALBUM_PERMISSIONS);
            newIndirectAlbumPermissions = BundleUtils.getLongHashSet(savedInstanceState, NEW_INDIRECT_ALBUM_PERMISSIONS);
            user = savedInstanceState.getParcelable(ARG_USER);
            availableGalleries = savedInstanceState.getParcelableArrayList(AVAILABLE_ALBUMS);
            newUser = savedInstanceState.getParcelable(NEW_USER);
            fieldsEditable = savedInstanceState.getBoolean(STATE_FIELDS_EDITABLE);
            newGroupMembership = BundleUtils.getHashSet(savedInstanceState, STATE_NEW_GROUP_MEMBERSHIP);
            CollectionUtils.addToCollectionNullSafe(saveActionIds, BundleUtils.getLongHashSet(savedInstanceState, IN_FLIGHT_SAVE_ACTION_IDS));
            selectGroupsActionId = savedInstanceState.getInt(STATE_SELECT_GROUPS_ACTION_ID);
        }

        RelativeLayout userEditControls = v.findViewById(R.id.user_edit_controls);

        editButton = v.findViewById(R.id.user_action_edit_button);
        editButton.setOnClickListener(v14 -> setFieldsEditable(true));
        discardButton = v.findViewById(R.id.user_action_discard_button);
        discardButton.setOnClickListener(v13 -> {
            newGroupMembership = null;
            newDirectAlbumPermissions = null;
            newIndirectAlbumPermissions = null;
            setFieldsFromModel(user);
            populateAlbumPermissionsList(currentDirectAlbumPermissions, currentIndirectAlbumPermissions);
            setFieldsEditable(user.getId() < 0);
        });
        saveButton = v.findViewById(R.id.user_action_save_button);
        saveButton.setOnClickListener(v12 -> saveUserChanges());
        deleteButton = v.findViewById(R.id.user_action_delete_button);
        deleteButton.setOnClickListener(v1 -> deleteUser(user));

        usernameField = v.findViewById(R.id.user_username);

        usertypeField = v.findViewById(R.id.user_usertype);
        userTypeValues = Arrays.asList(getResources().getStringArray(R.array.user_types_array));
        List<String> userTypes = Arrays.asList(getResources().getStringArray(R.array.user_types_array));
        ArrayAdapter<String> userTypesAdapter = new ArrayAdapter<>(v.getContext(), android.R.layout.simple_spinner_item, userTypes);
        userTypesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        usertypeField.setAdapter(userTypesAdapter);

        userPrivacyLevelField = v.findViewById(R.id.user_privacy_level);
        userPrivacyLevelValues = getResources().getIntArray(R.array.privacy_levels_values_array);
        List<String> userPrivacyLevels = Arrays.asList(getResources().getStringArray(R.array.privacy_levels_array));
        ArrayAdapter<String> userPrivacyLevelsAdapter = new ArrayAdapter<>(v.getContext(), android.R.layout.simple_spinner_item, userPrivacyLevels);
        userPrivacyLevelsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        userPrivacyLevelField.setAdapter(userPrivacyLevelsAdapter);


        emailField = v.findViewById(R.id.user_email_field);

        lastVisitedFieldLabel = v.findViewById(R.id.user_lastvisit_label);
        lastVisitedField = v.findViewById(R.id.user_lastvisit);

        TextView passwordFieldLabel = v.findViewById(R.id.user_password_label);
        passwordField = v.findViewById(R.id.user_password_field);

        CheckBox viewUnencryptedToggle = v.findViewById(R.id.toggle_visibility);
        if (viewUnencryptedToggle != null) {
            viewUnencryptedToggle.setOnCheckedChangeListener(new PasswordInputToggle(passwordField));
        }

        if (user.getId() < 0) {
            // this is a new user creation.
            fieldsEditable = true;
            currentGroupMembership = new HashSet<>(0);
        }

        highDefinitionEnabled = v.findViewById(R.id.user_high_definition_field);

        userGroupsField = v.findViewById(R.id.user_groups);
        userGroupsField.setOnClickListener(v15 -> selectGroupMembership());
        // can't just use a std click listener as it first focuses the field :-(
        CustomClickTouchListener.callClickOnTouch(userGroupsField);

        albumPermissionsField = v.findViewById(R.id.user_access_rights);
        albumPermissionsField.setOnTouchListener(new CustomClickTouchListener(albumPermissionsField) {
            @Override
            public boolean onClick() {
                onExpandPermissions();
                return true;
            }
        });

        setFieldsEditable(fieldsEditable);
        if (newUser != null) {
            setFieldsFromModel(newUser);
        } else {
            setFieldsFromModel(user);
        }

        if (availableGalleries == null) {
            addActiveServiceCall(R.string.progress_loading_user_details, new AlbumGetSubAlbumNamesResponseHandler(StaticCategoryItem.ROOT_ALBUM.getId(), true));
        }

        if (user.getId() < 0) {
            currentGroupMembership = new HashSet<>();
            currentDirectAlbumPermissions = new HashSet<>();
            currentIndirectAlbumPermissions = new HashSet<>();
        } else {
            if (currentGroupMembership == null) {
                if (user.getGroups().size() > 0) {
                    addActiveServiceCall(R.string.progress_loading_user_details, new GroupsGetListResponseHandler(user.getGroups()));
                } else {
                    currentGroupMembership = new HashSet<>();
                }
            }
            fillGroupMembershipField();
            if (currentDirectAlbumPermissions == null) {
                userGetPermissionsCallId = addActiveServiceCall(R.string.progress_loading_user_details, new UserGetPermissionsResponseHandler(user.getId()));
            }
        }

        albumPermissionsField.addOnLayoutChangeListener(new LayoutChangeListener());


        return v;
    }

    User getUser() {
        return user;
    }

    void setUser(User user) {
        this.user = user;
    }

    User getNewUser() {
        return newUser;
    }

    HashSet<Long> getCurrentDirectAlbumPermissions() {
        return currentDirectAlbumPermissions;
    }

    HashSet<Long> getCurrentIndirectAlbumPermissions() {
        return currentIndirectAlbumPermissions;
    }

    private static class UserFragmentAction<F extends UserFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends UIHelper.Action<FUIH,F, UserGetInfoResponseHandler.PiwigoGetUserDetailsResponse> implements Parcelable {

        private static final String TAG = "UsrFrag";

        protected UserFragmentAction() {}

        protected UserFragmentAction(Parcel in) {
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

        public static final Creator<UserFragmentAction<?,?>> CREATOR = new Creator<UserFragmentAction<?,?>>() {
            @Override
            public UserFragmentAction<?,?> createFromParcel(Parcel in) {
                return new UserFragmentAction<>(in);
            }

            @Override
            public UserFragmentAction<?,?>[] newArray(int size) {
                return new UserFragmentAction[size];
            }
        };

        @Override
        public boolean onSuccess(FUIH uiHelper, UserGetInfoResponseHandler.PiwigoGetUserDetailsResponse response) {
            F userFragment = uiHelper.getParent();
            userFragment.setUser(response.getSelectedUser());

            if(userFragment.getNewUser() == null) {
                if (userFragment.getUser() != null) {
                    userFragment.setFieldsFromModel(userFragment.getUser());
                    userFragment.populateAlbumPermissionsList(userFragment.getCurrentDirectAlbumPermissions(), userFragment.getCurrentIndirectAlbumPermissions());
                } else {
                    uiHelper.showDetailedMsg(R.string.alert_error, userFragment.getString(R.string.user_details_not_found_on_server));
                }
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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if(!PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile()) || (isSessionDetailsChanged() && !isServerConnectionChanged())){
            //trigger total screen refresh. Any errors will result in screen being closed.
            UIHelper.Action action = new UserFragmentAction<>();
            getUiHelper().invokeActiveServiceCall(R.string.progress_loading_user_details, new UserGetInfoResponseHandler(user.getId()), action);
        } else if((!PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) || isAppInReadOnlyMode()) {
            // immediately leave this screen.
            Logging.log(Log.INFO, TAG, "removing from activity as not admin user");
            getParentFragmentManager().popBackStack();
        }
    }

    private void saveUserChanges() {
        newUser = setModelFromFields(new User());
        setFieldsEditable(false);
        if (newUser.getId() < 0) {
            saveActionIds.add(addActiveServiceCall(R.string.progress_adding_user, new UserAddResponseHandler<>(newUser)));
        } else {
            saveActionIds.add(addActiveServiceCall(R.string.progress_saving_changes, new UserUpdateInfoResponseHandler<>(newUser)));
            saveUserPermissionsChangesIfRequired();
        }

    }

    private HashSet<Group> getLatestGroupMembership() {
        HashSet<Group> currentSelection;
        if (newGroupMembership != null) {
            currentSelection = newGroupMembership;
        } else {
            currentSelection = currentGroupMembership;
        }
        return currentSelection;
    }

    protected void setFieldsFromModel(User user) {
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

        if (user.getPassword() != null) {
            passwordField.setText(user.getPassword());
        } else if (user.getId() >= 0) {
            passwordField.setText(R.string.password_unchanged);
        } else {
            passwordField.setText("");
        }

        fillGroupMembershipField();

        if (user.getId() < 0) {
            // this is a new user creation.
            lastVisitedField.setVisibility(GONE);
            lastVisitedFieldLabel.setVisibility(GONE);
        } else {
            lastVisitedField.setVisibility(VISIBLE);
            lastVisitedFieldLabel.setVisibility(VISIBLE);
            if (user.getLastVisit() != null) {
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
        HashSet<Long> preselectedGroups = PiwigoUtils.toSetOfIds(currentSelection);
        GroupSelectionNeededEvent groupSelectionNeededEvent = new GroupSelectionNeededEvent(true, fieldsEditable, preselectedGroups);
        selectGroupsActionId = groupSelectionNeededEvent.getActionId();
        EventBus.getDefault().post(groupSelectionNeededEvent);
    }

    private void deleteUser(final User user) {
        String currentUser = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile()).getUsername();
        if (currentUser.equals(user.getUsername())) {
            getUiHelper().showDetailedMsg(R.string.alert_error, getString(R.string.alert_error_unable_to_delete_yourself_pattern, currentUser));
        } else {

            String message = getString(R.string.alert_confirm_really_delete_user);
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_cancel, R.string.button_ok, new OnDeleteUserAction<>(getUiHelper(), user));
        }
    }

    private static class OnDeleteUserAction<F extends UserFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends UIHelper.QuestionResultAdapter<FUIH, F> implements Parcelable {

        private User user;

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

        addActiveServiceCall(R.string.progress_delete_user, new UserDeleteResponseHandler(thisItem.getId()));
    }

    protected void onUserSaved(UserUpdateInfoResponseHandler.PiwigoUpdateUserInfoResponse response) {
        // copy across album permissions as they aren't normally retrieved.
        synchronized (saveActionIds) {
            saveActionIds.remove(response.getMessageId());
            // save the new user.
            user = response.getUser();
            // Groups don't come back set so lets set them here
            user.setGroups(newUser.getGroups());
            newUser = null;

            if (newGroupMembership == null) {
                if (user.getGroups().size() == 0) {
                    currentGroupMembership = new HashSet<>(0);
                } else {
                    addActiveServiceCall(R.string.progress_loading_user_details, new GroupsGetListResponseHandler(user.getGroups()));
                    currentGroupMembership = null;
                }
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
        if (newDirectAlbumPermissions == null) {
            //nothing to do.
            return;
        }

        HashSet<Long> userPermissionsToRemove = SetUtils.difference(currentDirectAlbumPermissions, newDirectAlbumPermissions);
        HashSet<Long> userPermissionsToAdd = SetUtils.difference(newDirectAlbumPermissions, currentDirectAlbumPermissions);

        if (userPermissionsToRemove.size() > 0) {
            saveActionIds.add(addActiveServiceCall(R.string.progress_saving_changes, new UserPermissionsRemovedResponseHandler(newUser.getId(), userPermissionsToRemove)));

        }
        if (userPermissionsToAdd.size() > 0) {
            saveActionIds.add(addActiveServiceCall(R.string.progress_saving_changes, new UserPermissionsAddResponseHandler(newUser.getId(), userPermissionsToAdd)));
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
        if (newDirectAlbumPermissions != null) {
            directAlbumPermissions = newDirectAlbumPermissions;
        } else if (currentDirectAlbumPermissions != null) {
            directAlbumPermissions = currentDirectAlbumPermissions;
        } else {
            currentDirectAlbumPermissions = new HashSet<>();
            directAlbumPermissions = currentDirectAlbumPermissions;
        }
        return directAlbumPermissions;
    }

    private HashSet<Long> getLatestIndirectAlbumPermissions() {
        HashSet<Long> indirectAlbumPermissions;
        if (newIndirectAlbumPermissions != null) {
            indirectAlbumPermissions = newIndirectAlbumPermissions;
        } else if (currentIndirectAlbumPermissions != null) {
            indirectAlbumPermissions = currentIndirectAlbumPermissions;
        } else {
            currentIndirectAlbumPermissions = new HashSet<>();
            indirectAlbumPermissions = currentIndirectAlbumPermissions;
        }
        return indirectAlbumPermissions;
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumPermissionsSelectionCompleteEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            newDirectAlbumPermissions = event.getSelectedAlbumIds();
            populateAlbumPermissionsList(getLatestDirectAlbumPermissions(), getLatestIndirectAlbumPermissions());
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
                Collections.sort(sortedList, (o1, o2) -> o1.getName().compareTo(o2.getName()));
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

    protected void onUserPermissionsRemoved(UserPermissionsRemovedResponseHandler.PiwigoUserPermissionsRemovedResponse response) {
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

    protected void onUserPermissionsAdded(UserPermissionsAddResponseHandler.PiwigoUserPermissionsAddedResponse response) {
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
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener<>();
    }

    protected void onUserCreated(UserAddResponseHandler.PiwigoAddUserResponse response) {
        saveActionIds.remove(response.getMessageId());
        User savedUser = response.getUser();
        newUser.setId(savedUser.getId());
        newUser.setPassword(null);
        setFieldsFromModel(newUser);
        addActiveServiceCall(R.string.progress_saving_changes, new UserUpdateInfoResponseHandler(newUser));
        saveUserPermissionsChangesIfRequired();
    }

    protected void onGroupsLoaded(GroupsGetListResponseHandler.PiwigoGetGroupsListRetrievedResponse response) {
        HashSet<Long> differences = SetUtils.differences(user.getGroups(), PiwigoUtils.toSetOfIds(response.getGroups()));
        if(!differences.isEmpty()) {
            Logging.log(Log.ERROR, getTag(), String.format("Rxd %1$s groups but asked for %2$s", user.getGroups().size(), response.getGroups().size()));
            throw new RuntimeException("error in group retrieval");
        }
        currentGroupMembership = new LinkedHashSet<>(response.getGroups());
        fillGroupMembershipField();
    }

    protected void onGroupMembershipAlbumPermissionsRetrieved(GroupGetPermissionsResponseHandler.PiwigoGroupPermissionsRetrievedResponse response) {
        // retrieve all those albums indirectly accessibly by this user.
        newIndirectAlbumPermissions = response.getAllowedAlbums();
        populateAlbumPermissionsList(getLatestDirectAlbumPermissions(), getLatestIndirectAlbumPermissions());
    }

    protected void onUserPermissionsRetrieved(UserGetPermissionsResponseHandler.PiwigoUserPermissionsResponse response) {
        // We're retrieving the current configuration
        currentDirectAlbumPermissions = response.getDirectlyAccessibleAlbumIds();
        currentIndirectAlbumPermissions = response.getIndirectlyAccessibleAlbumIds();
        if (availableGalleries != null) {
            populateAlbumPermissionsList(currentDirectAlbumPermissions, currentIndirectAlbumPermissions);
        }
    }

    protected void onGetSubGalleries(AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse response) {
        this.availableGalleries = response.getAlbumNames();
        if (currentIndirectAlbumPermissions != null) {
            populateAlbumPermissionsList(currentDirectAlbumPermissions, currentIndirectAlbumPermissions);
        }
    }

    protected void onUserDeleted(final UserDeleteResponseHandler.PiwigoDeleteUserResponse response) {
        EventBus.getDefault().post(new UserDeletedEvent(user));
        // return to previous screen
        if (isVisible()) {
            Logging.log(Log.INFO, TAG, "removing from activity immediately as user deleted piwigo response rxd");
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    protected synchronized void populateAlbumPermissionsList(HashSet<Long> initialSelection, HashSet<Long> indirectAlbumPermissions) {
        AlbumSelectionListAdapter adapter = (AlbumSelectionListAdapter) albumPermissionsField.getAdapter();
        if (adapter == null) {
            AlbumSelectionListAdapterPreferences adapterPreferences = new AlbumSelectionListAdapterPreferences(true, false, false, true, false);
            AlbumSelectionListAdapter availableItemsAdapter = new AlbumSelectionListAdapter(availableGalleries, indirectAlbumPermissions, adapterPreferences);
            availableItemsAdapter.linkToListView(albumPermissionsField, initialSelection, initialSelection);
        } else if (!CollectionUtils.equals(adapter.getSelectedItems(), initialSelection)
        || !CollectionUtils.equals(adapter.getIndirectlySelectedItems(), indirectAlbumPermissions)) {
            adapter.setSelectedItems(initialSelection);
            adapter.setIndirectlySelectedItems(indirectAlbumPermissions);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(GroupSelectionCompleteEvent groupSelectionCompleteEvent) {

        if (selectGroupsActionId == groupSelectionCompleteEvent.getActionId()) {
            newGroupMembership = new HashSet<>(groupSelectionCompleteEvent.getSelectedItems());
            fillGroupMembershipField();

            HashSet<Long> newGroupsMembership = new HashSet<>(groupSelectionCompleteEvent.getCurrentSelection());
            if (newGroupsMembership.size() > 0) {
                addActiveServiceCall(R.string.progress_reloading_album_permissions, new GroupGetPermissionsResponseHandler(newGroupsMembership));
            } else {
                newIndirectAlbumPermissions = new HashSet<>(0);
                populateAlbumPermissionsList(getLatestDirectAlbumPermissions(), getLatestIndirectAlbumPermissions());
            }
            selectGroupsActionId = -1;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AppLockedEvent event) {
        if (isVisible()) {
            Logging.log(Log.INFO, TAG, "removing from activity immediately as app locked event rxd");
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    private static class CustomPiwigoResponseListener<F extends UserFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends BasicPiwigoResponseListener<FUIH,F> {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (getParent().isVisible()) {
                if (!PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
                    Logging.log(Log.INFO, TAG, "removing from activity as not admin user");
                    getParent().getParentFragmentManager().popBackStack();
                    return;
                }
            }
            if (response instanceof UserPermissionsRemovedResponseHandler.PiwigoUserPermissionsRemovedResponse) {
                getParent().onUserPermissionsRemoved((UserPermissionsRemovedResponseHandler.PiwigoUserPermissionsRemovedResponse) response);
            } else if (response instanceof UserPermissionsAddResponseHandler.PiwigoUserPermissionsAddedResponse) {
                getParent().onUserPermissionsAdded((UserPermissionsAddResponseHandler.PiwigoUserPermissionsAddedResponse) response);
            } else if (response instanceof UserAddResponseHandler.PiwigoAddUserResponse) {
                getParent().onUserCreated((UserAddResponseHandler.PiwigoAddUserResponse) response);
            } else if (response instanceof UserUpdateInfoResponseHandler.PiwigoUpdateUserInfoResponse) {
                getParent().onUserSaved((UserUpdateInfoResponseHandler.PiwigoUpdateUserInfoResponse) response);
            } else if (response instanceof GroupsGetListResponseHandler.PiwigoGetGroupsListRetrievedResponse) {
                getParent().onGroupsLoaded((GroupsGetListResponseHandler.PiwigoGetGroupsListRetrievedResponse) response);
            } else if (response instanceof UserGetPermissionsResponseHandler.PiwigoUserPermissionsResponse) {
                getParent().onUserPermissionsRetrieved((UserGetPermissionsResponseHandler.PiwigoUserPermissionsResponse) response);
            } else if (response instanceof AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) {
                getParent().onGetSubGalleries((AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) response);
            } else if (response instanceof UserDeleteResponseHandler.PiwigoDeleteUserResponse) {
                getParent().onUserDeleted((UserDeleteResponseHandler.PiwigoDeleteUserResponse) response);
            } else if (response instanceof GroupGetPermissionsResponseHandler.PiwigoGroupPermissionsRetrievedResponse) {
                getParent().onGroupMembershipAlbumPermissionsRetrieved((GroupGetPermissionsResponseHandler.PiwigoGroupPermissionsRetrievedResponse) response);
            }
        }
    }

    private static class LayoutChangeListener implements View.OnLayoutChangeListener {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if (top != oldTop) {
                NestedScrollView scrollview = v.getRootView().findViewById(R.id.user_edit_scrollview);
                scrollview.fullScroll(View.FOCUS_UP);
                v.removeOnLayoutChangeListener(this);
            }
        }
    }
}
