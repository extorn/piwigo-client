package delit.piwigoclient.database;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Objects;

@Entity
public class PriorUpload implements Parcelable {

    public static final Creator<PriorUpload> CREATOR = new Creator<PriorUpload>() {
        @Override
        public PriorUpload createFromParcel(Parcel in) {
            return new PriorUpload(in);
        }

        @Override
        public PriorUpload[] newArray(int size) {
            return new PriorUpload[size];
        }
    };
    public String parentUri;
    @PrimaryKey
    @NonNull
    public String uri;
    public String checksum;

    public PriorUpload(@NonNull String parentUri, @NonNull String uri, @NonNull String checksum) {
        this.parentUri = parentUri;
        this.uri = uri;
        this.checksum = checksum;
    }

    protected PriorUpload(Parcel in) {
        parentUri = Objects.requireNonNull(in.readString());
        uri = Objects.requireNonNull(in.readString());
        checksum = Objects.requireNonNull(in.readString());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(parentUri);
        dest.writeString(uri);
        dest.writeString(checksum);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
