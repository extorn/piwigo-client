package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

import delit.libs.ui.util.ParcelUtils;

/**
 * Created by gareth on 26/06/17.
 */
public class Tag implements Identifiable, Parcelable, PhotoContainer {
    private long id = -1;
    private String name;
    private int photoCount;
    private Date lastModified;
    private boolean isAdminCopy;

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
        this.photoCount = usageCount;
        this.lastModified = lastModified;
    }

    public Tag(Parcel in) {
        id = in.readLong();
        name = in.readString();
        photoCount = in.readInt();
        lastModified = ParcelUtils.readDate(in);
        isAdminCopy = ParcelUtils.readBool(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(name);
        dest.writeInt(photoCount);
        ParcelUtils.writeDate(dest, lastModified);
        ParcelUtils.writeBool(dest, isAdminCopy);
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

    public int getPhotoCount() {
        return photoCount;
    }

    public void setPhotoCount(int usageCount) {
        this.photoCount = usageCount;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public boolean isAdminCopy() {
        return isAdminCopy;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Tag)) {
            return false;
        }
        Tag otherTag = (Tag) other;
        if(otherTag.id < 0 || this.id < 0) {
            return otherTag.name.equals(this.name) && otherTag.isAdminCopy == this.isAdminCopy;
        }
        return otherTag.id == this.id && otherTag.isAdminCopy == this.isAdminCopy;
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

    /**
     * @param pageSize max photo count to a page
     * @return number of pages of photos available (remember pages are zero indexed)
     */
    @Override
    public int getPagesOfPhotos(int pageSize) {
        int pages = ((getPhotoCount() / pageSize) + (getPhotoCount() % pageSize > 0 ? 1 : 0));
        return Math.max(pages, 0);
    }

    public void markAsAdminCopy() {
        isAdminCopy = true;
    }
}
