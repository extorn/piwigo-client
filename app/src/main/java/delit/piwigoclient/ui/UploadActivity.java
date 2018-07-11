package delit.piwigoclient.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.ui.album.create.CreateAlbumFragment;
import delit.piwigoclient.ui.common.MyActivity;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;
import delit.piwigoclient.ui.events.StopActivityEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreateNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.ui.events.trackable.TrackableRequestEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionNeededEvent;
import delit.piwigoclient.ui.permissions.groups.GroupSelectFragment;
import delit.piwigoclient.ui.permissions.users.UsernameSelectFragment;
import delit.piwigoclient.ui.upload.UploadFragment;

/**
 * Created by gareth on 12/07/17.
 */

public class UploadActivity extends MyActivity {

    private static final int FILE_SELECTION_INTENT_REQUEST = 10101;
    private static final String STATE_FILE_SELECT_EVENT_ID = "fileSelectionEventId";
    private static final String STATE_STARTED_ALREADY = "startedAlready";
    private final HashMap<String, String> errors = new HashMap<>();
    private int fileSelectionEventId;
    private boolean startedWithPermissions;

    @Override
    public void onStart() {
        super.onStart();
        startedWithPermissions = false;
        getUiHelper().runWithExtraPermissions(this, Build.VERSION_CODES.BASE, Integer.MAX_VALUE, Manifest.permission.READ_EXTERNAL_STORAGE, getString(R.string.alert_read_permissions_needed_for_file_upload));
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        // Need to register here as the call is handled immediately if the permissions are already present.
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        this.setIntent(intent);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_FILE_SELECT_EVENT_ID, fileSelectionEventId);
        outState.putBoolean(STATE_STARTED_ALREADY, startedWithPermissions);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);

        if(savedInstanceState != null) {
            fileSelectionEventId = savedInstanceState.getInt(STATE_FILE_SELECT_EVENT_ID);
            startedWithPermissions = savedInstanceState.getBoolean(STATE_STARTED_ALREADY);
        } else {
            fileSelectionEventId = TrackableRequestEvent.getNextEventId();
        }

        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();

        if(!hasAgreedToEula() || connectionPrefs.getTrimmedNonNullPiwigoServerAddress(prefs, getApplicationContext()).isEmpty()) {
            createAndShowDialogWithExitOnClose(R.string.alert_error, R.string.alert_error_app_not_yet_configured);
        } else {
            setContentView(R.layout.activity_upload);
            addUploadingAsFieldsIfAppropriate();
            showUploadFragment(true, connectionPrefs);
        }
    }

    private void addUploadingAsFieldsIfAppropriate() {
        TextView uploadingAsLabelField = findViewById(R.id.upload_username_label);
        TextView uploadingAsField = findViewById(R.id.upload_username);
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if(sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails()) {
            uploadingAsField.setText(sessionDetails.getUsername());
            uploadingAsField.setVisibility(View.VISIBLE);
            uploadingAsLabelField.setVisibility(View.VISIBLE);
        } else {
            uploadingAsField.setVisibility(View.GONE);
            uploadingAsLabelField.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.upload_details);
        if(fragment instanceof UploadFragment && fragment.isAdded()) {
            finish();
        } else {
            super.onBackPressed();
        }
    }

    private void createAndShowDialogWithExitOnClose(int titleId, int messageId) {

        final int trackingRequestId = TrackableRequestEvent.getNextEventId();
        getUiHelper().setTrackingRequest(trackingRequestId);

        getUiHelper().showOrQueueDialogMessage(titleId, getString(messageId), new UIHelper.QuestionResultListener() {
            @Override
            public void onDismiss(AlertDialog dialog) {
                //exit the app.
                EventBus.getDefault().post(new StopActivityEvent(trackingRequestId));
            }

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                // don't care
            }
        });
    }

    @Subscribe
    public void onEvent(StopActivityEvent event) {
        if(getUiHelper().isTrackingRequest(event.getActionId())) {
            finish();
        }
    }

    private boolean isCurrentUserAuthorisedToUpload(PiwigoSessionDetails sessionDetails) {

        boolean isAdminUser = sessionDetails != null && sessionDetails.isAdminUser();
        boolean hasCommunityPlugin = sessionDetails != null && sessionDetails.isUseCommunityPlugin();
        return isAdminUser || hasCommunityPlugin;
    }

    private void showUploadFragment(boolean allowLogin, ConnectionPreferences.ProfilePreferences connectionPrefs) {

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
        long initialGalleryId = getIntent().getLongExtra("galleryId", 0);

        if(isCurrentUserAuthorisedToUpload(sessionDetails)) {
            Fragment f = UploadFragment.newInstance(initialGalleryId, fileSelectionEventId);
            removeFragmentsFromHistory(UploadFragment.class, true);
            showFragmentNow(f);
        } else if(allowLogin && sessionDetails == null || !sessionDetails.isFullyLoggedIn()) {
            runLogin(connectionPrefs);
        } else {
            createAndShowDialogWithExitOnClose(R.string.alert_error, R.string.alert_error_admin_user_required);
        }
    }

    private void runLogin(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        String serverUri = connectionPrefs.getPiwigoServerAddress(prefs, getApplicationContext());
        if(serverUri == null || serverUri.trim().isEmpty()) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_warning_no_server_url_specified));
        } else {
            getUiHelper().addActiveServiceCall(String.format(getString(R.string.logging_in_to_piwigo_pattern), serverUri), new LoginResponseHandler().invokeAsync(getApplicationContext(), connectionPrefs));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumCreatedEvent event) {
        getSupportFragmentManager().popBackStackImmediate();
    }

    private ArrayList<File> handleSentFiles() {
        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        try {

            if (Intent.ACTION_SEND.equals(action) && type != null) {
                if (type.startsWith("image/") || type.startsWith("video/")) {
                    return handleSendImage(intent); // Handle single image being sent
                }
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
                if (type.startsWith("image/") || type.startsWith("video/")) {
                    return handleSendMultipleImages(intent); // Handle multiple images being sent
                }
            }
            return new ArrayList<>();

        } finally {
            if (errors.size() > 0) {
                // build a dialog.

                StringBuilder sb = new StringBuilder();
                sb.append(getString(R.string.upload_unacceptable_files_prefix));
                sb.append('\n');
                sb.append('\n');
                for (Map.Entry<String, String> errorEntry : errors.entrySet()) {
                    String uri = errorEntry.getKey();
                    String err = errorEntry.getValue();
                    sb.append(getString(R.string.filelist_item_prefix));
                    sb.append(uri);
                    sb.append('\n');
                    sb.append(err);
                    sb.append('\n');
                    sb.append('\n');
                }

                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, sb.toString());

            }
        }
    }

    private ArrayList<File> handleSendMultipleImages(Intent intent) {
        ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        ArrayList<File> filesToUpload;
        if (imageUris != null) {
            filesToUpload = new ArrayList<>(imageUris.size());
            for(Uri imageUri : imageUris) {
                handleSentImage(imageUri, filesToUpload);
            }
        } else {
            filesToUpload = new ArrayList<>(0);
        }
        intent.putExtra(Intent.EXTRA_STREAM, (Parcelable[])null);
        return filesToUpload;
    }

    private ArrayList<File> handleSendImage(Intent intent) {
        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        ArrayList<File> filesToUpload;
        if (imageUri != null) {
            filesToUpload = new ArrayList<>(1);
            handleSentImage(imageUri, filesToUpload);
        } else {
            filesToUpload = new ArrayList<>(0);
        }
        intent.putExtra(Intent.EXTRA_STREAM, (Parcelable[])null);
        return filesToUpload;
    }

    private void handleSentImage(Uri imageUri, ArrayList<File> filesToUpload) {
        File f = new File(imageUri.getPath());
        if (!f.exists()) {
            f = new File(getRealPathFromURI(imageUri));
        }
        if(f.exists()) {
            filesToUpload.add(f);
        } else {
            errors.put(imageUri.toString(), "File does not exist");
        }
    }

    public String getRealPathFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = getApplicationContext().getContentResolver().query(contentUri, proj,
                null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String path = cursor.getString(column_index);
        cursor.close();
        return path;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(FileSelectionNeededEvent event) {
        Intent intent = new Intent(getBaseContext(), FileSelectActivity.class);
        intent.putExtra(FileSelectActivity.INTENT_DATA, event);
        setTrackedIntent(event.getActionId(), FILE_SELECTION_INTENT_REQUEST);
        startActivityForResult(intent, event.getActionId());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (getTrackedIntentType(requestCode) == FILE_SELECTION_INTENT_REQUEST) {
            if (resultCode == RESULT_OK) {
                int sourceEventId = data.getExtras().getInt(FileSelectActivity.INTENT_SOURCE_EVENT_ID);
                ArrayList<File> filesForUpload = (ArrayList<File>) data.getExtras().get(FileSelectActivity.INTENT_SELECTED_FILES);

                int eventId = requestCode;
                if(requestCode != fileSelectionEventId) {
                    // this is an unexpected event - need to ensure the upload fragment will pick it up.
                    eventId = Integer.MIN_VALUE;
                }
                FileSelectionCompleteEvent event = new FileSelectionCompleteEvent(eventId, filesForUpload);

                EventBus.getDefault().postSticky(event);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final AlbumCreateNeededEvent event) {
        CreateAlbumFragment fragment = CreateAlbumFragment.newInstance(event.getActionId(), event.getParentAlbum());
        showFragmentNow(fragment);
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final UsernameSelectionNeededEvent event) {
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences().selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        if(!event.isAllowEditing()) {
            prefs.readonly();
        }
        UsernameSelectFragment fragment = UsernameSelectFragment.newInstance(prefs, event.getActionId(), event.getIndirectSelection(),  event.getInitialSelection());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final GroupSelectionNeededEvent event) {
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences().selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        if(!event.isAllowEditing()) {
            prefs.readonly();
        }
        GroupSelectFragment fragment = GroupSelectFragment.newInstance(prefs, event.getActionId(), event.getInitialSelection());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PermissionsWantedResponse event) {
        if(getUiHelper().completePermissionsWantedRequest(event)) {
            int fileSelectionEventIdToUse = fileSelectionEventId;
            if(startedWithPermissions) {
                // already started up. Therefore the fileSelectionEventId is valid and linked to the faragment
            } else {
                startedWithPermissions = true;
                fileSelectionEventIdToUse = Integer.MIN_VALUE;
            }
            if(event.areAllPermissionsGranted()) {
                FileSelectionCompleteEvent evt = new FileSelectionCompleteEvent(fileSelectionEventIdToUse, handleSentFiles());
                EventBus.getDefault().postSticky(evt);
            } else {
                createAndShowDialogWithExitOnClose(R.string.alert_error, R.string.alert_error_unable_to_access_local_filesystem);
            }
        }
    }

//    private void showFragmentNow(Fragment f) {
//        showFragmentNow(f, true);
//    }
//
//    private void showFragmentNow(Fragment f, boolean addToBackstack) {
//        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
//        if(addToBackstack) {
//            tx.addToBackStack(null);
//        }
//        tx.replace(R.id.upload_details, f, f.getClass().getName()).commit();
//
//        addUploadingAsFieldsIfAppropriate();
//    }

    private void showFragmentNow(Fragment f) {
        showFragmentNow(f, false);
    }

    private void showFragmentNow(Fragment f, boolean addDuplicatePreviousToBackstack) {

        checkLicenceIfNeeded();

        Fragment lastFragment = getSupportFragmentManager().findFragmentById(R.id.upload_details);
        String lastFragmentName = "";
        if(lastFragment != null) {
            lastFragmentName = lastFragment.getTag();
        }
        if(!addDuplicatePreviousToBackstack && f.getClass().getName().equals(lastFragmentName)) {
            getSupportFragmentManager().popBackStackImmediate();
        }
        //TODO I've added code that clears stack when showing root album... is this "good enough"?
        //TODO - trying to prevent adding duplicates here. not sure it works right.
//        TODO maybe should be using current fragment classname when adding to backstack rather than one being replaced... hmmmm
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.addToBackStack(f.getClass().getName());
        tx.replace(R.id.upload_details, f, f.getClass().getName()).commit();

        addUploadingAsFieldsIfAppropriate();
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener() {
        return super.buildPiwigoResponseListener();
    }

    class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public <T extends PiwigoResponseBufferingHandler.Response> void onAfterHandlePiwigoResponse(T response) {
            if(response instanceof LoginResponseHandler.PiwigoOnLoginResponse) {
                showUploadFragment(false, ((LoginResponseHandler.PiwigoOnLoginResponse)response).getNewSessionDetails().getConnectionPrefs());
            } else {
                super.onAfterHandlePiwigoResponse(response);
            }
        }
    }
}
