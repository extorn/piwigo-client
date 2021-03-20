package delit.piwigoclient.database;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.room.Transaction;
import androidx.sqlite.db.SimpleSQLiteQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Future;

import delit.libs.core.util.Logging;
import delit.libs.util.CollectionUtils;
import delit.piwigoclient.database.dao.PriorUploadDao;
import delit.piwigoclient.database.dao.UploadDestinationDao;
import delit.piwigoclient.database.pojo.UploadDestinationWithPriorUploads;

public class PriorUploadRepository {
    private static PriorUploadRepository instance;
    private final PriorUploadDao priorUploadDao;
    private final UploadDestinationDao uploadDestinationWithPriorUploadsDao;

    PriorUploadRepository(PiwigoUploadsDatabase database) {
        this.priorUploadDao = database.priorUploadDao();
        this.uploadDestinationWithPriorUploadsDao = database.uploadDestinationWithPriorUploadsDao();
    }

    public static PriorUploadRepository getInstance(final PiwigoUploadsDatabase database) {
        if (instance == null) {
            synchronized (PriorUploadRepository.class) {
                if (instance == null) {
                    instance = new PriorUploadRepository(database);
                }
            }
        }
        return instance;
    }

    public List<Uri> getAllPreviouslyUploadedUrisToServerKeyMatching(String uploadToKey, @NonNull Collection<Uri> uris) {
        // Usage of RawDao
        if(uris.isEmpty()) {
            return new ArrayList<>(0);
        }
        List<Object> args = new ArrayList<>(uris.size() + 1);
        args.add(uploadToKey);
        args.addAll(CollectionUtils.toStrings(uris, new HashSet<>(uris.size())));
        List<String> uriStrs = priorUploadDao.getAllMatchingRawQuery(new SimpleSQLiteQuery("SELECT PriorUpload.uri FROM PriorUpload INNER JOIN UploadDestinationPriorUploadCrossRef ON PriorUpload.id = UploadDestinationPriorUploadCrossRef.priorUploadId INNER JOIN UploadDestination ON UploadDestinationPriorUploadCrossRef.uploadToId = UploadDestination.id WHERE uploadToKey = :uploadToKey AND PriorUpload.uri IN(" + makePlaceholders(uris.size()) + ")", args.toArray()));
        List<Uri> retVal = new ArrayList<>();
        for(String uriStr : uriStrs) {
            retVal.add(Uri.parse(uriStr));
        }
        return retVal;
    }

    @Transaction
    public List<String> getAllPreviouslyUploadedChecksumsToServerKeyMatching(String uploadToKey, @NonNull Collection<String> checksums) {
        List<Object> args = new ArrayList<>(checksums.size() + 1);
        args.add(uploadToKey);
        args.addAll(checksums);
        return priorUploadDao.getAllMatchingRawQuery(new SimpleSQLiteQuery("SELECT PriorUpload.uri FROM PriorUpload INNER JOIN UploadDestinationPriorUploadCrossRef ON PriorUpload.id = UploadDestinationPriorUploadCrossRef.priorUploadId INNER JOIN UploadDestination ON UploadDestinationPriorUploadCrossRef.uploadToId = UploadDestination.id WHERE uploadToKey = :uploadToKey AND PriorUpload.checksum IN("+makePlaceholders(checksums.size())+")", args.toArray()));
    }

    protected String makePlaceholders(int len) {
        if (len < 1) {
            // It will lead to an invalid query anyway ..
            throw new RuntimeException("No placeholders");
        } else {
            StringBuilder sb = new StringBuilder(len * 2 - 1);
            sb.append("?");
            for (int i = 1; i < len; i++) {
                sb.append(",?");
            }
            return sb.toString();
        }
    }

    public Future<?> insert(@NonNull UploadDestinationWithPriorUploads uploadDestination) {
        return PiwigoUploadsDatabase.databaseWriteExecutor.submit(() -> {
            try {
                uploadDestinationWithPriorUploadsDao.upsert(uploadDestination);
            } catch(RuntimeException e) {
                Logging.recordException(e);
            }
        });
    }

    public void delete(@NonNull UploadDestinationWithPriorUploads uploadDestination) {
        PiwigoUploadsDatabase.databaseWriteExecutor.execute(() -> {
            try {
                uploadDestinationWithPriorUploadsDao.delete(uploadDestination);
            } catch(RuntimeException e) {
                Logging.recordException(e);
            }
        });
    }

    public void insertAll(List<UploadDestinationWithPriorUploads> destinations) {
        PiwigoUploadsDatabase.databaseWriteExecutor.execute(() -> {
            try {
                uploadDestinationWithPriorUploadsDao.insertAll(destinations);
            } catch(RuntimeException e) {
                Logging.recordException(e);
            }
        });

    }
}
