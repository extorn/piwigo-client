package delit.piwigoclient.model.piwigo;

import java.util.Date;

/**
 * Created by gareth on 12/07/17.
 */
public class PictureResourceItem extends ResourceItem {
    private ResourceFile fullScreenImage;

    public PictureResourceItem(long id, String name, String description, Date lastAltered, String thumbnailUrl) {
        super(id, name, description, lastAltered, thumbnailUrl);
    }

    public ResourceFile getFullScreenImage() {
        return fullScreenImage;
    }

    public void setFullScreenImage(ResourceFile fullScreenImage) {
        this.fullScreenImage = fullScreenImage;
    }


    public int getType() {
        return PICTURE_RESOURCE_TYPE;
    }
}
