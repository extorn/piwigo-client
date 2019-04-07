package delit.piwigoclient.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.drew.lang.StreamUtil;
import com.google.android.material.appbar.AppBarLayout;

import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.ui.album.drillDownSelect.CategoryItemViewAdapterPreferences;
import delit.piwigoclient.ui.album.drillDownSelect.RecyclerViewCategoryItemSelectFragment;
import delit.piwigoclient.ui.album.create.CreateAlbumFragment;
import delit.piwigoclient.ui.common.MyActivity;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.common.util.BundleUtils;
import delit.piwigoclient.ui.events.StopActivityEvent;
import delit.piwigoclient.ui.events.ToolbarEvent;
import delit.piwigoclient.ui.events.ViewJobStatusDetailsEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreateNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.ui.events.trackable.TrackableRequestEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionNeededEvent;
import delit.piwigoclient.ui.permissions.groups.GroupSelectFragment;
import delit.piwigoclient.ui.permissions.users.UsernameSelectFragment;
import delit.piwigoclient.ui.upload.UploadFragment;
import delit.piwigoclient.ui.upload.UploadJobStatusDetailsFragment;
import delit.piwigoclient.util.DisplayUtils;
import delit.piwigoclient.util.IOUtils;

/**
 * Created by gareth on 12/07/17.
 */

public class UploadActivity extends MyActivity {

    private static final String TAG = "uploadActivity";
    private static final int FILE_SELECTION_INTENT_REQUEST = 10101;
    private static final String STATE_FILE_SELECT_EVENT_ID = "fileSelectionEventId";
    private static final String STATE_STARTED_ALREADY = "startedAlready";
    private static final String INTENT_DATA_CURRENT_ALBUM = "currentAlbum";
    private final HashMap<String, String> errors = new HashMap<>();
    private int fileSelectionEventId;
    private boolean startedWithPermissions;
    private Toolbar toolbar;
    private AppBarLayout appBar;

    public static Intent buildIntent(Context context, CategoryItemStub currentAlbum) {
        Intent intent = new Intent(context, UploadActivity.class);
        intent.putExtra(INTENT_DATA_CURRENT_ALBUM, currentAlbum);
        return intent;
    }

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
        outState.putBoolean(STATE_STARTED_ALREADY, startedWithPermissions);
        outState.putInt(STATE_FILE_SELECT_EVENT_ID, fileSelectionEventId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);

        if (savedInstanceState != null) {
            fileSelectionEventId = savedInstanceState.getInt(STATE_FILE_SELECT_EVENT_ID);
            startedWithPermissions = savedInstanceState.getBoolean(STATE_STARTED_ALREADY);
        } else {
            fileSelectionEventId = TrackableRequestEvent.getNextEventId();
        }

        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();

