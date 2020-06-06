package delit.piwigoclient.database;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public void releasePersistableUriPermission(@NonNull Context context, @Nullable List<UriPermissionUse> uriPermissionConsumers, @NonNull Uri uri, int flags) {
        if(uriPermissionConsumers == null || uriPermissionConsumers.isEmpty()) {
            List<Uri> uriPermsHeld = IOUtils.removeUrisWeLackPermissionFor(context, Arrays.asList(uri));
            for(Uri uriPermHeld : uriPermsHeld) {
                context.getContentResolver().releasePersistableUriPermission(uriPermHeld, flags);
            }
            return;
        }
        int readPerm = flags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        int writePerm = flags & Intent.FLAG_GRANT_READ_URI_PERMISSION;

        Set<Uri> uniqueUris = new HashSet<>(uriPermissionConsumers.size());
        for(UriPermissionUse uriConsumerLink : uriPermissionConsumers) {
            int adjustedFlags = (uriConsumerLink.flags ^ readPerm) ^ writePerm;
            if(adjustedFlags == 0) {
                delete(uriConsumerLink);
            } else if(adjustedFlags != uriConsumerLink.flags) {
                uriConsumerLink.flags = adjustedFlags;
                insert(uriConsumerLink);
            }
            uniqueUris.add(Uri.parse(uriConsumerLink.uri));
        }

        IOUtils.removeUrisWeLackPermissionFor(context, uniqueUris);

        for(Uri uniqueUri : uniqueUris) {
            context.getContentResolver().releasePersistableUriPermission(uniqueUri, flags);
        }
    }

    private boolean hasRequiredPermissionsForUri(Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Uri rootUri = null;

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
