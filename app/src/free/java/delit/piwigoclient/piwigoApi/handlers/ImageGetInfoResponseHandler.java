package delit.piwigoclient.piwigoApi.handlers;

import java.util.List;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;

/**
 * Created by gareth on 07/04/18.
 */
public class ImageGetInfoResponseHandler<T extends ResourceItem> extends BaseImageGetInfoResponseHandler<T> {
    public ImageGetInfoResponseHandler(T piwigoResource) {
        super(piwigoResource);
    }

    @Override
    protected AlbumGetImagesBasicResponseHandler.BasicCategoryImageResourceParser buildResourceParser( boolean usingPiwigoClientOverride) {
        boolean defaultVal = Boolean.TRUE.equals(PiwigoSessionDetails.getInstance(getConnectionPrefs()).isUsingPiwigoPrivacyPlugin());
        boolean isApplyPrivacyPluginUriFix = getConnectionPrefs().isFixPiwigoPrivacyPluginMediaUris(getSharedPrefs(), getContext(), defaultVal);
        PiwigoSessionDetails sessionDetails = getPiwigoSessionDetails();
        List<String> piwigoSites = null;
        if(sessionDetails.getServerConfig() != null) {
            piwigoSites = sessionDetails.getServerConfig().getSites();
        }
        return new ImageGetInfoResourceParser(getPiwigoServerUrl(), piwigoSites, isApplyPrivacyPluginUriFix, usingPiwigoClientOverride);
    }

    public static class ImageGetInfoResourceParser extends BaseImageGetInfoResourceParser {

        public ImageGetInfoResourceParser(String basePiwigoUrl, List<String> piwigoSites, boolean isApplyPrivacyPluginUriFix, boolean usingPiwigoClientOverride) {
            super(basePiwigoUrl, piwigoSites, isApplyPrivacyPluginUriFix, usingPiwigoClientOverride);
        }
    }
}
