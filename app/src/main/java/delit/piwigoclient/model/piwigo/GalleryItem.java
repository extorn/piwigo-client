package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;

/**
 * An item representing a piece of content.
 */
public class GalleryItem implements Comparable<GalleryItem>, Identifiable, Parcelable {

    private static final String TAG = "GalleryItem";
    public static final int CATEGORY_TYPE = 0;
    public static final int PICTURE_RESOURCE_TYPE = 1;
    public static final int VIDEO_RESOURCE_TYPE = 2;
    public static final int ALBUM_HEADING_TYPE = 3;
    public static final int PICTURE_HEADING_TYPE = 4;
    public static final int ADVERT_TYPE = 5;

    public static final GalleryItem PICTURE_HEADING = new GalleryItem(Long.MIN_VALUE + 2, null, null, null, null) {

        @Override
        public int getType() {
            return GalleryItem.PICTURE_HEADING_TYPE;
        }

        @NonNull
        @Override
        public String toString() {
            return "PicturesHeading";
        }
    };

    private long id; // this is final... except blank category items need to alter it
    private String name;
    private String description;
    private Date lastAltered;
    private ArrayList<Long> parentageChain;
    private long loadedAt;
    private String baseResourceUrl;


    public GalleryItem(long id, String name, String description, Date lastAltered, String baseResourceUrl) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.lastAltered = lastAltered;
        parentageChain = new ArrayList<>();
        this.loadedAt = System.currentTimeMillis();
        this.baseResourceUrl = baseResourceUrl;
    }

    public GalleryItem(Parcel in) {
        id = in.readLong();
        name = in.readString();
        description = in.readString();
        lastAltered = ParcelUtils.readDate(in);
        parentageChain = ParcelUtils.readLongArrayList(in);
        loadedAt = in.readLong();
        baseResourceUrl = in.readString();
    }

    protected final String getFullPath(String urlPath) {
//        if (urlPath != null && baseResourceUrl != null) {
//            return baseResourceUrl + urlPath;
//        }
        return urlPath;
    }

    protected final String getRelativePath(String urlPath) {
//        if (urlPath != null && baseResourceUrl != null) {
//            return urlPath.substring(baseResourceUrl.length());
//        }
        return urlPath;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(id);
        out.writeString(name);
        out.writeString(description);
        ParcelUtils.writeDate(out, lastAltered);
        ParcelUtils.writeLongArrayList(out, parentageChain);
        out.writeLong(loadedAt);
        out.writeString(baseResourceUrl);
    }

    public long getId() {
        return id;
    }

    public Long getParentId() {
        return parentageChain.size() == 0 ? null : parentageChain.get(parentageChain.size() - 1);
    }

    protected String getBaseResourceUrl() {
        return baseResourceUrl;
    }

    public void setParentageChain(List<Long> parentageChain, long directParent) {
        this.parentageChain = new ArrayList<>(parentageChain);
        this.parentageChain.add(directParent);
    }

    public List<Long> getParentageChain() {
        return Collections.unmodifiableList(parentageChain);
    }

    public void setParentageChain(List<Long> parentageChain) {
        this.parentageChain = new ArrayList<>(parentageChain);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GalleryItem && ((GalleryItem) other).id == this.id;
    }

    @Override
    public int hashCode() {
        return (int) id;
    }

    @NonNull
    @Override
    public String toString() {
        return String.valueOf(id);
    }

    @Override
    public int compareTo(@NonNull GalleryItem o) {
        boolean isCategory = this instanceof CategoryItem;
        boolean otherIsCategory = o instanceof CategoryItem;
        // Place all Categories first
        if (isCategory && !otherIsCategory) {
            return -1;
        }
        if (!isCategory && otherIsCategory) {
            return 1;
        }
        // both are categories
        if (isCategory) {
            if (CategoryItem.BLANK.equals(this)  || CategoryItem.ALBUM_HEADING.equals(o)) {
                return 1;
            } else if (CategoryItem.BLANK.equals(o) || CategoryItem.ALBUM_HEADING.equals(this)) {
                return -1;
            }
            return -this.name.compareTo(o.name);
        }
        // neither are categories
        return -this.name.compareTo(o.name);
    }

    public int getType() {
        return PICTURE_RESOURCE_TYPE;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getLastAltered() {
        return lastAltered;
    }

    public String getThumbnailUrl() {
        return null;
    }

    public void copyFrom(GalleryItem other, boolean copyParentage) {
        if (id != other.id) {
            throw new IllegalArgumentException("IDs do not match");
        }
        this.name = other.name;
        this.description = other.description;
        this.lastAltered = other.lastAltered;
        if (copyParentage) {
            parentageChain = other.parentageChain;
        }
        this.loadedAt = other.loadedAt;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<GalleryItem> CREATOR
            = new Creator<GalleryItem>() {
        public GalleryItem createFromParcel(Parcel in) {
            try {
                return new GalleryItem(in);
            } catch(RuntimeException e) {
                Logging.log(Log.ERROR, TAG, "Unable to create gallery item from parcel: " + in.toString());
                throw e;
            }
        }

        public GalleryItem[] newArray(int size) {
            return new GalleryItem[size];
        }
    };

    protected boolean isLikelyOutdated(long flagTime) {
        return System.currentTimeMillis() - flagTime > 5 * 60 * 1000; // older than 5 minutes.
    }

    public boolean isLikelyOutdated() {
        return isLikelyOutdated(loadedAt);
    }

    protected void setId(long id) {
        this.id = id;
    }

    public ArrayList<Long> getFullPath() {
        ArrayList<Long> fullPath = new ArrayList<>(parentageChain.size() + 1);
        fullPath.addAll(parentageChain);
        fullPath.add(id);
        return fullPath;
    }

    public boolean isFromServer() {
        return id > 0;
    }
}
