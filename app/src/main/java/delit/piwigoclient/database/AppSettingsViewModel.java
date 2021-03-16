package delit.piwigoclient.database;

import android.app.Application;
import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.Collection;
import java.util.List;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.IOUtils;
import delit.piwigoclient.database.livedata.PermissionsRemovingObserver;
import delit.piwigoclient.ui.util.LiveDataTransientObserver;

public class AppSettingsViewModel extends AndroidViewModel {

    private static final String TAG = "AppSettingsVM";
    private final AppSettingsRepository appSettingsRepository;

    public AppSettingsViewModel(@NonNull Application application) {
        super(application);
        appSettingsRepository = new AppSettingsRepository(AppSettingsDatabase.getInstance(application));
    }

    public void insert(UriPermissionUse uriPermissionUse) {
        appSettingsRepository.insert(uriPermissionUse);
    }

    public LiveData<List<UriPermissionUse>> getAllForConsumer(String consumer) {
        return appSettingsRepository.getAllUriPermissions(consumer);
    }

    public LiveData<List<UriPermissionUse>> getAllForUri(Uri uri) {
        return appSettingsRepository.getAllUriPermissions(uri);
    }

    public LiveData<List<UriPermissionUse>> getAllForUris(Collection<Uri> uris) {
        return appSettingsRepository.getAllUriPermissions(uris);
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
        if(UriPermissionUse.TRANSIENT.equals(use.consumerId)) {
            Logging.log(Log.WARN, TAG, "Blocked requesting persistable Uri permissions when should be transient only");
            Logging.recordException(new Throwable("Unneeded persistent Uri permissions requested").fillInStackTrace());
        } else {
            context.getContentResolver().takePersistableUriPermission(uri, use.flags);
            appSettingsRepository.insert(use);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void removeAllUriPermissionsRecords(@NonNull Context context, String consumerId) {
        LiveData<List<UriPermissionUse>> liveData = getAllForConsumer(consumerId);
        liveData.observeForever(new LiveDataTransientObserver<List<UriPermissionUse>>(context, liveData) {
            @Override
            public void onChangeObserved(List<UriPermissionUse> permissionsHeld) {
                for(UriPermissionUse use : permissionsHeld) {
                    removeAllUriPermissionsRecords(context, Uri.parse(use.uri), use.consumerId);
                }
            }
        });
    }

    public void removeAllUriPermissionsRecords(@NonNull Context context, @NonNull UriPermissionUse uriPermissionUse) {
        removeAllUriPermissionsRecords(context, Uri.parse(uriPermissionUse.uri), uriPermissionUse.consumerId);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void removeAllUriPermissionsRecords(@NonNull Context context, @NonNull Uri uri, String consumerId) {
        LiveData<List<UriPermissionUse>> liveData = getAllForUri(uri);
        DisplayUtils.runOnUiThread(()-> liveData.observeForever(new PermissionsRemovingObserver(context.getApplicationContext(), appSettingsRepository, liveData, uri, consumerId)));
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void removeAllUriPermissionsRecords(@NonNull Context context, @NonNull Collection<Uri> uris, String consumerId) {
        LiveData<List<UriPermissionUse>> liveData = getAllForUris(uris);
        DisplayUtils.runOnUiThread(()-> liveData.observeForever(new PermissionsRemovingObserver(context.getApplicationContext(), appSettingsRepository, liveData, uris, consumerId)));
    }

    //TODO need to use this kind of thing to ensure we ask user to add permissions back for URis that they removed permissions for in the system UI
    private boolean hasRequiredPermissionsForUri(Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            List<UriPermission> perms = getApplication().getContentResolver().getPersistedUriPermissions();
            for (UriPermission perm : perms) {
                DocumentFile treeLinkedDocFile = IOUtils.getTreeLinkedDocFile(getApplication(), perm.getUri(), uri);
                if (perm.isWritePermission() && perm.getUri().equals(uri)  || null != treeLinkedDocFile) {
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
