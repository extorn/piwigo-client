package delit.piwigoclient.database;

import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.List;

public class PriorUploadWithUploadDestinations {

    @Embedded public PriorUpload priorUpload;
    @Relation(
            parentColumn = "uri",
            entityColumn = "uploadToKey",
            associateBy = @Junction(UploadDestinationPriorUploadCrossRef.class)
    )
    public List<UploadDestination> destinations;
}
