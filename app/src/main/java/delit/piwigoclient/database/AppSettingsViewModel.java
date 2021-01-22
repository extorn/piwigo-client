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
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import delit.libs.core.util.Logging;
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
    public void releaseAllPersistableUriPermissions(@NonNull Context context, String consumerId) {
        LifecycleOwner lifecycleOwner = DisplayUtils.getLifecycleOwner(context);
        LiveData<List<UriPermissionUse>> liveData = getAllForConsumer(consumerId);
        liveData.observe(lifecycleOwner, new Observer<List<UriPermissionUse>>() {
            @Override
            public void onChanged(List<UriPermissionUse> permissionsHeld) {
                liveData.removeObserver(this);
                for(UriPermissionUse use : permissionsHeld) {
                    releasePersistableUriPermission(context, Uri.parse(use.uri), use.consumerId, false);
                }
            }
        });
    }

    public void releasePersistableUriPermission(@NonNull Context context, @NonNull UriPermissionUse uriPermissionUse) {
        releasePersistableUriPermission(context, Uri.parse(uriPermissionUse.uri), uriPermissionUse.consumerId, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void releasePersistableUriPermission(@NonNull Context context, @NonNull Uri uri, String consumerId, boolean removeTreeToo) {
        LifecycleOwner lifecycleOwner = DisplayUtils.getLifecycleOwner(context);
        LiveData<List<UriPermissionUse>> liveData = getAllForUri(uri);
        DisplayUtils.runOnUiThread(()->{liveData.observe(lifecycleOwner, new PermissionsRemovingObserver(context.getApplicationContext(), liveData, uri, consumerId, removeTreeToo));});
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void releasePersistableUriPermission(@NonNull Context context, @NonNull Collection<Uri> uris, String consumerId, boolean removeTreeToo) {
        LifecycleOwner lifecycleOwner = DisplayUtils.getLifecycleOwner(context);
        LiveData<List<UriPermissionUse>> liveData = getAllForUris(uris);
        //TODO is this actually going to help?!
        DisplayUtils.runOnUiThread(()->{liveData.observe(lifecycleOwner, new PermissionsRemovingObserver(context.getApplicationContext(), liveData, uris, consumerId, removeTreeToo));});
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

    private class PermissionsRemovingObserver implements Observer<List<UriPermissionUse>> {
        private final Collection<Uri> uris;
        private final WeakReference<Context> contextRef;
        private final String consumerId;
        private final LiveData<List<UriPermissionUse>> liveData;
        private final boolean removeTreeToo;


        public PermissionsRemovingObserver(Context context, LiveData<List<UriPermissionUse>> liveData, Collection<Uri> uris, String consumerId, boolean removeTreeToo) {
            this.contextRef = new WeakReference<>(context);
            this.liveData = liveData;
            this.uris = uris;
            this.consumerId = consumerId;
            this.removeTreeToo = removeTreeToo;
        }

        public Context getContext() {
            return contextRef.get();
        }

        public PermissionsRemovingObserver(Context context, LiveData<List<UriPermissionUse>> liveData, Uri uri, String consumerId, boolean removeTreeToo) {
            this(context, liveData, Arrays.asList(uri), consumerId, removeTreeToo);
        }

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

            if ((flagsToRemove != 0 || flagsStillUsed == 0) && consumers.size() == 0) {
                // remove the Uri permission if it is no longer in use
                Collection<Uri> uriPermsHeld = IOUtils.removeUrisWeLackPermissionFor(getContext(), uris);
                for(Uri uri : uris) {
                    removePermissionsForUri(uriPermsHeld, uri, flagsToRemove);
                }
            }
        }

        private void removePermissionsForUri(Collection<Uri> uriPermsHeld, Uri uri, int flagsToRemove) {
            if(uriPermsHeld.contains(uri)) {
                try {
                    getContext().getContentResolver().releasePersistableUriPermission(uri, flagsToRemove);
                } catch(SecurityException e) {
                    String mimeType = getContext().getContentResolver().getType(uri);
                    Logging.log(Log.WARN, TAG, "unable to release permission for uri. Uri Mime type : "  + mimeType);
                    Logging.recordException(e);
                }
            } else if(removeTreeToo) {
                // check the tree uri.
                Uri treeUri = IOUtils.getTreeUri(uri);
                if(!treeUri.equals(uri)) {
                    uriPermsHeld = IOUtils.removeUrisWeLackPermissionFor(getContext(), Collections.singletonList(treeUri));
                    if (!uriPermsHeld.isEmpty()) {
                        releasePersistableUriPermission(getContext(), treeUri, consumerId, true);
                    }
                }
            }
        }
    }
}
