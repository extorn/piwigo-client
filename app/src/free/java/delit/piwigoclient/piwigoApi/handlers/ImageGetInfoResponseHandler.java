package delit.piwigoclient.piwigoApi.handlers;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;

/**
 * Created by gareth on 07/04/18.
 */
public class ImageGetInfoResponseHandler<T extends ResourceItem> extends BaseImageGetInfoResponseHandler<T> {
    public ImageGetInfoResponseHandler(T piwigoResource) {
        super(piwigoResource);
    }

    protected AlbumGetImagesBasicResponseHandler.BasicCategoryImageResourceParser buildResourceParser( boolean usingPiwigoClientOverride) {
        boolean defaultVal = Boolean.TRUE.equals(PiwigoSessionDetails.getInstance(getConnectionPrefs()).isUsingPiwigoPrivacyPlugin());
        boolean isApplyPrivacyPluginUriFix = getConnectionPrefs().isFixPiwigoPrivacyPluginMediaUris(getSharedPrefs(), getContext(), defaultVal);
        return new ImageGetInfoResourceParser(getPiwigoServerUrl(), isApplyPrivacyPluginUriFix, usingPiwigoClientOverride);
    }

    public static class ImageGetInfoResourceParser extends BaseImageGetInfoResourceParser {

        public ImageGetInfoResourceParser(String basePiwigoUrl, boolean isApplyPrivacyPluginUriFix, boolean usingPiwigoClientOverride) {
            super(basePiwigoUrl, isApplyPrivacyPluginUriFix, usingPiwigoClientOverride);
        }
    }
}
