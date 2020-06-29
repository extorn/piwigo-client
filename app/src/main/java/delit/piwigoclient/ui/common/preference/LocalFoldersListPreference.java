package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.RequiresApi;
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
                        thisPref.appSettingsViewModel.releasePersistableUriPermission(thisPref.getContext(), oldFolder, thisPref.getUriPermissionsKey());
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
        try {
            appSettingsViewModel.takePersistableUriPermissions(getContext(), selectedFolder, flags, getUriPermissionsKey(), getContext().getString(R.string.preference_uri_consumer_description_pattern, getTitle()));
        } catch(SecurityException e) {
            Logging.log(Log.WARN, TAG, "Unable to take persistable permissions for folder URI : " + selectedFolder);
            Logging.recordException(e);
        }
    }
}
