package delit.piwigoclient.database.entity;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.NO_ACTION;

/**
 * We do not delete an upload destination when a link to a child is deleted
 */
@Entity(tableName = "UploadDestination",
        indices = @Index(value = "uploadToKey", unique = true)/*,
        foreignKeys = @ForeignKey(entity = UploadDestinationPriorUploadCrossRef.class,
                                  parentColumns = "uploadToId", // the fk entity being watched
                                  childColumns = "id", // this entity
                                  onDelete = NO_ACTION)*/) // on fk entity delete
public class UploadDestination {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String uploadToKey;
    @NonNull
    public Uri serverUri;


    public UploadDestination(long id, @NonNull String uploadToKey, @NonNull Uri serverUri) {
        this.id = id;
        this.uploadToKey = uploadToKey;
        this.serverUri = serverUri;
    }

    @Ignore
    public UploadDestination(@NonNull String uploadToKey, @NonNull Uri serverUri) {
        this.uploadToKey = uploadToKey;
        this.serverUri = serverUri;
    }
}
