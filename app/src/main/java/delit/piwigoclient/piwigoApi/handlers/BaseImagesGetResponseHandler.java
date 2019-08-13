package delit.piwigoclient.piwigoApi.handlers;

import android.os.Bundle;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class BaseImagesGetResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetResourcesRspHdlr";
    private final Set<String> multimediaExtensionList;
    private final CategoryItem parentAlbum;
    private final String sortOrder;
    private final int pageSize;
    private final int page;

    public BaseImagesGetResponseHandler(CategoryItem parentAlbum, String sortOrder, int page, int pageSize, Set<String> multimediaExtensionList) {
        super("pwg.categories.getImages", TAG);
        this.parentAlbum = parentAlbum;
        this.sortOrder = sortOrder;
        this.page = page;
        this.pageSize = pageSize;
        this.multimediaExtensionList = multimediaExtensionList;
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
        params.put("order", sortOrder);
        params.put("page", String.valueOf(page));
        params.put("per_page", String.valueOf(pageSize));
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
            totalResourceCount = pagingObj.get("count").getAsInt();
        }
        if(result.has("images") && result.get("images").isJsonArray()) {
            images = result.get("images").getAsJsonArray();
        } else if(result.has("_content") && result.get("_content").isJsonArray()) {
            images = result.get("_content").getAsJsonArray();
        } else {
            if(isCached) {
                Log.w(TAG, "Unable to find images in cached response " + rsp.getAsString());
            } else {
                Log.w(TAG, "Unable to find images in response " + rsp.getAsString());
            }
            images = null;
        }

        BasicCategoryImageResourceParser resourceParser = buildResourceParser(multimediaExtensionList, getPiwigoServerUrl());

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
            FirebaseAnalytics.getInstance(getContext()).logEvent("PRIVACY_PLUGIN_URI_FIX_2", b);
        }
        if (resourceParser.isFixedImageUrisWithAmpEscaping()) {
            Bundle b = new Bundle();
            PiwigoSessionDetails.writeToBundle(b, getConnectionPrefs());
            FirebaseAnalytics.getInstance(getContext()).logEvent("AMPERSAND_URI_FIX", b);
        }
        if (resourceParser.isFixedPrivacyPluginImageUrisForPrivacyPluginUser()) {
            Bundle b = new Bundle();
            PiwigoSessionDetails.writeToBundle(b, getConnectionPrefs());
            FirebaseAnalytics.getInstance(getContext()).logEvent("PRIVACY_PLUGIN_URI_FIX_1", b);
        }

        PiwigoGetResourcesResponse r = new PiwigoGetResourcesResponse(getMessageId(), getPiwigoMethod(), page, pageSize, totalResourceCount, resources, isCached);
        storeResponse(r);
    }

    protected BasicCategoryImageResourceParser buildResourceParser(Set<String> multimediaExtensionList, String basePiwigoUrl) {
        return new BasicCategoryImageResourceParser(multimediaExtensionList, basePiwigoUrl);
    }

    public static class BasicCategoryImageResourceParser {

        private final Pattern multimediaPattern;
        private final SimpleDateFormat piwigoDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK);
        private Matcher multimediaPatternMatcher;
        private String basePiwigoUrl;
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

        public BasicCategoryImageResourceParser(Set<String> multimediaExtensionList, String basePiwigoUrl) {
            this.basePiwigoUrl = basePiwigoUrl;
            StringBuilder multimediaRegexpBuilder = new StringBuilder(".*\\.(");
            Iterator<String> extIter = multimediaExtensionList.iterator();
            while (extIter.hasNext()) {
                String ext = extIter.next();
                multimediaRegexpBuilder.append(ext);
                if (extIter.hasNext()) {
                    multimediaRegexpBuilder.append('|');
                }
            }
            multimediaRegexpBuilder.append(")$");
            multimediaPattern = Pattern.compile(multimediaRegexpBuilder.toString(), Pattern.CASE_INSENSITIVE);
            multimediaPatternMatcher = null;
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
            String thumbnail;

            ResourceItem item;

            if(originalResourceUrl != null) {
                if (multimediaPatternMatcher == null) {
                    multimediaPatternMatcher = multimediaPattern.matcher(originalResourceUrl);
                } else {
                    multimediaPatternMatcher.reset(originalResourceUrl);
                }
            }

            Date dateLastAltered = null;
            JsonElement dateLastAlteredElem = image.get("date_available");
            if (!dateLastAlteredElem.isJsonNull()) {
                String dateLastAlteredStr = dateLastAlteredElem.getAsString();
                try {
                    dateLastAltered = piwigoDateFormat.parse(dateLastAlteredStr);
                } catch (ParseException e) {
                    Crashlytics.logException(e);
                    throw new JSONException("Unable to parse date " + dateLastAlteredStr);
                }
            }

            // not available in standard response. Available in the community plugin response?
            Date dateCreated = null;
            JsonElement dateCreationElem = image.get("date_creation");
            if (!dateCreationElem.isJsonNull()) {
                String dateCreatedStr = dateCreationElem.getAsString();
                try {
                    dateCreated = piwigoDateFormat.parse(dateCreatedStr);
                } catch (ParseException e) {
                    Crashlytics.logException(e);
                    throw new JSONException("Unable to parse date " + dateCreatedStr);
                }
            }


            HashSet<Long> linkedAlbums = new HashSet<>();
            JsonElement elem = image.get("categories");
            if(elem != null && elem.isJsonArray()) {
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


            if (originalResourceUrl != null && multimediaPatternMatcher.matches() && originalResourceUrl.startsWith(basePiwigoUrl)) {
                //TODO why must we do something special for the privacy plugin?
                // is a video - need to ensure the file is accessed via piwigo privacy plugin if installed (direct access blocked).
                String mediaFile = originalResourceUrl.replaceFirst("^.*(/upload/.*)", "$1");

                thumbnail = derivatives.get("thumb").getAsJsonObject().get("url").getAsString();
                if (thumbnail.matches(".*piwigo_privacy/get\\.php\\?.*")) {
                    originalResourceUrl = thumbnail.replaceFirst("(^.*file=)([^&]*)(.*)", "$1." + mediaFile + "$3");
                    fixedPrivacyPluginImageUrisForPrivacyPluginUser = true;
                } else {

                    String thumbRelUri = thumbnail.substring(basePiwigoUrl.length());
                    if (thumbRelUri.startsWith("/" + id + "/_data")) {
                        boolean missingImageId = mediaFile.startsWith("/upload");
                        if (missingImageId) {
                            originalResourceUrl = originalResourceUrl.replaceFirst("/upload", "/" + id + "/upload");
                            fixedImageUrisForPrivacyPluginUser = true;
                        }
                    }
                }
                item = new VideoResourceItem(id, name, description, dateCreated, dateLastAltered, basePiwigoUrl);
                item.setThumbnailUrl(thumbnail);
                item.addResourceFile("original", fixUrl(originalResourceUrl), originalResourceUrlWidth, originalResourceUrlHeight);

            } else {

                Iterator<String> imageSizeKeys = derivatives.keySet().iterator();

                PictureResourceItem picItem = new PictureResourceItem(id, name, description, dateCreated, dateLastAltered, basePiwigoUrl);

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

                    picItem.addResourceFile(imageSizeKey, fixUrl(url), thisImageWidth, thisImageHeight);

                }

                if (originalResourceUrl != null) {
                    picItem.addResourceFile("original", fixUrl(originalResourceUrl), originalResourceUrlWidth, originalResourceUrlHeight);
                }

                item = picItem;
            }

            item.setLinkedAlbums(linkedAlbums);

            return item;
        }

        private String fixUrl(String url) {
            String fixedUrl = url;
            int idx = url.indexOf('&');
            if(idx > 0 && idx == url.indexOf("&amp;")) {
                //strip the unwanted extra html escaping
                fixedUrl = url.replaceAll("&amp;", "&");
                fixedImageUrisWithAmpEscaping = true;
            }
            return fixedUrl;
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