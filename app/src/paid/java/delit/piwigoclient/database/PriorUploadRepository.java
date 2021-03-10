package delit.piwigoclient.database;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import delit.libs.util.CollectionUtils;

public class PriorUploadRepository {
    private static PriorUploadRepository instance;
    private final PriorUploadDao priorUploadDao;

    PriorUploadRepository(PiwigoDatabase database) {
        this.priorUploadDao = database.priorUploadDao();
    }

    public static PriorUploadRepository getInstance(final PiwigoDatabase database) {
        if (instance == null) {
            synchronized (PriorUploadRepository.class) {
                if (instance == null) {
                    instance = new PriorUploadRepository(database);
                }
            }
        }
        return instance;
    }

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    public LiveData<List<PriorUpload>> getAllPriorUploadsWithUri(@NonNull Uri uri) {
        return priorUploadDao.loadAllByUri(uri.toString());
    }

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    public LiveData<List<PriorUpload>> getAllPriorUploadsWithParentUri(@NonNull Uri parentUri) {
        return priorUploadDao.loadAllByParentUri(parentUri.toString());
    }

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    public LiveData<List<PriorUpload>> getAllPriorUploads(@NonNull Collection<Uri> uris) {
        return priorUploadDao.loadAllByUris(CollectionUtils.toStrings(uris, new HashSet<>(uris.size())));
    }

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    public LiveData<List<PriorUpload>> getAllPriorUploadsByChecksum(@NonNull Collection<Uri> checksums) {
        return priorUploadDao.loadAllByChecksums(CollectionUtils.toStrings(checksums, new HashSet<>(checksums.size())));
    }

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    public LiveData<List<PriorUpload>> getAllPriorUploads(@NonNull String checksum) {
        return priorUploadDao.loadAllByChecksum(checksum);
    }

    // You must call this on a non-UI thread or your app will throw an exception. Room ensures
    // that you're not doing any long running operations on the main thread, blocking the UI.
    public void insert(@NonNull PriorUpload priorUpload) {
        AppSettingsDatabase.databaseWriteExecutor.execute(() -> priorUploadDao.insertAll(priorUpload));
    }

    public void delete(@NonNull PriorUpload priorUpload) {
        AppSettingsDatabase.databaseWriteExecutor.execute(() -> priorUploadDao.delete(priorUpload));
    }

    public void deleteAllForUri(@NonNull Uri uri) {
        AppSettingsDatabase.databaseWriteExecutor.execute(() -> priorUploadDao.deleteAll(uri.toString()));
    }

    public void deleteAll(@NonNull Set<Uri> uris) {
        HashSet<String> uriStrs = new HashSet<>();
        for(Uri u : uris) {
            uriStrs.add(u.toString());
        }
        AppSettingsDatabase.databaseWriteExecutor.execute(() -> priorUploadDao.deleteAll(uriStrs));
    }
}
