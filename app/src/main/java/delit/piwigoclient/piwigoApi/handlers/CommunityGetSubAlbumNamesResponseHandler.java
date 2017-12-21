package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

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
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        JSONArray categories = rsp.getJSONObject("result").getJSONArray("categories");
        HashMap<Long, CategoryItemStub> availableGalleriesMap = new HashMap<>(categories.length());
        ArrayList<CategoryItemStub> availableGalleries = new ArrayList<>();
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = (JSONObject) categories.get(i);
            Long id = category.getLong("id");
            String name = category.getString("name");
            Long parentId = null;
//            if (!"null".equals(category.getString("id_uppercat"))) {
//                parentId = category.getLong("id_uppercat");
//            }
            String[] parentage = category.getString("uppercats").split(",");
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
