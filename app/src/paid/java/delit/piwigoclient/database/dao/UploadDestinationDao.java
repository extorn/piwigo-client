package delit.piwigoclient.database.dao;

import android.util.Log;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import delit.libs.core.util.Logging;
import delit.piwigoclient.database.entity.PriorUpload;
import delit.piwigoclient.database.entity.UploadDestination;
import delit.piwigoclient.database.entity.UploadDestinationPriorUploadCrossRef;
import delit.piwigoclient.database.pojo.UploadDestinationWithPriorUploads;

import static androidx.room.OnConflictStrategy.IGNORE;
import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
public abstract class UploadDestinationDao {

    private static final String TAG = "UploadDestinationDao";

    @Insert(onConflict = IGNORE)
    abstract long insert(UploadDestination destination);

    @Delete
    abstract void delete(UploadDestination destination);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract long[] insertAll(PriorUpload... priorUploads);

    @Query("DELETE FROM PriorUpload WHERE id NOT IN (SELECT priorUploadId FROM UploadDestinationPriorUploadCrossRef)")
    abstract void deleteAllOrphanedPriorUploads();

    @Query("DELETE FROM UploadDestinationPriorUploadCrossRef WHERE uploadToId = :uploadToId")
    abstract void deletePriorUploadsCrossRefs(long uploadToId);

    @Insert(onConflict = REPLACE)
    abstract void insert(UploadDestinationPriorUploadCrossRef uploadDestinationPriorUploadJoin);

    @Insert(onConflict = REPLACE)
    abstract void insertAllCrossRefs(List<UploadDestinationPriorUploadCrossRef> crossRefs);

    @Update
    abstract void updateAllCrossRefs(List<UploadDestinationPriorUploadCrossRef> crossRefs);

    @Query("SELECT * FROM UploadDestination WHERE uploadToKey = :uploadToKey")
    abstract UploadDestination getUploadDestinationByKey(String uploadToKey);

    @Transaction
    public void delete(UploadDestinationWithPriorUploads destination) {
        deletePriorUploadsCrossRefs(destination.destination.id);
        delete(destination.destination);
        deleteAllOrphanedPriorUploads();
    }

    public void insertAll(List<UploadDestinationWithPriorUploads> uploadDestinations) {
        for(UploadDestinationWithPriorUploads destPojo : uploadDestinations) {
            insert(destPojo);
        }
    }

    @Transaction
    public void insert(UploadDestinationWithPriorUploads uploadDestination) {
        uploadDestination.destination.id = insert(uploadDestination.destination);
        long[] ids = insertAll(uploadDestination.priorUploads.toArray(new PriorUpload[0]));
        List<UploadDestinationPriorUploadCrossRef> crossRefs = buildCrossRefs(uploadDestination.destination, uploadDestination.priorUploads);
        for (int i = 0; i < ids.length; i++) {
            crossRefs.get(i).priorUploadId = ids[i];
        }
        insertAllCrossRefs(crossRefs);
    }

    @Update(onConflict = REPLACE)
    public abstract void update(UploadDestination destination);

    @Transaction
    public void upsert(UploadDestinationWithPriorUploads uploadDestination) {
        Logging.log(Log.DEBUG, TAG, "Starting upsert");
        long id = insert(uploadDestination.destination);
        if(id < 0) {
            uploadDestination.destination.id = getUploadDestinationByKey(uploadDestination.destination.uploadToKey).id;
        } else {
            uploadDestination.destination.id = id;
        }
        long[] ids = insertAll(uploadDestination.priorUploads.toArray(new PriorUpload[0]));
        List<PriorUpload> inserts = new ArrayList<>();
        List<PriorUpload> updates = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            PriorUpload priorUpload = uploadDestination.priorUploads.get(i);
            long dbId = ids[i];
            if(dbId < 0) {
                updates.add(priorUpload);
            } else {
                priorUpload.id = dbId;
                inserts.add(priorUpload);
            }
        }
        populateDatabaseIds(updates);
        updateAll(updates);

        List<UploadDestinationPriorUploadCrossRef> crossRefs = buildCrossRefs(uploadDestination.destination, inserts);
        insertAllCrossRefs(crossRefs);
        crossRefs = buildCrossRefs(uploadDestination.destination, updates);
        for(UploadDestinationPriorUploadCrossRef crossRef :crossRefs) {
            crossRef.id = getCrossRef(crossRef.uploadToId, crossRef.priorUploadId).id;
        }
        updateAllCrossRefs(crossRefs);
        Logging.log(Log.DEBUG, TAG, "Finishing upsert");
    }

    @Query("SELECT * FROM UploadDestinationPriorUploadCrossRef WHERE uploadToId = :uploadToId AND priorUploadId = :priorUploadId")
    abstract UploadDestinationPriorUploadCrossRef getCrossRef(long uploadToId, long priorUploadId);


    @Query("SELECT * FROM PriorUpload WHERE uri IN ( :uris) OR checksum IN ( :checksums)")
    abstract List<PriorUpload> getAllPriorUploadsLike(List<String> uris, List<String> checksums);


    private void populateDatabaseIds(List<PriorUpload> updates) {
        List<String> uris = new ArrayList<>();
        List<String> checksums = new ArrayList<>();
        for(PriorUpload upload :updates) {
            uris.add(upload.uri.toString());
            checksums.add(upload.checksum);
        }
        List<PriorUpload> dbCopies = getAllPriorUploadsLike(uris, checksums);
        for (PriorUpload update : updates) {
            for (Iterator<PriorUpload> iterator = dbCopies.iterator(); iterator.hasNext(); ) {
                PriorUpload dbCopy = iterator.next();
                if (dbCopy.isMatch(update)) {
                    update.id = dbCopy.id;
                    iterator.remove();
                    break;
                }
            }
        }
    }

    /**
     * @param updates
     * @return updateCount
     */
    @Update
    protected abstract void updateAll(List<PriorUpload> updates);


    private List<UploadDestinationPriorUploadCrossRef> buildCrossRefs(UploadDestination destination, Collection<PriorUpload> priorUploads) {
        List<UploadDestinationPriorUploadCrossRef> crossRefs = new ArrayList<>(priorUploads.size());
        for (PriorUpload priorUpload : priorUploads) {
            crossRefs.add(new UploadDestinationPriorUploadCrossRef(destination.id, priorUpload.id, priorUpload.lastUploadedAt));
        }
        return crossRefs;
    }
}
