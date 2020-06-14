package delit.piwigoclient.database;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import java.util.List;

public class AppSettingsRepository {
    private static AppSettingsRepository instance;
    private UriPermissionUseDao uriPermissionUseDao;

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
}
