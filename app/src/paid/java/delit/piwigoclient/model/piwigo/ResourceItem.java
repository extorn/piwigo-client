package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;

import delit.piwigoclient.ui.common.util.ParcelUtils;

/**
 * Created by gareth on 12/07/17.
 */
public class ResourceItem extends AbstractBaseResourceItem {
    private HashSet<Tag> tags;
    private Boolean isFavorite;

    public ResourceItem(long id, String name, String description, Date creationDate, Date lastAltered, String thumbnailUrl) {
        super(id, name, description, creationDate, lastAltered, thumbnailUrl);
    }

    public ResourceItem(Parcel in) {
        super(in);
        tags = ParcelUtils.readHashSet(in);
        isFavorite = (Boolean)in.readValue(null);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        super.writeToParcel(out, flags);
        ParcelUtils.writeSet(out, tags);
        out.writeValue(isFavorite);
    }


    public void setTags(HashSet<Tag> tags) {
        this.tags = tags;
    }

    public HashSet<Tag> getTags() {
        return tags;
    }


    public boolean isFavorite() {
        return isFavorite;
    }

    public boolean hasFavoriteInfo() {
        return isFavorite != null;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public void copyFrom(ResourceItem other, boolean copyParentage) {
        super.copyFrom(other, copyParentage);
        tags = other.tags;
        isFavorite = other.isFavorite;
    }

    public static final Parcelable.Creator<ResourceItem> CREATOR
            = new Parcelable.Creator<ResourceItem>() {
        public ResourceItem createFromParcel(Parcel in) {
            return new ResourceItem(in);
        }

        public ResourceItem[] newArray(int size) {
            return new ResourceItem[size];
        }
    };
}
