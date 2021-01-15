package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.preference.Preference;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashSet;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.ObjectUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.database.AppSettingsViewModel;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;

public class LocalFoldersListPreference extends EventDrivenPreference<FileSelectionNeededEvent> {


    private static final String TAG = "LocalFolderPref";
    private AppSettingsViewModel appSettingsViewModel;
    int requiredUriFlagsForSelectedItems = IOUtils.URI_PERMISSION_READ_WRITE; // default permissions

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

    public void setRequiredPermissions(int requiredUriFlagsForSelectedItems) {
        this.requiredUriFlagsForSelectedItems = requiredUriFlagsForSelectedItems;
    }

    private boolean hasPermissionForUri(Uri folderUri) {
        int permissionsHeld = IOUtils.getUriPermissionsFlags(getContext(), folderUri);
        boolean hasPermission = (requiredUriFlagsForSelectedItems & permissionsHeld) == requiredUriFlagsForSelectedItems;
        if(permissionsHeld == 0) {
            // need to remove this permission from our list of stored ones.
            Logging.log(Log.WARN, TAG, "Deleting all records of permissions uses for uri %1$s as app no longer has permissions", folderUri);
            appSettingsViewModel.deleteAllForUri(folderUri);
        } else if(!hasPermission) {
            // need to remove this permission from our list of stored ones against this use key. It doesn't reflect reality
            Logging.log(Log.WARN, TAG, "Deleting record of permissions use for uri %1$s by %2$s. Correct permissions not held", folderUri, getUriPermissionsKey());
            appSettingsViewModel.releasePersistableUriPermission(getContext(), folderUri, getUriPermissionsKey(), false);
        }
        return hasPermission;
    }

    @Override
    public void onDetached() {
        super.onDetached();
        appSettingsViewModel = null;
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
        fileSelectNeededEvent.setSelectedUriPermissionsForConsumerPurpose(getTitle().toString());
        fileSelectNeededEvent.requestUriReadWritePermissions();
        return fileSelectNeededEvent;
    }

    /**
     * Use the preference key as the uri permissions key - if this isn't unique, you might want to change it.
     * @return
     */
    private @NonNull String getUriPermissionsKey() {
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
                Uri selectedFolder = null;
                if(newValue != null) {
                    selectedFolder = Uri.parse((String)newValue);
                    try {
                        selectedFolder = IOUtils.getTreeUri(selectedFolder);
                    } catch(IllegalArgumentException e) {
                        Logging.log(Log.WARN, TAG, "Raw file cannot be converted to tree uri. Leaving as is.");
                    }
                }
                if(newValue != null) {
                    // only remove the old permission if we were able to take the more specific one. //TODO why is this not allowed by Android / working?
                    removingOldPermissions &= thisPref.takePersistableUriPermission(selectedFolder);
                }
                if(removingOldPermissions) {
                    Uri oldFolder = IOUtils.getLocalFileUri(oldValue);
                    if(!"file".equals(oldFolder.getScheme())) {
                        // file uris don't get dealt with in this way.
                        thisPref.appSettingsViewModel.releasePersistableUriPermission(thisPref.getContext(), oldFolder, thisPref.getUriPermissionsKey(), true);
                        DocumentFile docFile = DocumentFile.fromTreeUri(thisPref.getContext(), oldFolder);
                        if(docFile != null) {
                            // this is needed only because I messed up and added items incorrectly in the past.
                            thisPref.appSettingsViewModel.releasePersistableUriPermission(thisPref.getContext(), oldFolder, thisPref.getUriPermissionsKey(), true);
                        }
                    }
                }
            }
            return true;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private boolean takePersistableUriPermission(Uri selectedFolder) {
        try {
            appSettingsViewModel.takePersistableUriPermissions(getContext(), selectedFolder, requiredUriFlagsForSelectedItems, getUriPermissionsKey(), getContext().getString(R.string.preference_uri_consumer_description_pattern, getTitle()));
        } catch(SecurityException e) {
            Logging.log(Log.WARN, TAG, "Unable to take persistable permissions for folder URI : " + selectedFolder);
            Logging.recordException(e);
            return false;
        }
        return true;
    }
}
