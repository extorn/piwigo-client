package delit.piwigoclient.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(primaryKeys = {"uri", "uploadToKey"}, indices = {@Index("uri"),@Index("uploadToKey")})
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
