package delit.piwigoclient.piwigoApi.handlers;

import android.util.LongSparseArray;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class AlbumGetSubAlbumNamesResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetSubGalleryNamesRspHdlr";
    private final long parentAlbumId;
    private final boolean recursive;

    public AlbumGetSubAlbumNamesResponseHandler(long parentAlbumId, boolean recursive) {
        super("pwg.categories.getList", TAG);
        this.parentAlbumId = parentAlbumId;
        this.recursive = recursive;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        if (parentAlbumId != 0) {
            params.put("cat_id", String.valueOf(parentAlbumId));
        }
        if (recursive) {
            params.put("recursive", Boolean.toString(recursive));
            params.put("tree_output", Boolean.FALSE.toString());  // true returns broken json
        }
        boolean  communityPluginInstalled = PiwigoSessionDetails.isUseCommunityPlugin(getConnectionPrefs());
        if(communityPluginInstalled) {
            params.put("faked_by_community", String.valueOf(!communityPluginInstalled));
        }
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        ArrayList<CategoryItemStub> availableGalleries;

        if (rsp.isJsonArray()) {
            availableGalleries = parseCategoriesArray(rsp);
        } else {
            JsonObject result = rsp.getAsJsonObject();
            if (result.has("categories")) {
                JsonElement elem = result.get("categories");
                availableGalleries = parseCategoriesArray(elem);
            } else {
                JsonElement elem = result.get("item");
                availableGalleries = parseCategoriesArray(elem);
            }
        }
        PiwigoGetSubAlbumNamesResponse r = new PiwigoGetSubAlbumNamesResponse(getMessageId(), getPiwigoMethod(), availableGalleries, isCached);
        storeResponse(r);
    }

    private ArrayList<CategoryItemStub> parseCategoriesArray(JsonElement elem) throws JSONException {
        if (elem != null && !elem.isJsonNull()) {
            JsonArray categories = elem.getAsJsonArray();
            ArrayList<CategoryItemStub> availableGalleries = new ArrayList<>(categories.size());
            parseCategories(categories, availableGalleries);
            return availableGalleries;
        } else {
            return new ArrayList<>(0);
        }
    }

    private void parseCategories(JsonArray categories, ArrayList<CategoryItemStub> availableGalleries) {
        LongSparseArray<CategoryItemStub> availableGalleriesMap = new LongSparseArray<>(categories.size());
        for (int i = 0; i < categories.size(); i++) {
            JsonObject category = (JsonObject) categories.get(i);
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
            } else if (category.has("uppercats")) {
                String[] parentage = category.get("uppercats").getAsString().split(",");
                if (parentage.length > 1) {
                    parentId = Long.valueOf(parentage[parentage.length - 2]);
                } else {
                    parentId = 0L;
                }
            }

            CategoryItemStub album = new CategoryItemStub(name, id, null);
            if (parentId != null && parentId != 0) {
                CategoryItemStub parentAlbum = availableGalleriesMap.get(parentId);
                if (parentAlbum != null) {
                    album.setParentageChain(parentAlbum.getParentageChain(), parentAlbum.getId());
                }
            }
            if (album.getParentageChain() == null) {
                if (category.has("uppercats") && !category.get("uppercats").isJsonNull()) {
                    String parentCatsCsv = category.get("uppercats").getAsString();
                    List<Long> parentage = toParentageChain(id, parentCatsCsv);
                    album.setParentageChain(parentage);
                } else {
                    album.setParentageChain(CategoryItem.ROOT_ALBUM.getParentageChain(), CategoryItem.ROOT_ALBUM.getId());
                }
            }
            availableGalleries.add(album);
            availableGalleriesMap.put(album.getId(), album);
        }
    }

    private List<Long> toParentageChain(long thisAlbumId, String parentCatsCsv) {
        String[] cats = parentCatsCsv.split(",");
        ArrayList<Long> list = new ArrayList<>();
        list.add(CategoryItem.ROOT_ALBUM.getId());
        for (String cat : cats) {
            list.add(Long.valueOf(cat));
        }
        list.remove(thisAlbumId);
        return list;
    }

    public static class PiwigoGetSubAlbumNamesResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final ArrayList<CategoryItemStub> albumNames;

        public PiwigoGetSubAlbumNamesResponse(long messageId, String piwigoMethod, ArrayList<CategoryItemStub> albumNames, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.albumNames = albumNames;
        }

        public ArrayList<CategoryItemStub> getAlbumNames() {
            return albumNames;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}
