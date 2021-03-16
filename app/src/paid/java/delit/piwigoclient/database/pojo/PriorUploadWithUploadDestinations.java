package delit.piwigoclient.database.pojo;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.List;

import delit.piwigoclient.database.entity.PriorUpload;
import delit.piwigoclient.database.entity.UploadDestination;
import delit.piwigoclient.database.entity.UploadDestinationPriorUploadCrossRef;

public class PriorUploadWithUploadDestinations {

    @Embedded public PriorUpload priorUpload;
    @Relation(
            parentColumn = "uri",
            entityColumn = "uploadToKey",
            associateBy = @Junction(UploadDestinationPriorUploadCrossRef.class)
    )
    public List<UploadDestination> destinations;
}
