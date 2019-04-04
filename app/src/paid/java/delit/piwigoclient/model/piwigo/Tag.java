package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.Date;

import delit.piwigoclient.ui.common.util.ParcelUtils;

/**
 * Created by gareth on 26/06/17.
 */
public class Tag implements Identifiable, Parcelable, Serializable {
    private long id = -1;
    private String name;
    private int usageCount;
    private Date lastModified;

    public Tag(String name) {
        this.name = name;
    }

    public Tag(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Tag(long id, String name, int usageCount, Date lastModified) {
        this.id = id;
        this.name = name;
        this.usageCount = usageCount;
        this.lastModified = lastModified;
    }

    public Tag(Parcel in) {
        id = in.readLong();
        name = in.readString();
        usageCount = in.readInt();
        lastModified = ParcelUtils.readDate(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(name);
        dest.writeInt(usageCount);
        ParcelUtils.writeDate(dest, lastModified);
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(int usageCount) {
        this.usageCount = usageCount;
    }

    public Date getLastModified() {
        return lastModified;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Tag)) {
            return false;
        }
        if(((Tag) other).id < 0 || this.id < 0) {
            return ((Tag) other).name.equals(this.name);
        }
        return ((Tag) other).id == this.id;
    }

    @Override
    public int hashCode() {
        return (int) id;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<Tag> CREATOR
            = new Parcelable.Creator<Tag>() {
        public Tag createFromParcel(Parcel in) {
            return new Tag(in);
        }

        public Tag[] newArray(int size) {
            return new Tag[size];
        }
    };
}
