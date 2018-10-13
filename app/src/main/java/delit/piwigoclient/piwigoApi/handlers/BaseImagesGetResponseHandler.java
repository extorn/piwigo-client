package delit.piwigoclient.piwigoApi.handlers;

import com.crashlytics.android.Crashlytics;
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
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class BaseImagesGetResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetResourcesRspHdlr";
    private final String multimediaExtensionList;
    private final CategoryItem parentAlbum;
    private final String sortOrder;
    private final int pageSize;
    private final int page;

    public BaseImagesGetResponseHandler(CategoryItem parentAlbum, String sortOrder, int page, int pageSize, String multimediaExtensionList) {
        super("pwg.categories.getImages", TAG);
        this.parentAlbum = parentAlbum;
        this.sortOrder = sortOrder;
        this.page = page;
        this.pageSize = pageSize;
        this.multimediaExtensionList = multimediaExtensionList;
    }

    @Override
    public String getPiwigoMethod() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
        if(sessionDetails.isMethodAvailable("piwigo_client.categories.getImages")) {
            return "piwigo_client.categories.getImages";
        } else {
            return super.getPiwigoMethod();
        }
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("cat_id", String.valueOf(parentAlbum.getId()));
        params.put("order", sortOrder);
        params.put("page", String.valueOf(page));
        params.put("per_page", String.valueOf(pageSize));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {

        ArrayList<GalleryItem> resources = new ArrayList<>();

        JsonObject result = rsp.getAsJsonObject();
        JsonArray images;
        if(result.has("images")) {
            images = result.get("images").getAsJsonArray();
        } else if(result.has("_content")) {
            images = result.get("_content").getAsJsonArray();
        } else {
            images = null;
        }

        BasicCategoryImageResourceParser resourceParser = buildResourceParser(multimediaExtensionList);

        if(images != null) {
            for (int i = 0; i < images.size(); i++) {
                JsonObject image = (JsonObject) images.get(i);
                ResourceItem item = resourceParser.parseAndProcessResourceData(image);

                item.setParentageChain(parentAlbum.getParentageChain(), parentAlbum.getId());
                resources.add(item);

            }
        }

        PiwigoGetResourcesResponse r = new PiwigoGetResourcesResponse(getMessageId(), getPiwigoMethod(), page, pageSize, resources);
        storeResponse(r);
    }

    protected BasicCategoryImageResourceParser buildResourceParser(String multimediaExtensionList) {
        return new BasicCategoryImageResourceParser(multimediaExtensionList);
    }

    public static class BasicCategoryImageResourceParser {

        private final Pattern p;
        private final SimpleDateFormat piwigoDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK);
        private Matcher m;

        public BasicCategoryImageResourceParser(String multimediaExtensionList) {

            StringTokenizer st = new StringTokenizer(multimediaExtensionList, ",");
            StringBuilder multimediaRegexpBuilder = new StringBuilder(".*\\.(");
            String token;
            while (st.hasMoreTokens()) {
                token = st.nextToken();
                if (token.startsWith(".")) {
                    token = token.substring(1);
                }
                multimediaRegexpBuilder.append(token);
                if (st.hasMoreTokens()) {
                    multimediaRegexpBuilder.append('|');
                }
            }
            multimediaRegexpBuilder.append(")$");
            p = Pattern.compile(multimediaRegexpBuilder.toString());
            m = null;
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
                if (m == null) {
                    m = p.matcher(originalResourceUrl);
                } else {
                    m.reset(originalResourceUrl);
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

            if (originalResourceUrl != null && m.matches()) {
                //TODO why must we do something special for the privacy plugin?
                // is a video - need to ensure the file is accessed via piwigo privacy plugin if installed (direct access blocked).
                String mediaFile = originalResourceUrl.replaceFirst("^.*(/upload/.*)", "$1");
                thumbnail = derivatives.get("thumb").getAsJsonObject().get("url").getAsString();
                if (thumbnail.matches(".*piwigo_privacy/get\\.php\\?.*")) {
                    originalResourceUrl = thumbnail.replaceFirst("(^.*file=)([^&]*)(.*)", "$1." + mediaFile + "$3");
                }
                item = new VideoResourceItem(id, name, description, dateCreated, dateLastAltered, thumbnail);
                ResourceItem.ResourceFile originalImage = new ResourceItem.ResourceFile("original", originalResourceUrl, originalResourceUrlWidth, originalResourceUrlHeight);
                item.addResourceFile(originalImage);
                item.setFullSizeImage(originalImage);

            } else {

                ResourceItem.ResourceFile originalImage = null;
                if(originalResourceUrl != null) {
                    originalImage = new ResourceItem.ResourceFile("original", originalResourceUrl, originalResourceUrlWidth, originalResourceUrlHeight);
                }

                Iterator<String> imageSizeKeys = derivatives.keySet().iterator();
                thumbnail = derivatives.get("thumb").getAsJsonObject().get("url").getAsString();

                PictureResourceItem picItem = new PictureResourceItem(id, name, description, dateCreated, dateLastAltered, thumbnail);

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

                    ResourceItem.ResourceFile img = new ResourceItem.ResourceFile(imageSizeKey, url, thisImageWidth, thisImageHeight);
                    picItem.addResourceFile(img);

                }
                if(originalImage != null) {
                    picItem.addResourceFile(originalImage);
                    picItem.setFullSizeImage(originalImage);
                }
                item = picItem;
            }

            item.setLinkedAlbums(linkedAlbums);

            return item;
        }
    }

    public static class PiwigoGetResourcesResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {

        private final int page;
        private final int pageSize;
        private final ArrayList<GalleryItem> resources;

        public PiwigoGetResourcesResponse(long messageId, String piwigoMethod, int page, int pageSize, ArrayList<GalleryItem> resources) {
            super(messageId, piwigoMethod, true);
            this.page = page;
            this.pageSize = pageSize;
            this.resources = resources;
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
}