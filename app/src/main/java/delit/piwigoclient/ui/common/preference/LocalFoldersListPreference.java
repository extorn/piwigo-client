package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.crashlytics.android.Crashlytics;
import com.google.android.exoplayer2.util.UriUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.ObjectUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.database.AppSettingsViewModel;
import delit.piwigoclient.database.UriPermissionUse;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;

public class LocalFoldersListPreference extends EventDrivenPreference<FileSelectionNeededEvent> {


    private static final String TAG = "LocalFolderPref";
    private AppSettingsViewModel appSettingsViewModel;

    public LocalFoldersListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LocalFoldersListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LocalFoldersListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LocalFoldersListPreference(Context context) {
        super(context);
    }


    @Override
    public CharSequence getSummary() {
        CharSequence summaryPattern = super.getSummary();
        if (summaryPattern == null) {
            return null;
        }
        String albumUri = getValue();
        if (albumUri != null) {

            String albumName = IOUtils.getFilename(getContext(), Uri.parse(albumUri));
            return String.format(super.getSummary().toString(), albumName);
        }
        return getContext().getString(R.string.local_folder_preference_summary_default);
    }

    private boolean hasPermissionForUri(Uri folderUri) {

        boolean hasPermission = IOUtils.hasUriPermissions(getContext(), folderUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if(!hasPermission) {
            // need to remove this permission from our list of stored ones.
            appSettingsViewModel.deleteAllForUri(folderUri);
        }
        return hasPermission;
    }

    @Override
    public String getValue() {
        String albumUri = super.getValue();
        if(albumUri != null && !hasPermissionForUri(Uri.parse(albumUri))) {
            // set a new value without triggering a total reload of the preference (it may be mid binding).
            persistString(null);
            //TODO should I request a refresh of the summary ? Yes!
        }
        return getCurrentValue();
    }

    @Override
    protected void initPreference(Context context, AttributeSet attrs) {
        super.initPreference(context, attrs);
        ViewModelStoreOwner viewModelProvider = DisplayUtils.getViewModelStoreOwner(getContext());
        appSettingsViewModel = new ViewModelProvider(viewModelProvider).get(AppSettingsViewModel.class);
    }

    @Override
    protected FileSelectionNeededEvent buildOpenSelectionEvent() {

        if(getOnPreferenceChangeListener() == null || !(getOnPreferenceChangeListener() instanceof PersistablePermissionsChangeListener)) {
            throw new IllegalStateException("An On Preference change listener must be added that extends PersistablePermissionsChangeListener");
        }

        String val = getValue();
        Uri initialFolder = null;
        if(val != null) {
            initialFolder = Uri.parse(val);
        }

        Set<Uri> selection = new HashSet<>();
        if (initialFolder == null) {
            initialFolder = Uri.fromFile(getContext().getExternalFilesDir(null));
        } else {
            selection.add(initialFolder);
        }
        FileSelectionNeededEvent fileSelectNeededEvent = new FileSelectionNeededEvent(false, true, false);
        fileSelectNeededEvent.withInitialFolder(initialFolder);
        fileSelectNeededEvent.withVisibleContent(FileSelectionNeededEvent.ALPHABETICAL);
        fileSelectNeededEvent.withInitialSelection(selection);
        fileSelectNeededEvent.withSelectedUriPermissionsForConsumerId(getUriPermissionsKey());
        return fileSelectNeededEvent;
    }

    /**
     * Use the preference key as the uri permissions key - if this isn't unique, you might want to change it.
     * @return
     */
    private String getUriPermissionsKey() {
        return getKey();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED, sticky = true)
    public void onEvent(FileSelectionCompleteEvent event) {
        if(isTrackingEvent(event)) {
            EventBus.getDefault().removeStickyEvent(event);
            if(event.getSelectedFolderItems().size() == 0) {
                setValue(null);
            } else {
                Uri selectedFileUri = event.getSelectedFolderItems().get(0).getContentUri();
                if (IOUtils.isDirectory(getContext(), selectedFileUri)) {
                    String newValue = selectedFileUri.toString();
                    setValue(newValue);
                }
            }
        }
    }

    public static class PersistablePermissionsChangeListener implements OnPreferenceChangeListener {

        private final UIHelper uiHelper;

        public PersistablePermissionsChangeListener(UIHelper helper) {
            this.uiHelper = helper;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            LocalFoldersListPreference thisPref = (LocalFoldersListPreference) preference;
            String oldValue = thisPref.getCurrentValue();
            boolean removingOldPermissions = oldValue != null;
            if(ObjectUtils.areEqual(oldValue, newValue)) {
                removingOldPermissions = false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Uri selectedFolder;
                if(newValue != null) {
                    selectedFolder = IOUtils.getTreeUri(Uri.parse((String)newValue));
                } else {
                    selectedFolder = null;
                }
                if(removingOldPermissions) {
                    Uri oldFolder = IOUtils.getLocalFileUri(oldValue);
                    if(!"file".equals(oldFolder.getScheme())) {
                        // file uris dont get dealt with in this way.
                        LifecycleOwner lifecycleOwner = DisplayUtils.getLifecycleOwner(thisPref.getContext());
                        LiveData<List<UriPermissionUse>> liveData = thisPref.appSettingsViewModel.getAllForUri(oldFolder);
                        liveData.observe(lifecycleOwner, new Observer<List<UriPermissionUse>>() {
                            @Override
                            public void onChanged(List<UriPermissionUse> permissionsHeld) {
                                liveData.removeObserver(this);
                                List<String> consumers = new ArrayList<>();
                                for (UriPermissionUse use : permissionsHeld) {
                                    if (!use.consumerId.equals(thisPref.getUriPermissionsKey())) {
                                        consumers.add(use.consumerId);
                                    }
                                }
                                if (consumers.size() == 0 && permissionsHeld.size() > 0) {
                                    DocumentFile documentFile = DocumentFile.fromSingleUri(thisPref.getContext(), oldFolder);
                                    String folderName = documentFile != null ? documentFile.getName() : oldFolder.getLastPathSegment();
                                    uiHelper.showOrQueueDialogQuestion(R.string.alert_question_title, thisPref.getContext().getString(R.string.release_permissions_for_uri_pattern, folderName), R.string.button_no, R.string.button_yes, new PermissionReleaseAnswerHandler(uiHelper, thisPref, oldFolder, selectedFolder));
                                }
                            }
                        });
                    }
                }
                if(newValue != null) {
                    thisPref.takePersistableUriPermission(selectedFolder);
                }
            }
            return true;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void takePersistableUriPermission(Uri selectedFolder) {
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        appSettingsViewModel.takePersistableUriPermissions(getContext(), selectedFolder, flags, getUriPermissionsKey(), getContext().getString(R.string.preference_uri_consumer_description_pattern, getTitle()));
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private static class PermissionReleaseAnswerHandler extends UIHelper.QuestionResultAdapter {

        private final Uri oldFolder;
        private final Uri newFolder;
        private final LocalFoldersListPreference thisPreference;

        public PermissionReleaseAnswerHandler(UIHelper uiHelper, LocalFoldersListPreference thisPreference, Uri oldFolder, Uri newFolder) {
            super(uiHelper);
            this.oldFolder = oldFolder;
            this.newFolder = newFolder;
            this.thisPreference = thisPreference;
        }


        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            super.onResult(dialog, positiveAnswer);
            if(Boolean.TRUE.equals(positiveAnswer)) {
                LifecycleOwner lifecycleOwner = DisplayUtils.getLifecycleOwner(thisPreference.getContext());
                LiveData<List<UriPermissionUse>> uriPermissionsData = thisPreference.appSettingsViewModel.getAllForUri(oldFolder);
                uriPermissionsData.observe(lifecycleOwner, permissionsHeld -> thisPreference.appSettingsViewModel.releasePersistableUriPermission(getContext(), permissionsHeld, oldFolder, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
            }
            if(newFolder != null) {
                thisPreference.takePersistableUriPermission(newFolder);
            }
        }
    }
}
