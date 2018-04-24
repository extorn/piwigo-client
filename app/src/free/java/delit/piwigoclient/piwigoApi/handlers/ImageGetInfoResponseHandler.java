package delit.piwigoclient.piwigoApi.handlers;

import delit.piwigoclient.model.piwigo.ResourceItem;

/**
 * Created by gareth on 07/04/18.
 */
public class ImageGetInfoResponseHandler<T extends ResourceItem> extends BaseImageGetInfoResponseHandler<T> {
    public ImageGetInfoResponseHandler(T piwigoResource, String multimediaExtensionList) {
        super(piwigoResource, multimediaExtensionList);
    }
}
