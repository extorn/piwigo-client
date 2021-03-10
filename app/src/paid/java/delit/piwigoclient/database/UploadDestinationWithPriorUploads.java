package delit.piwigoclient.database;

import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.List;

public class UploadDestinationWithPriorUploads {
    @Embedded public UploadDestination destination;
    @Relation(
            parentColumn = "uploadToKey",
            entityColumn = "uri",
            associateBy = @Junction(UploadDestinationPriorUploadCrossRef.class)
    )
    public List<PriorUpload> priorUploads;
}
