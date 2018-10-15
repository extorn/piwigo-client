package delit.piwigoclient.piwigoApi.handlers;

import delit.piwigoclient.model.piwigo.ResourceItem;

/**
 * Created by gareth on 07/04/18.
 */
public class ImageUpdateInfoResponseHandler<T extends ResourceItem> extends BaseImageUpdateInfoResponseHandler<T> {
    public ImageUpdateInfoResponseHandler(T piwigoResource, boolean updateTags) {
        super(piwigoResource);
    }
}
