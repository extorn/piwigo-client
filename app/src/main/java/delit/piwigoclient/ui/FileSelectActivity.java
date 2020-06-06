package delit.piwigoclient.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.business.OtherPreferences;
import delit.piwigoclient.ui.common.BackButtonHandler;
import delit.piwigoclient.ui.common.MyActivity;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.StatusBarChangeEvent;
import delit.piwigoclient.ui.events.StopActivityEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.ui.events.trackable.TrackableRequestEvent;
import delit.piwigoclient.ui.file.FolderItemViewAdapterPreferences;
import delit.piwigoclient.ui.file.LegacyRecyclerViewFolderItemSelectFragment;
import delit.piwigoclient.ui.file.RecyclerViewDocumentFileFolderItemSelectFragment;

/**
 * Created by gareth on 12/07/17.
 */

public class FileSelectActivity extends MyActivity {

    private static final String TAG = "FileSelAct";

    public static final String INTENT_SELECTED_FILES = "FileSelectActivity.selectedFiles";
//    public static final String INTENT_SOURCE_EVENT_ID = "FileSelectActivity.sourceEventId";
    public static final String ACTION_TIME_MILLIS = "FileSelectActivity.actionTimeMillis";
    public static String INTENT_DATA = "configData";

    @Override
    public void onStart() {
        super.onStart();
        if (!isShowUriBasedFileSelection()) {
            getUiHelper().runWithExtraPermissions(this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.Q, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_read_permissions_needed_for_file_upload));
        }
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

        if(BuildConfig.DEBUG) {
            BundleUtils.logSize("Current File Select Activity", outState);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);

        if (!hasAgreedToEula()) {
            finish();
        } else {
            setContentView(R.layout.activity_file_select);
            if (savedInstanceState == null) {
                showFileSelectFragment();
            }
        }
    }

    @Override
    protected String getDesiredLanguage(Context context) {
        return AppPreferences.getDesiredLanguage(getSharedPrefs(context), context);
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

    private void showFileSelectFragment() {

        FileSelectionNeededEvent event = getFileSelectionNeededEvent();

        FolderItemViewAdapterPreferences folderItemSelectPrefs = new FolderItemViewAdapterPreferences(event.isAllowFileSelection(), event.isAllowFolderSelection(), event.isMultiSelectAllowed());
        // custom settings
        folderItemSelectPrefs.withInitialFolder(event.getInitialFolder());
        folderItemSelectPrefs.withVisibleContent(event.getVisibleFileTypes(), event.getFileSortOrder());
        folderItemSelectPrefs.withVisibleMimeTypes(event.getVisibleMimeTypes());
        folderItemSelectPrefs.withSelectedUriPermissionsForConsumerId(event.getSelectedUriPermissionsForConsumerId());
        // basic settings
        folderItemSelectPrefs.selectable(event.isMultiSelectAllowed(), false);
        folderItemSelectPrefs.setInitialSelection(event.getInitialSelection());
        folderItemSelectPrefs.withShowFilenames(OtherPreferences.isShowFilenames(getSharedPrefs(), getApplicationContext()));

        removeFragmentsFromHistory(RecyclerViewDocumentFileFolderItemSelectFragment.class, true);

        int uniqueEventId = TrackableRequestEvent.getNextEventId();

        Fragment fragment;
        if (isShowUriBasedFileSelection()) {
            Uri uri = folderItemSelectPrefs.getInitialFolder();
            if (uri != null && Build.VERSION.SDK_INT > Build.VERSION_CODES.Q && "file".equals(uri.getScheme())) {
                folderItemSelectPrefs.withInitialFolder(null);
            }
            fragment = RecyclerViewDocumentFileFolderItemSelectFragment.newInstance(folderItemSelectPrefs, uniqueEventId);
        } else {
            fragment = LegacyRecyclerViewFolderItemSelectFragment.newInstance(folderItemSelectPrefs, uniqueEventId);
        }

        setTrackedIntent(uniqueEventId, event.getActionId());
        showFragmentNow(fragment);
    }

    private boolean isShowUriBasedFileSelection() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    private FileSelectionNeededEvent getFileSelectionNeededEvent() {
        FileSelectionNeededEvent event = getIntent().getParcelableExtra(INTENT_DATA);
        if(event == null) {
            event = new FileSelectionNeededEvent(true,true, true);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                event.withInitialFolder(Uri.fromFile(Environment.getExternalStorageDirectory()));
            }
        }
        return event;
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

    private void createAndShowDialogWithExitOnClose(@StringRes int titleId, @StringRes  int messageId) {

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

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(PermissionsWantedResponse event) {
        if (getUiHelper().completePermissionsWantedRequest(event)) {
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
            if (event.getSelectedFolderItems() != null) {
                result.putParcelableArrayListExtra(INTENT_SELECTED_FILES, event.getSelectedFolderItems());
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
