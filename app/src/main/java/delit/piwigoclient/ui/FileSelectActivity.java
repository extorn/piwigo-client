package delit.piwigoclient.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;

import delit.libs.ui.util.BundleUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.OtherPreferences;
import delit.piwigoclient.ui.common.BackButtonHandler;
import delit.piwigoclient.ui.common.MyActivity;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.StopActivityEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.ui.events.trackable.TrackableRequestEvent;
import delit.piwigoclient.ui.file.FolderItemViewAdapterPreferences;
import delit.piwigoclient.ui.file.RecyclerViewFolderItemSelectFragment;

/**
 * Created by gareth on 12/07/17.
 */

public class FileSelectActivity extends MyActivity {
    public static final String INTENT_SELECTED_FILES = "FileSelectActivity.selectedFiles";
//    public static final String INTENT_SOURCE_EVENT_ID = "FileSelectActivity.sourceEventId";
    private static final String STATE_STARTED_ALREADY = "FileSelectActivity.startedAlready";
    public static final String ACTION_TIME_MILLIS = "FileSelectActivity.actionTimeMillis";
    public static String INTENT_DATA = "configData";
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
        outState.putBoolean(STATE_STARTED_ALREADY, startedWithPermissions);

        if(BuildConfig.DEBUG) {
            BundleUtils.logSize("Current File Select Activity", outState);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);

        if (savedInstanceState != null) {
            startedWithPermissions = savedInstanceState.getBoolean(STATE_STARTED_ALREADY);
        }

        if (!hasAgreedToEula()) {
            finish();
        } else {
            setContentView(R.layout.activity_file_select);
            showFileSelectFragment();
        }
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.app_content);
        if (fragment instanceof BackButtonHandler && fragment.isAdded()) {
            boolean sinkEvent = ((BackButtonHandler) fragment).onBackButton();
            if (!sinkEvent) {
                finish();
            }
        } else {
            super.onBackPressed();
        }
    }

    private void showFileSelectFragment() {

        FileSelectionNeededEvent event = (FileSelectionNeededEvent) getIntent().getSerializableExtra(INTENT_DATA);
        if(event == null) {
            event = new FileSelectionNeededEvent(true,true, true);
        }
        String initialFolder = event.getInitialFolder();

        File f = new File(initialFolder);
        while (!f.exists()) {
            f = f.getParentFile();
        }
        initialFolder = f.getAbsolutePath();

        FolderItemViewAdapterPreferences prefs = new FolderItemViewAdapterPreferences(event.isAllowFileSelection(), event.isAllowFolderSelection(), event.isMultiSelectAllowed());
        // custom settings
        prefs.withInitialFolder(initialFolder);
        prefs.withVisibleContent(event.getVisibleFileTypes(), event.getFileSortOrder());
        prefs.withVisibleMimeTypes(event.getVisibleMimeTypes());
        // basic settings
        prefs.selectable(event.isMultiSelectAllowed(), false);
        prefs.setInitialSelection(event.getInitialSelection());
        prefs.withShowFilenames(OtherPreferences.isShowFilenames(getSharedPrefs(), getApplicationContext()));

        removeFragmentsFromHistory(RecyclerViewFolderItemSelectFragment.class, true);

        int uniqueEventId = TrackableRequestEvent.getNextEventId();

        RecyclerViewFolderItemSelectFragment fragment = RecyclerViewFolderItemSelectFragment.newInstance(prefs, uniqueEventId);
        setTrackedIntent(uniqueEventId, event.getActionId());
        showFragmentNow(fragment);
    }

    private void showFragmentNow(Fragment f) {
        showFragmentNow(f, false);
    }


    private void showFragmentNow(Fragment f, boolean addDuplicatePreviousToBackstack) {

        checkLicenceIfNeeded();

        Fragment lastFragment = getSupportFragmentManager().findFragmentById(R.id.app_content);
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
        tx.replace(R.id.app_content, f, f.getClass().getName()).commit();
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PermissionsWantedResponse event) {
        if (getUiHelper().completePermissionsWantedRequest(event)) {
            if (startedWithPermissions) {
                // already started up. Therefore the fileSelectionEventId is valid and linked to the fragment
            } else {
                startedWithPermissions = true;
            }
            if (!event.areAllPermissionsGranted()) {
                createAndShowDialogWithExitOnClose(R.string.alert_error, R.string.alert_error_unable_to_access_local_filesystem);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(FileSelectionCompleteEvent event) {
        int sourceEventId = getTrackedIntentType(event.getActionId());
        if (sourceEventId >= 0) {
            Intent result = this.getIntent();
//            result.putExtra(INTENT_SOURCE_EVENT_ID, sourceEventId);
            result.putExtra(ACTION_TIME_MILLIS, event.getActionTimeMillis());
            if (event.getSelectedFiles() != null) {
                BundleUtils.putFileArrayListExtra(result,INTENT_SELECTED_FILES, event.getSelectedFiles());
                setResult(Activity.RESULT_OK, result);
            } else {
                setResult(Activity.RESULT_CANCELED, result);
            }
            finish();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(StopActivityEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            finish();
        }
    }


}
