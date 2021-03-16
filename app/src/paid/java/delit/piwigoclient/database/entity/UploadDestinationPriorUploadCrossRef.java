package delit.piwigoclient.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Date;

import static androidx.room.ForeignKey.CASCADE;
import static androidx.room.ForeignKey.RESTRICT;
import static androidx.room.OnConflictStrategy.IGNORE;

/**
 * If a prior upload is deleted, we cascade delete any links to the upload destination(s)
 * We do not allow an upload destination to be deleted while there are still linked prior uploads
 */
@Entity(tableName = "UploadDestinationPriorUploadCrossRef",
        indices = {@Index(value = "priorUploadId"), @Index(value= "uploadToId")},
        foreignKeys = {@ForeignKey(entity = UploadDestination.class,
                                   parentColumns = "id", // the fk entity field
                                   childColumns = "uploadToId", // this entity
                                   onUpdate = IGNORE,
                                   onDelete = RESTRICT), // on fk entity delete
                       @ForeignKey(entity = PriorUpload.class,
                                   parentColumns = "id", // the fk entity field
                                   childColumns = "priorUploadId", // this entity
                                   onUpdate = IGNORE,
                                   onDelete = CASCADE)} // on fk entity delete
        )
public class UploadDestinationPriorUploadCrossRef {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long priorUploadId;
    public long uploadToId;
    @NonNull
    public Date uploadedAt;


    public UploadDestinationPriorUploadCrossRef(long id, long uploadToId, long priorUploadId, @NonNull Date uploadedAt) {
        this.id = id;
        this.priorUploadId = priorUploadId;
        this.uploadToId = uploadToId;
        this.uploadedAt = uploadedAt;
    }

    @Ignore
    public UploadDestinationPriorUploadCrossRef(long uploadToId, long priorUploadId, @NonNull Date uploadedAt) {
        this.priorUploadId = priorUploadId;
        this.uploadToId = uploadToId;
        this.uploadedAt = uploadedAt;
    }

    @NonNull
    public Date getUploadedAt() {
        return uploadedAt;
    }
}
