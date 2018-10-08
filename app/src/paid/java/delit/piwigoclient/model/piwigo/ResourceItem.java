package delit.piwigoclient.model.piwigo;

import java.util.Date;
import java.util.HashSet;

/**
 * Created by gareth on 12/07/17.
 */
public class ResourceItem extends AbstractBaseResourceItem {
    private HashSet<Tag> tags;
    private boolean isFavorite;

    public ResourceItem(long id, String name, String description, Date creationDate, Date lastAltered, String thumbnailUrl) {
        super(id, name, description, creationDate, lastAltered, thumbnailUrl);
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

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public void copyFrom(ResourceItem other, boolean copyParentage) {
        super.copyFrom(other, copyParentage);
        tags = other.tags;
        isFavorite = other.isFavorite;
    }
}
