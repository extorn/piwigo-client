package delit.piwigoclient.database;

import android.app.Application;
import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.IOUtils;

public class AppSettingsViewModel extends AndroidViewModel {

    private static final String TAG = "AppSettingsVM";
    private AppSettingsRepository appSettingsRepository;

    public AppSettingsViewModel(@NonNull Application application) {
        super(application);
        appSettingsRepository = new AppSettingsRepository(AppSettingsDatabase.getInstance(application));
    }

    public void insert(UriPermissionUse uriPermissionUse) {
        appSettingsRepository.insert(uriPermissionUse);
    }

    public LiveData<List<UriPermissionUse>> getAllForUri(Uri uri) {
        return appSettingsRepository.getAllUriPermissions(uri);
    }

    public LiveData<List<UriPermissionUse>> getAll() {
        return appSettingsRepository.getAllUriPermissions();
    }

    public void delete(UriPermissionUse uriPermissionUse) {
        appSettingsRepository.delete(uriPermissionUse);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void takePersistableFileSelectionUriPermissions(@NonNull Context context, @NonNull Uri uri, int flags, @NonNull String localizedConsumerName) {
        UriPermissionUse uriPermissionUse = new UriPermissionUse(uri.toString(), localizedConsumerName, flags);
        takePersistableFileSelectionUriPermissions(context, uri, uriPermissionUse);
    }


    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void takePersistableUriPermissions(@NonNull Context context, @NonNull Uri uri, int flags, @NonNull String consumerId, @NonNull String localizedConsumerName) {
        UriPermissionUse uriPermissionUse = new UriPermissionUse(uri.toString(),consumerId, localizedConsumerName, flags);
        takePersistableFileSelectionUriPermissions(context, uri, uriPermissionUse);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void takePersistableFileSelectionUriPermissions(@NonNull Context context, @NonNull Uri uri, @NonNull UriPermissionUse use) {
        if("file".equals(uri.getScheme())) {
            return; // no need to get permissions (would cause error anyway)
        }
        context.getContentResolver().takePersistableUriPermission(uri, use.flags);
        appSettingsRepository.insert(use);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void releasePersistableUriPermission(@NonNull Context context, @NonNull Uri uri, String consumerId) {
        LifecycleOwner lifecycleOwner = DisplayUtils.getLifecycleOwner(context);
        LiveData<List<UriPermissionUse>> liveData = getAllForUri(uri);
        liveData.observe(lifecycleOwner, new Observer<List<UriPermissionUse>>() {
            @Override
            public void onChanged(List<UriPermissionUse> permissionsHeld) {
                liveData.removeObserver(this);
                List<String> consumers = new ArrayList<>();
                int flagsToRemove = 0;
                int flagsStillUsed = 0;
                for (UriPermissionUse use : permissionsHeld) {
                    if (!use.consumerId.equals(consumerId)) {
                        consumers.add(use.consumerId);
                        flagsStillUsed |= use.flags;
                    } else {
                        flagsToRemove = use.flags;
                        // remove our tracking entry for how we are using the uri permission
                        delete(use);
                    }
                }
                // remove the flags to keep from the flags to remove
                int mask = ~flagsStillUsed;
                flagsToRemove &= mask;

                if (flagsToRemove != 0 && consumers.size() == 0 && permissionsHeld.size() > 0) {
                    // remove the Uri permission if it is no longer in use
                    List<Uri> uriPermsHeld = IOUtils.removeUrisWeLackPermissionFor(context, Collections.singletonList(uri));
                    if(uriPermsHeld.indexOf(uri) >= 0) {
                        context.getContentResolver().releasePersistableUriPermission(uri, flagsToRemove);
                    }
                }
            }
        });
    }

    //TODO need to use this kind of thing to ensure we ask user to add permissions back for URis that they removed permissions for in the system UI
    private boolean hasRequiredPermissionsForUri(Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            List<UriPermission> perms = getApplication().getContentResolver().getPersistedUriPermissions();
            for (UriPermission perm : perms) {
                DocumentFile treeLinkedDocFile = IOUtils.getTreeLinkedDocFile(getApplication(), perm.getUri(), uri);
                if (perm.isWritePermission() && perm.getUri().equals(uri)
                        || null != treeLinkedDocFile) {
                    // no extra permission needed.
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    public void deleteAllForUri(@NonNull Uri folderUri) {
        appSettingsRepository.deleteAllForUri(folderUri);
    }

}
