package delit.piwigoclient.piwigoApi.handlers;

import java.util.HashSet;
import java.util.Iterator;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.http.RequestParams;

/**
 * Created by gareth on 07/04/18.
 */
public class ImageUpdateInfoResponseHandler<T extends ResourceItem> extends BaseImageUpdateInfoResponseHandler<T> {
    public ImageUpdateInfoResponseHandler(T piwigoResource) {
        super(piwigoResource);
    }
}
