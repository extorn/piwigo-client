package delit.piwigoclient.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface UploadedFileDao {
    @Query("SELECT * FROM uploadedFile WHERE parentPath = :parentPath AND serverId = :serverId")
    List<UploadedFile> loadAllByParentPathForServerId(String parentPath, String serverId);

    @Query("SELECT name FROM uploadedFile WHERE parentPath = :parentPath AND serverId = :serverId")
    List<String> loadAllFilenamesByParentPathForServerId(String parentPath, String serverId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(UploadedFile... uploadedFiles);

    @Delete
    void delete(UploadedFile uploadedFile);

    @Query("DELETE FROM uploadedFile WHERE parentPath = :parentPath AND name NOT IN (:filenamesToKeep)")
    void removeObsolete(String parentPath, String[] filenamesToKeep);
}
