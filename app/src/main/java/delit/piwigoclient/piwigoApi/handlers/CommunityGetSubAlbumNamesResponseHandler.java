package delit.piwigoclient.piwigoApi.handlers;

import android.util.LongSparseArray;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.http.RequestParams;
import delit.piwigoclient.util.ArrayUtils;

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
            if (nameElem != null && !nameElem.isJsonNull()) {
                name = category.get("name").getAsString();
            }

//            if (!"null".equals(category.get("id_uppercat").getAsString())) {
//                parentId = category.get("id_uppercat").getAsLong();
//            }
            String[] parentage = category.get("uppercats").getAsString().split(",");
            CategoryItemStub parentAlbum = getParentBuildingTreeIfNeeded(availableGalleriesMap, parentage);
            CategoryItemStub album = new CategoryItemStub(name, id);
            album.setParentageChain(parentAlbum.getParentageChain(), parentAlbum.getId());
            int insertPosition = availableGalleries.size();
            while (parentAlbum != null && !availableGalleries.contains(parentAlbum)) {
                availableGalleries.add(insertPosition, parentAlbum);
                if (parentAlbum.getParentId() == null) {
                    break;
                }
                parentAlbum = availableGalleriesMap.get(parentAlbum.getParentId());
            }
            availableGalleries.add(album);
            availableGalleriesMap.put(album.getId(), album);
        }
        availableGalleries.remove(CategoryItemStub.ROOT_GALLERY);
        PiwigoCommunityGetSubAlbumNamesResponse r = new PiwigoCommunityGetSubAlbumNamesResponse(getMessageId(), getPiwigoMethod(), availableGalleries);
        storeResponse(r);
    }

    private CategoryItemStub getParentBuildingTreeIfNeeded(LongSparseArray<CategoryItemStub> albumsParsed, String[] parentageArr) {
        if (parentageArr == null || parentageArr.length == 1) {
            return CategoryItemStub.ROOT_GALLERY;
        }
        List<Long> treeNodes = ArrayUtils.toList(ArrayUtils.getLongArray(parentageArr));

        List<CategoryItemStub> parentAlbums = new ArrayList<>(treeNodes.size());
        treeNodes.remove(treeNodes.size() - 1);
        for (int i = treeNodes.size(); i > 0; i--) {
            Long parentId = treeNodes.remove(treeNodes.size() - 1);
            if (albumsParsed.indexOfKey(parentId) < 0) {
                CategoryItemStub album = new CategoryItemStub(getContext().getString(R.string.inaccessible_remote_folder), parentId);
                album = album.markNonUserSelectable();
                if (treeNodes.size() == 0) {
                    album.setParentageChain(CategoryItem.ROOT_ALBUM.getParentageChain(), CategoryItem.ROOT_ALBUM.getId());
                } else {
                    album.setParentageChain(new ArrayList<>(treeNodes));
                }
                parentAlbums.add(0, album);
            }
        }
        if (albumsParsed.size() == 0) {
            parentAlbums.add(0, CategoryItemStub.ROOT_GALLERY);
        }

        for (CategoryItemStub parentAlbum : parentAlbums) {
            albumsParsed.put(parentAlbum.getId(), parentAlbum);
        }
        return albumsParsed.get(Long.valueOf(parentageArr[parentageArr.length - 2]));
    }

    public static class PiwigoCommunityGetSubAlbumNamesResponse extends AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse {

        public PiwigoCommunityGetSubAlbumNamesResponse(long messageId, String piwigoMethod, ArrayList<CategoryItemStub> albumNames) {
            super(messageId, piwigoMethod, albumNames);
        }
    }
}
