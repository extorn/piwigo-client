package delit.piwigoclient.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;
import java.util.Set;

@Dao
public interface PriorUploadDao {

    @Transaction
    @Query("SELECT * FROM PriorUpload WHERE checksum IN(:checksums)")
    LiveData<List<PriorUpload>> loadAllByChecksums(Set<String> checksums);

    @Transaction
    @Query("SELECT * FROM PriorUpload WHERE uri IN(:uris)")
    LiveData<List<PriorUpload>> loadAllByUris(Set<String> uris);

    @Transaction
    @Query("SELECT * FROM PriorUpload WHERE uri = :uri")
    LiveData<List<PriorUpload>> loadAllByUri(String uri);

    @Transaction
    @Query("SELECT * FROM PriorUpload WHERE parentUri = :parentUri")
    LiveData<List<PriorUpload>> loadAllByParentUri(String parentUri);

    @Transaction
    @Query("SELECT * FROM PriorUpload WHERE checksum = :checksum")
    LiveData<List<PriorUpload>> loadAllByChecksum(String checksum);

    @Transaction
    @Query("SELECT * FROM PriorUpload")
    LiveData<List<PriorUploadWithUploadDestinations>> loadAllWithUploadDestinations();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(PriorUpload... priorUploads);

    @Delete
    void delete(PriorUpload priorUpload);

    @Query("DELETE FROM PriorUpload WHERE uri = :uri")
    void deleteAll(String uri);

    @Query("DELETE FROM PriorUpload WHERE uri IN(:uris)")
    void deleteAll(Set<String> uris);


}
