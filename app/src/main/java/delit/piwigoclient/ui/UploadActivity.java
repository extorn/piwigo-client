package delit.piwigoclient.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;

import androidx.annotation.FloatRange;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.MimeTypeFilter;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.AppBarLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;

import delit.libs.core.util.Logging;
import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.CustomToolbar;
import delit.libs.util.progress.ProgressListener;
import delit.libs.util.progress.TaskProgressTracker;
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
import delit.piwigoclient.ui.permissions.groups.GroupRecyclerViewAdapter;
import delit.piwigoclient.ui.permissions.groups.GroupSelectFragment;
import delit.piwigoclient.ui.permissions.users.UsernameRecyclerViewAdapter;
import delit.piwigoclient.ui.permissions.users.UsernameSelectFragment;
import delit.piwigoclient.ui.upload.UploadFragment;
import delit.piwigoclient.ui.upload.UploadJobStatusDetailsFragment;

/**
 * Created by gareth on 12/07/17.
 */

public class UploadActivity extends MyActivity<UploadActivity> {

    private static final String TAG = "uploadActivity";
    private static final int OPEN_GOOGLE_PLAY_INTENT_REQUEST = 10102;
    private static final String STATE_FILE_SELECT_EVENT_ID = "fileSelectionEventId";
    private static final String INTENT_DATA_CURRENT_ALBUM = "currentAlbum";
    private int fileSelectionEventId;
    private CustomToolbar toolbar;
    private AppBarLayout appBar;
    private Intent lastIntent;

    public UploadActivity() {
        super(R.layout.activity_upload);
    }


