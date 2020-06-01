package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import delit.libs.util.ObjectUtils;

public class Username implements Identifiable, Parcelable, Comparable<Username> {
    private final long id;
    private final String username;
    private final String userType; //guest,    generic,    normal,    admin,    webmaster

    public Username(long id, String username, String userType) {
        this.id = id;
        this.username = username;
        this.userType = userType;
    }

    public Username(Parcel in) {
        id = in.readLong();
        username = in.readString();
        userType = in.readString();
    }

    public String getUsername() {
        return username;
    }

    public String getUserType() {
        return userType;
    }

    public long getId() {
        return id;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(username);
        dest.writeString(userType);
    }

    public static final Parcelable.Creator<Username> CREATOR
            = new Parcelable.Creator<Username>() {
        public Username createFromParcel(Parcel in) {
            return new Username(in);
        }

        public Username[] newArray(int size) {
            return new Username[size];
        }
    };

    @Override
    public int compareTo(Username o) {
        return ObjectUtils.compare(username, o.username); // null safe natural compare
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if(!(obj instanceof Username)) {
            return false;
        }
        Username other = (Username) obj;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return (int)id;
    }
}