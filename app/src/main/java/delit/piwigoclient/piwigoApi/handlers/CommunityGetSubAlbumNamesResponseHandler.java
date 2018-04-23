package delit.piwigoclient.piwigoApi.handlers;

import android.util.LongSparseArray;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class CommunityGetSubAlbumNamesResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "CommunityAlbLstRspHdlr";
    private final long parentAlbumId;
    private final boolean recursive;

    public CommunityGetSubAlbumNamesResponseHandler(long parentAlbumId, boolean recursive) {
        super("community.categories.getList", TAG);
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
        params.put("recursive", String.valueOf(recursive));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonArray categories = result.get("categories").getAsJsonArray();
        LongSparseArray<CategoryItemStub> availableGalleriesMap = new LongSparseArray<>(categories.size());
        ArrayList<CategoryItemStub> availableGalleries = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            JsonObject category = (JsonObject) categories.get(i);
            Long id = category.get("id").getAsLong();

            JsonElement nameElem = category.get("name");
            String name = null;
            if(nameElem != null && !nameElem.isJsonNull()) {
                name = category.get("name").getAsString();
            }

            Long parentId = null;
//            if (!"null".equals(category.get("id_uppercat").getAsString())) {
//                parentId = category.get("id_uppercat").getAsLong();
//            }
            String[] parentage = category.get("uppercats").getAsString().split(",");
            if(parentage.length >= 2) {
                parentId = Long.valueOf(parentage[parentage.length - 2]);
            }
            CategoryItemStub album = new CategoryItemStub(name, id);
            if(parentId != null) {
                CategoryItemStub parentAlbum = availableGalleriesMap.get(parentId);
                album.setParentageChain(parentAlbum.getParentageChain(), parentAlbum.getId());
            } else {
                album.setParentageChain(CategoryItem.ROOT_ALBUM.getParentageChain(), CategoryItem.ROOT_ALBUM.getId());
            }
            availableGalleries.add(album);
            availableGalleriesMap.put(album.getId(), album);
        }
        PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse r = new PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse(getMessageId(), getPiwigoMethod(), availableGalleries);
        storeResponse(r);
    }
}
