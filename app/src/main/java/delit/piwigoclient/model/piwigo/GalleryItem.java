package delit.piwigoclient.model.piwigo;

import android.support.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * An item representing a piece of content.
 */
public class GalleryItem implements Comparable<GalleryItem>, Identifiable, Serializable {

    public static final int CATEGORY_TYPE = 0;
    public static final int PICTURE_RESOURCE_TYPE = 1;
    public static final int VIDEO_RESOURCE_TYPE = 2;
    public static final int CATEGORY_ADVERT_TYPE = 3;
    public static final int RESOURCE_ADVERT_TYPE = 4;
    public static final GalleryItem ADVERT = new GalleryItem(Long.MIN_VALUE + 1, null, null, null, null) {
        @Override
        public int getType() {
            return GalleryItem.RESOURCE_ADVERT_TYPE;
        }
    };
    private final long id;
    private String thumbnailUrl;
    private String name;
    private String description;
    private Date lastAltered;
    private ArrayList<Long> parentageChain;


    public GalleryItem(long id, String name, String description, Date lastAltered, String thumbnailUrl) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.lastAltered = lastAltered;
        parentageChain = new ArrayList<>();
    }

    public long getId() {
        return id;
    }

    public Long getParentId() {
        return parentageChain.size() == 0 ? null : parentageChain.get(parentageChain.size() - 1);
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
            if (this == CategoryItem.BLANK || o == CategoryItem.ADVERT) {
                return 1;
            } else if (o == CategoryItem.BLANK || this == CategoryItem.ADVERT) {
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
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public void copyFrom(GalleryItem other, boolean copyParentage) {
        if (id != other.id) {
            throw new IllegalArgumentException("IDs do not match");
        }
        this.name = other.name;
        this.description = other.description;
        this.thumbnailUrl = other.thumbnailUrl;
        this.lastAltered = other.lastAltered;
        if (copyParentage) {
            parentageChain = other.parentageChain;
        }
    }
}
