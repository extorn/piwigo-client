package delit.piwigoclient.piwigoApi.handlers;

import android.util.LongSparseArray;

import com.crashlytics.android.Crashlytics;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class AlbumGetSubAlbumsResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetSubGalleriesRspHdlr";
    private final CategoryItem parentAlbum;
    private final boolean recursive;
    private final String thumbnailSize;
    private final SimpleDateFormat piwigoDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK);

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
        if (recursive) {
            params.put("recursive", Boolean.toString(recursive));
            params.put("tree_output", Boolean.FALSE.toString()); // true returns broken json
        }
        boolean communityPluginInstalled = PiwigoSessionDetails.isUseCommunityPlugin(getConnectionPrefs());
        if(communityPluginInstalled) {
            params.put("faked_by_community", String.valueOf(!communityPluginInstalled));
        }
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();

        ArrayList<CategoryItem> availableGalleries = null;
        if(result.has("categories")) {
            JsonElement elem = result.get("categories");
            if(elem != null && !elem.isJsonNull()) {
                JsonArray categories = elem.getAsJsonArray();
                availableGalleries = new ArrayList<>(categories.size());
                parseCategories(categories, availableGalleries);
            }
        } else {
            JsonElement elem = result.get("item");
            if(elem != null && !elem.isJsonNull()) {
                JsonArray categories = elem.getAsJsonArray();
                availableGalleries = new ArrayList<>(categories.size());
                parseCategories(categories, availableGalleries);
            }
        }
        if(availableGalleries == null) {
            availableGalleries = new ArrayList<>(0);
        }

        PiwigoGetSubAlbumsResponse r = new PiwigoGetSubAlbumsResponse(getMessageId(), getPiwigoMethod(), availableGalleries);
        storeResponse(r);
    }

    private void parseCategories(JsonArray categories, ArrayList<CategoryItem> availableGalleries) throws JSONException {
        LongSparseArray<CategoryItem> availableAlbumsMap = new LongSparseArray<>(categories.size());
        if(!parentAlbum.isRoot()) {
            availableAlbumsMap.put(parentAlbum.getParentId(), parentAlbum);
        }
        for (int i = 0; i < categories.size(); i++) {

            JsonObject category = (JsonObject) categories.get(i);
            CategoryItem item = parseCategory(availableAlbumsMap, category);
            availableAlbumsMap.put(item.getId(), item);
            if(recursive) {
                if(availableAlbumsMap.size() == 0 || item.getParentId() == CategoryItem.ROOT_ALBUM.getId()) {
                    // this is the overall parent being added in
                    availableGalleries.add(item);
                    if(item.getParentId() == null) {
                        // this is an album off root.
                        continue;
                    }
                }
                // dealing with a normal child
                if (item.getId() == parentAlbum.getId()) {
                    // this album is the one we requested the load of sub albums for.
                    item.setParentageChain(parentAlbum.getParentageChain());
                }
            } else {
                item.setParentageChain(parentAlbum.getParentageChain(), parentAlbum.getId());
                availableGalleries.add(item);
            }
        }
    }

    private CategoryItem parseCategory(LongSparseArray<CategoryItem> availableAlbumsMap, JsonObject category) throws JSONException {
        long id = category.get("id").getAsLong();

        JsonElement nameElem = category.get("name");
        String name = null;
        if (nameElem != null && !nameElem.isJsonNull()) {
            name = nameElem.getAsString();
        }

        Long parentId = null;
        if (category.has("id_uppercat")
                && !category.get("id_uppercat").isJsonNull()) {
            parentId = category.get("id_uppercat").getAsLong();
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

        if(parentId != null) {
            CategoryItem directParentAlbum = availableAlbumsMap.get(parentId);
            item.setParentageChain(directParentAlbum.getParentageChain(), directParentAlbum.getId());
            directParentAlbum.addChildAlbum(item);
        } else {
            item.setParentageChain(CategoryItem.ROOT_ALBUM.getParentageChain(), CategoryItem.ROOT_ALBUM.getId());
        }

        // this is superfluous as handled by the above code.
//        if (item.getParentageChain() == null) {
//            if (category.has("uppercats") && !category.get("uppercats").isJsonNull()) {
//                String parentCatsCsv = category.get("uppercats").getAsString();
//                List<Long> parentage = toParentageChain(id, parentCatsCsv);
//                item.setParentageChain(parentage);
//            } else {
//                item.setParentageChain(parentAlbum.getParentageChain(), parentAlbum.getId());
//            }
//        }

        return item;
    }

//    private List<Long> toParentageChain(long thisAlbumId, String parentCatsCsv) {
//        String[] cats = parentCatsCsv.split(",");
//        ArrayList<Long> list = new ArrayList<>();
//        list.add(CategoryItem.ROOT_ALBUM.getId());
//        for (String cat : cats) {
//            list.add(Long.valueOf(cat));
//        }
//        list.remove(thisAlbumId);
//        return list;
//    }

    public static class PiwigoGetSubAlbumsResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final ArrayList<CategoryItem> albums;

        public PiwigoGetSubAlbumsResponse(long messageId, String piwigoMethod, ArrayList<CategoryItem> albums) {
            super(messageId, piwigoMethod, true);
            this.albums = albums;
        }

        public ArrayList<CategoryItem> getAlbums() {
            return albums;
        }
    }
}