    public static Intent buildIntent(Context context, CategoryItemStub currentAlbum) {
        Intent intent = new Intent("delit.piwigoclient.MANUAL_UPLOAD", null, context.getApplicationContext(), UploadActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(INTENT_DATA_CURRENT_ALBUM, currentAlbum);
        return intent;
    }

    private void checkForSentFiles(@Nullable Intent intent) {
        if(intent == null) {
            return;
        }
        if(Intent.ACTION_SEND.equals(intent.getAction())
                || Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            getUiHelper().runWithExtraPermissions(this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.N_MR1, Manifest.permission.READ_EXTERNAL_STORAGE, getString(R.string.alert_read_permissions_needed_for_file_upload));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if(lastIntent != intent) {
            lastIntent = intent;
            checkForSentFiles(lastIntent);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent currentIntent = getIntent();
        if(lastIntent == null || currentIntent != lastIntent) {
            lastIntent = currentIntent;
        }

        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
        if (!isCurrentUserAuthorisedToUpload(sessionDetails)) {
            if(sessionDetails == null || !sessionDetails.isFullyLoggedIn()) {
                runLogin(connectionPrefs);
            } else {
                createAndShowDialogWithExitOnClose(R.string.alert_error, R.string.alert_error_admin_user_required);
            }
        }
        checkForSentFiles(lastIntent);
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

        if (savedInstanceState != null) {
            fileSelectionEventId = savedInstanceState.getInt(STATE_FILE_SELECT_EVENT_ID);
        } else {
            fileSelectionEventId = TrackableRequestEvent.getNextEventId();
        }

        toolbar = findViewById(R.id.toolbar);
        appBar = findViewById(R.id.appbar);
        if(BuildConfig.DEBUG) {
            toolbar.setTitle(getString(R.string.upload_page_title) + " ("+BuildConfig.FLAVOR+')');
        } else {
            toolbar.setTitle(R.string.upload_page_title);
        }
        setSupportActionBar(toolbar);


        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
        boolean canContinue = true;
        if(hasNotAcceptedEula()) {
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
            if (savedInstanceState == null) { // the fragment will be created automatically from the fragment manager state if there is state :-)
                showUploadFragment(connectionPrefs);
            }
        }
    }



    @Subscribe(threadMode = ThreadMode.MAIN)
    public final void onNavigationItemSelected(NavigationItemSelectEvent event) {
        // Handle navigation view item clicks here.
        int id = event.navigationitemSelected;

        if (id == R.id.nav_groups) {
            showGroups();
        } else if (id == R.id.nav_users) {
            showUsers();
        } else if (id == R.id.nav_top_tips) {
            showTopTips();
        } else if (id == R.id.nav_gallery) {
            showGallery();
        } else if (id == R.id.nav_settings) {
            showPreferences();
        } else {
            onNavigationItemSelected(event, id);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
    }

    private void showGroups() {
        try {
            startActivity(MainActivity.buildShowGroupsIntent(this));
        } catch(ActivityNotFoundException e) {
            Logging.recordException(e);
        }
    }

    private void showUsers() {
        try {
            startActivity(MainActivity.buildShowUsersIntent(this));
        } catch(ActivityNotFoundException e) {
            Logging.recordException(e);
        }
    }

    private void showTopTips() {
        try {
            startActivity(MainActivity.buildShowTopTipsIntent(this));
        } catch(ActivityNotFoundException e) {
            Logging.recordException(e);
        }
    }

    private void showPreferences() {
        try {
            startActivity(PreferencesActivity.buildIntent(this));
        } catch(ActivityNotFoundException e) {
            Logging.recordException(e);
        }
    }

    private void showGallery() {
        try {
            startActivity(MainActivity.buildShowGalleryIntent(this));
        } catch(ActivityNotFoundException e) {
            Logging.recordException(e);
        }
    }

    protected void onNavigationItemSelected(NavigationItemSelectEvent event, @IdRes int itemId) {
    }

    @Override
    protected String getDesiredLanguage(Context context) {
        return AppPreferences.getDesiredLanguage(getSharedPrefs(context), context);
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
            Logging.log(Log.ERROR, TAG, "hiding status bar!");
        } else {
            Logging.log(Log.ERROR, TAG, "showing status bar!");
        }

        v.requestApplyInsets();
        EventBus.getDefault().post(new StatusBarChangeEvent(!hasFocus));
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



    void showUploadFragment(ConnectionPreferences.ProfilePreferences connectionPrefs) {

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
        CategoryItemStub currentAlbum = getIntent().getParcelableExtra(INTENT_DATA_CURRENT_ALBUM);

        if (isCurrentUserAuthorisedToUpload(sessionDetails)) {
            Fragment f = UploadFragment.newInstance(currentAlbum, fileSelectionEventId);
            removeFragmentsFromHistory(UploadFragment.class);
            showFragmentNow(f);
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
        Logging.log(Log.INFO, TAG, "removing from activity immediately on album created");
        getSupportFragmentManager().popBackStackImmediate();
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
        if (event.isHandled()) {
            return;
        }
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
        UsernameRecyclerViewAdapter.UsernameRecyclerViewAdapterPreferences prefs = new UsernameRecyclerViewAdapter.UsernameRecyclerViewAdapterPreferences().selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        if (!event.isAllowEditing()) {
            prefs.readonly();
        }
        UsernameSelectFragment fragment = UsernameSelectFragment.newInstance(prefs, event.getActionId(), event.getIndirectSelection(), event.getInitialSelection());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final GroupSelectionNeededEvent event) {
        GroupRecyclerViewAdapter.GroupViewAdapterPreferences prefs = new GroupRecyclerViewAdapter.GroupViewAdapterPreferences().selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        if (!event.isAllowEditing()) {
            prefs.readonly();
        }
        GroupSelectFragment fragment = GroupSelectFragment.newInstance(prefs, event.getActionId(), event.getInitialSelection());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ToolbarEvent event) {
        if(!this.equals(event.getActivity())) {
            return;
        }
        if(toolbar == null) {
            Logging.log(Log.ERROR, TAG, "Cannot set title. Toolbar not initialised yet");
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
                new SharedFilesIntentProcessingTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, lastIntent);
            } else {
                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    createAndShowDialogWithExitOnClose(R.string.alert_error, R.string.alert_error_unable_to_access_local_filesystem);
                } else {
                    Logging.log(Log.ERROR, TAG, "Unexpected warning about file permissions");
                    createAndShowDialogWithExitOnClose(R.string.alert_error, R.string.alert_error_unable_to_access_local_filesystem_scoped_storage);
                }
            }
        }
    }


    private static class SharedFilesIntentProcessingTask extends OwnedSafeAsyncTask<UploadActivity, Intent, Integer, Void> implements ProgressListener {


        public SharedFilesIntentProcessingTask(UploadActivity parent) {
            super(parent);
            withContext(parent);
        }

        @Override
        protected void onPreExecuteSafely() {
            super.onPreExecuteSafely();
            getOwner().getUiHelper().showProgressIndicator(getOwner().getString(R.string.progress_importing_files),0);
        }

        @Override
        protected Void doInBackgroundSafely(Intent[] objects) {
            Intent intent = objects[0];
            ArrayList<Uri> sentFiles = handleSentFiles(intent);
            if(sentFiles != null) {
                // this activity was invoked from another application
                FileSelectionCompleteEvent evt = new FileSelectionCompleteEvent(getOwner().fileSelectionEventId, -1).withFiles(sentFiles);
                EventBus.getDefault().postSticky(evt);
            }
            return null;
        }

        @Override
        protected void onProgressUpdateSafely(Integer... progress) {
            super.onProgressUpdateSafely(progress);
            try {
                getOwner().getUiHelper().showProgressIndicator(getOwner().getString(R.string.progress_importing_files),progress[0]);
            } catch(NullPointerException e) {
                Logging.log(Log.ERROR, TAG, "Unable to report progress. Likely not attached");
                Logging.recordException(e);
            }

        }

        @Override
        protected void onPostExecuteSafely(Void params) {
            getOwner().getUiHelper().hideProgressIndicator();
        }

        @Override
        public void onProgress(@FloatRange(from = 0, to = 1) double percent) {
            publishProgress((int) Math.rint(percent * 100));
        }

        @Override
        public double getUpdateStep() {
            return 0.01;//1%
        }

        private ArrayList<Uri> handleSentFiles(Intent intent) {
            // Get intent, action and MIME type
            String action = intent.getAction();
            String type = intent.getType();

            if (Intent.ACTION_SEND.equals(action) && type != null) {
                if (!type.startsWith("*/")) {
                    return handleSendMultipleImages(intent); // Handle single image being sent
                } else {
                    getOwner().getUiHelper().showDetailedMsg(R.string.alert_error, getContext().getString(R.string.alert_error_unable_to_handle_shared_mime_type, type));
                }
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
                // type is */* if it contains a mixture of file types
                if (type.equals("*/*") || null != MimeTypeFilter.matches(type, new String[]{"image/*","video/*","application/pdf","application/zip"})) {
                    return handleSendMultipleImages(intent); // Handle multiple images being sent
                } else {
                    getOwner().getUiHelper().showDetailedMsg(R.string.alert_error, getContext().getString(R.string.alert_error_unable_to_handle_shared_mime_type, type));
                }
            } else if ("application/octet-stream".equals(type)) {
                return handleSendMultipleImages(intent); // Handle multiple images being sent
//                getUiHelper().showDetailedMsg(R.string.alert_error, getString(R.string.alert_error_unable_to_handle_shared_mime_type, type));
            }
            return null;
        }

        private ArrayList<Uri> handleSendMultipleImages(Intent intent) {

            ArrayList<Uri> filesToUpload;

            ClipData clipData = intent.getClipData();
            if(clipData != null && clipData.getItemCount() > 0) {
                // process clip data
                filesToUpload = new ArrayList<>(clipData.getItemCount());
//            String mimeType = clipData.getDescription().getMimeTypeCount() == 1 ? clipData.getDescription().getMimeType(0) : null;
                TaskProgressTracker fileImportTracker = new TaskProgressTracker(clipData.getItemCount(), this);
                for(int i = 0; i < clipData.getItemCount(); i++) {
                    ClipData.Item sharedItem = clipData.getItemAt(i);
                    Uri sharedUri = sharedItem.getUri();
                    if (sharedUri != null) {
                        String mimeType = getContext().getContentResolver().getType(sharedUri);
                        handleSentImage(sharedUri, mimeType, filesToUpload);
                    }
                    fileImportTracker.incrementWorkDone(1);
                }
                intent.setClipData(null);
            } else {
                // process the extra stream data
                try {
                    ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    filesToUpload = handleSendMultipleImages(intent, imageUris);
                    intent.removeExtra(Intent.EXTRA_STREAM);
                } catch(ClassCastException e) {
                    Uri sharedUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    filesToUpload = new ArrayList<>(1);
                    if (sharedUri != null) {
                        String mimeType = getContext().getContentResolver().getType(sharedUri);
                        handleSentImage(sharedUri, mimeType, filesToUpload);
                    }
                    intent.removeExtra(Intent.EXTRA_STREAM);
                }
            }
            return filesToUpload;
        }

        private ArrayList<Uri> handleSendMultipleImages(Intent intent, ArrayList<Uri> imageUris) {
            ArrayList<Uri> filesToUpload;
            String[] mimeTypes = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mimeTypes = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES);
            }
            if (imageUris != null) {
                filesToUpload = new ArrayList<>(imageUris.size());
                TaskProgressTracker fileImportTracker = new TaskProgressTracker(imageUris.size(), this);
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
                    fileImportTracker.incrementWorkDone(1);
                }
            } else {
                String mimeType = intent.getType();
                Uri imageUri = intent.getData();
                if(imageUri != null) {
                    TaskProgressTracker fileImportTracker = new TaskProgressTracker(1, this);
                    filesToUpload = new ArrayList<>(1);
                    handleSentImage(imageUri, mimeType, filesToUpload);
                    fileImportTracker.markComplete();
                } else {
                    filesToUpload = new ArrayList<>(0);
                }
            }
            intent.removeExtra(Intent.EXTRA_STREAM);
            return filesToUpload;
        }

