package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
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
        params.put("recursive", String.valueOf(recursive));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        JSONArray categories = rsp.getJSONObject("result").getJSONArray("categories");
        HashMap<Long, CategoryItemStub> availableGalleriesMap = new HashMap<>(categories.length());
        ArrayList<CategoryItemStub> availableGalleries = new ArrayList<>();
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = (JSONObject) categories.get(i);
            Long id = category.getLong("id");
            String name = category.getString("name");
            Long parentId = null;
            if (!"null".equals(category.getString("id_uppercat"))) {
                parentId = category.getLong("id_uppercat");
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
