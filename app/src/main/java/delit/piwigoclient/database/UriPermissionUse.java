package delit.piwigoclient.database;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;

import java.util.Objects;

@Entity(primaryKeys = {"uri", "consumerId"})
public class UriPermissionUse implements Parcelable {
    public static final String CONSUMER_ID_FILE_SELECT = "fileSelect";
    public static final String TRANSIENT = "transient";

    public UriPermissionUse() {}

    @Ignore
    public UriPermissionUse(@NonNull String uri, @NonNull String localizedConsumerName, int flags) {
        this.uri = uri;
        this.consumerId = CONSUMER_ID_FILE_SELECT;
        this.localizedConsumerName = localizedConsumerName;
        this.flags = flags;
    }

    @Ignore
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


    protected UriPermissionUse(Parcel in) {
        uri = Objects.requireNonNull(in.readString());
        consumerId = Objects.requireNonNull(in.readString());
        localizedConsumerName = Objects.requireNonNull(in.readString());
        flags = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(uri);
        dest.writeString(consumerId);
        dest.writeString(localizedConsumerName);
        dest.writeInt(flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<UriPermissionUse> CREATOR = new Creator<UriPermissionUse>() {
        @Override
        public UriPermissionUse createFromParcel(Parcel in) {
            return new UriPermissionUse(in);
        }

        @Override
        public UriPermissionUse[] newArray(int size) {
            return new UriPermissionUse[size];
        }
    };
}