        private void handleSentImage(Uri sharedUri, String mimeType, ArrayList<Uri> filesToUpload) {
            filesToUpload.add(sharedUri);
        }
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener() {
        return new CustomPiwigoResponseListener();
    }

    private static class CustomPiwigoResponseListener<A extends UploadActivity> extends BasicPiwigoResponseListener<A> {
        public A getParent() {
            return super.getParent();
        }

        @Override
        public <T extends PiwigoResponseBufferingHandler.Response> void onAfterHandlePiwigoResponse(T response) {
            if (response instanceof LoginResponseHandler.PiwigoOnLoginResponse) {
                if (((LoginResponseHandler.PiwigoOnLoginResponse) response).getNewSessionDetails() != null) {
                    getParent().showUploadFragment(((LoginResponseHandler.PiwigoOnLoginResponse) response).getNewSessionDetails().getConnectionPrefs());
                } else {
                    getParent().createAndShowDialogWithExitOnClose(R.string.alert_error, R.string.alert_error_admin_user_required);
                }
            } else {
                super.onAfterHandlePiwigoResponse(response);
            }
        }
    }

    private static class ActivityUIHelperUploadActivityQuestionResultAdapter extends UIHelper.QuestionResultAdapter<ActivityUIHelper<UploadActivity>, UploadActivity> implements Parcelable {


        public ActivityUIHelperUploadActivityQuestionResultAdapter(ActivityUIHelper<UploadActivity> uiHelper) {
            super(uiHelper);
        }

        @Override
        public void onDismiss(AlertDialog dialog) {
            if (!BuildConfig.DEBUG) {
                getParent().finish();
            }
        }
    }
}
