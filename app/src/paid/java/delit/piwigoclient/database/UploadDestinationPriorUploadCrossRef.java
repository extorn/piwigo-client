package delit.piwigoclient.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(primaryKeys = {"uri", "uploadToKey"})
public class UploadDestinationPriorUploadCrossRef {
    @NonNull
    public String uri;
    @NonNull
    public String uploadToKey;

    public UploadDestinationPriorUploadCrossRef(@NonNull String uri, @NonNull String uploadToKey) {
        this.uri = uri;
        this.uploadToKey = uploadToKey;
    }
}
