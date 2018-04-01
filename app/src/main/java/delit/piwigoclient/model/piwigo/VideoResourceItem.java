package delit.piwigoclient.model.piwigo;

import java.util.Date;

/**
 * Created by gareth on 12/07/17.
 */
public class VideoResourceItem extends ResourceItem {

    public VideoResourceItem(long id, String name, String description, Date lastAltered, String thumbnailUrl) {
        super(id, name, description, lastAltered, thumbnailUrl);
    }

    public int getType() {
        return VIDEO_RESOURCE_TYPE;
    }

    public void copyFrom(VideoResourceItem other) {
        super.copyFrom(other);
    }
}
