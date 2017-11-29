package delit.piwigoclient.piwigoApi.handlers;

import android.content.Context;
import android.graphics.Point;
import android.view.Display;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
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

        JSONArray images = rsp.getJSONObject("result").getJSONArray("images");
        for (int i = 0; i < images.length(); i++) {
            JSONObject image = (JSONObject) images.get(i);
            long id = image.getLong("id");

            String name = image.getString("name");
            if ("null".equals(name)) {
                name = null;
            }
            String description = image.getString("comment");
            if ("null".equals(description)) {
                description = null;
            }
            String originalResourceUrl = image.getString("element_url");
            JSONObject derivatives = image.getJSONObject("derivatives");
            String thumbnail = null;
            ResourceItem item;

            if (m == null) {
                m = p.matcher(originalResourceUrl);
            } else {
                m.reset(originalResourceUrl);
            }

            String dateLastAlteredStr = image.getString("date_available");
            Date dateLastAltered = null;
            try {
                dateLastAltered = piwigoDateFormat.parse(dateLastAlteredStr);
            } catch (ParseException e) {
                throw new JSONException("Unable to parse date " + dateLastAlteredStr);
            }

            HashSet<Long> linkedAlbums = new HashSet<>();
            JSONArray linkedAlbumsJsonArr = image.getJSONArray("categories");
            for(int j = 0; j < linkedAlbumsJsonArr.length(); j++) {
                JSONObject catJsonObj = linkedAlbumsJsonArr.getJSONObject(j);
                linkedAlbums.add(catJsonObj.getLong("id"));
            }

            int originalResourceUrlWidth = image.optInt("width", 0);
            int originalResourceUrlHeight = image.optInt("height", 0);


            if (m.matches()) {
                //TODO why must we do something special for the privacy plugin?
                // is a video - need to ensure the file is accessed via piwigo privacy plugin if installed (direct access blocked).
                String mediaFile = originalResourceUrl.replaceFirst("^.*(/upload/.*)", "$1");
                thumbnail = derivatives.getJSONObject("thumb").getString("url");
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

                Iterator<String> imageSizeKeys = derivatives.keys();
                thumbnail = derivatives.getJSONObject("thumb").getString("url");

                PictureResourceItem picItem = new PictureResourceItem(id, name, description, dateLastAltered, thumbnail);

                long bestWidth = 0;
                String bestImageSize = null;

                while (imageSizeKeys.hasNext()) {
                    String imageSizeKey = imageSizeKeys.next();
                    JSONObject imageSizeObj = derivatives.getJSONObject(imageSizeKey);
                    String url = imageSizeObj.getString("url");
                    int thisWidth = imageSizeObj.getInt("width");
                    int thisHeight = imageSizeObj.getInt("height");
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