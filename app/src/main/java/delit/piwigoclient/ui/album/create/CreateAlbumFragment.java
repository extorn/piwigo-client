package delit.piwigoclient.ui.album.create;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
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
import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumAddPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumCreateResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumSetStatusResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UsernamesGetListResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionNeededEvent;

/**
 * Created by gareth on 23/05/17.
 */
public class CreateAlbumFragment extends MyFragment<CreateAlbumFragment> {

    private static final String STATE_UPLOAD_TO_GALLERY = "uploadToGallery";
    private static final String STATE_NEW_GALLERY = "newAlbum";
    private static final String STATE_CREATE_GALLERY_CALL_ID = "createGalleryCallId";
    private static final String STATE_SET_GALLERY_PERMISSIONS_CALL_ID = "setGalleryPermissionsCallId";
    private static final String STATE_DELETE_GALLERY_CALL_ID = "deleteGalleryCallId";
    private static final String STATE_ACTION_ID = "actionId";
    private CategoryItemStub parentGallery;
    private TextView galleryNameEditField;
    private TextView galleryDescriptionEditField;
    private SwitchMaterial galleryCommentsAllowedSwitchField;
    private SwitchMaterial galleryIsPrivateSwitchField;
    private ArrayList<Group> selectedGroups;
    private ArrayList<Username> selectedUsernames;
    private TextView allowedGroupsTextView;
    private TextView allowedUsernamesTextView;

    private PiwigoGalleryDetails newAlbum;
    private long createGalleryMessageId;
    private long setGalleryPermissionsMessageId;
    private long deleteGalleryMessageId;
    private View privateGalleryConfigView;
    private HashSet<Long> userIdsInSelectedGroups;
    private int actionId;

