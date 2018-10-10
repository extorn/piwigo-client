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
import java.util.Locale;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class AlbumGetSubAlbumsResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetSubGalleriesRspHdlr";
    private final CategoryItem parentAlbum;
    private final boolean recursive;
    private final String thumbnailSize;

    public AlbumGetSubAlbumsResponseHandler(CategoryItem parentAlbum, String thumbnailSize, boolean recursive) {
        super("pwg.categories.getList", TAG);
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
        if (thumbnailSize != null) {
            params.put("thumbnail_size", thumbnailSize);
        }
        if (recursive || !PiwigoSessionDetails.isUseCommunityPlugin(getConnectionPrefs())) {
            // community plugin is very broken!
            params.put("recursive", String.valueOf(recursive));
        }
        boolean communityPluginInstalled = PiwigoSessionDetails.isUseCommunityPlugin(getConnectionPrefs());
        params.put("faked_by_community", String.valueOf(!communityPluginInstalled));
        return params;
    }

    @Override
    public boolean getNewLogin() {
        boolean success = super.getNewLogin();
//        if(success) {
//            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
//            if(sessionDetails != null && sessionDetails.isUseCommunityPlugin() && !sessionDetails.isGuest()) {
//                withConnectionPreferences(getConnectionPrefs().asGuest());
//                return super.getNewLogin();
//            }
//        }
        return success;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonArray categories = result.get("categories").getAsJsonArray();
        ArrayList<CategoryItem> availableGalleries = new ArrayList<>(categories.size());

        SimpleDateFormat piwigoDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK);

        for (int i = 0; i < categories.size(); i++) {

            JsonObject category = (JsonObject) categories.get(i);
            long id = category.get("id").getAsLong();

            JsonElement nameElem = category.get("name");
            String name = null;
            if (nameElem != null && !nameElem.isJsonNull()) {
                name = nameElem.getAsString();
            }

            int photos = category.get("nb_images").getAsInt();
            long totalPhotos = category.get("total_nb_images").getAsLong();
            long subCategories = category.get("nb_categories").getAsLong();

            JsonElement descriptionElem = category.get("comment");
            String description = null;
            if (descriptionElem != null && !descriptionElem.isJsonNull()) {
                description = descriptionElem.getAsString();
            }

            boolean isPublic = false;
            //TODO No support in community plugin for anything except private albums for PIWIGO API.
            if (!PiwigoSessionDetails.isUseCommunityPlugin(getConnectionPrefs()) || PiwigoSessionDetails.isAdminUser(getConnectionPrefs())) {
                JsonElement statusElem = category.get("status");
                if(statusElem != null && !statusElem.isJsonNull()) {
                    isPublic = "public".equals(statusElem.getAsString());
                }
            }

            JsonElement maxDateLastJsonElem = category.get("max_date_last");
            String dateLastAlteredStr = null;
            if (maxDateLastJsonElem != null && !maxDateLastJsonElem.isJsonNull()) {
                dateLastAlteredStr = maxDateLastJsonElem.getAsString();
            }

            String thumbnail = null;
            Long representativePictureId = null;
            if (category.has("representative_picture_id") && !category.get("representative_picture_id").isJsonNull()) {
                representativePictureId = category.get("representative_picture_id").getAsLong();
            }
            if (category.has("tn_url") && !category.get("tn_url").isJsonNull()) {
                thumbnail = category.get("tn_url").getAsString();
            }

            Date dateLastAltered;
            try {
                if (null == dateLastAlteredStr) {
                    dateLastAltered = new Date(0);
                } else {
                    dateLastAltered = piwigoDateFormat.parse(dateLastAlteredStr);
                }
            } catch (ParseException e) {
                Crashlytics.logException(e);
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
        PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsResponse r = new PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsResponse(getMessageId(), getPiwigoMethod(), availableGalleries);
        storeResponse(r);
    }

}
