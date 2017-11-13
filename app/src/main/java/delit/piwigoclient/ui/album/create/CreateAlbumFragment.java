package delit.piwigoclient.ui.album.create;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatTextView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoAccessService;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.MyFragment;
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

public class CreateAlbumFragment extends MyFragment {

    public static final String STATE_UPLOAD_TO_GALLERY = "uploadToGallery";
    public static final String STATE_NEW_GALLERY = "newAlbum";
    public static final String STATE_CREATE_GALLERY_CALL_ID = "createGalleryCallId";
    public static final String STATE_SET_GALLERY_PERMISSIONS_CALL_ID = "setGalleryPermissionsCallId";
    public static final String STATE_DELETE_GALLERY_CALL_ID = "deleteGalleryCallId";
    private static final String STATE_ACTION_ID = "actionId";
    private CategoryItemStub parentGallery;
    private TextView galleryNameEditField;
    private TextView galleryDescriptionEditField;
    private Switch galleryCommentsAllowedSwitchField;
    private Switch galleryIsPrivateSwitchField;
    private ArrayList<Group> selectedGroups;
    private ArrayList<Username> selectedUsernames;
    private AppCompatTextView allowedGroupsTextView;
    private AppCompatTextView allowedUsernamesTextView;

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
        Bundle args = new Bundle();
        args.putSerializable(STATE_UPLOAD_TO_GALLERY, uploadToGallery);
        args.putInt(STATE_ACTION_ID, actionId);
        fragment.setArguments(args);
        return fragment;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(GroupSelectionCompleteEvent groupSelectionCompleteEvent) {

        if(getUiHelper().isTrackingRequest(groupSelectionCompleteEvent.getActionId())) {
            this.selectedGroups = new ArrayList<>(groupSelectionCompleteEvent.getSelectedItems());
            userIdsInSelectedGroups = null;
            setAllowedGroupsText();
        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UsernameSelectionCompleteEvent usernameSelectionCompleteEvent) {

        if(getUiHelper().isTrackingRequest(usernameSelectionCompleteEvent.getActionId())) {
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

    private void showDialogBox(int titleId, String message) {

        getUiHelper().showOrQueueDialogMessage(titleId, message, R.string.button_close);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            parentGallery = (CategoryItemStub)args.getSerializable(STATE_UPLOAD_TO_GALLERY);
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
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(STATE_UPLOAD_TO_GALLERY, parentGallery);
        outState.putSerializable(STATE_NEW_GALLERY, newAlbum);
        outState.putLong(STATE_CREATE_GALLERY_CALL_ID, createGalleryMessageId);
        outState.putLong(STATE_SET_GALLERY_PERMISSIONS_CALL_ID, setGalleryPermissionsMessageId);
        outState.putLong(STATE_DELETE_GALLERY_CALL_ID, deleteGalleryMessageId);
        outState.putInt(STATE_ACTION_ID, actionId);
        super.onSaveInstanceState(outState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (PiwigoSessionDetails.isFullyLoggedIn() && (!PiwigoSessionDetails.isAdminUser() || isAppInReadOnlyMode())) {
            // immediately leave this screen.
            getFragmentManager().popBackStack();
            return null;
        }
        if(savedInstanceState != null) {
            parentGallery = (CategoryItemStub) savedInstanceState.getSerializable(STATE_UPLOAD_TO_GALLERY);
            newAlbum = (PiwigoGalleryDetails) savedInstanceState.getSerializable(STATE_NEW_GALLERY);
            createGalleryMessageId = savedInstanceState.getLong(STATE_CREATE_GALLERY_CALL_ID);
            setGalleryPermissionsMessageId = savedInstanceState.getLong(STATE_SET_GALLERY_PERMISSIONS_CALL_ID);
            deleteGalleryMessageId = savedInstanceState.getLong(STATE_DELETE_GALLERY_CALL_ID);
            actionId = savedInstanceState.getInt(STATE_ACTION_ID);
        }
        // ensure the dialog boxes are setup
        super.onCreateView(inflater, container, savedInstanceState);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_create_new_gallery, container, false);

        AdView adView = (AdView)view.findViewById(R.id.createGallery_adView);
        if(AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            adView.loadAd(new AdRequest.Builder().build());
            adView.setVisibility(View.VISIBLE);
        } else {
            adView.setVisibility(View.GONE);
        }

        galleryNameEditField = (TextView) view.findViewById(R.id.createGallery_galleryName);
        galleryDescriptionEditField = (TextView) view.findViewById(R.id.createGallery_galleryDescription);
        galleryCommentsAllowedSwitchField = (Switch) view.findViewById(R.id.createGallery_galleryCommentsAllowed);
        galleryIsPrivateSwitchField = (Switch) view.findViewById(R.id.createGallery_galleryIsPrivate);
        privateGalleryConfigView = view.findViewById(R.id.createGallery_privatePermissions);
        galleryIsPrivateSwitchField.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setGalleryPermissionsFieldsVisibility();
            }
        });
        ((TextView) view.findViewById(R.id.createGallery_parentGallery)).setText(parentGallery.getName());
        allowedGroupsTextView = (AppCompatTextView) view.findViewById(R.id.createGallery_permissions_allowedGroups);
        allowedGroupsTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                HashSet<Long> preselectedGroups = null;
                if (selectedGroups != null) {
                    preselectedGroups = new HashSet<>(selectedGroups.size());
                    int i = 0;
                    for (Group g : selectedGroups) {
                        preselectedGroups.add(g.getId());
                    }
                }
                GroupSelectionNeededEvent groupSelectionNeededEvent = new GroupSelectionNeededEvent(true, true, preselectedGroups);
                getUiHelper().setTrackingRequest(groupSelectionNeededEvent.getActionId());
                EventBus.getDefault().post(groupSelectionNeededEvent);
            }
        });
        setAllowedGroupsText();
        allowedUsernamesTextView = (AppCompatTextView) view.findViewById(R.id.createGallery_permissions_allowedUsers);
        allowedUsernamesTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(selectedGroups == null || selectedGroups.size() == 0 || userIdsInSelectedGroups != null) {
                    if(selectedGroups == null || selectedGroups.size() == 0) {
                        userIdsInSelectedGroups = new HashSet<Long>(0);
                    }

                    HashSet<Long> preselectedUsernames = buildPreselectedUserIds(selectedUsernames);

                    UsernameSelectionNeededEvent usernameSelectionNeededEvent = new UsernameSelectionNeededEvent(true, true, userIdsInSelectedGroups, preselectedUsernames);
                    getUiHelper().setTrackingRequest(usernameSelectionNeededEvent.getActionId());
                    EventBus.getDefault().post(usernameSelectionNeededEvent);

                } else {
                    ArrayList<Long> selectedGroupIds = new ArrayList<Long>(selectedGroups.size());
                    for(Group g : selectedGroups) {
                        selectedGroupIds.add(g.getId());
                    }
                    addActiveServiceCall(R.string.progress_loading_group_details,PiwigoAccessService.startActionGetUsernamesList(selectedGroupIds, 0, 100, getContext()));
                }
            }
        });
        setAllowedUsernamesText();
        Button createGalleryButton = (Button) view.findViewById(R.id.createGallery_createGalleryButton);
        createGalleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                onCreateGallery();
            }
        });

        setGalleryPermissionsFieldsVisibility();

        return view;
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

    private void setGalleryPermissionsFieldsVisibility() {
        if (galleryIsPrivateSwitchField.isChecked()) {
            privateGalleryConfigView.setVisibility(View.VISIBLE);
        } else {
            privateGalleryConfigView.setVisibility(View.GONE);
        }
    }

    private void onCreateGallery() {
        String galleryName = galleryNameEditField.getText().toString();
        if(galleryName.isEmpty()) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_please_provide_an_album_name));
            return;
        }
        String galleryDescription = galleryDescriptionEditField.getText().toString();
        boolean userCommentsAllowed = galleryCommentsAllowedSwitchField.isChecked();
        boolean isPrivate = galleryIsPrivateSwitchField.isChecked();

        newAlbum = new PiwigoGalleryDetails(parentGallery, null, galleryName, galleryDescription, userCommentsAllowed, isPrivate);

        createGalleryMessageId = PiwigoAccessService.startActionAddGallery(newAlbum, getContext());
        addActiveServiceCall(R.string.progress_creating_album, createGalleryMessageId);
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof PiwigoResponseBufferingHandler.PiwigoAlbumDeletedResponse) {
                onAlbumDeleted((PiwigoResponseBufferingHandler.PiwigoAlbumDeletedResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoAlbumCreatedResponse) {
                onAlbumCreated((PiwigoResponseBufferingHandler.PiwigoAlbumCreatedResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoAddAlbumPermissionsResponse) {
                onAlbumPermissionsAdded((PiwigoResponseBufferingHandler.PiwigoAddAlbumPermissionsResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoSetAlbumStatusResponse) {
                onAlbumStatusAltered((PiwigoResponseBufferingHandler.PiwigoSetAlbumStatusResponse) response);
            } else if(response instanceof PiwigoResponseBufferingHandler.PiwigoGetUsernamesListResponse) {
                onUsernamesRetrievedForSelectedGroups((PiwigoResponseBufferingHandler.PiwigoGetUsernamesListResponse)response);
            }
        }

        @Override
        protected void handlePiwigoUnexpectedReplyErrorResponse(PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse msg) {

            switch (msg.getRequestOutcome()) {
                case PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_SUCCESS:
                    showOrQueueDialogMessage(R.string.alert_title_error_handling_response, getString(R.string.alert_error_handling_response_prefix) + " (" + msg.getRawResponse() + ")");
                    break;
                default:
                    super.handlePiwigoUnexpectedReplyErrorResponse(msg);
            }
        }

        @Override
        protected void handlePiwigoServerErrorResponse(PiwigoResponseBufferingHandler.PiwigoServerErrorResponse msg) {
            if (msg.getMessageId() == createGalleryMessageId) {
                // error action failed and server state unchanged.
                showDialogBox(R.string.alert_failure, getString(R.string.album_create_failed));
            } else if (msg.getMessageId() == setGalleryPermissionsMessageId) {
                deleteGalleryMessageId = PiwigoAccessService.startActionDeleteGallery(newAlbum.getGalleryId(), getContext());
                addActiveServiceCall(deleteGalleryMessageId);
            } else if (msg.getMessageId() == deleteGalleryMessageId) {
                showDialogBox(R.string.alert_failure, getString(R.string.album_created_but_permissions_set_failed));
            } else {
                super.handlePiwigoServerErrorResponse(msg);
            }
        }
    }

    private void onUsernamesRetrievedForSelectedGroups(PiwigoResponseBufferingHandler.PiwigoGetUsernamesListResponse response) {
        if(response.getItemsOnPage() == response.getPageSize()) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_too_many_users_message));
        } else {
            ArrayList<Username> usernamesInGroups = response.getUsernames();
            userIdsInSelectedGroups = buildPreselectedUserIds(usernamesInGroups);

            HashSet<Long> preselectedUsernames = buildPreselectedUserIds(selectedUsernames);

            UsernameSelectionNeededEvent usernameSelectionNeededEvent = new UsernameSelectionNeededEvent(true, true, userIdsInSelectedGroups, preselectedUsernames);
            getUiHelper().setTrackingRequest(usernameSelectionNeededEvent.getActionId());
            EventBus.getDefault().post(usernameSelectionNeededEvent);
        }
    }

    public void onAlbumDeleted(PiwigoResponseBufferingHandler.PiwigoAlbumDeletedResponse response) {
        getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.createGallery_alert_permissions_set_failed_gallery_deleted));
    }

    public void onAlbumCreated(PiwigoResponseBufferingHandler.PiwigoAlbumCreatedResponse response) {
        this.newAlbum = response.getAlbumDetails();
        if (newAlbum.isPrivate()) {

            HashSet<Long> allowedUsers = new HashSet<>(selectedUsernames == null ? 0 : selectedUsernames.size());
            HashSet<Long> allowedGroups = new HashSet<>(selectedGroups == null ? 0 : selectedGroups.size());
            if (selectedUsernames != null) {

                for (Username username : selectedUsernames) {
                    allowedUsers.add(username.getId());
                }
            }
            if (selectedGroups != null) {

                for (Group group : selectedGroups) {
                    allowedGroups.add(group.getId());
                }
            }

            // don't need the call to be recursive since it is a leaf node already.
            setGalleryPermissionsMessageId = PiwigoAccessService.startActionAddAlbumPermissions(newAlbum, allowedGroups, allowedUsers, false, getContext());
            addActiveServiceCall(setGalleryPermissionsMessageId);
        } else {
            //TODO why are we doing this unnecessary call?
            setGalleryPermissionsMessageId = PiwigoAccessService.startActionSetGalleryStatus(newAlbum, getContext());
            addActiveServiceCall(setGalleryPermissionsMessageId);
        }

    }

    public void onAlbumPermissionsAdded(PiwigoResponseBufferingHandler.PiwigoAddAlbumPermissionsResponse response) {

        newAlbum.setAllowedUsers(response.getUserIdsAffected());
        newAlbum.setAllowedGroups(response.getGroupIdsAffected());

        showDialogBox(R.string.alert_success, getString(R.string.alert_album_created));
        EventBus.getDefault().post(new AlbumCreatedEvent(actionId, parentGallery.getId(), newAlbum.getGalleryId()));
        for(Long parentId : newAlbum.getParentageChain()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(parentId));
        }
    }

    public void onAlbumStatusAltered(PiwigoResponseBufferingHandler.PiwigoSetAlbumStatusResponse response) {

        showDialogBox(R.string.alert_success, getString(R.string.alert_album_created));
        EventBus.getDefault().post(new AlbumCreatedEvent(actionId, parentGallery.getId(), newAlbum.getGalleryId()));
        for(Long parentId : newAlbum.getParentageChain()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(parentId));
        }
    }

    private interface OnDialogCloseListener {
        void onDialogClose();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAppLockedEvent(AppLockedEvent event) {
        if(isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }
}
