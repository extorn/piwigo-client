package delit.piwigoclient.ui;

import android.Manifest;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.MimeTypeFilter;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.appbar.AppBarLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.ui.album.create.CreateAlbumFragment;
import delit.piwigoclient.ui.album.drillDownSelect.CategoryItemViewAdapterPreferences;
import delit.piwigoclient.ui.album.drillDownSelect.RecyclerViewCategoryItemSelectFragment;
import delit.piwigoclient.ui.common.ActivityUIHelper;
import delit.piwigoclient.ui.common.MyActivity;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.NavigationItemSelectEvent;
import delit.piwigoclient.ui.events.StatusBarChangeEvent;
import delit.piwigoclient.ui.events.StopActivityEvent;
import delit.piwigoclient.ui.events.ToolbarEvent;
import delit.piwigoclient.ui.events.ViewJobStatusDetailsEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreateNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.ui.events.trackable.TrackableRequestEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionNeededEvent;
import delit.piwigoclient.ui.permissions.groups.GroupSelectFragment;
import delit.piwigoclient.ui.permissions.users.UsernameSelectFragment;
import delit.piwigoclient.ui.upload.UploadFragment;
import delit.piwigoclient.ui.upload.UploadJobStatusDetailsFragment;

/**
 * Created by gareth on 12/07/17.
 */

public class UploadActivity extends MyActivity {

    private static final String TAG = "uploadActivity";
    private static final int OPEN_GOOGLE_PLAY_INTENT_REQUEST = 10102;
    private static final String STATE_FILE_SELECT_EVENT_ID = "fileSelectionEventId";
    private static final String INTENT_DATA_CURRENT_ALBUM = "currentAlbum";
    private final HashMap<String, String> errors = new HashMap<>();
    private int fileSelectionEventId;
    private Toolbar toolbar;
    private AppBarLayout appBar;


