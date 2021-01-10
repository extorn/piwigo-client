package delit.piwigoclient.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.business.OtherPreferences;
import delit.piwigoclient.ui.common.BaseMyActivity;
import delit.piwigoclient.ui.common.MyActivity;
import delit.piwigoclient.ui.events.StatusBarChangeEvent;
import delit.piwigoclient.ui.events.StopActivityEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.ui.events.trackable.TrackableRequestEvent;
import delit.piwigoclient.ui.file.FolderItem;
import delit.piwigoclient.ui.file.FolderItemViewAdapterPreferences;
import delit.piwigoclient.ui.file.RecyclerViewDocumentFileFolderItemSelectFragment;

/**
 * Created by gareth on 12/07/17.
 */

public class FileSelectActivity extends MyActivity {

    private static final String TAG = "FileSelAct";

    public static final String INTENT_SELECTED_FILES = "FileSelectActivity.selectedFiles";
    public static final String ACTION_TIME_MILLIS = "FileSelectActivity.actionTimeMillis";
    public static String INTENT_DATA = "configData";
    private FolderItemViewAdapterPreferences folderItemSelectPrefs;

    public FileSelectActivity() {
        super(R.layout.activity_file_select);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            getUiHelper().runWithExtraPermissions(this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.Q, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_read_permissions_needed_for_file_upload));

        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        this.setIntent(intent);
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