        if (!hasAgreedToEula() || connectionPrefs.getTrimmedNonNullPiwigoServerAddress(prefs, getApplicationContext()).isEmpty()) {
            createAndShowDialogWithExitOnClose(R.string.alert_error, R.string.alert_error_app_not_yet_configured);
        } else {
            setContentView(R.layout.activity_upload);
            toolbar = findViewById(R.id.toolbar);
            appBar = findViewById(R.id.appbar);
            if(BuildConfig.DEBUG) {
                toolbar.setTitle(getString(R.string.upload_page_title) + " ("+BuildConfig.FLAVOR+')');
            } else {
                toolbar.setTitle(R.string.upload_page_title);
            }
            setSupportActionBar(toolbar);
            showUploadFragment(true, connectionPrefs);
        }
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_view);
        if (fragment instanceof UploadFragment && fragment.isAdded()) {
            finish();
        } else {
            super.onBackPressed();
        }
    }

    private void createAndShowDialogWithExitOnClose(int titleId, int messageId) {

        final int trackingRequestId = TrackableRequestEvent.getNextEventId();
        getUiHelper().setTrackingRequest(trackingRequestId);

        getUiHelper().showOrQueueDialogMessage(titleId, getString(messageId), new UIHelper.QuestionResultAdapter(getUiHelper()) {
            @Override
            public void onDismiss(AlertDialog dialog) {
                //exit the app.
                EventBus.getDefault().post(new StopActivityEvent(trackingRequestId));
            }
        });
    }

    @Subscribe
    public void onEvent(StopActivityEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
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
        CategoryItemStub currentAlbum = getIntent().getParcelableExtra(INTENT_DATA_CURRENT_ALBUM);

        if (isCurrentUserAuthorisedToUpload(sessionDetails)) {
            Fragment f = UploadFragment.newInstance(currentAlbum, fileSelectionEventId);
            removeFragmentsFromHistory(UploadFragment.class, true);
            showFragmentNow(f);
        } else if (allowLogin && sessionDetails == null || !sessionDetails.isFullyLoggedIn()) {
            runLogin(connectionPrefs);
        } else {
            createAndShowDialogWithExitOnClose(R.string.alert_error, R.string.alert_error_admin_user_required);
        }
    }

    private void runLogin(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        String serverUri = connectionPrefs.getPiwigoServerAddress(prefs, getApplicationContext());
        if (serverUri == null || serverUri.trim().isEmpty()) {
            getUiHelper().showDetailedMsg(R.string.alert_error, getString(R.string.alert_warning_no_server_url_specified));
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
                    return handleSendMultipleImages(intent); // Handle single image being sent
                }
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
                // type is */* if it contains a mixture of file types
                if (type.equals("*/*") || type.startsWith("image/") || type.startsWith("video/")) {
                    return handleSendMultipleImages(intent); // Handle multiple images being sent
                }
            }
            return null;

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

        ArrayList<File> filesToUpload;

        ClipData clipData = intent.getClipData();
        if(clipData != null && clipData.getItemCount() > 0) {
            // process clip data
            filesToUpload = new ArrayList<>(clipData.getItemCount());
            boolean hasImages = clipData.getDescription().hasMimeType("image/*");
            boolean hasVideos = clipData.getDescription().hasMimeType("video/*");
            boolean mightHaveImagesOrVideos = clipData.getDescription().hasMimeType("*/*");
            String mimeType = clipData.getDescription().getMimeTypeCount() == 1 ? clipData.getDescription().getMimeType(0) : null;
            for(int i = 0; i < clipData.getItemCount(); i++) {
                ClipData.Item sharedItem = clipData.getItemAt(i);
                Uri sharedUri = sharedItem.getUri();
                if(hasImages || hasVideos || mightHaveImagesOrVideos) {
                    String fileExt = (!mightHaveImagesOrVideos && mimeType != null) ? MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) : null;
                    if (sharedUri != null) {
                        handleSentImage(sharedUri, fileExt, filesToUpload);
                    }
                } else {
                    Crashlytics.log("Unable to process received data with mime type : " + mimeType);
                }
            }
        } else {
            // process the extra stream data
            ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            String[] mimeTypes = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES);
            if (imageUris != null) {
                filesToUpload = new ArrayList<>(imageUris.size());
                int i = 0;
                for (Uri imageUri : imageUris) {
                    String mimeType;
                    if(mimeTypes != null && mimeTypes.length >= i) {
                        mimeType = mimeTypes[i];
                        i++;
                    } else {
                        mimeType = intent.getType();
                    }
                    if (imageUri != null) {
                        String fileExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                        handleSentImage(imageUri, fileExt, filesToUpload);
                    }
                }
            } else {
                String mimeType = intent.getType();
                Uri imageUri = intent.getData();
                if(imageUri != null) {
                    String fileExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                    filesToUpload = new ArrayList<>(1);
                    handleSentImage(imageUri, fileExt, filesToUpload);
                } else {
                    filesToUpload = new ArrayList<>(0);
                }
            }
            intent.putExtra(Intent.EXTRA_STREAM, (Parcelable[]) null);
        }
        return filesToUpload;
    }

    private void handleSentImage(@NonNull Uri imageUri, String fileExt, ArrayList<File> filesToUpload) {
        File f = new File(imageUri.getPath());
        if (!f.exists()) {
            try {
                f = new File(getRealPathFromURI(imageUri));
            } catch (IllegalArgumentException e) {
                // thrown when the URI is not a place in data. (lets just create a tmp file)
                try {
                    File tmp_upload_folder = new File(getApplicationContext().getExternalCacheDir(), "piwigo-upload");
                    tmp_upload_folder.mkdir();
                    String filename = imageUri.getLastPathSegment();
                    if(fileExt != null && !filename.endsWith("." + fileExt)) {
                        filename += '.' + fileExt;
                    }
                    f = new File(tmp_upload_folder, filename);
                    int i = 0;
                    while(f.exists()) {
                        i++;
                        int insertAt = imageUri.getLastPathSegment().lastIndexOf('.');
                        filename = imageUri.getLastPathSegment().substring(0, insertAt);
                        String ext = imageUri.getLastPathSegment().substring(insertAt);
                        f = new File(tmp_upload_folder, filename + '_' + i + ext);
                    }
                    f.deleteOnExit();
                    IOUtils.write(getApplicationContext().getContentResolver().openInputStream(imageUri), f);
                } catch(FileNotFoundException e1) {
                    throw new RuntimeException("Unable to write shared data to a temporary file");
                } catch(IOException e1) {
                    throw new RuntimeException("Unable to write shared data to a temporary file");
                }
            }
        }
        if (f.exists() && f.isFile()) {
            filesToUpload.add(f);
        } else {
            if(!f.isDirectory()) {
                errors.put(imageUri.toString(), "File does not exist");
            }
        }
    }

    public String getRealPathFromURI(Uri contentUri) {
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = getApplicationContext().getContentResolver().query(contentUri, proj,
                null, null, null);

        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String path = cursor.getString(column_index);
        cursor.close();
        return path;
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ExpandingAlbumSelectionNeededEvent event) {
//        ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences prefs = new ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences();
//        AlbumSelectExpandableFragment f = AlbumSelectExpandableFragment.newInstance(prefs, event.getActionId(), event.getInitialSelection());
        CategoryItemViewAdapterPreferences prefs = new CategoryItemViewAdapterPreferences();
        if(event.isAllowEditing()) {
            prefs.selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        }
        if(event.getInitialRoot() != null) {
            prefs.withInitialRoot(new CategoryItemStub("???", event.getInitialRoot()));
        } else {
            prefs.withInitialRoot(CategoryItemStub.ROOT_GALLERY);
        }
        prefs.setAllowItemAddition(true);
        prefs.withInitialSelection(event.getInitialSelection());
        RecyclerViewCategoryItemSelectFragment f = RecyclerViewCategoryItemSelectFragment.newInstance(prefs, event.getActionId());
        showFragmentNow(f);
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
//                int sourceEventId = data.getExtras().getInt(FileSelectActivity.INTENT_SOURCE_EVENT_ID);
                long actionTimeMillis = data.getExtras().getLong(FileSelectActivity.ACTION_TIME_MILLIS);
                ArrayList<File> filesForUpload = BundleUtils.getFileArrayList(data.getExtras(), FileSelectActivity.INTENT_SELECTED_FILES);

                int eventId = requestCode;
                FileSelectionCompleteEvent event = new FileSelectionCompleteEvent(eventId, filesForUpload, actionTimeMillis);
                // post sticky because the fragment to handle this event may not yet be created and registered with the event bus.
                EventBus.getDefault().postSticky(event);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final ViewJobStatusDetailsEvent event) {
        UploadJobStatusDetailsFragment fragment = UploadJobStatusDetailsFragment.newInstance(event.getJob());
        showFragmentNow(fragment);
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final AlbumCreateNeededEvent event) {
        CreateAlbumFragment fragment = CreateAlbumFragment.newInstance(event.getActionId(), event.getParentAlbum());
        showFragmentNow(fragment);
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final UsernameSelectionNeededEvent event) {
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences().selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        if (!event.isAllowEditing()) {
            prefs.readonly();
        }
        UsernameSelectFragment fragment = UsernameSelectFragment.newInstance(prefs, event.getActionId(), event.getIndirectSelection(), event.getInitialSelection());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final GroupSelectionNeededEvent event) {
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences().selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        if (!event.isAllowEditing()) {
            prefs.readonly();
        }
        GroupSelectFragment fragment = GroupSelectFragment.newInstance(prefs, event.getActionId(), event.getInitialSelection());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ToolbarEvent event) {
        toolbar.setTitle(event.getTitle());
        if(event.isExpandToolbarView()) {
            ((AppBarLayout) toolbar.getParent()).setExpanded(true, true);
        } else if(event.isContractToolbarView()) {
            ((AppBarLayout) toolbar.getParent()).setExpanded(false, true);
        }
        appBar.setEnabled(event.getTitle()!= null);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(PermissionsWantedResponse event) {
        if (getUiHelper().completePermissionsWantedRequest(event)) {
            if (event.areAllPermissionsGranted()) {
                ArrayList<File> sentFiles = handleSentFiles();
                if(sentFiles != null) {
                    // this activity was invoked from another application
                    FileSelectionCompleteEvent evt = new FileSelectionCompleteEvent(fileSelectionEventId, sentFiles, -1);
                    EventBus.getDefault().postSticky(evt);
                }
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

        Crashlytics.log(Log.DEBUG, TAG, "showing fragment: " + f.getTag());
        checkLicenceIfNeeded();

        DisplayUtils.hideKeyboardFrom(getApplicationContext(), getWindow());

        Fragment lastFragment = getSupportFragmentManager().findFragmentById(R.id.main_view);
        String lastFragmentName = "";
        if (lastFragment != null) {
            lastFragmentName = lastFragment.getTag();
        }
        if (!addDuplicatePreviousToBackstack && f.getClass().getName().equals(lastFragmentName)) {
            getSupportFragmentManager().popBackStackImmediate();
        }
        //TODO I've added code that clears stack when showing root album... is this "good enough"?
        //TODO - trying to prevent adding duplicates here. not sure it works right.
//        TODO maybe should be using current fragment classname when adding to backstack rather than one being replaced... hmmmm
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.addToBackStack(f.getClass().getName());
        tx.replace(R.id.main_view, f, f.getClass().getName()).commit();
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener() {
        return new CustomPiwigoResponseListener();
    }

    class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public <T extends PiwigoResponseBufferingHandler.Response> void onAfterHandlePiwigoResponse(T response) {
            if (response instanceof LoginResponseHandler.PiwigoOnLoginResponse) {
                if (((LoginResponseHandler.PiwigoOnLoginResponse) response).getNewSessionDetails() != null) {
                    Log.e("UploadActivity", "Retrieved user login success response");
                    showUploadFragment(false, ((LoginResponseHandler.PiwigoOnLoginResponse) response).getNewSessionDetails().getConnectionPrefs());
                } else {
                    createAndShowDialogWithExitOnClose(R.string.alert_error, R.string.alert_error_admin_user_required);
                }
            } else {
                super.onAfterHandlePiwigoResponse(response);
            }
        }
    }
}
