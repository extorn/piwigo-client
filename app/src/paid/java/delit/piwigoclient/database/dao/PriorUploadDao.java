package delit.piwigoclient.database.dao;

import androidx.room.Dao;
import androidx.room.RawQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import java.util.List;

import delit.piwigoclient.database.entity.PriorUpload;
import delit.piwigoclient.database.entity.UploadDestinationPriorUploadCrossRef;

@Dao
public interface PriorUploadDao {

    @RawQuery(observedEntities = {PriorUpload.class, UploadDestinationPriorUploadCrossRef.class})
    List<String> getAllMatchingRawQuery(SupportSQLiteQuery query);
}
