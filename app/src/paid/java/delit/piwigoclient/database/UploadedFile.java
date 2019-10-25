package delit.piwigoclient.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(primaryKeys = {"parentPath", "name", "serverId"})
public class UploadedFile {
    @NonNull
    public String parentPath;
    @NonNull
    public String name;
    @NonNull
    public String serverId;
}
