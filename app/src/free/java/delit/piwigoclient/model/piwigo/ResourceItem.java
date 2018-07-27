package delit.piwigoclient.model.piwigo;

import android.support.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by gareth on 12/07/17.
 */
public class ResourceItem extends AbstractBaseResourceItem {

    public ResourceItem(long id, String name, String description, Date created, Date lastAltered, String thumbnailUrl) {
        super(id, name, description, created, lastAltered, thumbnailUrl);
    }
}
