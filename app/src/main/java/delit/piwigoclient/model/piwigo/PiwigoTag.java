package delit.piwigoclient.model.piwigo;

import java.io.Serializable;

/**
 * Helper class for providing sample content for user interfaces created by
 * Android template wizards.
 * <p>
 */
public class PiwigoTag extends ResourceContainer<Tag> implements Serializable {

    public PiwigoTag(Tag tag) {
        super(tag, "ResourceItem", tag.getUsageCount());
    }

    @Override
    public long getImgResourceCount() {
        return getContainerDetails().getUsageCount();
    }
}
