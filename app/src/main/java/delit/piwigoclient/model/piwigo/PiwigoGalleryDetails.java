package delit.piwigoclient.model.piwigo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by gareth on 23/05/17.
 */

public class PiwigoGalleryDetails implements Serializable {

    private final ArrayList<Long> parentageChain;
    private CategoryItemStub parentGallery;
    private Long galleryId;
    private String galleryName;
    private String galleryDescription;
    private boolean userCommentsAllowed;
    private boolean isPrivate;
    private HashSet<Long> allowedGroups;
    private HashSet<Long> allowedUsers;

    public PiwigoGalleryDetails(CategoryItemStub parentGallery, Long galleryId, String galleryName, String galleryDescription, boolean userCommentsAllowed, boolean isPrivate) {
        this.parentGallery = parentGallery;
        parentageChain = new ArrayList<Long>(parentGallery.getParentageChain());
        parentageChain.add(parentGallery.getId());
        this.galleryName = galleryName;
        this.galleryId = galleryId;
        this.galleryDescription = galleryDescription;
        this.userCommentsAllowed = userCommentsAllowed;
        this.isPrivate = isPrivate;
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
}

