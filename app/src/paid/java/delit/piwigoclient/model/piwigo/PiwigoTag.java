package delit.piwigoclient.model.piwigo;

import java.io.Serializable;

/**
 * Helper class for providing sample content for user interfaces created by
 * Android template wizards.
 * <p>
 */
public class PiwigoTag extends ResourceContainer<Tag, GalleryItem> implements Serializable {

    public PiwigoTag(Tag tag) {
        super(tag, "ResourceItem", tag.getUsageCount());
    }

    @Override
    public int getImgResourceCount() {
        return getContainerDetails().getUsageCount();
    }
}
