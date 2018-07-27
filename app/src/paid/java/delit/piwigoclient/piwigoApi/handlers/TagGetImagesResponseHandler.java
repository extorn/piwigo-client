package delit.piwigoclient.piwigoApi.handlers;

import android.content.Context;
import android.graphics.Point;
import android.view.Display;
import android.view.WindowManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class TagGetImagesResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "TagGetResRspHdlr";
    private final Context context;
    private final String multimediaExtensionList;
    private final Tag tag;
    private final String sortOrder;
    private final int pageSize;
    private final int page;

    public TagGetImagesResponseHandler(Tag tag, String sortOrder, int page, int pageSize, Context c, String multimediaExtensionList) {
        super("pwg.tags.getImages", TAG);
        this.context = c;
        this.tag = tag;
        this.sortOrder = sortOrder;
        this.page = page;
        this.pageSize = pageSize;
        this.multimediaExtensionList = multimediaExtensionList;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("tag_id", String.valueOf(tag.getId()));
        params.put("order", sortOrder);
        params.put("page", String.valueOf(page));
        params.put("per_page", String.valueOf(pageSize));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {

        ArrayList<GalleryItem> resources = new ArrayList<>();

        JsonObject result = rsp.getAsJsonObject();
        JsonArray images = result.get("images").getAsJsonArray();

        ResourceParser resourceParser = new ResourceParser(context, multimediaExtensionList);

        for (int i = 0; i < images.size(); i++) {
            JsonObject image = (JsonObject) images.get(i);
            ResourceItem item = resourceParser.parseAndProcessResourceData(image);
            resources.add(item);

        }

        PiwigoResponseBufferingHandler.PiwigoGetResourcesResponse r = new PiwigoResponseBufferingHandler.PiwigoGetResourcesResponse(getMessageId(), getPiwigoMethod(), page, pageSize, resources);
        storeResponse(r);
    }

    protected static class ResourceParser {

        private final Pattern p;
        private Matcher m;
        private final SimpleDateFormat piwigoDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK);
        private final int screenWidth;

        public ResourceParser(Context context, String multimediaExtensionList) {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            Point point = new Point();
            display.getSize(point);
            int screenWidth = point.x;
            int screenHeight = point.y;
            if (screenHeight < screenWidth) {
                //Assume portrait mode
                //noinspection SuspiciousNameCombination
                screenHeight = point.x;
                //noinspection SuspiciousNameCombination
                screenWidth = point.y;
            }
            this.screenWidth = screenWidth;

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

            String originalResourceUrl = image.get("element_url").getAsString();

            JsonObject derivatives = image.get("derivatives").getAsJsonObject();
            String thumbnail;
            ResourceItem item;

            if (m == null) {
                m = p.matcher(originalResourceUrl);
            } else {
                m.reset(originalResourceUrl);
            }

            Date dateLastAltered = null;
            JsonElement dateLastAlteredElem = image.get("date_available");
            if(!dateLastAlteredElem.isJsonNull()) {
                String dateLastAlteredStr = dateLastAlteredElem.getAsString();
                try {
                    dateLastAltered = piwigoDateFormat.parse(dateLastAlteredStr);
                } catch (ParseException e) {
                    throw new JSONException("Unable to parse date " + dateLastAlteredStr);
                }
            }

            Date dateCreated = null;
            JsonElement dateCreationElem = image.get("date_creation");
            if(!dateCreationElem.isJsonNull()) {
                String dateCreatedStr = dateCreationElem.getAsString();
                try {
                    dateCreated = piwigoDateFormat.parse(dateCreatedStr);
                } catch (ParseException e) {
                    throw new JSONException("Unable to parse date " + dateCreatedStr);
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

            if (m.matches()) {
                //TODO why must we do something special for the privacy plugin?
                // is a video - need to ensure the file is accessed via piwigo privacy plugin if installed (direct access blocked).
                String mediaFile = originalResourceUrl.replaceFirst("^.*(/upload/.*)", "$1");
                thumbnail = derivatives.get("thumb").getAsJsonObject().get("url").getAsString();
                if (thumbnail.matches(".*piwigo_privacy/get\\.php\\?.*")) {
                    originalResourceUrl = thumbnail.replaceFirst("(^.*file=)([^&]*)(.*)", "$1." + mediaFile + "$3");
                }
                item = new VideoResourceItem(id, name, null, dateCreated, dateLastAltered, thumbnail);
                ResourceItem.ResourceFile originalImage = new ResourceItem.ResourceFile("original", originalResourceUrl, originalResourceUrlWidth, originalResourceUrlHeight);
                item.addResourceFile(originalImage);
                item.setFullSizeImage(originalImage);

            } else {

                ResourceItem.ResourceFile originalImage = new ResourceItem.ResourceFile("original", originalResourceUrl, originalResourceUrlWidth, originalResourceUrlHeight);

                ResourceItem.ResourceFile fullScreenImage = null;

                Iterator<String> imageSizeKeys = derivatives.keySet().iterator();
                thumbnail = derivatives.get("thumb").getAsJsonObject().get("url").getAsString();

                PictureResourceItem picItem = new PictureResourceItem(id, name, null, dateCreated, dateLastAltered, thumbnail);

                long bestWidth = 0;

                while (imageSizeKeys.hasNext()) {
                    String imageSizeKey = imageSizeKeys.next();
                    JsonObject imageSizeObj = derivatives.get(imageSizeKey).getAsJsonObject();
                    String url = imageSizeObj.get("url").getAsString();
                    int thisWidth = imageSizeObj.get("width").getAsInt();
                    int thisHeight = imageSizeObj.get("height").getAsInt();
                    ResourceItem.ResourceFile img = new ResourceItem.ResourceFile(imageSizeKey, url, thisWidth, thisHeight);
                    picItem.addResourceFile(img);

                    if ((thisWidth < bestWidth && thisWidth > screenWidth)
                            || (thisWidth > bestWidth && bestWidth < screenWidth)) {

                        bestWidth = thisWidth;
                        fullScreenImage = img;
                    }
                }
                picItem.addResourceFile(originalImage);
                picItem.setFullSizeImage(originalImage);
                picItem.setFullScreenImage(fullScreenImage);
                item = picItem;
            }

            return item;
        }
    }

}