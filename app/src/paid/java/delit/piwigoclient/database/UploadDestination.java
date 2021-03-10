package delit.piwigoclient.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class UploadDestination {
    @PrimaryKey
    @NonNull
    public String uploadToKey;

    public UploadDestination(@NonNull String uploadToKey) {
        this.uploadToKey = uploadToKey;
    }
}
