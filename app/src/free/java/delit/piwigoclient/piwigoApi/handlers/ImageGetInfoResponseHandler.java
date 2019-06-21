package delit.piwigoclient.piwigoApi.handlers;

import java.util.Set;

import delit.piwigoclient.model.piwigo.ResourceItem;

/**
 * Created by gareth on 07/04/18.
 */
public class ImageGetInfoResponseHandler<T extends ResourceItem> extends BaseImageGetInfoResponseHandler<T> {
    public ImageGetInfoResponseHandler(T piwigoResource, Set<String> multimediaExtensionList) {
        super(piwigoResource, multimediaExtensionList);
    }

    protected BaseImagesGetResponseHandler.BasicCategoryImageResourceParser buildResourceParser(Set<String> multimediaExtensionList, boolean usingPiwigoClientOveride) {
        return new ImageGetInfoResourceParser(multimediaExtensionList, getPiwigoServerUrl(), usingPiwigoClientOveride);
    }

    public static class ImageGetInfoResourceParser extends BaseImageGetInfoResourceParser {

        public ImageGetInfoResourceParser(Set<String> multimediaExtensionList, String basePiwigoUrl, boolean usingPiwigoClientOveride) {
            super(multimediaExtensionList, basePiwigoUrl, usingPiwigoClientOveride);
        }
    }
}
