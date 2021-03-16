package delit.piwigoclient.database.pojo;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.List;

import delit.piwigoclient.database.entity.PriorUpload;
import delit.piwigoclient.database.entity.UploadDestination;
import delit.piwigoclient.database.entity.UploadDestinationPriorUploadCrossRef;

public class UploadDestinationWithPriorUploads {
    @Embedded public UploadDestination destination;
    @Relation(
            parentColumn = "uploadToKey",
            entityColumn = "uri",
            associateBy = @Junction(UploadDestinationPriorUploadCrossRef.class)
    )
    public List<PriorUpload> priorUploads;

    public UploadDestinationWithPriorUploads(UploadDestination uploadDestination) {
        destination = uploadDestination;
    }

    public void setPriorUploads(List<PriorUpload> priorUploads) {
        this.priorUploads = priorUploads;
    }
}
