package delit.piwigoclient.database;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.room.Entity;

@Entity(primaryKeys = {"uri", "consumerId"})
public class UriPermissionUse {
    public static final String CONSUMER_ID_FILE_SELECT = "fileSelect";

    public UriPermissionUse() {}

    public UriPermissionUse(@NonNull String uri, @NonNull String localizedConsumerName, int flags) {
        this.uri = uri;
        this.consumerId = CONSUMER_ID_FILE_SELECT;
        this.localizedConsumerName = localizedConsumerName;
        this.flags = flags;
    }

    public UriPermissionUse(@NonNull String uri,@NonNull String consumerId, @NonNull String localizedConsumerName, int flags) {
        this(uri, localizedConsumerName, flags);
        this.consumerId = consumerId;
        if(CONSUMER_ID_FILE_SELECT.equals(consumerId)) {
            throw new IllegalArgumentException("Protected ID - cannot use this ID ("+consumerId+")");
        }
    }

    @NonNull
    public String uri;
    @NonNull
    public String consumerId;
    @NonNull
    public String localizedConsumerName;
    @NonNull
    public int flags;


}