    public static Intent buildIntent(Context context, CategoryItemStub currentAlbum) {
        Intent intent = new Intent("delit.piwigoclient.MANUAL_UPLOAD", null, context.getApplicationContext(), UploadActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.putExtra(INTENT_DATA_CURRENT_ALBUM, currentAlbum);
        return intent;
    }

    @Override
    public void onStart() {
        super.onStart();

        if(Intent.ACTION_SEND.equals(getIntent().getAction())
            || Intent.ACTION_SEND_MULTIPLE.equals(getIntent().getAction())) {
            getUiHelper().runWithExtraPermissions(this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.Q, Manifest.permission.READ_EXTERNAL_STORAGE, getString(R.string.alert_read_permissions_needed_for_file_upload));
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        // Need to register here as the call is handled immediately if the permissions are already present.
        EventBus.getDefault().register(this);
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

        if(BuildConfig.DEBUG) {
            BundleUtils.logSize("Current Upload Activity", outState);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);

        if (savedInstanceState != null) {
            fileSelectionEventId = savedInstanceState.getInt(STATE_FILE_SELECT_EVENT_ID);
        } else {
            fileSelectionEventId = TrackableRequestEvent.getNextEventId();
        }

        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
        boolean canContinue = true;
        if(!hasAgreedToEula()) {
            Log.e(TAG, "User agreement to EULA could not be found");
            canContinue = false;
        }
        if(connectionPrefs.getTrimmedNonNullPiwigoServerAddress(prefs, getApplicationContext()).isEmpty()) {
            Log.e(TAG, "No PIWIGO server address found in settings : " + connectionPrefs.getAbsoluteProfileKey(prefs, getApplicationContext()));
            canContinue = false;
        }
        if (!canContinue) {
            createAndShowDialogWithExitOnClose(R.string.alert_error, R.string.alert_error_app_not_yet_configured);
        } else {
            setContentView(R.layout.activity_upload);

//            DrawerLayout drawer = findViewById(R.id.drawer_layout);
//
//            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
//                drawer.setFitsSystemWindows(true);
//            }

            toolbar = findViewById(R.id.toolbar);
            appBar = findViewById(R.id.appbar);
            if(BuildConfig.DEBUG) {
                toolbar.setTitle(getString(R.string.upload_page_title) + " ("+BuildConfig.FLAVOR+')');
            } else {
                toolbar.setTitle(R.string.upload_page_title);
            }
            setSupportActionBar(toolbar);
            if (savedInstanceState == null) { // the fragment will be created automatically from the fragment manager state if there is state :-)
                showUploadFragment(true, connectionPrefs);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
        int result = googleApi.isGooglePlayServicesAvailable(getApplicationContext());
        if (!BuildConfig.DEBUG && result != ConnectionResult.SUCCESS) {
            if (googleApi.isUserResolvableError(result)) {
                Dialog d = googleApi.getErrorDialog(this, result, OPEN_GOOGLE_PLAY_INTENT_REQUEST);
                d.setOnDismissListener(dialog -> {
                    if (!BuildConfig.DEBUG) {
                        finish();
                    }
                });
                d.show();
            } else {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.unsupported_device), new UIHelper.QuestionResultAdapter<ActivityUIHelper<UploadActivity>>(getUiHelper()) {
                    @Override
                    public void onDismiss(AlertDialog dialog) {
                        if (!BuildConfig.DEBUG) {
                            finish();
                        }
                    }
                });
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public final void onNavigationItemSelected(NavigationItemSelectEvent event) {
        // Handle navigation view item clicks here.
        int id = event.navigationitemSelected;

        switch (id) {
//            case R.id.nav_upload:
//                showUpload();
//                break;
            case R.id.nav_groups:
                showGroups();
                break;
//            case R.id.nav_tags:
//                showTags();
//                break;
            case R.id.nav_users:
                showUsers();
                break;
            case R.id.nav_top_tips:
                showTopTips();
                break;
            case R.id.nav_gallery:
                showGallery();
                break;
//            case R.id.nav_favorites:
//                showFavorites();
//                break;
//            case R.id.nav_about:
//                showAboutFragment();
//                break;
//            case R.id.nav_oss_licences:
//                showLicencesFragment();
//                break;
            case R.id.nav_settings:
                showPreferences();
                break;
//            case R.id.nav_eula:
//                showEula();
//                break;
            default:
                onNavigationItemSelected(event, id);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
    }

    private void showGroups() {
        try {
            startActivity(MainActivity.buildShowGroupsIntent(this));
        } catch(ActivityNotFoundException e) {
            Crashlytics.logException(e);
        }
    }

    private void showUsers() {
        try {
            startActivity(MainActivity.buildShowUsersIntent(this));
        } catch(ActivityNotFoundException e) {
            Crashlytics.logException(e);
        }
    }

    private void showTopTips() {
        try {
            startActivity(MainActivity.buildShowTopTipsIntent(this));
        } catch(ActivityNotFoundException e) {
            Crashlytics.logException(e);
        }
    }

    private void showPreferences() {
        try {
            startActivity(PreferencesActivity.buildIntent(this));
        } catch(ActivityNotFoundException e) {
            Crashlytics.logException(e);
        }
    }

    private void showGallery() {
        try {
            startActivity(MainActivity.buildShowGalleryIntent(this));
        } catch(ActivityNotFoundException e) {
            Crashlytics.logException(e);
        }
    }

    protected void onNavigationItemSelected(NavigationItemSelectEvent event, @IdRes int itemId) {
    }

    @Override
    protected String getDesiredLanguage(Context context) {
        return AppPreferences.getDesiredLanguage(getSharedPrefs(context), context);
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            return; // don't mess with the status bar
        }

        View v = getWindow().getDecorView();
        v.setFitsSystemWindows(!hasFocus);

        if (hasFocus) {
            DisplayUtils.setUiFlags(this, AppPreferences.isAlwaysShowNavButtons(prefs, this), AppPreferences.isAlwaysShowStatusBar(prefs, this));
            Crashlytics.log(Log.ERROR, TAG, "hiding status bar!");
        } else {
            Crashlytics.log(Log.ERROR, TAG, "showing status bar!");
        }

        v.requestApplyInsets();
        EventBus.getDefault().post(new StatusBarChangeEvent(!hasFocus));
    }

    private void createAndShowDialogWithExitOnClose(int titleId, int messageId) {

        final int trackingRequestId = TrackableRequestEvent.getNextEventId();
        getUiHelper().setTrackingRequest(trackingRequestId);

        getUiHelper().showOrQueueDialogMessage(titleId, getString(messageId), new OnStopActivityAction(getUiHelper(), trackingRequestId));
    }

    private static class OnStopActivityAction extends UIHelper.QuestionResultAdapter {
        private final int trackingRequestId;

        public OnStopActivityAction(UIHelper uiHelper, int trackingRequestId) {
            super(uiHelper);
            this.trackingRequestId = trackingRequestId;
        }

        @Override
        public void onDismiss(AlertDialog dialog) {
            //exit the app.
            EventBus.getDefault().post(new StopActivityEvent(trackingRequestId));
        }
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
            LoginResponseHandler handler = new LoginResponseHandler();
            getUiHelper().addActiveServiceCall(getString(R.string.logging_in_to_piwigo_pattern, serverUri), handler.invokeAsync(getApplicationContext(), connectionPrefs), handler.getTag());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumCreatedEvent event) {
        getSupportFragmentManager().popBackStackImmediate();
    }

    private ArrayList<Uri> handleSentFiles() {
        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        try {

            if (Intent.ACTION_SEND.equals(action) && type != null) {
                if (!type.startsWith("*/")) {
                    return handleSendMultipleImages(intent); // Handle single image being sent
                } else {
                    getUiHelper().showDetailedMsg(R.string.alert_error, getString(R.string.alert_error_unable_to_handle_shared_mime_type, type));
                }
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
                // type is */* if it contains a mixture of file types
                if (type.equals("*/*") || null != MimeTypeFilter.matches(type, new String[]{"image/*","video/*","application/pdf","application/zip"})) {
                    return handleSendMultipleImages(intent); // Handle multiple images being sent
                } else {
                    getUiHelper().showDetailedMsg(R.string.alert_error, getString(R.string.alert_error_unable_to_handle_shared_mime_type, type));
                }
            } else if ("application/octet-stream".equals(type)) {
                return handleSendMultipleImages(intent); // Handle multiple images being sent
//                getUiHelper().showDetailedMsg(R.string.alert_error, getString(R.string.alert_error_unable_to_handle_shared_mime_type, type));
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

    private ArrayList<Uri> handleSendMultipleImages(Intent intent) {

        ArrayList<Uri> filesToUpload;

        ClipData clipData = intent.getClipData();
        if(clipData != null && clipData.getItemCount() > 0) {
            // process clip data
            filesToUpload = new ArrayList<>(clipData.getItemCount());
//            String mimeType = clipData.getDescription().getMimeTypeCount() == 1 ? clipData.getDescription().getMimeType(0) : null;
            for(int i = 0; i < clipData.getItemCount(); i++) {
                ClipData.Item sharedItem = clipData.getItemAt(i);
                Uri sharedUri = sharedItem.getUri();
                if (sharedUri != null) {
                    String mimeType = getContentResolver().getType(sharedUri);
                    if (sharedUri != null) {
                        handleSentImage(sharedUri, mimeType, filesToUpload);
                    }
                }
            }
            intent.setClipData(null);
        } else {
            // process the extra stream data
            ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            String[] mimeTypes = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES);
            }
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
                        handleSentImage(imageUri, mimeType, filesToUpload);
                    }
                }
            } else {
                String mimeType = intent.getType();
                Uri imageUri = intent.getData();
                if(imageUri != null) {
                    filesToUpload = new ArrayList<>(1);
                    handleSentImage(imageUri, mimeType, filesToUpload);
                } else {
                    filesToUpload = new ArrayList<>(0);
                }
            }
            intent.removeExtra(Intent.EXTRA_STREAM);
        }
        return filesToUpload;
    }

    private void handleSentImage(Uri sharedUri, String mimeType, ArrayList<Uri> filesToUpload) {
//        DocumentFile documentFile = DocumentFile.fromSingleUri(this, imageUri);
//        //TODO check stuff manually here. Maybe need to do something sensible - perhaps don't need local copy now!
//        Uri sharedFile = IOUtils.saveUriToDevice(this, imageUri, mimeType, BasePiwigoUploadService.getTmpUploadFolder(this));
//
//        if (sharedFile == null) {
//            if (MimeTypeFilter.matches(mimeType,"video/*")) {
//                getUiHelper().showDetailedMsg(R.string.alert_error, R.string.unable_to_handle_shared_uri_missing_content_description_information);
////                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
////                    try {
////                        MediaExtractor mExtractor = new MediaExtractor();
////                        mExtractor.setDataSource(sharedFile.getAbsolutePath());
////                        PersistableBundle mediaMetrics = mExtractor.getMetrics();
////                        String containerFomat = mediaMetrics.getString(MediaExtractor.MetricsConstants.FORMAT);
//////                        String containerMimeType = mediaMetrics.getString(MediaExtractor.MetricsConstants.MIME_TYPE);
////                        File renamedTo = new File(sharedFile.getParent(), sharedFile.getName() + "." + containerFomat);
////                        sharedFile.renameTo(renamedTo);
////                        sharedFile = renamedTo;
////
////                    } catch (IOException e) {
////                        Crashlytics.log(Log.ERROR, TAG, "Error retrieving correct file extension for shared media file!");
////                    }
////                } else {
////                    if(mimeType.equals("video/mpeg")) {
////                        // guess
////                        File renamedTo = new File(sharedFile.getParent(), sharedFile.getName() + ".mp4");
////                        sharedFile.renameTo(renamedTo);
////                        sharedFile = renamedTo;
////                    }
////                }
//            }
//        }
        filesToUpload.add(sharedUri);
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
        if(toolbar == null) {
            Log.e(TAG, "Cannot set title. Toolbar not initialised yet");
            return;
        }
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
                ArrayList<Uri> sentFiles = handleSentFiles();
                if(sentFiles != null) {
                    // this activity was invoked from another application
                    FileSelectionCompleteEvent evt = new FileSelectionCompleteEvent(fileSelectionEventId, -1).withFiles(sentFiles);
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

        Crashlytics.log(Log.DEBUG, TAG, String.format("showing fragment: %1$s (%2$s)", f.getTag(), f.getClass().getName()));
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
