package delit.piwigoclient.piwigoApi.handlers;

import android.os.Bundle;
import android.util.Log;

import com.drew.lang.annotations.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import delit.libs.core.util.Logging;
import delit.libs.http.RequestParams;
import delit.libs.util.CollectionUtils;
import delit.libs.util.IOUtils;
import delit.piwigoclient.model.piwigo.AbstractBaseResourceItem;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class AlbumGetImagesBasicResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetResourcesRspHdlr";
    private final static String AMP_HTML_TAG = "&amp;";
    private final CategoryItem parentAlbum;
    private final String sortOrder;
    private final int pageSize;
    private final int page;

    public AlbumGetImagesBasicResponseHandler(CategoryItem parentAlbum, String sortOrder, int page, int pageSize) {
        super("pwg.categories.getImages", TAG);
        this.parentAlbum = parentAlbum;
        this.sortOrder = sortOrder;
        this.page = page;
        this.pageSize = pageSize;
    }

    @Override
    public String getPiwigoMethod() {
        return getPiwigoMethodOverrideIfPossible("piwigo_client.categories.getImages");
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        if(parentAlbum != null) {
            params.put("cat_id", String.valueOf(parentAlbum.getId()));
        }
        if(!"server".equals(sortOrder)) {
            params.put("order", sortOrder);
        }
        params.put("page", String.valueOf(page));
        params.put("per_page", String.valueOf(pageSize));
        params.put("pwg_token", getPwgSessionToken());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {

        ArrayList<GalleryItem> resources = new ArrayList<>();


        JsonObject result = rsp.getAsJsonObject();
        JsonArray images;

        int totalResourceCount = pageSize + 1; // to ensure it tries getting the next page (if there's no paging info something is very odd!)
        if(result.has("paging")) {
            JsonObject pagingObj = result.get("paging").getAsJsonObject();
            int page = pagingObj.get("page").getAsInt();
            int pageSize = pagingObj.get("per_page").getAsInt();
            totalResourceCount = pagingObj.get("total_count").getAsInt();
        }
        if(result.has("images") && result.get("images").isJsonArray()) {
            images = result.get("images").getAsJsonArray();
        } else if(result.has("_content") && result.get("_content").isJsonArray()) {
            images = result.get("_content").getAsJsonArray();
        } else {
            if(isCached) {
                Logging.log(Log.WARN, TAG, "Unable to find images in cached response " + result.toString());
            } else {
                Logging.log(Log.WARN, TAG, "Unable to find images in response " + result.toString());
            }
            images = null;
        }

        PiwigoSessionDetails sessionDetails = getPiwigoSessionDetails();
        List<String> sites = null;
        if(sessionDetails.getServerConfig() != null) {
            sites = sessionDetails.getServerConfig().getSites();
        }
        BasicCategoryImageResourceParser resourceParser = buildResourceParser(getPiwigoServerUrl(), sites);

        if(images != null) {
            for (int i = 0; i < images.size(); i++) {
                JsonObject image = (JsonObject) images.get(i);
                ResourceItem item = resourceParser.parseAndProcessResourceData(image);
                if(parentAlbum != null) {
                    // will be null when retrieving favorites.
                    item.setParentageChain(parentAlbum.getParentageChain(), parentAlbum.getId());
                }
                resources.add(item);

            }
        }

        if (resourceParser.isFixedImageUrisForPrivacyPluginUser()) {
            Bundle b = new Bundle();
            PiwigoSessionDetails.writeToBundle(b, getConnectionPrefs());
            Logging.logAnalyticEvent(getContext(),"PRIVACY_PLUGIN_URI_FIX_2", b);
        }
        if (resourceParser.isFixedImageUrisWithAmpEscaping()) {
            Bundle b = new Bundle();
            PiwigoSessionDetails.writeToBundle(b, getConnectionPrefs());
            Logging.logAnalyticEvent(getContext(),"AMPERSAND_URI_FIX", b);
        }
        if (resourceParser.isFixedPrivacyPluginImageUrisForPrivacyPluginUser()) {
            Bundle b = new Bundle();
            PiwigoSessionDetails.writeToBundle(b, getConnectionPrefs());
            Logging.logAnalyticEvent(getContext(),"PRIVACY_PLUGIN_URI_FIX_1", b);
        }

        PiwigoGetResourcesResponse r = new PiwigoGetResourcesResponse(getMessageId(), getPiwigoMethod(), page, pageSize, totalResourceCount, resources, isCached);
        storeResponse(r);
    }

    protected BasicCategoryImageResourceParser buildResourceParser(String basePiwigoUrl, List<String> piwigoSites) {
        boolean defaultVal = Boolean.TRUE.equals(getPiwigoSessionDetails().isUsingPiwigoPrivacyPlugin());
        boolean isApplyPrivacyPluginUriFix = getConnectionPrefs().isFixPiwigoPrivacyPluginMediaUris(getSharedPrefs(), getContext(), defaultVal);
        return new BasicCategoryImageResourceParser(basePiwigoUrl, piwigoSites, isApplyPrivacyPluginUriFix);
    }

    public static class BasicCategoryImageResourceParser {

        private final boolean usePrivacyPluginFix;
        private MultimediaUriMatcherUtil multimediaUriMatcherUtil;
        private final String basePiwigoUrl;
        private final List<String> piwigoSites;
        private boolean fixedImageUrisForPrivacyPluginUser;
        private boolean fixedImageUrisWithAmpEscaping;
        private boolean fixedPrivacyPluginImageUrisForPrivacyPluginUser;

        public boolean isFixedImageUrisForPrivacyPluginUser() {
            return fixedImageUrisForPrivacyPluginUser;
        }

        public boolean isFixedImageUrisWithAmpEscaping() {
            return fixedImageUrisWithAmpEscaping;
        }

        public boolean isFixedPrivacyPluginImageUrisForPrivacyPluginUser() {
            return fixedPrivacyPluginImageUrisForPrivacyPluginUser;
        }

        /**
         * Sets up the multimedia pattern to have 3 groups.
         * For input: http://myserver.com/piwigo/2021/_data/i/upload/2021/01/01/myFile.jpg
         * Group 1: http://myserver.com/piwigo/
         * Group 2: 2021/_data/i
         * Group 3: upload/2021/01/01/myfile.mp4
         * Group 4: mp4
         *
         * @param basePiwigoUrl the Uri path to piwigo homepage
         */
        public BasicCategoryImageResourceParser(String basePiwigoUrl, List<String> piwigoSites, boolean usePrivacyPluginFix) {
            this.basePiwigoUrl = basePiwigoUrl;
            this.piwigoSites = piwigoSites;
            this.usePrivacyPluginFix = usePrivacyPluginFix;
        }

        public ResourceItem parseAndProcessResourceData(JsonObject image) throws JSONException {

            long id = image.get("id").getAsLong();

            String name = null;
            JsonElement nameJsonElem = image.get("name");
            if (nameJsonElem != null && !nameJsonElem.isJsonNull()) {
                name = nameJsonElem.getAsString();
            }

            //TODO check if this is the community plugin only... not available in standard response
            String description = null;
            JsonElement descJsonElem = image.get("comment");
            if (descJsonElem != null && !descJsonElem.isJsonNull()) {
                description = descJsonElem.getAsString();
            }

            String originalResourceUrl = null;
            JsonElement origUrlElem = image.get("element_url");
            if (origUrlElem != null && !origUrlElem.isJsonNull()) {
                originalResourceUrl = origUrlElem.getAsString();
            }
            JsonObject derivatives = image.get("derivatives").getAsJsonObject();

            ResourceItem item = null;

            if (originalResourceUrl != null) {
                if (multimediaUriMatcherUtil == null) {
                    multimediaUriMatcherUtil = new MultimediaUriMatcherUtil(basePiwigoUrl, piwigoSites, originalResourceUrl);
                } else {
                    multimediaUriMatcherUtil.withNewUri(originalResourceUrl);
                }
            }

            Date dateLastAltered = null;
            JsonElement dateLastAlteredElem = image.get("date_available");
            if (!dateLastAlteredElem.isJsonNull()) {
                String dateLastAlteredStr = dateLastAlteredElem.getAsString();
                try {
                    dateLastAltered = parsePiwigoServerDate(dateLastAlteredStr);
                } catch (ParseException e) {
                    Logging.recordException(e);
                    throw new JSONException("Unable to parse date " + dateLastAlteredStr);
                }
            }

            // not available in standard response. Available in the community plugin response?
            Date dateCreated = null;
            JsonElement dateCreationElem = image.get("date_creation");
            if (!dateCreationElem.isJsonNull()) {
                String dateCreatedStr = dateCreationElem.getAsString();
                try {
                    dateCreated = parsePiwigoServerDate(dateCreatedStr);
                } catch (ParseException e) {
                    Logging.recordException(e);
                    throw new JSONException("Unable to parse date " + dateCreatedStr);
                }
            }


            HashSet<Long> linkedAlbums = new HashSet<>();
            JsonElement elem = image.get("categories");
            if (elem != null && elem.isJsonArray()) {
                JsonArray linkedAlbumsJsonArr = image.get("categories").getAsJsonArray();
                for (int j = 0; j < linkedAlbumsJsonArr.size(); j++) {
                    JsonObject catJsonObj = linkedAlbumsJsonArr.get(j).getAsJsonObject();
                    linkedAlbums.add(catJsonObj.get("id").getAsLong());
                }
            }

            int originalResourceUrlWidth = 0;
            if (image.has("width") && !image.get("width").isJsonNull()) {
                originalResourceUrlWidth = image.get("width").getAsInt();
            }

            int originalResourceUrlHeight = 0;
            if (image.has("height") && !image.get("height").isJsonNull()) {
                originalResourceUrlHeight = image.get("height").getAsInt();
            }


            if (originalResourceUrl != null && multimediaUriMatcherUtil.matchesUri()) {
                //FIXME Ask PiwigoPrivacy dev why must we do something special for the privacy plugin?

                String thumbnailUriStr = derivatives.get("thumb").getAsJsonObject().get("url").getAsString();

                // don't need to explicit instruction about privacy plugin this mode. we just do it if we find the uri matches.
                String fixedUri = multimediaUriMatcherUtil.fixUsingThumbnailUri(thumbnailUriStr);
                if (fixedUri != null) {
                    originalResourceUrl = fixedUri;
                    fixedPrivacyPluginImageUrisForPrivacyPluginUser = true;
                } else if (usePrivacyPluginFix) {
                    //is a video - need to ensure the file is accessed via piwigo privacy plugin if installed (direct access blocked).
                    //NOTE, it will be null if we aren't admin user.
                    fixedUri = multimediaUriMatcherUtil.ensurePathContainsResourceId(id);
                    if (fixedUri != null) {
                        originalResourceUrl = fixedUri;
                        fixedImageUrisForPrivacyPluginUser = true;
                    }
                }
                String mimeType = IOUtils.getMimeType(multimediaUriMatcherUtil.getFileExt());
                if (IOUtils.isPlayableMedia(mimeType)) {
                    item = new VideoResourceItem(id, name, description, dateCreated, dateLastAltered, basePiwigoUrl);
                    item.setThumbnailUrl(thumbnailUriStr); // note we are using this as is. Only the original resource Uri gets altered.
                    item.addResourceFile(AbstractBaseResourceItem.ResourceFile.ORIGINAL, fixUrl(originalResourceUrl), originalResourceUrlWidth, originalResourceUrlHeight, false);
                }
            }
            if (item == null) {


                PictureResourceItem picItem = new PictureResourceItem(id, name, description, dateCreated, dateLastAltered, basePiwigoUrl);

                addDerivatives(derivatives, picItem);

                if (originalResourceUrl != null) {
                    picItem.addResourceFile(AbstractBaseResourceItem.ResourceFile.ORIGINAL, fixUrl(originalResourceUrl), originalResourceUrlWidth, originalResourceUrlHeight, false);
                }

                item = picItem;
            }

            if(image.has("formats")) {
                JsonElement formatsElem = image.get("formats");
                if(!(formatsElem instanceof JsonNull)) {
                    JsonArray formats = formatsElem.getAsJsonArray();
                    addFormats(formats, item, originalResourceUrlWidth, originalResourceUrlHeight);
                }
            }

            item.setLinkedAlbums(linkedAlbums);

            return item;
        }

        private void addFormats(JsonArray formats, ResourceItem item, int originalWidth, int originalHeight) {

            boolean needToFixUrl = true; // if we don't fix one, we don't fix them all.
            for (JsonElement format : formats) {
                JsonObject imageSizeObj = format.getAsJsonObject().get("format").getAsJsonObject();
                String imageFormatKey = imageSizeObj.get("ext").getAsString();
                long imageFormatSizeKb = imageSizeObj.get("sizeKb").getAsLong();
                JsonElement jsonElem = imageSizeObj.get("uri");

                String url = jsonElem.getAsString();

                if (needToFixUrl) {
                    String fixed = fixUrl(url);
                    if (fixed.equals(url)) {
                        needToFixUrl = false;
                    } else {
                        url = fixed;
                    }
                }
                item.addResourceFile(imageFormatKey, url, originalWidth, originalHeight, true);
            }
        }

        private void addDerivatives(JsonObject derivatives, PictureResourceItem picItem) {
            Iterator<String> imageSizeKeys = derivatives.keySet().iterator();
            boolean needToFixUrl = true; // if we don't fix one, we don't fix them all.
            while (imageSizeKeys.hasNext()) {
                String imageSizeKey = imageSizeKeys.next();
                JsonObject imageSizeObj = derivatives.get(imageSizeKey).getAsJsonObject();
                JsonElement jsonElem = imageSizeObj.get("url");
                if (jsonElem == null || jsonElem.isJsonNull()) {
                    continue;
                }
                String url = jsonElem.getAsString();

                jsonElem = imageSizeObj.get("width");
                if (jsonElem == null || jsonElem.isJsonNull()) {
                    continue;
                }
                int thisImageWidth = jsonElem.getAsInt();

                jsonElem = imageSizeObj.get("height");
                if (jsonElem == null || jsonElem.isJsonNull()) {
                    continue;
                }
                int thisImageHeight = jsonElem.getAsInt();

                if (needToFixUrl) {
                    String fixed = fixUrl(url);
                    if (fixed.equals(url)) {
                        needToFixUrl = false;
                    } else {
                        url = fixed;
                    }
                }
                picItem.addResourceFile(imageSizeKey, url, thisImageWidth, thisImageHeight, false);

            }
        }

        private String fixUrl(String url) {
            String fixedUrl = url;
            int idx = url.indexOf('&');
            if (idx > 0 && url.regionMatches(idx, AMP_HTML_TAG, 0, AMP_HTML_TAG.length())) {
                //strip the unwanted extra html escaping
                fixedUrl = url.replaceAll(AMP_HTML_TAG, "&");
                fixedImageUrisWithAmpEscaping = true;
            }
            return fixedUrl;
        }
    }

    public static class MultimediaUriMatcherUtil {

        private final Matcher matcher;

        public MultimediaUriMatcherUtil(String basePiwigoUrl, List<String> sitePaths, String originalResourceUrl) {
            String basePiwigoUri = basePiwigoUrl;
            if (basePiwigoUri.charAt(basePiwigoUri.length() - 1) != '/') {
                basePiwigoUri += '/';
            }
            String sitePathsPattern = CollectionUtils.toCsvList(sitePaths, "|");
            if(sitePathsPattern != null) {
                sitePathsPattern = "|" + sitePathsPattern;
            } else {
                sitePathsPattern = "";
            }
            String pattern = "^(" + basePiwigoUri + ")([\\d]*/.*)?((?<=/)(?:upload"+sitePathsPattern+")/.*\\.([a-zA-Z0-9]{3,5}))$";
            Pattern multimediaPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            matcher = multimediaPattern.matcher(originalResourceUrl);
        }

        public boolean matchesUri() {
            return matcher.matches();
        }

        public void withNewUri(String uri) {
            matcher.reset(uri);
        }

        public String getServerBasePath() {
            return matcher.group(1); // "Https://myserver.com/piwigo/"
        }

        public String getPathPrefix() {
            return matcher.group(2);  // "12344/.*/"
        }

        public String getUploadsPath() {
            return matcher.group(3); // "upload.*"
        }

        public String getFileExt() {
            return matcher.group(4); // "upload.*"
        }

        private boolean thumbnailUriMatchesPrivacyPlugin(String thumbnailUriStr) {
            return thumbnailUriStr.matches(".*piwigo_privacy/get\\.php\\?.*");
        }

        private String getUriBasedOnThumbnailUri(String thumbnailUriStr) {
            return thumbnailUriStr.replaceFirst("(^.*file=)([^&]*)(.*)", "$1." + getUploadsPath() + "$3");
        }

        /**
         * Only use this if the privacy plugin is enabled.
         * @param id the piwigo resource id
         * @return null if no change was needed (i.e. use the original uri)
         */
        public @Nullable String ensurePathContainsResourceId(long id) {
            if (isPathMissingResourceId()) {
                return getServerBasePath() + id + '/' + getUploadsPath();
            }
            return null;
        }

        /**
         *
         * @param thumbnailUriStr the uri for the media resource thumbnail
         * @return null if no change was needed (i.e. use the original uri)
         */
        public @Nullable String fixUsingThumbnailUri(String thumbnailUriStr) {
            if(thumbnailUriMatchesPrivacyPlugin(thumbnailUriStr)) {
                // I think this is piwigo_privacy simple mode.
                return getUriBasedOnThumbnailUri(thumbnailUriStr);
            }
            return null;
        }

        public boolean isPathMissingResourceId() {
            return getPathPrefix() == null;
        }
    }


    public static class PiwigoGetResourcesResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {

        private final int page;
        private final int pageSize;
        private final int totalResourceCount;
        private final ArrayList<GalleryItem> resources;

        public PiwigoGetResourcesResponse(long messageId, String piwigoMethod, int page, int pageSize, int totalResourceCount, ArrayList<GalleryItem> resources, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.page = page;
            this.pageSize = pageSize;
            this.totalResourceCount = totalResourceCount;
            this.resources = resources;
        }

        public int getTotalResourceCount() {
            return totalResourceCount;
        }

        public int getPage() {
            return page;
        }

        public int getPageSize() {
            return pageSize;
        }

        public ArrayList<GalleryItem> getResources() {
            return resources;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}