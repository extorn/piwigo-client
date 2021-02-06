package delit.piwigoclient.piwigoApi.handlers;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;

public class AlbumGetImagesResponseHandler extends AlbumGetImagesBasicResponseHandler {

    public AlbumGetImagesResponseHandler(CategoryItem parentAlbum, String sortOrder, int page, int pageSize) {
        super(parentAlbum, sortOrder, page, pageSize);
    }

    @Override
    protected ResourceParser buildResourceParser(String basePiwigoUrl) {
        boolean defaultVal = Boolean.TRUE.equals(PiwigoSessionDetails.getInstance(getConnectionPrefs()).isUsingPiwigoPrivacyPlugin());
        boolean isApplyPrivacyPluginUriFix = getConnectionPrefs().isFixPiwigoPrivacyPluginMediaUris(getSharedPrefs(), getContext(), defaultVal);
        return new ResourceParser(basePiwigoUrl, isApplyPrivacyPluginUriFix);
    }

    public static class ResourceParser extends BasicCategoryImageResourceParser {

        public ResourceParser(String basePiwigoUrl, boolean usePrivacyPluginFix) {

            super(basePiwigoUrl, usePrivacyPluginFix);
        }
    }
}