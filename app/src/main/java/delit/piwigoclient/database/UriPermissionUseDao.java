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
public interface UriPermissionUseDao {

    @Transaction
    @Query("SELECT * FROM UriPermissionUse WHERE uri IN(:uris)")
    LiveData<List<UriPermissionUse>> loadAllByUris(Set<String> uris);

    @Transaction
    @Query("SELECT * FROM UriPermissionUse WHERE uri = :uri")
    LiveData<List<UriPermissionUse>> loadAllByUri(String uri);

    @Transaction
    @Query("SELECT * FROM UriPermissionUse")
    LiveData<List<UriPermissionUse>> loadAll();

    @Transaction
    @Query("SELECT * FROM UriPermissionUse WHERE consumerId = :consumerId")
    LiveData<List<UriPermissionUse>> loadAllByUriConsumerId(String consumerId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(UriPermissionUse... uriPermissionUses);

    @Delete
    void delete(UriPermissionUse uriPermission);

    @Query("DELETE FROM UriPermissionUse WHERE uri = :uri")
    void deleteAll(String uri);
}