    /**
     * Use this factory method to create a pkg instance of
     * this fragment using the provided parameters.
     *
     * @param uploadToGallery
     * @return A pkg instance of fragment UploadFragment.
     */
    public static CreateAlbumFragment newInstance(int actionId, CategoryItemStub uploadToGallery) {
        CreateAlbumFragment fragment = new CreateAlbumFragment();
        fragment.setTheme(R.style.Theme_App_EditPages);
        Bundle args = new Bundle();
        args.putParcelable(STATE_UPLOAD_TO_GALLERY, uploadToGallery);
        args.putInt(STATE_ACTION_ID, actionId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.createGallery_pageTitle);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(GroupSelectionCompleteEvent groupSelectionCompleteEvent) {

        if (getUiHelper().isTrackingRequest(groupSelectionCompleteEvent.getActionId())) {
            this.selectedGroups = new ArrayList<>(groupSelectionCompleteEvent.getSelectedItems());
            userIdsInSelectedGroups = null;
            setAllowedGroupsText();
        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(UsernameSelectionCompleteEvent usernameSelectionCompleteEvent) {

        if (getUiHelper().isTrackingRequest(usernameSelectionCompleteEvent.getActionId())) {
            this.selectedUsernames = new ArrayList<>(usernameSelectionCompleteEvent.getSelectedItems());
            setAllowedUsernamesText();
        }
    }

    private void setAllowedGroupsText() {
        if (selectedGroups != null && this.selectedGroups.size() > 0) {
            Collections.sort(selectedGroups, new Comparator<Group>() {
                @Override
                public int compare(Group o1, Group o2) {
                    return o1.getName().compareTo(o2.getName());
                }
            });
            StringBuilder sb = new StringBuilder();
            Iterator<Group> iter = this.selectedGroups.iterator();
            while (iter.hasNext()) {
                sb.append(iter.next().getName());
                if (iter.hasNext()) {
                    sb.append(", ");
                }
            }
            allowedGroupsTextView.setText(sb.toString());
        } else {
            allowedGroupsTextView.setText(getString(R.string.none_selected));
        }
    }

    private void setAllowedUsernamesText() {
        if (selectedUsernames != null && this.selectedUsernames.size() > 0) {
            Collections.sort(selectedUsernames, new Comparator<Username>() {
                @Override
                public int compare(Username o1, Username o2) {
                    return o1.getUsername().compareTo(o2.getUsername());
                }
            });
            StringBuilder sb = new StringBuilder();
            Iterator<Username> iter = this.selectedUsernames.iterator();
            while (iter.hasNext()) {
                sb.append(iter.next().getUsername());
                if (iter.hasNext()) {
                    sb.append(", ");
                }
            }
            allowedUsernamesTextView.setText(sb.toString());
        } else {
            allowedUsernamesTextView.setText(getString(R.string.none_selected));
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            parentGallery = args.getParcelable(STATE_UPLOAD_TO_GALLERY);
            actionId = args.getInt(STATE_ACTION_ID);
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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_UPLOAD_TO_GALLERY, parentGallery);
        outState.putParcelable(STATE_NEW_GALLERY, newAlbum);
        outState.putLong(STATE_CREATE_GALLERY_CALL_ID, createGalleryMessageId);
        outState.putLong(STATE_SET_GALLERY_PERMISSIONS_CALL_ID, setGalleryPermissionsMessageId);
        outState.putLong(STATE_DELETE_GALLERY_CALL_ID, deleteGalleryMessageId);
        outState.putInt(STATE_ACTION_ID, actionId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {

        if (savedInstanceState != null) {
            parentGallery = savedInstanceState.getParcelable(STATE_UPLOAD_TO_GALLERY);
            newAlbum = savedInstanceState.getParcelable(STATE_NEW_GALLERY);
            createGalleryMessageId = savedInstanceState.getLong(STATE_CREATE_GALLERY_CALL_ID);
            setGalleryPermissionsMessageId = savedInstanceState.getLong(STATE_SET_GALLERY_PERMISSIONS_CALL_ID);
            deleteGalleryMessageId = savedInstanceState.getLong(STATE_DELETE_GALLERY_CALL_ID);
            actionId = savedInstanceState.getInt(STATE_ACTION_ID);
        }
        // ensure the dialog boxes are setup
        super.onCreateView(inflater, container, savedInstanceState);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_album_create, container, false);

        AdView adView = view.findViewById(R.id.createGallery_adView);
        if (AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        galleryNameEditField = view.findViewById(R.id.createGallery_galleryName);
        galleryDescriptionEditField = view.findViewById(R.id.createGallery_galleryDescription);
        galleryCommentsAllowedSwitchField = view.findViewById(R.id.createGallery_galleryCommentsAllowed);
        galleryIsPrivateSwitchField = view.findViewById(R.id.createGallery_galleryIsPrivate);
        privateGalleryConfigView = view.findViewById(R.id.createGallery_privatePermissions);
        galleryIsPrivateSwitchField.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setGalleryPermissionsFieldsVisibility();
            }
        });
        ((TextView) view.findViewById(R.id.createGallery_parentGallery)).setText(parentGallery.getName());
        allowedGroupsTextView = view.findViewById(R.id.createGallery_permissions_allowedGroups);
        allowedGroupsTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                HashSet<Long> preselectedGroups = PiwigoUtils.toSetOfIds(selectedGroups);
                GroupSelectionNeededEvent groupSelectionNeededEvent = new GroupSelectionNeededEvent(true, true, preselectedGroups);
                getUiHelper().setTrackingRequest(groupSelectionNeededEvent.getActionId());
                EventBus.getDefault().post(groupSelectionNeededEvent);
            }
        });
        setAllowedGroupsText();
        allowedUsernamesTextView = view.findViewById(R.id.createGallery_permissions_allowedUsers);
        allowedUsernamesTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedGroups == null || selectedGroups.size() == 0 || userIdsInSelectedGroups != null) {
                    if (selectedGroups == null || selectedGroups.size() == 0) {
                        userIdsInSelectedGroups = new HashSet<>(0);
                    }

                    HashSet<Long> preselectedUserIds = PiwigoUtils.toSetOfIds(selectedUsernames);

                    UsernameSelectionNeededEvent usernameSelectionNeededEvent = new UsernameSelectionNeededEvent(true, true, userIdsInSelectedGroups, preselectedUserIds);
                    getUiHelper().setTrackingRequest(usernameSelectionNeededEvent.getActionId());
                    EventBus.getDefault().post(usernameSelectionNeededEvent);

                } else {
                    Set<Long> selectedGroupIds = PiwigoUtils.toSetOfIds(selectedGroups);
                    addActiveServiceCall(R.string.progress_loading_group_details, new UsernamesGetListResponseHandler(selectedGroupIds, 0, 100));
                }
            }
        });
        setAllowedUsernamesText();
        Button createGalleryButton = view.findViewById(R.id.createGallery_createGalleryButton);
        createGalleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                onCreateGallery();
            }
        });

        setGalleryPermissionsFieldsVisibility();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!isAllowedToCreateAlbum()) {
            // immediately leave this screen.
            getParentFragmentManager().popBackStack();
        }
    }

    private boolean isAllowedToCreateAlbum() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        boolean isAuthorisedNonAdminUser = sessionDetails != null && sessionDetails.isFullyLoggedIn() && sessionDetails.isUseCommunityPlugin();
        return !isAppInReadOnlyMode() && (isAuthorisedNonAdminUser || sessionDetails != null && sessionDetails.isAdminUser());
    }

    private void setGalleryPermissionsFieldsVisibility() {
        if (galleryIsPrivateSwitchField.isChecked()) {
            privateGalleryConfigView.setVisibility(View.VISIBLE);
        } else {
            privateGalleryConfigView.setVisibility(View.GONE);
        }
    }

    private void onCreateGallery() {
        String galleryName = galleryNameEditField.getText().toString();
        if (galleryName.isEmpty()) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_please_provide_an_album_name));
            return;
        }
        String galleryDescription = galleryDescriptionEditField.getText().toString();
        boolean userCommentsAllowed = galleryCommentsAllowedSwitchField.isChecked();
        boolean isPrivate = galleryIsPrivateSwitchField.isChecked();

        newAlbum = new PiwigoGalleryDetails(parentGallery, null, galleryName, galleryDescription, userCommentsAllowed, isPrivate);

        createGalleryMessageId = addActiveServiceCall(R.string.progress_creating_album, new AlbumCreateResponseHandler(newAlbum));
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    void onUsernamesRetrievedForSelectedGroups(UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse response) {
        if (response.getItemsOnPage() == response.getPageSize()) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_too_many_users_message));
        } else {
            ArrayList<Username> usernamesInGroups = response.getUsernames();
            userIdsInSelectedGroups = PiwigoUtils.toSetOfIds(usernamesInGroups);

            HashSet<Long> preselectedUsernames = PiwigoUtils.toSetOfIds(selectedUsernames);

            UsernameSelectionNeededEvent usernameSelectionNeededEvent = new UsernameSelectionNeededEvent(true, true, userIdsInSelectedGroups, preselectedUsernames);
            getUiHelper().setTrackingRequest(usernameSelectionNeededEvent.getActionId());
            EventBus.getDefault().post(usernameSelectionNeededEvent);
        }
    }

    public void onAlbumDeleted(AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse response) {
        getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.createGallery_alert_permissions_set_failed_gallery_deleted));
    }

    public void onAlbumCreated(AlbumCreateResponseHandler.PiwigoAlbumCreatedResponse response) {
        this.newAlbum = response.getAlbumDetails();
        if (newAlbum.isPrivate()) {

            HashSet<Long> allowedUsers = PiwigoUtils.toSetOfIds(selectedUsernames);
            HashSet<Long> allowedGroups = PiwigoUtils.toSetOfIds(selectedGroups);

            // don't need the call to be recursive since it is a leaf node already.
            setGalleryPermissionsMessageId = addActiveServiceCall(R.string.progress_setting_permissions, new AlbumAddPermissionsResponseHandler(newAlbum, allowedGroups, allowedUsers, false));

        } else {
            //TODO why are we doing this unnecessary call?
            setGalleryPermissionsMessageId = addActiveServiceCall(R.string.progress_setting_permissions, new AlbumSetStatusResponseHandler(newAlbum));
        }

    }

    public void onAlbumPermissionsAdded(AlbumAddPermissionsResponseHandler.PiwigoAddAlbumPermissionsResponse response) {

        newAlbum.setAllowedUsers(response.getUserIdsAffected());
        newAlbum.setAllowedGroups(response.getGroupIdsAffected());
        informInterestedParties();
    }

    public void onAlbumStatusAltered(AlbumSetStatusResponseHandler.PiwigoSetAlbumStatusResponse response) {
        informInterestedParties();
    }

    private void informInterestedParties() {
        getUiHelper().showDetailedMsg(R.string.alert_success, getString(R.string.alert_album_created));
        CategoryItem newItem = new CategoryItem(newAlbum.getGalleryId(), newAlbum.getGalleryName(), newAlbum.getGalleryDescription(), newAlbum.isPrivate(), null, 0, 0, 0, null);
        newItem.setParentageChain(newAlbum.getParentageChain());

        EventBus.getDefault().post(new AlbumCreatedEvent(actionId, parentGallery.getId(), newItem));
        List<Long> parentageChain = newAlbum.getParentageChain();
        if(!parentageChain.isEmpty()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(0), newAlbum.getGalleryId()));
            for (int i = 1; i < parentageChain.size(); i++) {
                EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(i), parentageChain.get(i - 1)));
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onAppLockedEvent(AppLockedEvent event) {
        if (isVisible()) {
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    private interface OnDialogCloseListener {
        void onDialogClose();
    }

    private static class CustomPiwigoResponseListener<S extends CreateAlbumFragment> extends BasicPiwigoResponseListener<S> {


        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse) {
                getParent().onAlbumDeleted((AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse) response);
            } else if (response instanceof AlbumCreateResponseHandler.PiwigoAlbumCreatedResponse) {
                getParent().onAlbumCreated((AlbumCreateResponseHandler.PiwigoAlbumCreatedResponse) response);
            } else if (response instanceof AlbumAddPermissionsResponseHandler.PiwigoAddAlbumPermissionsResponse) {
                getParent().onAlbumPermissionsAdded((AlbumAddPermissionsResponseHandler.PiwigoAddAlbumPermissionsResponse) response);
            } else if (response instanceof AlbumSetStatusResponseHandler.PiwigoSetAlbumStatusResponse) {
                getParent().onAlbumStatusAltered((AlbumSetStatusResponseHandler.PiwigoSetAlbumStatusResponse) response);
            } else if (response instanceof UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse) {
                getParent().onUsernamesRetrievedForSelectedGroups((UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse) response);
            }
        }

        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof PiwigoResponseBufferingHandler.RemoteErrorResponse) {
                getParent().onServerCallFailed(response.getMessageId());
            }
        }
    }

    void onServerCallFailed(long messageId) {
        if (messageId == createGalleryMessageId) {
            // error action failed and server state unchanged.
            getUiHelper().showDetailedMsg(R.string.alert_failure, getString(R.string.album_create_failed));
        } else if (messageId == setGalleryPermissionsMessageId) {
//                    deleteGalleryMessageId = PiwigoAccessService.startActionDeleteAlbum(newAlbum.getGalleryId(), getContext());
//                    callServer(deleteGalleryMessageId);
            getUiHelper().showDetailedMsg(R.string.alert_failure, getString(R.string.album_created_but_permissions_set_failed));
        } else if (messageId == deleteGalleryMessageId) {
//                    showDialogBox(R.string.alert_failure, getString(R.string.album_created_but_permissions_set_failed));
        }
    }
}
