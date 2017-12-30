package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class CommunityAlbumGetSubAlbumsAdminResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "CommunityGetAlbumsRspHdlr";

    private final CategoryItem parentAlbum;
    private final boolean recursive;
    private final String thumbnailSize;

    public CommunityAlbumGetSubAlbumsAdminResponseHandler(CategoryItem parentAlbum, String thumbnailSize, boolean recursive) {
        super("community.categories.getList", TAG);
        this.parentAlbum = parentAlbum;
        this.recursive = recursive;
        this.thumbnailSize = thumbnailSize;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        if (!parentAlbum.isRoot()) {
            params.put("cat_id", String.valueOf(parentAlbum.getId()));
        }
        if(thumbnailSize != null) {
            params.put("thumbnail_size", thumbnailSize);
        }
        params.put("recursive", String.valueOf(recursive));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonArray categories = result.get("categories").getAsJsonArray();
        ArrayList<CategoryItem> availableGalleries = new ArrayList<>(categories.size());

        SimpleDateFormat piwigoDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (int i = 0; i < categories.size(); i++) {
            JsonObject category = (JsonObject) categories.get(i);
            long id = category.get("id").getAsLong();
            String name = category.get("name").getAsString();
            long photos = category.get("nb_images").getAsLong();
            long totalPhotos = category.get("total_nb_images").getAsLong();
            long subCategories = category.get("nb_categories").getAsLong();
            String description = category.get("comment").getAsString();
            boolean isPublic = "public".equals(category.get("status").getAsString());
            JsonElement maxDateLastJsonElem = category.get("max_date_last");
            String dateLastAlteredStr = null;
            if(maxDateLastJsonElem != null && !maxDateLastJsonElem.isJsonNull()) {
                dateLastAlteredStr = maxDateLastJsonElem.getAsString();
            }
            String thumbnail = null;
            Long representativePictureId = null;
            if(category.has("representative_picture_id") && !category.get("representative_picture_id").isJsonNull()) {
                representativePictureId = category.get("representative_picture_id").getAsLong();
            }

            Date dateLastAltered = null;
            try {
                if (dateLastAlteredStr == null) {
                    dateLastAltered = new Date(0);
                } else {
                    dateLastAltered = piwigoDateFormat.parse(dateLastAlteredStr);
                }
            } catch (ParseException e) {
                throw new JSONException("Unable to parse date " + dateLastAlteredStr);
            }

            CategoryItem item = new CategoryItem(id, name, description, !isPublic, dateLastAltered, photos, totalPhotos, subCategories, thumbnail);
            item.setRepresentativePictureId(representativePictureId);

            if (item.getId() == parentAlbum.getId()) {
                // this album is the one we requested the load of sub albums for.
                item.setParentageChain(parentAlbum.getParentageChain());
            } else {
                item.setParentageChain(parentAlbum.getParentageChain(), parentAlbum.getId());
            }
            availableGalleries.add(item);
        }

        //TODO now go and get all the thumbnail urls for those albums.... or maybe get it on demand later?

        PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsResponse r = new PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsResponse(getMessageId(), getPiwigoMethod(), availableGalleries);
        storeResponse(r);
    }

}
