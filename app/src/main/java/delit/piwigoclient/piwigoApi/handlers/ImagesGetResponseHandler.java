package delit.piwigoclient.piwigoApi.handlers;

import android.content.Context;
import android.graphics.Point;
import android.view.Display;
import android.view.WindowManager;

import com.google.gson.JsonElement;

import org.json.JSONException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class ImagesGetResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetResourcesRspHdlr";
    private final Context context;
    private final String multimediaExtensionList;
    private final CategoryItem parentAlbum;
    private final String sortOrder;
    private final int pageSize;
    private final int page;

    public ImagesGetResponseHandler(CategoryItem parentAlbum, String sortOrder, int page, int pageSize, Context c, String multimediaExtensionList) {
        super("pwg.categories.getImages", TAG);
        this.context = c;
        this.parentAlbum = parentAlbum;
        this.sortOrder = sortOrder;
        this.page = page;
        this.pageSize = pageSize;
        this.multimediaExtensionList = multimediaExtensionList;
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
        Pattern p = Pattern.compile(multimediaRegexpBuilder.toString());
        Matcher m = null;

        ArrayList<GalleryItem> resources = new ArrayList<>();

        SimpleDateFormat piwigoDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        JsonObject result = rsp.getAsJsonObject();
        JsonArray images = result.get("images").getAsJsonArray();
        for (int i = 0; i < images.size(); i++) {
            JsonObject image = (JsonObject) images.get(i);
            long id = image.get("id").getAsLong();

            String name = null;
            JsonElement nameJsonElem = image.get("name");
            if (nameJsonElem != null && !nameJsonElem.isJsonNull()) {
                name = nameJsonElem.getAsString();
            }

            String description = null;
            JsonElement descJsonElem = image.get("comment");
            if (descJsonElem != null && !descJsonElem.isJsonNull()) {
                description = descJsonElem.getAsString();
            }

            String originalResourceUrl = image.get("element_url").getAsString();
            JsonObject derivatives = image.get("derivatives").getAsJsonObject();
            String thumbnail = null;
            ResourceItem item;

            if (m == null) {
                m = p.matcher(originalResourceUrl);
            } else {
                m.reset(originalResourceUrl);
            }

            String dateLastAlteredStr = image.get("date_available").getAsString();
            Date dateLastAltered = null;
            try {
                dateLastAltered = piwigoDateFormat.parse(dateLastAlteredStr);
            } catch (ParseException e) {
                throw new JSONException("Unable to parse date " + dateLastAlteredStr);
            }

            HashSet<Long> linkedAlbums = new HashSet<>();
            JsonArray linkedAlbumsJsonArr = image.get("categories").getAsJsonArray();
            for(int j = 0; j < linkedAlbumsJsonArr.size(); j++) {
                JsonObject catJsonObj = linkedAlbumsJsonArr.get(j).getAsJsonObject();
                linkedAlbums.add(catJsonObj.get("id").getAsLong());
            }

            int originalResourceUrlWidth = 0;
            if(image.has("width") && !image.get("width").isJsonNull()) {
                originalResourceUrlWidth = image.get("width").getAsInt();
            }

            int originalResourceUrlHeight = 0;
            if(image.has("height") && !image.get("height").isJsonNull()) {
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
                item = new VideoResourceItem(id, name, description, dateLastAltered, thumbnail);
                ResourceItem.ResourceFile originalImage = new ResourceItem.ResourceFile("original", originalResourceUrl, originalResourceUrlWidth, originalResourceUrlHeight);
                item.addResourceFile(originalImage);
                item.setFullSizeImage(originalImage);

            } else {

                ResourceItem.ResourceFile originalImage = new ResourceItem.ResourceFile("original", originalResourceUrl, originalResourceUrlWidth, originalResourceUrlHeight);


                ResourceItem.ResourceFile thumbnailImg = null;
                ResourceItem.ResourceFile fullScreenImage = null;

                Iterator<String> imageSizeKeys = derivatives.keySet().iterator();
                thumbnail = derivatives.get("thumb").getAsJsonObject().get("url").getAsString();

                PictureResourceItem picItem = new PictureResourceItem(id, name, description, dateLastAltered, thumbnail);

                long bestWidth = 0;
                String bestImageSize = null;

                while (imageSizeKeys.hasNext()) {
                    String imageSizeKey = imageSizeKeys.next();
                    JsonObject imageSizeObj = derivatives.get(imageSizeKey).getAsJsonObject();
                    String url = imageSizeObj.get("url").getAsString();
                    int thisWidth = imageSizeObj.get("width").getAsInt();
                    int thisHeight = imageSizeObj.get("height").getAsInt();
                    ResourceItem.ResourceFile img = new ResourceItem.ResourceFile(imageSizeKey, url, thisWidth, thisHeight);
                    picItem.addResourceFile(img);

                    if (imageSizeKey.equals("thumb")) {
                        thumbnailImg = img;
                    }
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

            item.setLinkedAlbums(linkedAlbums);
            item.setParentageChain(parentAlbum.getParentageChain(), parentAlbum.getId());
            resources.add(item);

        }

        PiwigoResponseBufferingHandler.PiwigoGetResourcesResponse r = new PiwigoResponseBufferingHandler.PiwigoGetResourcesResponse(getMessageId(), getPiwigoMethod(), page, pageSize, resources);
        storeResponse(r);
    }

}