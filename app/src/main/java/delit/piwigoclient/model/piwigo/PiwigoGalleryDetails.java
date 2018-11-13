package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import delit.piwigoclient.ui.common.util.ParcelUtils;

/**
 * Created by gareth on 23/05/17.
 */

public class PiwigoGalleryDetails implements Parcelable {

    private final ArrayList<Long> parentageChain;
    private final CategoryItemStub parentGallery;
    private final String galleryName;
    private final String galleryDescription;
    private final boolean userCommentsAllowed;
    private final boolean isPrivate;
    private Long galleryId;
    private HashSet<Long> allowedGroups;
    private HashSet<Long> allowedUsers;

    public PiwigoGalleryDetails(CategoryItemStub parentGallery, Long galleryId, String galleryName, String galleryDescription, boolean userCommentsAllowed, boolean isPrivate) {
        this.parentGallery = parentGallery;
        parentageChain = new ArrayList<>(parentGallery.getParentageChain());
        parentageChain.add(parentGallery.getId());
        this.galleryName = galleryName;
        this.galleryId = galleryId;
        this.galleryDescription = galleryDescription;
        this.userCommentsAllowed = userCommentsAllowed;
        this.isPrivate = isPrivate;
    }

    public PiwigoGalleryDetails(Parcel in) {
        parentageChain = new ArrayList<>();
        in.readList(parentageChain, getClass().getClassLoader());
        parentGallery = in.readParcelable(getClass().getClassLoader());
        galleryName = in.readString();
        galleryDescription = in.readString();
        userCommentsAllowed = ParcelUtils.readValue(in,null, boolean.class);
        isPrivate = ParcelUtils.readValue(in,null, boolean.class);
        galleryId = ParcelUtils.readValue(in,null, Long.class);
        allowedGroups = ParcelUtils.readLongSet(in, null);
        allowedUsers = ParcelUtils.readLongSet(in, null);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeList(parentageChain);
        dest.writeParcelable(parentGallery, flags);
        dest.writeString(galleryName);
        dest.writeString(galleryDescription);
        dest.writeValue(userCommentsAllowed);
        dest.writeValue(isPrivate);
        dest.writeValue(galleryId);
        ParcelUtils.writeLongSet(dest, allowedGroups);
        ParcelUtils.writeLongSet(dest, allowedUsers);
    }

    public CategoryItemStub getParentGallery() {
        return parentGallery;
    }

    public String getGalleryName() {
        return galleryName;
    }

    public String getGalleryDescription() {
        return galleryDescription;
    }

    public boolean isUserCommentsAllowed() {
        return userCommentsAllowed;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public Set<Long> getAllowedGroups() {
        return allowedGroups;
    }

    public void setAllowedGroups(Set<Long> allowedGroups) {
        if (allowedGroups != null) {
            this.allowedGroups = new HashSet<>(allowedGroups);
        } else {
            this.allowedGroups = null;
        }
    }

    public Set<Long> getAllowedUsers() {
        return allowedUsers;
    }

    public void setAllowedUsers(Set<Long> allowedUsers) {
        if (allowedUsers != null) {
            this.allowedUsers = new HashSet<>(allowedUsers);
        } else {
            this.allowedUsers = null;
        }
    }

    public Long getGalleryId() {
        return galleryId;
    }

    public void setGalleryId(long id) {
        this.galleryId = id;
    }

    public List<Long> getParentageChain() {
        return parentageChain;
    }

    public Long getParentGalleryId() {
        return parentGallery.getId();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<PiwigoGalleryDetails> CREATOR
            = new Parcelable.Creator<PiwigoGalleryDetails>() {
        public PiwigoGalleryDetails createFromParcel(Parcel in) {
            return new PiwigoGalleryDetails(in);
        }

        public PiwigoGalleryDetails[] newArray(int size) {
            return new PiwigoGalleryDetails[size];
        }
    };
}

