package delit.piwigoclient.database;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import delit.libs.core.util.Logging;
import delit.libs.util.CollectionUtils;

public class AppSettingsRepository {
    private static final String TAG = "AppSettingsRepository";
    private static AppSettingsRepository instance;
    private final UriPermissionUseDao uriPermissionUseDao;

    AppSettingsRepository(AppSettingsDatabase database) {
        this.uriPermissionUseDao = database.uriPermissionUseDao();
    }

    public static AppSettingsRepository getInstance(final AppSettingsDatabase database) {
        if (instance == null) {
            synchronized (AppSettingsRepository.class) {
                if (instance == null) {
                    instance = new AppSettingsRepository(database);
                }
            }
        }
        return instance;
    }

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    public LiveData<List<UriPermissionUse>> getAllUriPermissions() {
        return uriPermissionUseDao.loadAll();
    }

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    public LiveData<List<UriPermissionUse>> getAllUriPermissions(@NonNull Uri uri) {
        return uriPermissionUseDao.loadAllByUri(uri.toString());
    }

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    public LiveData<List<UriPermissionUse>> getAllUriPermissions(@NonNull Collection<Uri> uris) {
        return uriPermissionUseDao.loadAllByUris(CollectionUtils.toStrings(uris, new HashSet<>(uris.size())));
    }

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    public LiveData<List<UriPermissionUse>> getAllUriPermissions(@NonNull String consumerId) {
        return uriPermissionUseDao.loadAllByUriConsumerId(consumerId);
    }

    // You must call this on a non-UI thread or your app will throw an exception. Room ensures
    // that you're not doing any long running operations on the main thread, blocking the UI.
    public void insert(@NonNull UriPermissionUse uriPermissionUse) {
        AppSettingsDatabase.databaseWriteExecutor.execute(() -> uriPermissionUseDao.insertAll(uriPermissionUse));
    }

    public void delete(@NonNull UriPermissionUse uriPermissionUse) {
        AppSettingsDatabase.databaseWriteExecutor.execute(() -> uriPermissionUseDao.delete(uriPermissionUse));
    }

    public void deleteAllForUri(@NonNull Uri uri) {
        AppSettingsDatabase.databaseWriteExecutor.execute(() -> uriPermissionUseDao.deleteAll(uri.toString()));
    }

    private class UriUseDetail {
        List<String> consumers;
        int flagsStillNeeded;
        int flagsToRemove;
    }

    public void removePermissionsAndUpdateRealAppPermissions(@NonNull Context context, String consumerId, Collection<Uri> uris, List<UriPermissionUse> permissionsHeld) {

        Map<String, UriUseDetail> uriUseDetail = buildUriUseDetailForUris(consumerId, uris, permissionsHeld);

        for (Map.Entry<String, UriUseDetail> stringUriUseDetailEntry : uriUseDetail.entrySet()) {
            int flagsToRemove = stringUriUseDetailEntry.getValue().flagsToRemove;
//            int flagsStillNeeded = stringUriUseDetailEntry.getValue().flagsStillNeeded;
//            boolean hasNoConsumers = stringUriUseDetailEntry.getValue().consumers.isEmpty();
            safeReleaseAppPermissions(context, Uri.parse(stringUriUseDetailEntry.getKey()), flagsToRemove);
            /*if ((flagsToRemove != 0 || flagsStillNeeded == 0) && hasNoConsumers) {
                urisNoLongerUsedByApp.add(Uri.parse(stringUriUseDetailEntry.getKey()));
            }*/
        }
    }

    private void safeReleaseAppPermissions(Context context, Uri uri, int flagsToRemove) {
        try {
            context.getContentResolver().releasePersistableUriPermission(uri, flagsToRemove);
        } catch(SecurityException e) {
            String mimeType = context.getContentResolver().getType(uri);
            Logging.log(Log.WARN, TAG, "unable to release permission for uri. Uri Mime type : "  + mimeType);
            Logging.recordException(e);
        }
    }


    private Map<String, UriUseDetail> buildUriUseDetailForUris(String consumerId, Collection<Uri> uris, List<UriPermissionUse> permissionsHeld) {
        Map<String, UriUseDetail> uriUseDetailMap = new HashMap<>(uris.size());
        for (UriPermissionUse use : permissionsHeld) {
            UriUseDetail uriUseDetail = uriUseDetailMap.get(use.uri);
            if(uriUseDetail == null) {
                uriUseDetail = new UriUseDetail();
                uriUseDetailMap.put(use.uri, uriUseDetail);
            }
            if (!use.consumerId.equals(consumerId)) {
                uriUseDetail.consumers.add(use.consumerId);
                uriUseDetail.flagsStillNeeded |= use.flags;
            } else {
                uriUseDetail.flagsToRemove = use.flags;
                // remove our tracking entry for how we are using the uri permission
                delete(use);
            }
        }
        for (Map.Entry<String, UriUseDetail> stringUriUseDetailEntry : uriUseDetailMap.entrySet()) {
            // remove the flags to keep from the flags to remove
            int mask = ~stringUriUseDetailEntry.getValue().flagsStillNeeded; // get inverse of flags still needed
            stringUriUseDetailEntry.getValue().flagsToRemove &= mask; // remove all flags to remove no in that set
        }
        return uriUseDetailMap;
    }
}