        if (hasNotAcceptedEula()) {
            finish();
        } else {
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

    private void showFileSelectFragment() {

        FileSelectionNeededEvent event = getFileSelectionNeededEvent();

        FolderItemViewAdapterPreferences folderItemSelectPrefs = new FolderItemViewAdapterPreferences(event.isAllowFileSelection(), event.isAllowFolderSelection(), event.isMultiSelectAllowed());
        // custom settings
        folderItemSelectPrefs.withInitialFolder(event.getInitialFolder());
        folderItemSelectPrefs.withVisibleContent(event.getVisibleFileTypes(), event.getFileSortOrder());
        folderItemSelectPrefs.withVisibleMimeTypes(event.getVisibleMimeTypes());
        if(event.getSelectedUriPermissionsForConsumerId() == null) {
            Logging.log(Log.ERROR, TAG, "File Consumer id is null. Purpose  : " + event.getSelectedUriPermissionsForConsumerPurpose());
        }
        folderItemSelectPrefs.withSelectedUriPermissionsForConsumerId(event.getSelectedUriPermissionsForConsumerId());
        folderItemSelectPrefs.setSelectedUriPermissionConsumerPurpose(event.getSelectedUriPermissionsForConsumerPurpose());
        folderItemSelectPrefs.setSelectedUriPermissionFlags(event.getSelectedUriPermissionsFlags());
        // basic settings
        folderItemSelectPrefs.selectable(event.isMultiSelectAllowed(), false);
        folderItemSelectPrefs.setInitialSelection(event.getInitialSelection());
        folderItemSelectPrefs.withShowFilenames(OtherPreferences.isShowFilenames(getSharedPrefs(), getApplicationContext()));

        int uniqueEventId = TrackableRequestEvent.getNextEventId();

        Fragment fragment;
        Uri uri = folderItemSelectPrefs.getInitialFolder();
        if (uri != null && Build.VERSION.SDK_INT > Build.VERSION_CODES.Q && "file".equals(uri.getScheme())) {
            folderItemSelectPrefs.withInitialFolder(null);
        }
        fragment = RecyclerViewDocumentFileFolderItemSelectFragment.newInstance(folderItemSelectPrefs, uniqueEventId);
        this.folderItemSelectPrefs = folderItemSelectPrefs;

        setTrackedIntent(uniqueEventId, event.getActionId());
        showFragmentNow(fragment, false);
    }

    private FileSelectionNeededEvent getFileSelectionNeededEvent() {
        FileSelectionNeededEvent event = getIntent().getParcelableExtra(INTENT_DATA);
        if(event == null) {
            FirebaseAnalytics.getInstance(this).logEvent("FileSelectStandalone", null);
            HashSet<String> mimeTypes = getVisibleMimeTypes(getIntent());
            boolean allowFolderSelection = false;
            if(mimeTypes != null) {
                if(mimeTypes.remove("vnd.android.document/directory")) {
                    allowFolderSelection = true;
                }
            }
            boolean allowFileSelection = mimeTypes == null || mimeTypes.size() > 0;
            event = new FileSelectionNeededEvent(allowFileSelection, allowFolderSelection, true);
            event.withVisibleMimeTypes(mimeTypes);

            event.withInitialFolder(Uri.fromFile(this.getExternalFilesDir(null)));
        }
        return event;
    }

    private HashSet<String> getVisibleMimeTypes(Intent intent) {
        String intentMimeType = intent.getType();
        if(intentMimeType != null) {
            List<String> intentMimeTypes;
            if(intentMimeType.indexOf('|') > 0) {
                intentMimeTypes = Arrays.asList(intentMimeType.split("\\|"));
            } else {
                intentMimeTypes = new ArrayList<>();
                intentMimeTypes.add(intentMimeType);
            }
            return new HashSet<>(intentMimeTypes);
        }

        if(intent.getExtras() != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                String[] desiredMimeTypes = intent.getExtras().getStringArray(Intent.EXTRA_MIME_TYPES);
                if (desiredMimeTypes != null) {
                    return new HashSet<>(Arrays.asList(desiredMimeTypes));
                }
            }
        }
        return null;
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
            if (getIntent().getParcelableExtra(INTENT_DATA) != null) {

                Intent result = this.getIntent();
//            result.putExtra(INTENT_SOURCE_EVENT_ID, sourceEventId);
                result.putExtra(ACTION_TIME_MILLIS, event.getActionTimeMillis());
                if (event.getSelectedFolderItems() != null) {
                    // need to make sure the caller can read and write any items selected as requested.
                    result.setFlags(folderItemSelectPrefs.getSelectedUriPermissionFlags());
                    ClipData clipData = buildClipData(event);
                    result.setClipData(clipData);
                    //result.putParcelableArrayListExtra(INTENT_SELECTED_FILES, event.getSelectedFolderItems());
                    BaseMyActivity.relayFileSelectionCompleteEvent(sourceEventId, event);
                    setResult(Activity.RESULT_OK, result);
                } else {
                    setResult(Activity.RESULT_CANCELED, result);
                }
                finish();
            } else {
                Intent result = this.getIntent();
                result.setFlags(folderItemSelectPrefs.getSelectedUriPermissionFlags());
                if(event.getSelectedFolderItems() != null) {
                    if (!event.getSelectedFolderItems().isEmpty()) {
                        ClipData clipData = buildClipData(event);
                        result.setClipData(clipData);
                    }
                    setResult(Activity.RESULT_OK, result);
                } else {
                    setResult(Activity.RESULT_CANCELED, result);
                }

                finish();
            }
        }
    }

    @NonNull
    private ClipData buildClipData(FileSelectionCompleteEvent event) {
        ArrayList<String> mimes = new ArrayList<>(event.getSelectedFolderItems().size());
        ArrayList<ClipData.Item> clipItems = new ArrayList<>(event.getSelectedFolderItems().size());
        for (FolderItem item : event.getSelectedFolderItems()) {

            ClipData.Item clipItem = new ClipData.Item(item.getName(), null, item.getContentUri());
            mimes.add(item.getMime());
            clipItems.add(clipItem);
        }
        ClipDescription desc = new ClipDescription(getString(R.string.selected_files), mimes.toArray(new String[0]));
        ClipData clipData = new ClipData(desc, clipItems.remove(0));
        for (ClipData.Item clipItem : clipItems) {
            clipData.addItem(clipItem);
        }
        return clipData;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(StopActivityEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            finish();
        }
    }


}
