package delit.piwigoclient.model.piwigo;

import java.util.Date;

/**
 * Created by gareth on 12/07/17.
 */
public class ResourceItem extends AbstractBaseResourceItem {

    public ResourceItem(long id, String name, String description, Date created, Date lastAltered, String thumbnailUrl) {
        super(id, name, description, created, lastAltered, thumbnailUrl);
    }
}
