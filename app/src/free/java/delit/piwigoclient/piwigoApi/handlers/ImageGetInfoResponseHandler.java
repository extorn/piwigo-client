package delit.piwigoclient.piwigoApi.handlers;

import delit.piwigoclient.model.piwigo.ResourceItem;

/**
 * Created by gareth on 07/04/18.
 */
public class ImageGetInfoResponseHandler<T extends ResourceItem> extends BaseImageGetInfoResponseHandler<T> {
    public ImageGetInfoResponseHandler(T piwigoResource) {
        super(piwigoResource);
    }

    protected AlbumGetImagesBasicResponseHandler.BasicCategoryImageResourceParser buildResourceParser( boolean usingPiwigoClientOverride) {
        return new ImageGetInfoResourceParser(getPiwigoServerUrl(), usingPiwigoClientOverride);
    }

    public static class ImageGetInfoResourceParser extends BaseImageGetInfoResourceParser {

        public ImageGetInfoResourceParser(String basePiwigoUrl, boolean usingPiwigoClientOverride) {
            super(basePiwigoUrl, usingPiwigoClientOverride);
        }
    }
}
